package it.unipi.lmmsdb.coogether.coogetherapp.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.mongodb.client.model.*;
import it.unipi.lmmsdb.coogether.coogetherapp.bean.Comment;
import it.unipi.lmmsdb.coogether.coogetherapp.bean.Recipe;
import it.unipi.lmmsdb.coogether.coogetherapp.bean.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.mongodb.client.*;
import com.mongodb.ConnectionString;
import static com.mongodb.client.model.Aggregates.*;
import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Projections.*;
import it.unipi.lmmsdb.coogether.coogetherapp.config.ConfigurationParameters;
import it.unipi.lmmsdb.coogether.coogetherapp.pojo.RecipePojo;
import it.unipi.lmmsdb.coogether.coogetherapp.utils.Utils;
import org.bson.BsonArray;
import org.bson.BsonString;
import org.bson.Document;
import org.bson.conversions.Bson;
import java.util.ArrayList;
import java.util.Arrays;

public class MongoDBDriver{

    private static MongoClient myClient;
    private static MongoDatabase db;
    private static MongoCollection<org.bson.Document> collection;

    private static final String connectionString = "mongodb://" + ConfigurationParameters.getMongoFirstIp() + ":" + ConfigurationParameters.getMongoFirstPort() +
            "," + ConfigurationParameters.getMongoSecondIp() + ":" + ConfigurationParameters.getMongoSecondPort() +
            "," + ConfigurationParameters.getMongoThirdIp() + ":" + ConfigurationParameters.getMongoThirdPort() +
            "/?retryWrites=true&w=3&wtimeoutMS=5000&readPreference=nearest";

    private static final ConnectionString uri= new ConnectionString(connectionString);

    //returns recipes given a list of mongoDB documents
    private static ArrayList<Recipe> getRecipesFromDocuments(ArrayList<Document> results){

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ArrayList<Recipe> recipes = new ArrayList<>();
        try {
            for (Document doc : results) {
                RecipePojo pojo = objectMapper.readValue(doc.toJson(), RecipePojo.class);
                Recipe recipe = Utils.mapRecipe(pojo);
                recipes.add(recipe);
            }

            return recipes;

        }catch(Exception e){
            e.printStackTrace();
            return null;
        }
    }

    private static void openConnection(){
        try{
            myClient= MongoClients.create(uri);
            db= myClient.getDatabase(ConfigurationParameters.getMongoDbName());
            collection= db.getCollection("recipe");
        }catch (Exception ex){
            System.out.println("Impossible open connection with MongoDB");
        }
    }

    private static void openUtilConnection(){
        try{
            myClient= MongoClients.create(uri);
            db= myClient.getDatabase(ConfigurationParameters.getMongoDbName());
            collection= db.getCollection("utility");
        }catch (Exception ex){
            System.out.println("Impossible open connection with MongoDB");
        }
    }


    private static void closeConnection(){
        try{
            myClient.close();
        }catch (Exception ex){
            System.out.println("Impossible close connection with MongoDB");
        }
    }

    //******************************************************************************************************************
    //                              CRUD OPERATIONS
    //******************************************************************************************************************

    public static boolean addRecipe(Recipe r){
        openConnection();

        try{

            Document doc= new Document();
            doc.append("recipeId", r.getRecipeId());
            doc.append("name", r.getName());
            doc.append("authorId", r.getAuthorId());
            doc.append("authorName", r.getAuthorName());
            if(r.getCookTime() != -1)
                doc.append("cookTime", r.getCookTime());
            if(r.getPrepTime()!=-1)
                doc.append("prepTime", r.getPrepTime());
            doc.append("datePublished", r.getDatePublished());
            doc.append("description", r.getDescription());
            doc.append("image", r.getImage());
            doc.append("recipeCategory", r.getCategory());
            doc.append("ingredients", r.getIngredients());
            ArrayList<Document> commentsToAdd = new ArrayList<>();
            for(Comment c: r.getComments()){
                Document d = new Document("commentId", c.getCommentId())
                        .append("authorId", c.getAuthorId())
                        .append("rating", c.getRating())
                        .append("authorName", c.getAuthorName())
                        .append("comment", c.getText())
                        .append("dateSubmitted", c.getDatePublished());
                commentsToAdd.add(d);
            }
            doc.append("comments", commentsToAdd);
            if(r.getCalories()!=-1)
                doc.append("calories", r.getCalories());
            if(r.getFatContent()!=-1)
                doc.append("fatContent", r.getFatContent());
            if(r.getSodiumContent()!=-1)
                doc.append("sodiumContent", r.getSodiumContent());
            if(r.getProteinContent()!=-1)
                doc.append("proteinContent", r.getProteinContent());
            if(r.getRecipeServings()!=-1)
                doc.append("recipeServings", r.getRecipeServings());
            doc.append("recipeInstructions", r.getRecipeInstructions());

            collection.insertOne(doc);

        }catch(Exception ex){
            closeConnection();
            return false;
        }
        closeConnection();
        return true;
    }

