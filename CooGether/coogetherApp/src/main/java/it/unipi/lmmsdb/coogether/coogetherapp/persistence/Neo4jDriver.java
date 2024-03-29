package it.unipi.lmmsdb.coogether.coogetherapp.persistence;

import it.unipi.lmmsdb.coogether.coogetherapp.bean.Recipe;
import it.unipi.lmmsdb.coogether.coogetherapp.bean.User;
import it.unipi.lmmsdb.coogether.coogetherapp.config.ConfigurationParameters;
import org.neo4j.driver.Record;
import org.neo4j.driver.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;

public class Neo4jDriver{

    private Driver driver;
    private final String uri;
    private final String user;
    private final String password;

    private static Neo4jDriver instance = null;

    private Neo4jDriver()
    {
        uri = "neo4j://" + ConfigurationParameters.getNeo4jIp() + ":" + ConfigurationParameters.getNeo4jPort();
        this.user = ConfigurationParameters.getNeo4jUsername();
        this.password = ConfigurationParameters.getNeo4jPassword();
        openConnection();
    }

    public static Neo4jDriver getInstance()
    {
        if (instance == null)
        {
            instance = new Neo4jDriver();
        }
        return instance;
    }


    private void openConnection() {
        try {
            driver = GraphDatabase.driver( uri, AuthTokens.basic( user, password ) );
        }catch (Exception ex){
            System.out.println("Impossible open connection with Neo4j");
        }
    }

    public void closeConnection() {
        try{
            driver.close();
        }catch (Exception ex){
            System.out.println("Impossible close connection with Neo4j");
        }
    }


    //******************************************************************************************************************
    //                              CRUD OPERATIONS
    //******************************************************************************************************************

    //ogni volta che si elimina un nodo, ricordarsi di eliminare anche i relativi archi

