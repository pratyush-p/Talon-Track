package edu.wpi.first.talontrack;

import org.fxmisc.easybind.EasyBind;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;
import javafx.util.Duration;
import tech.units.indriya.format.SimpleUnitFormat;

import javax.measure.Unit;
import javax.measure.quantity.Length;

@SuppressWarnings("PMD")
public class CreateProjectController {
	@FXML
	private Label title;
	@FXML
	private Button browse;
	@FXML
	private Button browseOutput;
	@FXML
	private Button browseCommand;
	@FXML
	private Button create;
	@FXML
	private Button cancel;
	@FXML
	private VBox vBox;
	@FXML
	private TextField directory;
	@FXML
	private TextField outputDirectory;
	@FXML
	private TextField commandDirectory;
	@FXML
	private TextField maxVelocity;
	@FXML
	private TextField maxAcceleration;
	@FXML
	private TextField trackWidth;
	@FXML
	private TextField wheelBase;
	@FXML
	private TextField bumperWidth;
	@FXML
	private TextField bumperLength;
	@FXML
	private ChoiceBox<Game> game;
	@FXML
	private ChoiceBox<Unit<Length>> length;
	@FXML
	public ChoiceBox<ProjectPreferences.ExportUnit> export;
	@FXML
	private Label browseLabel;
	@FXML
	private Label outputLabel;
	@FXML
	private Label commandLabel;
	@FXML
	private Label velocityLabel;
	@FXML
	private Label accelerationLabel;
	@FXML
	private Label trackWidthLabel;
	@FXML
	private Label wheelBaseLabel;
	@FXML
	private Label bumperWidthLabel;
	@FXML
	private Label bumperLengthLabel;
	@FXML
	private Label velocityUnits;
	@FXML
	private Label accelerationUnits;
	@FXML
	private Label trackWidthUnits;
	@FXML
	private Label wheelBaseUnits;
	@FXML
	private Label bumperWidthUnits;
	@FXML
	private Label bumperLengthUnits;

	private boolean editing = false;

	@FXML

