package uav.util.mavlinkviewer;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MAVLinkViewer extends Application {
	public static MAVLinkViewer instance;
	public Stage primaryStage;
	public Scene scene;

	@Override
	public void start(Stage primaryStage) throws Exception {
		instance = this;
		this.primaryStage = primaryStage;
		this.primaryStage.setOpacity(0.0);

		Parent root = FXMLLoader.load(MAVLinkViewerController.class.getResource("MAVLinkViewer.fxml"));
		scene = new Scene(root);
		scene.getStylesheets().add(MAVLinkViewerController.class.getResource("style_dark.css").toExternalForm());
		
		primaryStage.setTitle("MAVLinkViewer");
		primaryStage.setScene(scene);
		primaryStage.setMaximized(true);
		
		primaryStage.show();
		this.primaryStage.setOpacity(1.0);
	}
	
	@Override
	public void stop() {
		System.exit(0);
	}
	
	public static void main(String[] args) {
		launch(args);
	}
}


