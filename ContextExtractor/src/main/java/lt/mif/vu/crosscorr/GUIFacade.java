package lt.mif.vu.crosscorr;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Callback;
import lt.mif.vu.crosscorr.nlp.NLPUtil;
import lt.mif.vu.crosscorr.processors.CVectorProcessor;
import lt.mif.vu.crosscorr.processors.CrossCorrResults;
import lt.mif.vu.crosscorr.processors.CrossCorrelationProcessor;
import lt.mif.vu.crosscorr.processors.EVectorProcessor;
import lt.mif.vu.crosscorr.stanfordnlp.StanfordNLPUtils;
import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.utils.GlobalIdfCalculator;
import lt.mif.vu.crosscorr.utils.enums.Algorithm;
import lt.mif.vu.crosscorr.utils.enums.Approximators;
import lt.mif.vu.crosscorr.utils.model.SentenceInfo;
import lt.mif.vu.crosscorr.utils.model.SentencesDataPoint;
import lt.mif.vu.crosscorr.windows.ChartWindow;
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
	private ComboBox<Integer> selectedContextResonance = new ComboBox<>();
	private ComboBox<Approximators> selectedApproximator = new ComboBox<>();
	
	ExecutorService processorExecutor = Executors.newSingleThreadExecutor();
	
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
	
	
	ChartWindow sentimentCorrWindow = new ChartWindow("Cross correlation (sentiment): "
			, new NumberAxis()
			, new NumberAxis(-1.0, 1.0, 0.05));
	ChartWindow topicCorrWindow = new ChartWindow("Cross correlation (topic): "
			, new NumberAxis()
			, new NumberAxis(0.0, 1.0, 0.05));
	
	
	
	//----------------DATA---------------//
	private List<SentenceInfo> cVectorLeft, cVectorRight;
	private List<Double> eVectorLeft, eVectorRight;

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
		TextArea fieldccOutput = new TextArea("Click button to begin...");
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
			fieldccOutput.setText("");
			if (documentsLeft.getItems().isEmpty()
					|| documentsRight.getItems().isEmpty()) {
				fieldcVectorOutput.setText("Input Q was empty! Try again!");
				fieldeVectorOutput.setText("Input Q was empty! Try again!");
				fieldccOutput.setText("Input Q was empty! Try again!");
				btnProcess.setDisable(false);
				selectedAlgorithmBox.setDisable(false);
				selectedDampeningFactor.setDisable(false);
				selectedApproximator.setDisable(false);
			} else {
				try {
					//CVECTOR LAUNCH
					launchDocumentsListProcessing(fieldcVectorOutput, fieldeVectorOutput, fieldccOutput);
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
		Label lblccOutput = new Label("CrossCorr Output:");
		VBox rightSideBox = new VBox(
				lblccOutput
				, fieldccOutput
				, lbleVOutput
				, fieldeVectorOutput
		);
		rightSideBox.setAlignment(Pos.BOTTOM_LEFT);
		fieldccOutput.setPrefHeight(375.0);
		fieldccOutput.setPrefWidth(500.0);
		fieldeVectorOutput.setPrefHeight(250.0);
		fieldeVectorOutput.setPrefWidth(500.0);
		VBox.setMargin(lblccOutput, new Insets(5, 10, 5, 10));
		VBox.setMargin(fieldccOutput, new Insets(5, 10, 5, 10));
		VBox.setMargin(lbleVOutput, new Insets(5, 10, 5, 10));
		VBox.setMargin(fieldeVectorOutput, new Insets(5, 10, 5, 10));
		
		HBox listBox = new HBox(getListPane(), centralInputBox, rightSideBox);
		HBox.setHgrow(centralInputBox, Priority.ALWAYS);
		primaryStage.setTitle("cVector processing");
		primaryStage.setWidth(1200);
		primaryStage.setHeight(750);
		Scene scene = new Scene(listBox);
		primaryStage.setScene(scene);
		primaryStage.show();
	}

	private void launchDocumentsListProcessing(
			TextArea fieldOutput
			, TextArea fldeVectorOutput
			, TextArea fieldccOutput) throws InvalidFormatException, IOException {
		
		GlobalIdfCalculator.init(flatten(documentsLeft.getItems()), flatten(documentsRight.getItems()));
		
		Runnable[] initialProcessors = {
				
			new CVectorProcessor(documentsLeft.getItems(),
					text -> Platform.runLater(() -> fieldOutput.appendText(text))) {
	
				@Override
				public void runFinished(List<SentenceInfo> sentences) {
					cVectorLeft = sentences;
					Platform.runLater(() -> {
						fieldOutput.appendText("\n\n\n\n\n");
					});
				}
			}, 
			new EVectorProcessor(documentsLeft.getItems(),
					text -> Platform.runLater(() -> fldeVectorOutput.appendText(text))) {

				@Override
				public void runFinished(List<Double> sentiments) {
					Platform.runLater(() -> { 
						fldeVectorOutput.appendText("\n\n\n\n\n");
					});
					eVectorLeft = sentiments;
				}
			},
			new CVectorProcessor(documentsRight.getItems(),
					text -> Platform.runLater(() -> fieldOutput.appendText(text))) {

				@Override
				public void runFinished(List<SentenceInfo> sentences) {
					cVectorRight = sentences;
				}
			},
			new EVectorProcessor(documentsRight.getItems(),
					text -> Platform.runLater(() -> fldeVectorOutput.appendText(text))) {

				@Override
				public void runFinished(List<Double> sentiments) {
					eVectorRight = sentiments;
					Platform.runLater(() -> {
						btnProcess.setDisable(false);
						selectedAlgorithmBox.setDisable(false);
						selectedDampeningFactor.setDisable(false);
						selectedApproximator.setDisable(false);
						
					});
				}
			}
		};
		
		new Thread(() -> {
			Stream.of(initialProcessors)
			.forEach(task -> {
				try {
					processorExecutor.submit(task).get();
				} catch (Exception e) {
					e.printStackTrace();
				}
			});		
			try {
				processorExecutor.submit(new CrossCorrelationProcessor(eVectorLeft, eVectorRight, cVectorLeft, cVectorRight,
						text -> Platform.runLater(() -> fieldccOutput.appendText(text))) {
					@Override
					public void runFinished(CrossCorrResults results) {
						double[] evectorCorr = results.getEVectorCrossCorr();
						List<SentencesDataPoint> cvectorCorr = results.getCVectorCrossCorr();
						Series<Number, Number> sentimentCorrResults = new Series<>();
						Series<Number, Number> topicCorrResults = new Series<>();
						int halfLength = evectorCorr.length / 2;
						List<Data<Number, Number>> sentimentSeriesData = IntStream.range(-1 * halfLength, halfLength)
						.mapToObj(d -> new Data<Number, Number>(d, evectorCorr[halfLength + d]))
						.collect(Collectors.toList());
						List<Data<Number, Number>> topiceSeriesData = IntStream.rangeClosed(1, cvectorCorr.size())
						.mapToObj(index -> {
							SentencesDataPoint point = cvectorCorr.get(index - 1);
							return new Data<Number, Number>(index, point.getRelatednessScore(), point);
						})
						.collect(Collectors.toList());
						sentimentCorrResults.getData().addAll(sentimentSeriesData);
						topicCorrResults.getData().addAll(topiceSeriesData);
						Platform.runLater(() -> {
							sentimentCorrWindow.addSeries(sentimentCorrResults);
							sentimentCorrWindow.show();
							topicCorrWindow.addSeries(topicCorrResults);
							topicCorrWindow.show();
							addCVectorTooltips(topicCorrResults);
						});
						
					}

					private void addCVectorTooltips(Series<Number, Number> cVectorSeries) {
						cVectorSeries.getData().forEach(point -> {
							SentencesDataPoint relation = (SentencesDataPoint) point.getExtraValue();
							Node node = point.getNode();
							Tooltip.install(node
									, new Tooltip(
											"Relatedness: " + relation.getRelatednessScore()
											+ "\nSentence #1: " + relation.getSentence1()
											+ "\nSentence #2: " + relation.getSentence2()
											));
							
							node.setOnMouseEntered(e -> node.getStyleClass().add("onHover"));
							node.setOnMouseExited(e -> node.getStyleClass().remove("onHover"));
						});
					}
				}).get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}).start();
		
	}

	private String flatten(List<String> strings) {
		return strings.stream().collect(Collectors.joining());
	}

	private EventHandler<ActionEvent> makeClearQButtonHandler(final ListView<String> documents) {
		EventHandler<ActionEvent> clearQButtonHandler = e -> {
			documents.getItems().clear();
			GlobalIdfCalculator.clear();
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
		IntStream.range(0, 10).forEach(num -> selectedContextResonance.getItems().add(num));
		Stream.of(Algorithm.values()).forEach(algo -> selectedAlgorithmBox.getItems().add(algo));
		Stream.of(Approximators.values()).forEach(approx -> selectedApproximator.getItems().add(approx));
		selectedAlgorithmBox.valueProperty().addListener((obsValue, from, to) -> {
			GlobalConfig.SELECTED_ALGORITHM = to;
		});
		selectedDampeningFactor.valueProperty().addListener((obsValue, from, to) -> {
			GlobalConfig.DAMPENING_FACTOR = to;
		});
		selectedContextResonance.valueProperty().addListener((obsValue, from, to) -> {
			GlobalConfig.CONTEXT_RESONANCE = to;
		});
		selectedApproximator.valueProperty().addListener((obsValue, from, to) -> {
			GlobalConfig.APPROXIMATOR = to;
		});
		HBox selectedAlgorithmBoxPanel = new HBox(new Label("Bias algorithm: "), selectedAlgorithmBox);
		HBox selectedDampeningFactorPanel = new HBox(new Label("Dampening factor: "), selectedDampeningFactor);
		HBox selectedApproximatorPanel = new HBox(new Label("Approximator: "), selectedApproximator);
		HBox selectedContextResonancePanel = new HBox(new Label("C-Resonance: "), selectedContextResonance);
		VBox.setMargin(selectedAlgorithmBoxPanel, new Insets(5, 50, 5, 5));
		VBox.setMargin(selectedDampeningFactorPanel, new Insets(5, 50, 5, 5));
		VBox.setMargin(selectedApproximatorPanel, new Insets(5, 50, 5, 5));
		VBox.setMargin(selectedContextResonancePanel, new Insets(5, 50, 5, 5));
		VBox optionsBox = new VBox(
				checkboxPanel
				, eVcheckboxPanel
				, selectedAlgorithmBoxPanel
				, selectedDampeningFactorPanel
				, selectedApproximatorPanel
				, selectedContextResonancePanel
		);
		selectedAlgorithmBox.setValue(GlobalConfig.SELECTED_ALGORITHM);
		selectedDampeningFactor.setValue(GlobalConfig.DAMPENING_FACTOR);
		selectedContextResonance.setValue(GlobalConfig.CONTEXT_RESONANCE);
		selectedApproximator.setValue(GlobalConfig.APPROXIMATOR);
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
