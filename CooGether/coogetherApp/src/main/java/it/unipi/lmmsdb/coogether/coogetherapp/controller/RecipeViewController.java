package it.unipi.lmmsdb.coogether.coogetherapp.controller;

import it.unipi.lmmsdb.coogether.coogetherapp.bean.Recipe;
import it.unipi.lmmsdb.coogether.coogetherapp.bean.Comment;
import it.unipi.lmmsdb.coogether.coogetherapp.bean.User;
import it.unipi.lmmsdb.coogether.coogetherapp.config.SessionUtils;
import it.unipi.lmmsdb.coogether.coogetherapp.persistence.MongoDBDriver;

import it.unipi.lmmsdb.coogether.coogetherapp.utils.Utils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.ResourceBundle;

public class RecipeViewController implements Initializable {

    @FXML private Text recipeTitle;
    @FXML private Text recipeAuthorName;
    @FXML private ImageView recipeImg;
    @FXML private Text recipeCategory;
    @FXML private Text recipeDescription;
    @FXML private VBox recipeIngredients;
    @FXML private Text recipeCookTime;
    @FXML private Text recipePrep;
    @FXML private Text recipeKal;
    @FXML private Text recipeFat;
    @FXML private Text recipeServings;
    @FXML private Text recipeSodium;
    @FXML private Text recipeProtein;
    @FXML private VBox recipeInstructions;
    @FXML private VBox comments;
    @FXML private ChoiceBox starSpinner;
    @FXML private TextArea textNewComment;
    private User logged;
    @FXML private HBox boxUpdate;

    private Recipe recipe;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        recipe= MongoDBDriver.getRecipesFromId(SessionUtils.getRecipeToShow().getRecipeId());
        recipeTitle.setText(recipe.getName());
        recipeAuthorName.setText(recipe.getAuthorName());
        if(recipe.getImage() != null) {
            Image img = new Image(recipe.getImage());
            recipeImg.setImage(img);
        }
        recipeCategory.setText(recipe.getCategory());
        recipeDescription.setText(recipe.getDescription());
        recipeCookTime.setText(String.valueOf(recipe.getCookTime()));
        recipePrep.setText(String.valueOf(recipe.getPrepTime()));
        recipeKal.setText(String.valueOf(recipe.getCalories()));
        recipeFat.setText(String.valueOf(recipe.getFatContent()));
        recipeServings.setText(String.valueOf(recipe.getRecipeServings()));
        recipeSodium.setText(String.valueOf(recipe.getSodiumContent()));
        recipeProtein.setText(String.valueOf(recipe.getProteinContent()));

        for (String s : recipe.getIngredients()){
            Text text = new Text();
            text.setText(s);
            text.setWrappingWidth(1000);
            recipeIngredients.getChildren().add(text);
        }

        for (String s : recipe.getRecipeInstructions()){
            Text text = new Text();
            text.setWrappingWidth(1000);
            text.setText(s);
            recipeInstructions.getChildren().add(text);
        }

        if(recipe.getComments() != null)
            showComment();
        logged = SessionUtils.getUserLogged();

        if(logged!= null) {
            if (recipe.getAuthorId() == logged.getUserId() || logged.getRole()==1) {
                //metto un button per poter modificare la ricetta e uno per eliminarla
                VBox container = new VBox();
                container.setSpacing(15);

                Button update = new Button();
                update.setText("Update Recipe");
                update.setMaxWidth(100);
                update.setOnAction(actionEvent -> updateRecipe(actionEvent, recipe));
                container.getChildren().add(update);

                Button delete = new Button();
                delete.setText("Delete Recipe");
                delete.setMaxWidth(100);
                delete.setOnAction(actionEvent -> deleteRecipe(actionEvent, recipe));
                container.getChildren().add(delete);

                boxUpdate.getChildren().add(container);
            }
        }
   }

   @FXML
    private void addComment(ActionEvent actionEvent) {
        //deve controllare se lo user e loggato prima di aggiungere il commento
       String star=(String) starSpinner.getValue();
       logged =SessionUtils.getUserLogged();
       if(logged == null){
           Utils.showErrorAlert("You have to log in in order to add comments");
           return;
       }
       Comment c = new Comment();
       c.setAuthorId(logged.getUserId());
       c.setAuthorName(logged.getUsername());
       c.setDatePublished(new Date(System.currentTimeMillis()));
       c.setText(textNewComment.getText());
       c.setRating(Integer.parseInt(star));
       c.setCommentId(MongoDBDriver.getMaxCommentId() + 1);
       MongoDBDriver.setMaxCommentId(c.getCommentId());
       MongoDBDriver.addComment(recipe, c);
       recipe.addComments(c);
       showComment();
    }

    private void showComment() {
        comments.getChildren().clear();
        Label comm=new Label("Comments:");
        Font bold=new Font("System Bold", 18);
        comm.setFont(bold);
        comments.getChildren().add(comm);
        for (Comment c : recipe.getComments()){
            VBox oneComment=new VBox();
            oneComment.setMaxWidth(1000);
            oneComment.setStyle("-fx-border-style: solid inside;"
                    + "-fx-border-width: 2;" + "-fx-border-insets: 5;"
                    + "-fx-border-radius: 5;" + "-fx-border-color: #596cc2;");
            HBox box = new HBox();
            Text author = new Text();
            StringBuilder starString = new StringBuilder();
            for(int i=0; i< c.getRating(); i++){
                starString.append("☆");
            }
            author.setText(c.getAuthorName() + "     " + starString);
            bold = new Font("System Bold", 12);
            author.setFont(bold);
            box.getChildren().add(author);
            Text text = new Text();
            text.setText(c.getText());
            oneComment.getChildren().addAll(box,text);
            comments.getChildren().addAll(oneComment);
        }
    }

    @FXML
    private void log(MouseEvent mouseEvent) {
        //mostra i dati dello user se questo è loggato, altrimenti ad una pagina per fare il login
        ActionEvent ae = new ActionEvent(mouseEvent.getSource(), mouseEvent.getTarget());
        logged =SessionUtils.getUserLogged();
        SessionUtils.setRecipeToShow(null);
        if(logged==null){
            Utils.changeScene("login-view.fxml", ae);
        }else{

            Utils.changeScene("user-details-view.fxml", ae);
        }
    }

    @FXML
    private void goBack(MouseEvent mouseEvent) {
        //torna alla hello page
        SessionUtils.setRecipeToShow(null);
        ActionEvent ae = new ActionEvent(mouseEvent.getSource(), mouseEvent.getTarget());
        Utils.changeScene("hello-view.fxml", ae);
    }

    private void updateRecipe(ActionEvent actionEvent, Recipe r){
        SessionUtils.setRecipeToShow(r);
        Utils.changeScene("add-recipe.fxml", actionEvent);
    }

    private void deleteRecipe(ActionEvent actionEvent, Recipe r){
        //elimino la ricetta
        if(Utils.deleteRecipe(r)){
            SessionUtils.setRecipeToShow(null);
            Utils.showInfoAlert("Recipe successfully deleted");
            Utils.changeScene("hello-view.fxml", actionEvent);
        }else
            Utils.showErrorAlert("Error in delete this recipe");

    }
}