    //delete the recipe and add the modified recipe
    public static boolean updateRecipe(Recipe r){
        openConnection();
        try{
            boolean res=deleteRecipe(r);
            if(!res)
            {
                System.out.println("A problem has occurred in modify recipe");
                return false;
            }

            res= addRecipe(r);
            if(!res)
            {
                System.out.println("A problem has occurred in modify recipe");
                return false;
            }

        }catch(Exception ex){
            closeConnection();
            return false;
        }
        closeConnection();
        return true;
    }

    public static boolean deleteRecipe(Recipe r){
        openConnection();
        try{
            collection.deleteOne(Filters.eq("recipeId", r.getRecipeId()));
        }catch(Exception ex){
            closeConnection();
            return false;
        }
        closeConnection();
        return true;
    }

    public static boolean addComment(Recipe r, Comment c){
        openConnection();
        System.out.println(r.getRecipeId());
        try{
            Document com = new Document("commentId", c.getCommentId())
                    .append("authorId", c.getAuthorId())
                    .append("rating", c.getRating())
                    .append("authorName", c.getAuthorName())
                    .append("comment", c.getText())
                    .append("dateSubmitted", c.getDatePublished());

            Bson filter = Filters.eq( "recipeId", r.getRecipeId() ); //get the parent-document
            Bson setUpdate;
            if(r.getComments() != null && r.getComments().size() > 0)
                setUpdate = Updates.push("comments", com);
            else {
                ArrayList<Document> comList = new ArrayList<>();
                comList.add(com);
                setUpdate = Updates.set("comments", comList);
            }

            collection.updateOne(filter, setUpdate);

        }catch(Exception ex){
            closeConnection();
            return false;
        }
        closeConnection();
        return true;
    }

    public static boolean deleteRecipesOfAUser(User u){
        openConnection();
        try{
            collection.deleteMany(Filters.eq("authorId", u.getUserId()));
        }catch(Exception ex){
            closeConnection();
            return false;
        }
        closeConnection();
        return true;
    }

    public static Recipe getRecipesFromId( int id){
        openConnection();
        Recipe recipe;
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ArrayList<Document> myDoc = new ArrayList<>();
        try{
            for(Document doc: collection.find(eq("recipeId", id))){
                System.out.println(doc.toString());
                myDoc.add(doc);
            }
            RecipePojo pojo = objectMapper.readValue(myDoc.get(0).toJson(), RecipePojo.class);
            recipe = Utils.mapRecipe(pojo);
            closeConnection();
            return recipe;
        } catch (Exception e) {
            e.printStackTrace();
            closeConnection();
            return null;
        }
    }

    public static ArrayList<Recipe> getRecipesFromAuthorName(String username){
        ArrayList<Document> results;

        openConnection();
        Bson myMatch = Aggregates.match(Filters.eq("authorName", username));
        Bson mySort = sort(Sorts.descending("datePublished"));
        Bson projection = Aggregates.project( fields(excludeId(), include("recipeId","name", "authorName", "recipeCategory", "datePublished")));

        results = collection.aggregate(Arrays.asList(myMatch, mySort, projection))
                .into(new ArrayList<>());

        closeConnection();
        return getRecipesFromDocuments(results);
    }

    public static ArrayList<Recipe> getRecipesFromCategory(String category){
        ArrayList<Document> results;

        openConnection();
        Bson myMatch = Aggregates.match(Filters.eq("recipeCategory", category));
        Bson mySort = sort(Sorts.descending("datePublished"));
        Bson projection= Aggregates.project(fields(excludeId(), include("recipeId", "name", "authorName", "recipeCategory", "datePublished")));

        results = collection.aggregate(Arrays.asList(myMatch,mySort, projection)).into(new ArrayList<>());

        closeConnection();
        return getRecipesFromDocuments(results);
    }

