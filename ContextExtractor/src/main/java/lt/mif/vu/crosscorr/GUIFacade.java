package lt.mif.vu.crosscorr;

import java.util.Iterator;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import lt.mif.vu.crosscorr.nlp.NLPUtil;
import lt.mif.vu.crosscorr.processors.CVectorProcessor;
import lt.mif.vu.crosscorr.processors.EVectorProcessor;
import lt.mif.vu.crosscorr.stanfordnlp.StanfordNLPUtils;
import lt.mif.vu.crosscorr.utils.Algorithm;
import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.wordnet.WordNetUtils;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.relationship.AsymmetricRelationship;
import net.didion.jwnl.data.relationship.Relationship;
import net.didion.jwnl.data.relationship.RelationshipFinder;
import net.didion.jwnl.data.relationship.RelationshipList;

public class GUIFacade extends Application {

	ListView<String> documents = new ListView<String>();
	private ComboBox<Algorithm> selectedAlgorithmBox = new ComboBox<>();
	private ComboBox<Integer> selectedDampeningFactor = new ComboBox<>();

	@Override
	public void start(Stage primaryStage) throws Exception {
		
		documents.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {

			@Override
			public ListCell<String> call(ListView<String> param) {
				return new ListCell<String>() {
					@Override
					protected void updateItem(String item, boolean empty) {
						super.updateItem(item, empty);
						if (empty) {
							setText(null);
							setGraphic(null);
						} else {
							Platform.runLater(() -> {setText(StringUtils.substring(item, 0, 30) + "...");});
						}
					}
				};
			}
		});
		// start loading the nlp cruft
		NLPUtil.getInstance();
		WordNetUtils.getInstance();
		StanfordNLPUtils.getInstance();
		Label lblInput = new Label("Input text: ");
		TextArea fieldInput = new TextArea();
		fieldInput.setWrapText(true);
		Label lblOutput = new Label("cVector output: ");
		TextArea fieldOutput = new TextArea("Click button to begin...");
		TextArea fldeVectorOutput = new TextArea("Click button to begin...");
		fieldOutput.setEditable(false);
		fieldOutput.setMaxHeight(Double.MAX_VALUE);
		fieldInput.setMaxHeight(Double.MAX_VALUE);
		Button btnProcess = new Button("Process docs!");
		Button btnAddDoc = new Button("Add document #1");
		btnAddDoc.setOnAction(e -> {
			String input = fieldInput.getText() != null ? fieldInput.getText() : "";
			input = input.trim();
			if (StringUtils.isWhitespace(input)) {
				fieldOutput.setText("Need text to make document!");
				fldeVectorOutput.setText("Need text to make document!");
			} else {
				documents.getItems().add(input);
				btnAddDoc.setText("Add document #" + (documents.getItems().size() + 1));
			}
		});
		Button btnClearDocs = new Button("Clear Q");
		btnClearDocs.setOnAction(e -> {
			documents.getItems().clear();
			btnAddDoc.setText("Add document #1");
		});
		btnProcess.setOnAction(e -> {
			btnProcess.setDisable(true);
			selectedAlgorithmBox.setDisable(true);
			selectedDampeningFactor.setDisable(true);
			fieldOutput.setText("");
			fldeVectorOutput.setText("");
			if (documents.getItems().isEmpty()) {
				fieldOutput.setText("Input Q was empty! Try again!");
				fldeVectorOutput.setText("Input Q was empty! Try again!");
				btnProcess.setDisable(false);
				selectedAlgorithmBox.setDisable(false);
				selectedDampeningFactor.setDisable(false);
			} else {
				try {
					//CVECTOR LAUNCH
					new Thread(new CVectorProcessor(documents.getItems(),
							text -> Platform.runLater(() -> fieldOutput.appendText(text))) {

						@Override
						public void runFinished() {
							Platform.runLater(() -> {
								
//								EVECTOR LAUNCH
								new Thread(new EVectorProcessor(documents.getItems(),
										text -> Platform.runLater(() -> fldeVectorOutput.appendText(text))) {

									@Override
									public void runFinished() {
										Platform.runLater(() -> {
											btnProcess.setDisable(false);
											selectedAlgorithmBox.setDisable(false);
											selectedDampeningFactor.setDisable(false);
										});
									}
									
								}).start();
								
							});
						}

					}).start();
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
		HBox buttonsBox = new HBox(btnAddDoc, btnClearDocs);
		btnAddDoc.setMaxWidth(Double.MAX_VALUE);
		btnClearDocs.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(btnAddDoc, Priority.ALWAYS);
		HBox.setHgrow(btnClearDocs, Priority.ALWAYS);
		VBox cVectorBox = new VBox(
				lblInput
				, fieldInput
				, buttonsBox
				, btnProcess
				, lblOutput
				, fieldOutput
		);
		btnProcess.setMaxWidth(Double.MAX_VALUE);
		btnProcess.setMaxHeight(Double.MAX_VALUE);
		cVectorBox.setSpacing(5.0);
		VBox.setMargin(btnProcess, new Insets(5, 10, 5, 10));
		VBox.setMargin(buttonsBox, new Insets(5, 10, 5, 10));
		VBox.setMargin(lblInput, new Insets(5, 10, 5, 10));
		VBox.setMargin(fieldInput, new Insets(5, 10, 5, 10));
		VBox.setMargin(lblOutput, new Insets(5, 10, 5, 10));
		VBox.setMargin(fieldOutput, new Insets(5, 10, 5, 10));
		VBox.setVgrow(fieldOutput, Priority.ALWAYS);
		fieldOutput.setPrefWidth(500.0);
		VBox.setVgrow(fieldInput, Priority.ALWAYS);
		VBox.setVgrow(btnProcess, Priority.SOMETIMES);
		
		//EVECTOR BOX
		Label lbleVOutput = new Label("eVector Output:");
		VBox eVectorBox = new VBox(lbleVOutput, fldeVectorOutput);
		eVectorBox.setAlignment(Pos.BOTTOM_LEFT);
		fldeVectorOutput.setPrefHeight(250.0);
		fldeVectorOutput.setPrefWidth(500.0);
		VBox.setMargin(lbleVOutput, new Insets(5, 10, 5, 10));
		VBox.setMargin(fldeVectorOutput, new Insets(5, 10, 5, 10));
		
		HBox listBox = new HBox(getListPane(), cVectorBox, eVectorBox);
		HBox.setHgrow(cVectorBox, Priority.ALWAYS);
		primaryStage.setTitle("cVector processing");
		primaryStage.setWidth(1200);
		primaryStage.setHeight(750);
		primaryStage.setScene(new Scene(listBox));
		primaryStage.show();
	}

	private Node getListPane() {
		Label title = new Label("Documents: ");
		Label lblOptions = new Label("Options: ");
		documents.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		VBox.setVgrow(documents, Priority.ALWAYS);
		VBox.setMargin(documents, new Insets(5, 15, 5, 5));
		VBox.setMargin(title, new Insets(5, 15, 5, 5));
		VBox.setMargin(lblOptions, new Insets(5, 15, 5, 5));
		CheckBox checkboxCVectorLogs = new CheckBox();
		CheckBox checkboxEVectorLogs = new CheckBox();
		checkboxCVectorLogs.selectedProperty().addListener((obsValue, oldVal, newVal) -> {
			GlobalConfig.LOG_CVECTOR_VERBOSE = newVal;
		});
		checkboxEVectorLogs.selectedProperty().addListener((obsValue, oldVal, newVal) -> {
			GlobalConfig.LOG_EVECTOR_VERBOSE = newVal;
		});
		HBox checkboxPanel = new HBox(new Label("Verbose logging (cVector): "), checkboxCVectorLogs);
		HBox eVcheckboxPanel = new HBox(new Label("Verbose logging (eVector): "), checkboxEVectorLogs);
		VBox.setMargin(checkboxPanel, new Insets(5, 5, 5, 5));
		VBox.setMargin(eVcheckboxPanel, new Insets(5, 20, 5, 5));
		IntStream.range(0, 10).forEach(num -> selectedDampeningFactor.getItems().add(num));
		Stream.of(Algorithm.values()).forEach(algo -> selectedAlgorithmBox.getItems().add(algo));
		selectedAlgorithmBox.valueProperty().addListener((obsValue, from, to) -> {
			GlobalConfig.SELECTED_ALGORITHM = to;
		});
		selectedDampeningFactor.valueProperty().addListener((obsValue, from, to) -> {
			GlobalConfig.DAMPENING_FACTOR = to;
		});
		HBox selectedAlgorithmBoxPanel = new HBox(new Label("Bias algorithm: "), selectedAlgorithmBox);
		HBox selectedDampeningFactorPanel = new HBox(new Label("Dampening factor: "), selectedDampeningFactor);
		VBox.setMargin(selectedAlgorithmBoxPanel, new Insets(5, 50, 5, 5));
		VBox.setMargin(selectedDampeningFactorPanel, new Insets(5, 50, 5, 5));
		VBox optionsBox = new VBox(checkboxPanel, eVcheckboxPanel, selectedAlgorithmBoxPanel, selectedDampeningFactorPanel);
		selectedAlgorithmBox.setValue(Algorithm.FRONT_TO_BACK);
		selectedDampeningFactor.setValue(0);
		
		VBox box = new VBox(lblOptions, optionsBox, title, documents);
		return box;
	}
	
	private void demonstrateAsymmetricRelationshipOperation(IndexWord start, IndexWord end) throws JWNLException {
		// Try to find a relationship between the first sense of <var>start</var> and the first sense of <var>end</var>
		RelationshipList list = RelationshipFinder.getInstance().findRelationships(start.getSense(1), end.getSense(1), PointerType.HYPERNYM, 4);
		System.out.println("Hypernym relationship between \"" + start.getLemma() + "\" and \"" + end.getLemma() + "\":");
		for (Iterator itr = list.iterator(); itr.hasNext();) {
			((Relationship) itr.next()).getNodeList().print();
		}
		System.out.println("Common Parent Index: " + ((AsymmetricRelationship) list.get(0)).getCommonParentIndex());
		System.out.println("Depth: " + ((Relationship) list.get(0)).getDepth());
	}

	public static void main(String[] args) {
//		System.out.println("Known parts of speech(" + PartOfSpeech.values().length + "): ");
//		Stream.of(PartOfSpeech.values()).forEach(pos -> System.out.println(pos));
		
		GUIFacade.launch(args);
	}

}
