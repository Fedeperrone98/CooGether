package it.unipi.lmmsdb.coogether.coogetherapp.controller;

import it.unipi.lmmsdb.coogether.coogetherapp.bean.Recipe;
import it.unipi.lmmsdb.coogether.coogetherapp.bean.User;
import it.unipi.lmmsdb.coogether.coogetherapp.config.SessionUtils;
import it.unipi.lmmsdb.coogether.coogetherapp.persistence.Neo4jDriver;
import it.unipi.lmmsdb.coogether.coogetherapp.utils.Utils;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.input.MouseEvent;

import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ResourceBundle;

public class HelloController implements Initializable {

    @FXML
    private VBox recipeContainer;

    @FXML private Button signIn;
    @FXML private Button signUp;
    @FXML private TextField email;
    @FXML private TextField password;

    private int skip = 0;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // retrieve first 20 recipes
        showRecipes();


    }

    //Called by the show more button
    private void showMoreRecipes(){
        recipeContainer.getChildren().remove(recipeContainer.getChildren().size() - 1);
        showRecipes();

    }

    //Create the show more button
    private void createShowMore(){
        Button showMore = new Button("Show more");
        showMore.setOnAction(actionEvent -> showMoreRecipes());
        recipeContainer.getChildren().add(showMore);
    }

    //Adds 20 recipes in the scene and places the show more button
    private void showRecipes(){
        Neo4jDriver neo4j = Neo4jDriver.getInstance();
        ArrayList<Recipe> recipes;
        User logged = SessionUtils.getUserLogged();
        if(logged == null)
            recipes = neo4j.getRecipes(skip, 20);
        else {
            recipes = neo4j.searchSuggestedRecipes(skip, 20, logged.getUsername());
            if(recipes == null || recipes.size() == 0)
                recipes = neo4j.getRecipes(skip, 20);
        }
        for(Recipe r: recipes){
            Label title = new Label(r.getName());
            Label category = new Label(r.getCategory());
            Label datePublished  = new Label(new SimpleDateFormat("dd-MM-yyyy").format(r.getDatePublished()));
            HBox recContainer = new HBox();
            recContainer.setAlignment(Pos.CENTER_LEFT);
            recContainer.getChildren().add(title);
            recContainer.getChildren().add(category);
            recContainer.getChildren().add(datePublished);
            recContainer.setOnMouseClicked(mouseEvent -> {goToRecipe(r, mouseEvent);});
            recContainer.setSpacing(10);

            recipeContainer.getChildren().add(recContainer);
        }
        skip = skip + 20;

        createShowMore();

    }

    private void goToRecipe(Recipe r, MouseEvent mouseEvent){
        SessionUtils.setRecipeToShow(r);
        ActionEvent ae = new ActionEvent(mouseEvent.getSource(), mouseEvent.getTarget());
        Utils.changeScene("recipe-view.fxml", ae);
    }

    @FXML
    private void login(ActionEvent ae){
        Neo4jDriver neo4j = Neo4jDriver.getInstance();
        User u = neo4j.getUsersFromUnique(email.getText());

        if(u == null){
            System.out.println("user not found");
            return;
        }
        if(!u.getPassword().equals(password.getText())){
            System.out.println("Wrong password");
            return;
        }
        SessionUtils.setUserLogged(u);
        if(u.getRole() == 2){
            //code to the admin page
        }
        else{
            Utils.changeScene("login-view.fxml", ae);

        }


    }
    @FXML
    private void signUp(ActionEvent actionEvent) {
        Utils.changeScene("registration-view.fxml", actionEvent);
    }
}