	private void initialize() {
		ObservableList<TextField> numericFields = FXCollections.observableArrayList(maxVelocity,
				maxAcceleration, trackWidth, wheelBase, bumperWidth, bumperLength);
		ObservableList<TextField> allFields = FXCollections.observableArrayList(numericFields);
		allFields.add(directory);

		var directoryControls = List.of(browseLabel, directory, browse);
		var outputControls = List.of(outputLabel, outputDirectory, browseOutput);
		var commandControls = List.of(commandLabel, commandDirectory, browseCommand);
		var velocityControls = List.of(velocityLabel, maxVelocity, velocityUnits);
		var accelerationControls = List.of(accelerationLabel, maxAcceleration, accelerationUnits);
		var trackWidthControls = List.of(trackWidthLabel, trackWidth, trackWidthUnits);
		var wheelBaseControls = List.of(wheelBaseLabel, wheelBase, wheelBaseUnits);
		var bumperWidthControls = List.of(bumperWidthLabel, bumperWidth, bumperWidthUnits);
		var bumperLengthControls = List.of(bumperLengthLabel, bumperLength, bumperLengthUnits);

		BooleanBinding bind = new SimpleBooleanProperty(true).not();
		for (TextField field : allFields) {
			bind = bind.or(field.textProperty().isEmpty());
		}
		bind = bind.or(game.valueProperty().isNull());
		create.disableProperty().bind(bind);

		// Validate that numericFields contain decimal numbers
		numericFields.forEach(textField -> textField.setTextFormatter(FxUtils.onlyPositiveDoubleText()));

		game.getItems().addAll(Game.getGames());
		game.getSelectionModel().selectFirst();
		game.converterProperty().setValue(new StringConverter<>() {
			@Override
			public String toString(Game object) {
				return object.getName();
			}

			@Override
			public Game fromString(String string) {
				return Game.fromPrettyName(string);
			}
		});

		length.getItems().addAll(PathUnits.LENGTHS);
		length.getSelectionModel().select(PathUnits.METER);
		length.setConverter(new StringConverter<>() {
			@Override
			public String toString(Unit<Length> object) {
				return object.getName();
			}

			@Override
			public Unit<Length> fromString(String string) {
				throw new UnsupportedOperationException();
			}
		});

		export.getItems().addAll(ProjectPreferences.ExportUnit.values());
		export.getSelectionModel().selectFirst();
		export.setConverter(new StringConverter<>() {
			@Override
			public String toString(ProjectPreferences.ExportUnit object) {
				return object.getName();
			}

			@Override
			public ProjectPreferences.ExportUnit fromString(String string) {
				throw new UnsupportedOperationException();
			}
		});

		var lengthUnit = EasyBind.monadic(length.getSelectionModel().selectedItemProperty());
		directoryControls.forEach(control -> control.setTooltip(new Tooltip("The directory to store your project.\n"
				+ "It will be stored at a talontrack subdirectory of this location.\n"
				+ "It is recommended this be the root folder of your robot code\n"
				+ "to simplify version control and path deployment.")));
		outputControls
				.forEach(control -> control.setTooltip(new Tooltip("(Optional) The directory to output paths to.\n"
						+ "If it is the root folder of your FRC robot project,\nthe paths will automatically be copied to the "
						+ "robot at deploy time.\nDefault: will search relative to your project directory,\n"
						+ "attempting to find deploy folder.")));
		commandControls
				.forEach(control -> control.setTooltip(
						new Tooltip("The directory to get Commands from. Default assumes that project directory is in project.")));
		velocityControls
				.forEach(control -> control.setTooltip(new Tooltip("The maximum velocity your robot can travel.")));
		velocityUnits.textProperty()
				.bind(lengthUnit.map(PathUnits.getInstance()::speedUnit).map(SimpleUnitFormat.getInstance()::format));
		accelerationControls
				.forEach(control -> control.setTooltip(new Tooltip("The maximum capable acceleration of your robot.")));
		accelerationUnits.textProperty().bind(
				lengthUnit.map(PathUnits.getInstance()::accelerationUnit).map(SimpleUnitFormat.getInstance()::format));
		trackWidthControls.forEach(
				control -> control.setTooltip(new Tooltip("The distance between the center of the right and left wheels.")));
		trackWidthUnits.textProperty().bind(lengthUnit.map(SimpleUnitFormat.getInstance()::format));
		wheelBaseControls.forEach(
				control -> control.setTooltip(new Tooltip("The distance between the center of the front and back wheels.")));
		wheelBaseUnits.textProperty().bind(lengthUnit.map(SimpleUnitFormat.getInstance()::format));
		bumperWidthControls.forEach(
				control -> control.setTooltip(new Tooltip("The distance between the left and right edges of the bumper.")));
		bumperWidthUnits.textProperty().bind(lengthUnit.map(SimpleUnitFormat.getInstance()::format));
		bumperLengthControls.forEach(
				control -> control.setTooltip(new Tooltip("The distance between the front and back edges of the bumper.")));
		bumperLengthUnits.textProperty().bind(lengthUnit.map(SimpleUnitFormat.getInstance()::format));
		// Show longer text for an extended period of time
		Stream.of(directoryControls, outputControls, commandControls).flatMap(List::stream)
				.forEach(control -> control.getTooltip().setShowDuration(Duration.seconds(10)));
		Stream.of(directoryControls, outputControls, velocityControls, accelerationControls,
				trackWidthControls, wheelBaseControls, bumperWidthControls, bumperLengthControls).flatMap(List::stream)
				.forEach(control -> control.getTooltip().setShowDelay(Duration.millis(150)));

		// We are editing a project
		if (ProjectPreferences.getInstance() != null) {
			setupEditProject();
		} else {
			setupCreateProject();
		}
	}

	private void setupCreateProject() {
		directory.setText("");
		outputDirectory.setText("");
		commandDirectory.setText("");
		create.setText("Create Project");
		title.setText("Create Project...");
		cancel.setText("Cancel");
		browse.setVisible(true);
		game.getSelectionModel().selectFirst();
		length.getSelectionModel().select(3); // Default is Meter
		export.getSelectionModel().selectFirst();
		maxVelocity.setText("");
		maxAcceleration.setText("");
		trackWidth.setText("");
		wheelBase.setText("");
		bumperWidth.setText("");
		bumperLength.setText("");
		editing = false;
	}

