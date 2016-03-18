package lt.mif.vu.crosscorr.windows;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart.Series;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.Getter;

public class ChartWindow {
	
	private Label chartLabel;
	@Getter
	private Stage graphStage;
	@Getter
	private AreaChart<Number, Number> chart;
	@Getter
	private List<Series<Number, Number>> chartSeriesList;
	
	public ChartWindow(String chartlabel, Axis<Number> xAxis, Axis<Number> yAxis) {
		this.chartSeriesList = new ArrayList<>();
		this.chartLabel = new Label(chartlabel);
		this.chart = new AreaChart<>(xAxis, yAxis);
		this.graphStage = new Stage();
		this.graphStage.setScene(new Scene(getCorrelationPane()));
		this.graphStage.getScene().getStylesheets().add("charts.css");
	}
	
	private Parent getCorrelationPane() {
		VBox.setVgrow(chart, Priority.ALWAYS);
		VBox mainBox = new VBox(chartLabel, chart);
		return mainBox;
	}
	
	public void show() {
		this.graphStage.show();
	}
	
	public void addSeries(Series<Number, Number> series) {
		this.chartSeriesList.add(series);
		this.chart.getData().add(series);
	}

}
