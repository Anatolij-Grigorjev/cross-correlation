package lt.mif.vu.crosscorr;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.lang.StringUtils;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.chart.XYChart.Series;
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
import lt.mif.vu.crosscorr.utils.Approximators;
import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.utils.SentenceInfo;
import lt.mif.vu.crosscorr.wordnet.WordNetUtils;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.relationship.AsymmetricRelationship;
import net.didion.jwnl.data.relationship.Relationship;
import net.didion.jwnl.data.relationship.RelationshipFinder;
import net.didion.jwnl.data.relationship.RelationshipList;
import opennlp.tools.util.InvalidFormatException;

public class GUIFacade extends Application {

	ListView<String> documentsLeft = new ListView<String>();
	ListView<String> documentsRight = new ListView<String>();
	private ComboBox<Algorithm> selectedAlgorithmBox = new ComboBox<>();
	private ComboBox<Integer> selectedDampeningFactor = new ComboBox<>();
	private ComboBox<Approximators> selectedApproximator = new ComboBox<>();
	private Callback<ListView<String>, ListCell<String>> cellFactory = new Callback<ListView<String>, ListCell<String>>() {

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
	};
	private Button btnProcess = new Button("Process docs!");
	
	Series<Number, Number> eVectorLeftSeries = new Series<>();
	Series<Number, Number> eVectorRightSeries = new Series<>();
	{
		eVectorLeftSeries.setName("eVector Left");
		eVectorLeftSeries.getData().clear();
		eVectorRightSeries.setName("eVector Right");
		eVectorRightSeries.getData().clear();
	}
	
	//----------------DATA---------------//
	private List<SentenceInfo> cVectorLeft, cVectorRight;
	private List<Double> eVectorLeft, eVectorRight;
	private final AreaChart<Number, Number> chart = new AreaChart<>(
			new NumberAxis()
			, new NumberAxis(0, 5.0, 0.05)
	);
	
	