	@FXML
	private void createProject() {
		String folderString = directory.getText().trim();
		// create a "talontrack" subdirectory if not editing an existing project
		File directory = editing ? new File(folderString) : new File(folderString, "talontrack");
		directory.mkdir();
		String outputString = outputDirectory.getText();
		String outputPath = outputString;
		boolean newOutput = !editing
				|| !Objects.equals(outputString, ProjectPreferences.getInstance().getValues().getOutputDir());
		if (outputString != null && !outputString.isEmpty() && newOutput) {
			// Find the relative path for the output directory to the project directory,
			// using / file separators
			outputPath = directory.toPath().relativize(new File(outputString.trim()).toPath()).toString().replace("\\",
					"/");
		}
		String commandString = commandDirectory.getText();
		String commandPath = commandString;
		boolean newcommand = !editing
				|| !Objects.equals(commandString, ProjectPreferences.getInstance().getValues().getCommandDir());
		if (commandString != null && !commandString.isEmpty() && newcommand) {
			// Find the relative path for the command directory to the project directory,
			// using / file separators
			commandPath = directory.toPath().relativize(new File(commandString.trim()).toPath()).toString().replace("\\",
					"/");
		}
		ProgramPreferences.getInstance().addProject(directory.getAbsolutePath());
		String lengthUnit = length.getValue().getName();
		String exportUnit = export.getValue().getName();
		double velocityMax = Double.parseDouble(maxVelocity.getText());
		double accelerationMax = Double.parseDouble(maxAcceleration.getText());
		double trackWidthDistance = Double.parseDouble(trackWidth.getText());
		double wheelBaseDistance = Double.parseDouble(wheelBase.getText());
		double bumperWidthDistance = Double.parseDouble(bumperWidth.getText());
		double bumperLengthDistance = Double.parseDouble(bumperLength.getText());
		ProjectPreferences.Values values = new ProjectPreferences.Values(lengthUnit, exportUnit, velocityMax,
				accelerationMax, trackWidthDistance, wheelBaseDistance, bumperWidthDistance, bumperLengthDistance,
				game.getValue().getName(), outputPath, commandPath);
		ProjectPreferences prefs = ProjectPreferences.getInstance(directory.getAbsolutePath());
		prefs.setValues(values);
		editing = false;
		FxUtils.loadMainScreen(vBox.getScene(), getClass());
	}

	@FXML
	private void browseDirectory() {
		browse(directory);
	}

	@FXML
	private void browseOutput() {
		browse(outputDirectory);
	}

	@FXML
	private void browseCommand() {
		browse(commandDirectory);
	}

	private void browse(TextField location) {
		DirectoryChooser chooser = new DirectoryChooser();
		File selectedDirectory = chooser.showDialog(vBox.getScene().getWindow());
		if (selectedDirectory != null) {
			location.setText(selectedDirectory.getPath());
			location.positionCaret(selectedDirectory.getPath().length());
		}
	}

	@FXML
	private void cancel() throws IOException {
		ProjectPreferences.resetInstance();
		Pane root = FXMLLoader.load(getClass().getResource("welcomeScreen.fxml"));
		vBox.getScene().setRoot(root);
	}

	private void setupEditProject() {
		ProjectPreferences.Values values = ProjectPreferences.getInstance().getValues();
		directory.setText(ProjectPreferences.getInstance().getDirectory());
		outputDirectory.setText(ProjectPreferences.getInstance().getValues().getOutputDir());
		commandDirectory.setText(ProjectPreferences.getInstance().getValues().getCommandDir());
		create.setText("Save Project");
		title.setText("Edit Project");
		cancel.setText("Select Project");
		browse.setVisible(false);
		game.setValue(Game.fromPrettyName(values.getGameName()));
		length.setValue(values.getLengthUnit());
		export.setValue(values.getExportUnit());
		maxVelocity.setText(String.valueOf(values.getMaxVelocity()));
		maxAcceleration.setText(String.valueOf(values.getMaxAcceleration()));
		trackWidth.setText(String.valueOf(values.getTrackWidth()));
		wheelBase.setText(String.valueOf(values.getWheelBase()));
		bumperWidth.setText(String.valueOf(values.getBumperWidth()));
		bumperLength.setText(String.valueOf(values.getBumperLength()));
		editing = true;
	}
}