    public boolean addRecipe(Recipe r){

        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx ->{
                tx.run("match (u:User) where u.id=$usId CREATE (r:Recipe {id:$recId, name:$title, category:$recCat, datePublished:$datePublished}), (u)-[:ADDS]->(r)",
                        Values.parameters(
                                "usId", r.getAuthorId(),
                                "recId", r.getRecipeId(),
                                "title", r.getName(),
                                "recCat", r.getCategory(),
                                "datePublished", r.getDatePublished().toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                );

                return null;
            });
        }catch(Exception ex){
            ex.printStackTrace();

            return false;
        }
        return true;
    }

    //change the title of one recipe
    public boolean updateRecipe(Recipe r){
        Recipe old = getRecipeFromId(r.getRecipeId());
        if(deleteRecipe(r)){
            if(addRecipe(r))
                return true;
            else{
                addRecipe(old);
                return false;
            }
        }
        else
            return false;


    }

    public boolean deleteRecipe(Recipe r){
        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run( "MATCH (r:Recipe) " +
                                "WHERE r.id=$id " +
                                "DETACH DELETE r",
                        Values.parameters( "id", r.getRecipeId()) );
                return null;
            });
            return true;

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
    }

    public boolean deleteRecipesOfAUser(User u){
        try(Session session= driver.session()){
            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run( "MATCH (u:User)-->(r:Recipe) " +
                                "WHERE u.id=$id " +
                                "DETACH DELETE r",
                        Values.parameters( "id", u.getUserId()) );
                return null;
            });
        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }


    public boolean addUser(User u){
        try(Session session= driver.session()){
            session.writeTransaction((TransactionWork<Void>) tx -> {
                String[] names = u.getFullName().split(" ");
                tx.run ("CREATE (u:User {id: $id, username: $username, firstName: $firstName, lastName: $lastName, email: $email, password: $password})",
                Values.parameters("id", u.getUserId(), "username", u.getUsername(), "firstName", names[0], "lastName", names[1], "email", u.getEmail(), "password", u.getPassword()));
                return null;
            } );

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    //modify all the parameters
    public boolean updateUser(User u){
        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run ( "match (u:User {id:$id}) " +
                                "set u.email=$email, u.fullname=$fullName, u.password=$pass, u.username=$userName",
                        Values.parameters("email", u.getEmail(), "fullName", u.getFullName(), "pass",
                                u.getPassword(), "userName", u.getUsername(), "id", u.getUserId()));
                return null;
            } );

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean deleteUser(User u){
        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run( "MATCH (u:User) WHERE u.id=$id DETACH DELETE u",
                        Values.parameters( "id", u.getUserId()) );
                return null;
            });
            return true;

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
    }


    public boolean follow(int following, int follower){
        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run ("match (a:User) where a.id= $usera " +
                           "match (b:User) where b.id=$userb " +
                           "merge (a)-[:FOLLOWS]->(b)",
                        Values.parameters("usera", following, "userb", follower));
                return null;
            } );

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }


    public boolean unfollow(int following, int follower){
        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run ("match (a:User {id: $usera}) -[f:FOLLOWS]-> (b:User {id:$userb}) " +
                           "delete f",
                        Values.parameters("usera", following, "userb", follower));
                return null;
            } );

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    public ArrayList<User> getUsers( int skip, int limit){
        ArrayList<User> users= new ArrayList<>();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (u:User) " +
                                "return u.id, u.username, u.email, u.firstName, u.lastName " +
                                "order by u.username asc " +
                                "skip $toSkip " +
                                "limit $toLimit "
                        , Values.parameters("toSkip", skip, "toLimit",limit));

                while(result.hasNext()){
                    Record r= result.next();
                    int id = r.get("u.id").asInt();
                    String username = r.get("u.username").asString();
                    String email = r.get("u.email").asString();
                    String fullName = r.get("u.firstName").asString() + " " + r.get("u.lastName").asString();
                    User user= new User(id, username, fullName, email);
                    users.add(user);
                }
                return users;
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }

        return users;
    }

    public User getUsersFromUnique(String unique){

        try(Session session= driver.session()){
            User user;
            user = session.readTransaction(tx->{
                Result result = tx.run("match (u:User) where u.username=$name or u.email=$name " +
                                "return u.id, u.password, u.email, u.username, u.firstName, u.lastName, u.role",
                        Values.parameters("name", unique));

                if (result.hasNext()){
                    Record r= result.next();
                    int id = r.get("u.id").asInt();
                    String username = r.get("u.username").asString();
                    String password = r.get("u.password").asString();
                    String email = r.get("u.email").asString();
                    String fullName = r.get("u.firstName").asString() + " " + r.get("u.lastName").asString();
                    int role;
                    if(!r.get("u.role").isNull()) {
                        role = r.get("u.role").asInt();
                    }
                    else
                        role = 0;
                    return new User(id, username, fullName, password, email, role);
                }
                return null;
            });
            return user;
        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }
    }

    public ArrayList<User> getUsersFromFullname(String name){
        ArrayList<User> users= new ArrayList<>();

        try(Session session= driver.session()){
            String[] sName= name.split(" ");
            session.readTransaction(tx->{
                Result result = tx.run("match (u:User) " +
                                          "where u.firstName = $fName and u.lastName=$lName " +
                                          "return u.id, u.email, u.username, u.firstName, u.lastName"
                        , Values.parameters("fName", sName[0], "lName", sName[1]));

                while(result.hasNext()){
                    Record r= result.next();
                    int id = r.get("u.id").asInt();
                    String username = r.get("u.username").asString();
                    String email = r.get("u.email").asString();
                    String fullName = r.get("u.firstName").asString() + " " + r.get("u.lastName").asString();
                    User user= new User(id, username, fullName, email);
                    users.add(user);
                }
                return users;
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }

        return users;
    }

    public User getUsersFromId(int id){

        try(Session session= driver.session()){
            User user;
            user = session.readTransaction(tx->{
                Result result = tx.run("match (u:User) where u.id = $id " +
                                "return u.id, u.password, u.email, u.username, u.firstName, u.lastName, u.role",
                        Values.parameters("id", id));

                if (result.hasNext()){
                    Record r= result.next();
                    int uid = r.get("u.id").asInt();
                    String username = r.get("u.username").asString();
                    String password = r.get("u.password").asString();
                    String email = r.get("u.email").asString();
                    String fullName = r.get("u.firstName").asString() + " " + r.get("u.lastName").asString();
                    int role;
                    if(!r.get("u.role").isNull()) {
                        System.out.println("role defined");
                        role = r.get("u.role").asInt();
                    }
                    else
                        role = 0;
                    return new User(uid, username, fullName, password, email, role);
                }
                return null;
            });
            return user;
        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }
    }

    public int getNFollowingUser(int id){
        AtomicInteger following= new AtomicInteger();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (u:User)-[f:FOLLOWS]->(u2:User) " +
                                          "where u.id= $id " +
                                          "return count (distinct f) as following",
                        Values.parameters("id", id));

                while(result.hasNext()){
                    Record r= result.next();
                    following.set(r.get("following").asInt());
                }
                return following.get();
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return 0;
        }
        return following.get();
    }

    public int getNFollowerUser(int id){
        AtomicInteger followers= new AtomicInteger();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (u:User)<-[f:FOLLOWS]-(u2:User) " +
                                "where u.id= $id " +
                                "return count (distinct f) as followers",
                        Values.parameters("id", id));

                while(result.hasNext()){
                    Record r= result.next();
                    followers.set(r.get("followers").asInt());
                }
                return followers.get();
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return 0;
        }
        return followers.get();
    }

    public ArrayList<User> getFollowingUsers(User u){
        ArrayList<User> users= new ArrayList<>();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (u1:User)-[f:FOLLOWS]->(u2:User) " +
                                          "where u1.id = $userId "+
                                           "return u2.id, u2.username, u2.fisrtName, u2.lastName, u2.email", Values.parameters("userId", u.getUserId()));

                while(result.hasNext()){
                    Record r= result.next();
                    int id = r.get("u2.id").asInt();
                    String username = r.get("u2.username").asString();
                    String email = r.get("u2.email").asString();
                    String fullName = r.get("u2.firstName").asString() + " " + r.get("u2.lastName").asString();
                    User user= new User(id, username, fullName, email);
                    users.add(user);
                }
                return users;
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }
        return users;
    }

    public ArrayList<User> getFollowerUsers(User u){
        ArrayList<User> users= new ArrayList<>();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (u1:User)<-[f:FOLLOWS]-(u2:User) " +
                        "where u1.id = $userId "+
                        "return u2.id, u2.username, u2.firstName, u2.lastName, u2.email", Values.parameters("userId", u.getUserId()));

                while(result.hasNext()){
                    Record r= result.next();
                    int id = r.get("u2.id").asInt();
                    String username = r.get("u2.username").asString();
                    String email = r.get("u2.email").asString();
                    String fullName = r.get("u2.firstName").asString() + " " + r.get("u2.lastName").asString();
                    User user= new User(id, username, fullName, email);
                    users.add(user);
                }
                return users;
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }
        return users;
    }

    public ArrayList<Recipe> getRecipes(int skip, int limit){
        ArrayList<Recipe> recipes= new ArrayList<>();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (r:Recipe) where r.id IS NOT NULL and (r.name is not null or r.name <> 'null') " +
                                          "return r.id, r.name, r.datePublished, r.category order by r.datePublished desc " +
                                          "skip $toSkip " +
                                          "limit $toLimit"
                                          , Values.parameters("toLimit",limit, "toSkip", skip));

                while(result.hasNext()){
                    Record r= result.next();
                    int id = r.get("r.id").asInt();
                    String name = r.get("r.name").asString();
                    Date date;
                    if(!r.get("r.datePublished").isNull()){
                        date = java.util.Date.from(r.get("r.datePublished").asLocalDate()
                            .atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
                    }
                    else
                        date = new Date();
                    String category = r.get("r.category").asString();
                    Recipe recipe= new Recipe(id, name, date, category);
                    recipes.add(recipe);
                }
                return recipes;
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }
        return recipes;
    }

    public Recipe getRecipeFromId(int id){
        ArrayList<Recipe> recipes= new ArrayList<>();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (r:Recipe) where r.id = $id " +
                                "return r.id, r.name, r.datePublished, r.category order by r.datePublished desc " +
                                "limit 1"
                        , Values.parameters("id", id));

                while(result.hasNext()){
                    Record r= result.next();
                    int rid = r.get("r.id").asInt();
                    String name = r.get("r.name").asString();
                    Date date;
                    if(!r.get("r.datePublished").isNull()){
                        date = java.util.Date.from(r.get("r.datePublished").asLocalDate()
                                .atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
                    }
                    else
                        date = new Date();
                    String category = r.get("r.category").asString();
                    Recipe recipe= new Recipe(rid, name, date, category);
                    recipes.add(recipe);
                }
                return recipes;
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }
        if(recipes.size() > 0)
            return recipes.get(0);
        else
            return null;
    }

    public ArrayList<String> getAllCategories(){
        ArrayList<String> cat=new ArrayList<>();
        try(Session session=driver.session()){
            session.readTransaction(tx->{
                Result result=tx.run("match (r:Recipe) " +
                        "return distinct r.category order by r.category");
                while (result.hasNext())
                {
                    Record r=result.next();
                    String catString=r.get("r.category").asString();
                    cat.add(catString);
                }
                return cat;
            });
        }
        catch (Exception ex){
            ex.printStackTrace();
            return null;
        }
        return cat;
    }


    public Boolean makeAdmin( User u){
        try(Session session= driver.session()){
            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run ("match (u:User {id:$id}) " +
                        "set u.role=1 " +
                        "return u.role", Values.parameters("id", u.getUserId()));
                return null;
            } );

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean makeNotAdmin( User u){
        try(Session session= driver.session()){
            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run ("match (u:User {id: $id}) " +
                        "set u.role=0 " +
                        "return u.role", Values.parameters("id", u.getUserId()));
                return null;
            } );

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    //******************************************************************************************************************
    //                              ANALYTICS
    //******************************************************************************************************************

    public ArrayList<Recipe> searchSuggestedRecipes(int skip, int howMany, int userId){
        ArrayList<Recipe> recipes= new ArrayList<>();

        try(Session session= driver.session()){
            session.readTransaction(tx->{
                Result result = tx.run("match (u:User)-[f:FOLLOWS]->(u2:User) " +
                                "match (r:Recipe)<-[a:ADDS]-(u2) " +
                                "where u.id=$userId " +
                                "return r.id, r.name, r.category, r.datePublished order by r.datePublished desc " +
                                "skip $skip limit $limit",
                        Values.parameters("userId", userId, "skip", skip, "limit", howMany));

                while(result.hasNext()){
                    Record r= result.next();
                    int id= r.get("r.id").asInt();
                    String name = r.get("r.name").asString();
                    String category = r.get("r.category").asString();
                    Date date;
                    if(!r.get("r.datePublished").isNull()){
                        date = java.util.Date.from(r.get("r.datePublished").asLocalDate()
                                .atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
                    }
                    else
                        date = new Date();
                    Recipe rec= new Recipe(id, name, date, category);
                    recipes.add(rec);
                }
                return recipes;
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }

        return recipes;
    }

    public ArrayList<User> mostFollowedUsers(int limit){
        ArrayList<User> users = new ArrayList<>();

        try ( Session session = driver.session() ) {
            session.readTransaction( tx -> {Result result = tx.run("match (u:User)<-[f:FOLLOWS]-(:User) " +
                                                                      "return u.id, u.username, u.email, u.firstName, u.lastName, count(DISTINCT f) as follower " +
                                                                      "order by follower desc " +
                                                                      "limit $l",
                                                            Values.parameters( "l", limit) );

                while(result.hasNext()){
                    Record r = result.next();
                    int id = r.get("u.id").asInt();
                    String username = r.get("u.username").asString();
                    String email = r.get("u.email").asString();
                    String fullName = r.get("u.firstName").asString() + " " + r.get("u.lastName").asString();
                    User user= new User(id, username, fullName, email);

                    users.add(user);
                }

                return users;
            });
        } catch (Exception ex){
            ex.printStackTrace();
            return null;
        }
        return users;
    }

    public ArrayList<User> getMostActiveUsers(int k){
        ArrayList<User> users = new ArrayList<>();

        try(Session session = driver.session()){
            session.readTransaction( tx -> {Result result = tx.run("match (user:User) --> (x:Recipe) " +
                          			                                  "return user.id, user.username, user.email, user.firstName, user.lastName, count(x) " +
                            			                              "order by count(x)" +
                           				                              "limit $k",
                			                                Values.parameters("k", k) );

                while(result.hasNext()){
                    Record r = result.next();
                    int id = r.get("user.id").asInt();
                    String username = r.get("user.username").asString();
                    String email = r.get("user.email").asString();
                    String fullName = r.get("user.firstName").asString() + " " + r.get("user.lastName").asString();
                    User user= new User(id, username, fullName, email);
               		users.add(user);
                }
           		return users;
            });
        }catch(Exception ex){
            ex.printStackTrace();
            return null;
        }
        return users;
    }

}
