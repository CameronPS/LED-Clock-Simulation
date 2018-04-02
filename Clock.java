package clock;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Clock extends Application {

  @Override
  public void start(Stage stage) throws Exception {
    Parent root = FXMLLoader.load(getClass().getResource("FXMLDocument.fxml"));
    Scene scene = new Scene(root);
    stage.setScene(scene);
    stage.setTitle("Team 49 ENGG2800 Clock v2.0");

    // Terminate all threads
    Platform.setImplicitExit(true);
    stage.setOnCloseRequest((ae) -> {
      Platform.exit();
      System.exit(0);
    });

    stage.show();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
