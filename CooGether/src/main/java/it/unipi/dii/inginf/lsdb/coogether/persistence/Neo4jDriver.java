package it.unipi.dii.inginf.lsdb.coogether.persistence;

import it.unipi.dii.inginf.lsdb.coogether.bean.Recipe;
import it.unipi.dii.inginf.lsdb.coogether.bean.User;
import org.neo4j.driver.Record;
import org.neo4j.driver.*;

import java.util.ArrayList;
import java.util.List;

import static org.neo4j.driver.Values.parameters;

public class Neo4jDriver implements DatabaseDriver{

    private Driver driver;
    private String uri="neo4j://localhost:7687";
    private String user= "neo4j";
    private String password= "root";

    @Override
    public boolean openConnection() {
        try {
            driver = GraphDatabase.driver( uri, AuthTokens.basic( user, password ) );
        }catch (Exception ex){
            System.out.println("Impossible open connection with Neo4j");
            return false;
        }
        return true;
    }

    @Override
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
                tx.run("match (u:User) where u.id=$usId CREATE (r:Recipe {id:$recId, name:$title}), (u)-[:ADDS]->(r)",
                        parameters("usId", r.getAuthorId(),"recId", r.getRecipeId(), "title", r.getName()));

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
        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx ->{
                tx.run("match(r:Recipe {id:$recId}) set r.name=$newName",
                        parameters("recId", r.getRecipeId(), "newName", r.getName()));

                return null;
            });

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean deleteRecipe(Recipe r){
        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run( "MATCH (r:Recipe) " +
                                "WHERE r.id=$id " +
                                "DETACH DELETE r",
                        parameters( "id", r.getRecipeId()) );
                return null;
            });
            return true;

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
    }

    //decidere se quando viene eliminato un utente devono essere eliminate tutte le sue ricette
    /*public boolean deleteRecipesOfAUser(User u){
        try(Session session= driver.session()){

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }*/

    //da implementare
    public boolean addUser(User u){
        try(Session session= driver.session()){
            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run ("CREATE (u:User {u.id: $id, u.username: $username, u.fullname: $fullname, u.email: $email, u.password: $password})",
                parameters("id", u.getUserId(), "username", u.getUsername(), "fullname", u.getFullName(), "email", u.getEmail(), "password", u.getPassword()));
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
                tx.run ( "match (u:User {id:1234})" +
                                "set u.email=$email, u.fullname=$fullName, u.password=$pass, u.username=$userName",
                        parameters("email", u.getEmail(), "fullName", u.getFullName(), "pass",
                                u.getPassword(), "userName", u.getUsername()));
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
                        parameters( "id", u.getUserId()) );
                return null;
            });
            return true;

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
    }

    //da implementare
    public boolean follow(int following, int follower){
        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run ("match (a:User) where a.id= $usera" +
                           "match (b:User) where b.id=$userb" +
                           "merge (a)-[:FOLLOWS]->(b)",
                        parameters("usera", following, "userb", follower));
                return null;
            } );

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    //da implementare
    public boolean unfollow(int following, int follower){
        try(Session session= driver.session()){

            session.writeTransaction((TransactionWork<Void>) tx -> {
                tx.run ("match (a:User {a.id: $usera}) -[f:FOLLOWS]-> (b:User {b.id:$userb})" +
                           "delete f",
                        parameters("usera", following, "userb", follower));
                return null;
            } );

        }catch(Exception ex){
            ex.printStackTrace();
            return false;
        }
        return true;
    }

    public List<User> getUsers(){
        List<User> users= new ArrayList<>();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (u:User)" +
                                "return u");

                while(result.hasNext()){
                    Record r= result.next();
                    int id = r.get("u.id").asInt();
                    String username = r.get("u.username").asString();
                    User user= new User(id, username);
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

    public List<User> getFollowedUsers(User u){
        List<User> users= new ArrayList<>();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (u1:User)-[f:FOLLOWS]->(u2:User)" +
                                          "where u1.id = $userId"+
                                           "return u2", parameters("userId", u.getUserId()));

                while(result.hasNext()){
                    Record r= result.next();
                    int id = r.get("u2.id").asInt();
                    String username = r.get("u2.username").asString();
                    User user= new User(id, username);
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

    //******************************************************************************************************************
    //                              ANALYTICS
    //******************************************************************************************************************

    public List<Recipe> searchSuggestedRecipes(String userId){
        List<Recipe> recipes= new ArrayList<>();

        try(Session session= driver.session()){

            session.readTransaction(tx->{
                Result result = tx.run("match (u:User)-[f:FOLLOWS]->(u2:User)" +
                                "match (r:Recipe)<-[a:ADDS]-(u2)" +
                                "where u.id=$userId" +
                                "return r, u, u2, f, a",
                        parameters("userId", userId));

                while(result.hasNext()){
                    Record r= result.next();
                    int id= r.get("r.id").asInt();
                    String name = r.get("r.name").asString();
                    Recipe rec= new Recipe(id, name);
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

    public List<User> mostFollowedUsers(int limit){
        ArrayList<User> users = new ArrayList<>();

        try ( Session session = driver.session() ) {
            session.readTransaction( tx -> {Result result = tx.run("match (u:User)<-[f:FOLLOWS]-(:User) " +
                                                                      "return u.id, u.username, count(DISTINCT f) as follower " +
                                                                      "order by follower desc " +
                                                                      "limit $l",
                                                            parameters( "l", limit) );

                while(result.hasNext()){
                    Record r = result.next();
                    int id= r.get("u.id").asInt();
                    String name= r.get("u.username").asString();
                    int followers= r.get("follower").asInt();
                    User u= new User(id, name, followers);

                    users.add(u);
                }

                return users;
            });
        } catch (Exception ex){
            ex.printStackTrace();
            return null;
        }
        return users;
    }

    public List<User> getMostActiveUsers(int k){
        List<User> users = new ArrayList<>();

        try(Session session = driver.session()){
            session.readTransaction( tx -> {Result result = tx.run("match (user:User) --> (x:Recipe)" +
                          			                                  "return user, count(x)" +
                            			                              "order by count(x)" +
                           				                              "limit $k",
                			                                parameters("k", k) );

                while(result.hasNext()){
                    Record r = result.next();
                    int id = r.get("user.id").asInt();
               		String username = r.get("user.username").asString();
                    User u = new User(id, username);
               		users.add(u);
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