    public static ArrayList<Recipe> getRecipesFromTwoIngredients(String ing1, String ing2){
        ArrayList<Document> results;

        openConnection();
        String pattern1=".*" + ing1 +".*";
        String pattern2=".*" + ing2 +".*";
        Bson myMatch_1= Aggregates.match(Filters.regex("ingredients", pattern1));
        Bson myMatch_2= Aggregates.match(Filters.regex("ingredients", pattern2));
        Bson projection= Aggregates.project(fields(excludeId(), include("recipeId", "name", "authorName", "recipeCategory", "datePublished")));

        results = collection.aggregate(Arrays.asList(myMatch_1,myMatch_2, projection)).into(new ArrayList<>());

        closeConnection();
        return getRecipesFromDocuments(results);
    }

    public static int getMaxCommentId(){
        ArrayList<Document> results;

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        openUtilConnection();
        results = collection.find(eq("name", "maxID")).into(new ArrayList<>());
        closeConnection();
        try {
            return results.get(0).getInteger("comment");
        }catch(Exception e){
            e.printStackTrace();
            return 0;
        }
    }

    public static int getMaxRecipeId(){
        ArrayList<Document> results;

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        openUtilConnection();
        results = collection.find(eq("name", "maxID")).into(new ArrayList<>());
        closeConnection();
        try {
            return results.get(0).getInteger("recipe");
        }catch(Exception e){
            e.printStackTrace();
            return 0;
        }
    }

    public static int getMaxUserId(){
        ArrayList<Document> results;

        openUtilConnection();
        results = collection.find(eq("name", "maxID")).into(new ArrayList<>());
        closeConnection();
        try {
            return results.get(0).getInteger("user");
        }catch(Exception e){
            e.printStackTrace();
            return 0;
        }
    }

    public static void setMaxUserId(int uid){
        openUtilConnection();
        collection.updateOne(eq("name", "maxID"), Updates.set("user", uid));
        closeConnection();
    }

    public static void setMaxRecipeId(int uid){
        openUtilConnection();
        collection.updateOne(eq("name", "maxID"), Updates.set("recipe", uid));
        closeConnection();
    }

    public static void setMaxCommentId(int uid){
        openUtilConnection();
        collection.updateOne(eq("name", "maxID"), Updates.set("comment", uid));
        closeConnection();
    }





    //******************************************************************************************************************
    //                              ANALYTICS
    //******************************************************************************************************************

    public static ArrayList<Recipe> searchTopKHealthiestRecipes(String category, int k){
        ArrayList<Document> results;

        openConnection();
        Bson m = Aggregates.match(Filters.eq("recipeCategory", category));
        Bson m1 = Aggregates.match(Filters.exists("calories", true));
        Bson m2 = Aggregates.match(Filters.exists("fatContent", true));
        Bson m3 = Aggregates.match(Filters.exists("sodiumContent", true));
        Bson m4 = Aggregates.match(Filters.exists("proteinContent", true));
        Bson s= sort(Sorts.ascending("calories"));
        Bson s1= sort(Sorts.ascending("fatContent"));
        Bson s2= sort(Sorts.ascending("sodiumContent"));
        Bson s3= sort(Sorts.descending("proteinContent"));
        Bson l= limit(k);

        results = collection.aggregate(Arrays.asList(m, m1, m2, m3, m4, s, s1, s2, s3, l)).into(new ArrayList<>());

        closeConnection();
        return getRecipesFromDocuments(results);
    }

    public static ArrayList<Recipe> searchTopKReviewedRecipes(String category, int k){
        ArrayList<Document> results;

        openConnection();
        Bson myMatch_1 = Aggregates.match(Filters.eq("recipeCategory", category));
        Bson myUnwind = unwind("$comments");
        Bson myMatch_2 = Aggregates.match(Filters.eq("comments.rating", 5));
        Bson myGroup = new Document("$group", new Document("_id", new Document("recipeId","$recipeId")
                .append("name", "$name")
                .append("recipeCategory", "$recipeCategory")
                .append("datePublished", "$datePublished")
                .append("authorName", "$authorName"))
                .append("count", new Document("$sum", 1)));
        Bson mySort = sort(Sorts.descending("count"));
        Bson myLimit = limit(k);

        results = collection.aggregate(Arrays.asList(myMatch_1,myUnwind,myMatch_2,myGroup,mySort,myLimit))
                .into(new ArrayList<>());

        closeConnection();
        ArrayList<Document> recipeValues = new ArrayList<>();
        for(Document doc: results){
            recipeValues.add((Document) doc.get("_id"));
        }

        return getRecipesFromDocuments(recipeValues);
    }