	@Override
	public void start(Stage primaryStage) throws Exception {
		
		documentsLeft.setCellFactory(cellFactory);
		documentsRight.setCellFactory(cellFactory);
		// start loading the nlp cruft
		NLPUtil.getInstance();
		WordNetUtils.getInstance();
		StanfordNLPUtils.getInstance();
		Label lblInput = new Label("Input text: ");
		TextArea fieldInput = new TextArea();
		fieldInput.setWrapText(true);
		Label lblOutput = new Label("cVector output: ");
		TextArea fieldcVectorOutput = new TextArea("Click button to begin...");
		TextArea fieldeVectorOutput = new TextArea("Click button to begin...");
		fieldcVectorOutput.setEditable(false);
		fieldcVectorOutput.setMaxHeight(Double.MAX_VALUE);
		fieldInput.setMaxHeight(Double.MAX_VALUE);
		//------------------BUTTONS LEFT------------------------//
		Button btnAddDocLeft = new Button("Add document Left");
		Button btnClearDocsLeft = new Button("Clear Q Left");
		{
			EventHandler<ActionEvent> addButtonHandler = makeAddButtonHandler(fieldInput,
					fieldcVectorOutput,
					fieldeVectorOutput, 
					documentsLeft);
			btnAddDocLeft.setOnAction(addButtonHandler);
			EventHandler<ActionEvent> clearQButtonHandler = makeClearQButtonHandler(documentsLeft);
			btnClearDocsLeft.setOnAction(clearQButtonHandler);
		}
		
		//------------------BUTTONS RIGHT------------------------//
		Button btnAddDocRight = new Button("Add document Right");
		Button btnClearDocsRight = new Button("Clear Q Right");
		{
			EventHandler<ActionEvent> addButtonHandler = makeAddButtonHandler(fieldInput,
					fieldcVectorOutput,
					fieldeVectorOutput, 
					documentsRight);
			btnAddDocRight.setOnAction(addButtonHandler);
			EventHandler<ActionEvent> clearQButtonHandler = makeClearQButtonHandler(documentsRight);
			btnClearDocsRight.setOnAction(clearQButtonHandler);
		}
		//---------------------------------------------------------//		
		
		btnProcess.setOnAction(e -> {
			btnProcess.setDisable(true);
			selectedAlgorithmBox.setDisable(true);
			selectedDampeningFactor.setDisable(true);
			selectedApproximator.setDisable(true);
			fieldcVectorOutput.setText("");
			fieldeVectorOutput.setText("");
			if (documentsLeft.getItems().isEmpty()
					|| documentsRight.getItems().isEmpty()) {
				fieldcVectorOutput.setText("Input Q was empty! Try again!");
				fieldeVectorOutput.setText("Input Q was empty! Try again!");
				btnProcess.setDisable(false);
				selectedAlgorithmBox.setDisable(false);
				selectedDampeningFactor.setDisable(false);
				selectedApproximator.setDisable(false);
			} else {
				try {
					//CVECTOR LAUNCH
					launchDocumentsListProcessing(fieldcVectorOutput, fieldeVectorOutput);
				} catch (Exception e1) {
					e1.printStackTrace();
				}
			}
		});
		//---------------BUTTONS BOX LEFT-------------------------------//
		HBox buttonsBoxLeft = new HBox(btnAddDocLeft, btnAddDocRight);
		btnAddDocLeft.setMaxWidth(Double.MAX_VALUE);
		btnAddDocRight.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(btnAddDocLeft, Priority.ALWAYS);
		HBox.setHgrow(btnAddDocRight, Priority.ALWAYS);
		
		//---------------BUTTONS BOX RIGHT-------------------------------//
		HBox buttonsBoxRight = new HBox(btnClearDocsLeft, btnClearDocsRight);
		btnClearDocsLeft.setMaxWidth(Double.MAX_VALUE);
		btnClearDocsRight.setMaxWidth(Double.MAX_VALUE);
		HBox.setHgrow(btnClearDocsLeft, Priority.ALWAYS);
		HBox.setHgrow(btnClearDocsRight, Priority.ALWAYS);
		
		VBox centralInputBox = new VBox(
				lblInput
				, fieldInput
				, buttonsBoxLeft
				, buttonsBoxRight
				, btnProcess
				, lblOutput
				, fieldcVectorOutput
		);
		btnProcess.setMaxWidth(Double.MAX_VALUE);
		btnProcess.setMaxHeight(Double.MAX_VALUE);
		centralInputBox.setSpacing(5.0);
		VBox.setMargin(btnProcess, new Insets(5, 10, 5, 10));
		VBox.setMargin(buttonsBoxLeft, new Insets(5, 10, 5, 10));
		VBox.setMargin(buttonsBoxRight, new Insets(5, 10, 5, 10));
		VBox.setMargin(lblInput, new Insets(5, 10, 5, 10));
		VBox.setMargin(fieldInput, new Insets(5, 10, 5, 10));
		VBox.setMargin(lblOutput, new Insets(5, 10, 5, 10));
		VBox.setMargin(fieldcVectorOutput, new Insets(5, 10, 5, 10));
		VBox.setVgrow(fieldcVectorOutput, Priority.ALWAYS);
		fieldcVectorOutput.setPrefWidth(500.0);
		VBox.setVgrow(fieldInput, Priority.ALWAYS);
		VBox.setVgrow(btnProcess, Priority.SOMETIMES);
		
		//EVECTOR BOX
		Label lbleVOutput = new Label("eVector Output:");
		VBox rightSideBox = new VBox(getCorrelationPane(), lbleVOutput, fieldeVectorOutput);
		rightSideBox.setAlignment(Pos.BOTTOM_LEFT);
		fieldeVectorOutput.setPrefHeight(250.0);
		fieldeVectorOutput.setPrefWidth(500.0);
		VBox.setMargin(lbleVOutput, new Insets(5, 10, 5, 10));
		VBox.setMargin(fieldeVectorOutput, new Insets(5, 10, 5, 10));
		
		HBox listBox = new HBox(getListPane(), centralInputBox, rightSideBox);
		HBox.setHgrow(centralInputBox, Priority.ALWAYS);
		primaryStage.setTitle("cVector processing");
		primaryStage.setWidth(1200);
		primaryStage.setHeight(750);
		Scene scene = new Scene(listBox);
		scene.getStylesheets().add("charts.css");
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	@SuppressWarnings("unchecked")
	private Node getCorrelationPane() {
		Label lblCorr = new Label("Correlation: ");
		VBox.setVgrow(chart, Priority.ALWAYS);
		chart.getData().addAll(eVectorLeftSeries, eVectorRightSeries);
		VBox mainBox = new VBox(lblCorr, chart);
		return mainBox;
	}

	private void launchDocumentsListProcessing(
			TextArea fieldOutput
			, TextArea fldeVectorOutput) throws InvalidFormatException, IOException {
		new Thread(new CVectorProcessor(documentsLeft.getItems(),
				text -> Platform.runLater(() -> fieldOutput.appendText(text))) {

			@Override
			public void runFinished(List<SentenceInfo> sentences) {
				cVectorLeft = sentences;
				Platform.runLater(() -> {
					fieldOutput.appendText("\n\n\n\n\n");
//								EVECTOR LAUNCH
					new Thread(new EVectorProcessor(documentsLeft.getItems(),
							text -> Platform.runLater(() -> fldeVectorOutput.appendText(text))) {

						@Override
						public void runFinished(List<Double> sentiments) {
							fldeVectorOutput.appendText("\n\n\n\n\n");
							try {
								eVectorLeft = sentiments;
								Platform.runLater(() -> {
									eVectorLeftSeries.getData().clear();
									eVectorLeftSeries.getData().addAll(
										IntStream
										.range(0, eVectorLeft.size())
										.mapToObj(i -> new Data<Number, Number>(i, eVectorLeft.get(i)))
										.collect(Collectors.toList())
									);
								});
								
								new Thread(new CVectorProcessor(documentsRight.getItems(),
										text -> Platform.runLater(() -> fieldOutput.appendText(text))) {
	
									@Override
									public void runFinished(List<SentenceInfo> sentences) {
										cVectorRight = sentences;
										Platform.runLater(() -> {
											
	//													EVECTOR LAUNCH
											new Thread(new EVectorProcessor(documentsRight.getItems(),
													text -> Platform.runLater(() -> fldeVectorOutput.appendText(text))) {
	
												@Override
												public void runFinished(List<Double> sentiments) {
													eVectorRight = sentiments;
													Platform.runLater(() -> {
														eVectorRightSeries.getData().clear();
														eVectorRightSeries.getData().addAll(
															IntStream
															.range(0, eVectorRight.size())
															.mapToObj(i -> new Data<Number, Number>(i, eVectorRight.get(i)))
															.collect(Collectors.toList())
														);
														btnProcess.setDisable(false);
														selectedAlgorithmBox.setDisable(false);
														selectedDampeningFactor.setDisable(false);
														selectedApproximator.setDisable(false);
														
														
//														chart.getData().clear();
//														chart.getData().addAll(eVectorLeftSeries, eVectorRightSeries);
													});
												}			
											}).start();
										});
									}
								}).start();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}			
					}).start();
				});
			}
		}).start();
		
	}

	private EventHandler<ActionEvent> makeClearQButtonHandler(final ListView<String> documents) {
		EventHandler<ActionEvent> clearQButtonHandler = e -> {
			documents.getItems().clear();
		};
		return clearQButtonHandler;
	}

	private EventHandler<ActionEvent> makeAddButtonHandler(TextArea fieldInput,
			TextArea fieldOutput, TextArea fldeVectorOutput, ListView<String> documents) {
		EventHandler<ActionEvent> addButtonHandler = e -> {
			String input = fieldInput.getText() != null ? fieldInput.getText() : "";
			input = input.trim();
			if (StringUtils.isWhitespace(input)) {
				fieldOutput.setText("Need text to make document!");
				fldeVectorOutput.setText("Need text to make document!");
			} else {
				documents.getItems().add(input);
			}
		};
		return addButtonHandler;
	}

	private Node getListPane() {
		Label titleLeft = new Label("Documents A: ");
		Label titleRight = new Label("Documents B: ");
		
		documentsLeft.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		VBox.setVgrow(documentsLeft, Priority.ALWAYS);
		VBox.setMargin(documentsLeft, new Insets(5, 15, 5, 5));
		VBox.setMargin(titleLeft, new Insets(5, 15, 5, 5));
		documentsRight.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		VBox.setVgrow(documentsRight, Priority.ALWAYS);
		VBox.setMargin(documentsRight, new Insets(5, 15, 5, 5));
		VBox.setMargin(titleRight, new Insets(5, 15, 5, 5));

		
		Label lblOptions = new Label("Options: ");
		VBox.setMargin(lblOptions, new Insets(5, 15, 5, 5));
		VBox optionsBox = getOptionsPane();
		
		HBox documentsBox = new HBox(
				new VBox(titleLeft, documentsLeft)
				, new VBox(titleRight, documentsRight)
		);
		
		VBox box = new VBox(lblOptions, optionsBox, documentsBox);
		return box;
	}

	private VBox getOptionsPane() {
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
		Stream.of(Approximators.values()).forEach(approx -> selectedApproximator.getItems().add(approx));
		selectedAlgorithmBox.valueProperty().addListener((obsValue, from, to) -> {
			GlobalConfig.SELECTED_ALGORITHM = to;
		});
		selectedDampeningFactor.valueProperty().addListener((obsValue, from, to) -> {
			GlobalConfig.DAMPENING_FACTOR = to;
		});
		selectedApproximator.valueProperty().addListener((obsValue, from, to) -> {
			GlobalConfig.APPROXIMATOR = to;
		});
		HBox selectedAlgorithmBoxPanel = new HBox(new Label("Bias algorithm: "), selectedAlgorithmBox);
		HBox selectedDampeningFactorPanel = new HBox(new Label("Dampening factor: "), selectedDampeningFactor);
		HBox selectedApproximatorPanel = new HBox(new Label("Approximator: "), selectedApproximator);
		VBox.setMargin(selectedAlgorithmBoxPanel, new Insets(5, 50, 5, 5));
		VBox.setMargin(selectedDampeningFactorPanel, new Insets(5, 50, 5, 5));
		VBox.setMargin(selectedApproximatorPanel, new Insets(5, 50, 5, 5));
		VBox optionsBox = new VBox(
				checkboxPanel
				, eVcheckboxPanel
				, selectedAlgorithmBoxPanel
				, selectedDampeningFactorPanel
				, selectedApproximatorPanel
		);
		selectedAlgorithmBox.setValue(Algorithm.FRONT_TO_BACK);
		selectedDampeningFactor.setValue(0);
		selectedApproximator.setValue(Approximators.ROUND);
		return optionsBox;
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