    public static ArrayList<Recipe> searchFastestRecipes(String category, int k){
        ArrayList<Document> results;

        openConnection();
        Bson m1 = Aggregates.match(Filters.eq("recipeCategory", category));
        Bson m2 = Aggregates.match(Filters.exists("cookTime", true));
        Bson m3 = Aggregates.match(Filters.exists("prepTime", true));
        Bson s1 = sort(Sorts.ascending("cookTime"));
        Bson s2 = sort(Sorts.ascending("prepTime"));
        Bson myLimit = limit(k);

        results = collection.aggregate(Arrays.asList(m1, m2, m3, s1, s2, myLimit)).into(new ArrayList<>());

        closeConnection();
        return getRecipesFromDocuments(results);
    }

    public static ArrayList<Recipe> searchFewestIngredientsRecipes(String category, int k){
        ArrayList<Document> results;

        openConnection();
        Bson m1 = Aggregates.match(Filters.eq("recipeCategory", category));
        Bson u= unwind("$ingredients");
        Bson g= new Document("$group", new Document("_id", new Document("recipeId","$recipeId")
                    .append("name", "$name")
                    .append("recipeCategory", "$recipeCategory")
                    .append("datePublished", "$datePublished")
                    .append("authorName", "$authorName"))
                .append("numberOfIngredients", new Document("$sum", 1)));
        Bson s= sort(Sorts.ascending("numberOfIngredients"));
        Bson myLimit = limit(k);

        results = collection.aggregate(Arrays.asList(m1, u, g, s, myLimit)).into(new ArrayList<>());
        closeConnection();

        ArrayList<Document> recipeValues = new ArrayList<>();
        for(Document doc: results){
            recipeValues.add((Document) doc.get("_id"));
        }

        return getRecipesFromDocuments(recipeValues);
    }

    public static ArrayList<User> userRankingSystem(int k){
        ArrayList<User> users;
        ArrayList<Document> results;

        openConnection();
        Bson unwind_comments = unwind("$comments");
        Bson group1 = new Document("$group", new Document("_id",
                	                                        new Document("recipe", "$recipeId")
                                                            .append("author", "$authorId"))
        	                                                .append("avgRating", new Document("$avg", "$comments.rating")));
        Bson group2 = Aggregates.group("$_id.author", Accumulators.avg("avgRating", "$avgRating"));
        Bson sort_by_rating = sort(Sorts.descending("avgRating"));
        Bson limitResults = limit(k);

        results= collection.aggregate(Arrays.asList(unwind_comments, group1, group2, sort_by_rating, limitResults))
                .into(new ArrayList<>());
        closeConnection();
        Neo4jDriver neo4j = Neo4jDriver.getInstance();
        if(results.size() == 0)
            return null;
        users = new ArrayList<>();
        for(Document d: results){
            User user = neo4j.getUsersFromId(d.getInteger("_id"));
            users.add(user);
        }
        return users;
    }

    public static ArrayList<Recipe> searchHighestLifespanRecipes(int k){
        ArrayList<Document> results;

        openConnection();
        Bson unwind_comments = unwind("$comments");
        Bson group1 = new Document("$group", new Document("_id", new Document("recipeId","$recipeId")
                                .append("name", "$name")
                                .append("recipeCategory", "$recipeCategory")
                                .append("datePublished", "$datePublished")
                                .append("authorName", "$authorName"))
        	                .append("mostRecentComment", new Document("$max", "$comments.dateModified"))
        	                .append("leastRecentComment",new Document("$min", "$comments.dateModified")));
        BsonArray operands = new BsonArray();
        operands.add(new BsonString("$mostRecentComment"));
        operands.add(new BsonString("$leastRecentComment"));
        Bson project_lifespan = Aggregates.project(fields(excludeId(), include("_id"),
                	        new Document("lifespan", new Document("$subtract", operands))));
        Bson sort_by_lifespan = sort(Sorts.descending("lifespan"));
        Bson limitResults = limit(k);

        results= collection.aggregate(Arrays.asList(unwind_comments, group1, project_lifespan, sort_by_lifespan,
                limitResults)).into(new ArrayList<>());
        closeConnection();
        ArrayList<Document> recipeValues = new ArrayList<>();
        for(Document doc: results){
            recipeValues.add((Document) doc.get("_id"));
        }

        return getRecipesFromDocuments(recipeValues);
    }


}
