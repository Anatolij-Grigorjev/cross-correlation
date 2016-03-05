package lt.mif.vu.crosscorr.processors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import lt.mif.vu.crosscorr.OutputAppender;
import lt.mif.vu.crosscorr.nlp.NLPUtil;
import lt.mif.vu.crosscorr.nlp.PartOfSpeech;
import lt.mif.vu.crosscorr.stanfordnlp.StanfordNLPUtils;
import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.utils.MathUtils;
import lt.mif.vu.crosscorr.utils.PrintUtils;
import lt.mif.vu.crosscorr.utils.graph.Graph;
import lt.mif.vu.crosscorr.utils.graph.Vertex;
import lt.mif.vu.crosscorr.wordnet.WordNetUtils;

public abstract class EVectorProcessor implements Runnable {

	private List<String> inputDocs;
	private OutputAppender appender;
	private StanfordNLPUtils nlpProcessor;
	private List<Double> sentimentsSignal;
	
	
	public EVectorProcessor(List<String> inputDocs, OutputAppender appender) {
		this.inputDocs = inputDocs;
		this.appender = appender;
		this.nlpProcessor = StanfordNLPUtils.getInstance();
		this.sentimentsSignal = new ArrayList<>();
	}

	public abstract void runFinished(List<Double> result);

	@Override
	public void run() {
		appender.appendOut("Processing " + inputDocs.size() + " input docs...\n");
		// unite all of the texts
		String allText = inputDocs.stream().reduce("", String::concat);
		String[] sentences = NLPUtil.getInstance().getSentenceDetector().sentDetect(allText);
		appender.appendOut("Doing " + sentences.length + " sentences...\n\n\n");
		try {
			Arrays.stream(sentences).forEach(sentence -> processTextToGraph(sentence));
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		appender.appendOut("\n\nHere is the sentiment flow for the text: \n" 
				+ Arrays.toString(sentimentsSignal.toArray()));
		
		appender.appendOut("\nSignal length: " + sentimentsSignal.size());
		appender.appendOut("\nSignal min: " + sentimentsSignal.stream().mapToDouble(Double::new).min().orElse(0.0));
		appender.appendOut("\nSignal max: " + sentimentsSignal.stream().mapToDouble(Double::new).max().orElse(0.0));
		appender.appendOut("\nSignal avg: " + sentimentsSignal.stream().mapToDouble(Double::new).average().orElse(0.0));
		
		Map<String, List<Double>> map = sentimentsSignal.stream()
			.collect(Collectors.groupingBy(StanfordNLPUtils::closestSentimentClass));
		
		appender.appendOut("\n" + PrintUtils.printGroupingMap(map));
		
		runFinished(sentimentsSignal);
	}

	private void processTextToGraph(String text) {
		
		appender.appendOut("Total text length: " + text.length() + "\n");

		// find terms groups and group sizes
		String[] wordTokens = getFilteredWordTokens(text);
		if (GlobalConfig.LOG_EVECTOR_VERBOSE) {
			appender.appendOut("Filtered sentence: " + Arrays.toString(wordTokens));
		}
		Graph<String> sentenceGraph = assembleConnectionGraph(wordTokens);
		
		appender.appendOut("Graph complete! \n"
				+ "Vertices: " + sentenceGraph.getVerticies().size()
				+ "\nEdges: " + sentenceGraph.getEdges().size()
				+ "\n");
		
		Double graphSentiment = resolveGraphToSentiment(sentenceGraph, text);
		if (GlobalConfig.LOG_EVECTOR_VERBOSE) {
			appender.appendOut("Sentence: \n" 
					+ text 
					+ "\nSentiment: " 
					+ graphSentiment 
					+ "\n\n");
		}
		//even if the sentiment was not recovered, 
		//the signal need to be padded to sentence count
		if (graphSentiment > 0.0 ) {
			sentimentsSignal.add(graphSentiment);
		} else {
			if (sentimentsSignal.isEmpty()) {
				//neutral sentiment class
				sentimentsSignal.add(0.0 + StanfordNLPUtils.sentimentClassIndex("Neutral"));
			} else {
				//add last recorded sentiment again
				sentimentsSignal.add(sentimentsSignal.get(sentimentsSignal.size() - 1));
			}
		}
	}

	private Double resolveGraphToSentiment(Graph<String> sentenceGraph, String sentenceText) {
		//the last and first vertices are the last and first sentence tokens
		//due to graph implementations
		
		List<Vertex<String>> verticies = sentenceGraph.getVerticies();
		
		//do front-to-back calc
		Double ftbSentiment = getAccumulatedSentimentAverage(verticies);
		//and back-to-front
		List<Vertex<String>> copyList = new ArrayList<>(verticies);
		Collections.reverse(copyList);
		Double btfSentiment = getAccumulatedSentimentAverage(copyList);
		
		if (GlobalConfig.LOG_EVECTOR_VERBOSE) {
			appender.appendOut("Pre-bias FTB and BTF: \n"
					+ "FTB: "
					+ ftbSentiment
					+ "\nBTF: "
					+ btfSentiment
					+ "\n");
		}
		
		Double graphSentiment = 0.0;
		//introducing sentiment bias
		switch (GlobalConfig.SELECTED_ALGORITHM) {
		case BACK_TO_FRONT:
			graphSentiment = performBiasCalc(ftbSentiment, btfSentiment);
			break;
		case FRONT_TO_BACK:
			graphSentiment = performBiasCalc(btfSentiment, ftbSentiment);
			break;
		case STANFROD_NLP:
			double stanfordSent = StanfordNLPUtils.sentimentClassIndex(nlpProcessor.analyzeString(sentenceText));
			if (GlobalConfig.LOG_EVECTOR_VERBOSE) {
				appender.appendOut("Stanford value: " + stanfordSent + "\n");
			}
			if (Math.abs(stanfordSent - ftbSentiment) < 
					Math.abs(stanfordSent - btfSentiment)) {
				//front sentiment was closer
				graphSentiment = performBiasCalc(btfSentiment, ftbSentiment);
			} else {
				//back sentiment was closer
				graphSentiment = performBiasCalc(ftbSentiment, btfSentiment);
			}
			break;
		default:
			throw new RuntimeException("Unknown Algorithm class!");
		}
		
		return graphSentiment;
	}

	private Double performBiasCalc(Double toDampen, Double dampener) {
		double ftbFinal = MathUtils.dampen(toDampen, dampener, GlobalConfig.DAMPENING_FACTOR);
		return (dampener + ftbFinal) / 2;
	}

	private Double getAccumulatedSentimentAverage(List<Vertex<String>> verticies) {
		double accumSentimentAverage = 0.0;
		for (int i = 0; i < verticies.size(); i++) {
			Vertex<String> vertex = verticies.get(i);
			String sentiments = nlpProcessor.analyzeString(vertex.getData());
			double sentimentClassIndex = StanfordNLPUtils.sentimentClassIndex(sentiments);
			if (GlobalConfig.LOG_EVECTOR_VERBOSE) {
				appender.appendOut("\n" + vertex.getData() + "=" + sentimentClassIndex + "\n");
			}
			accumSentimentAverage += sentimentClassIndex;
			if (i > 0) { 
				accumSentimentAverage /= 2.0;
			}
		}
		return accumSentimentAverage;
	}

	private Graph<String> assembleConnectionGraph(String[] wordTokens) {
		
		Graph<String> sentenceGraph = new Graph<>();
		
		if (GlobalConfig.LOG_EVECTOR_VERBOSE) {
			appender.appendOut("Making a graph of " + wordTokens.length + " tokens...\n\n");
		}
		//creating all initial vertices (skips duplicates)
		Arrays.stream(wordTokens).forEach(word -> {
			Vertex<String> vertex = new Vertex<String>("Vertex-" + word, word) {
				
				@Override
				public boolean equals(Object obj) {
					if (obj instanceof Vertex) {
						Vertex<?> other = (Vertex<?>) obj;
						return other.getName().equals(this.getName()) 
								&& other.getData().equals(this.getData());
					} else {
						return obj.equals(this);
					}
				};
			};
			
			sentenceGraph.addVertex(vertex);
		});
		
		//start connecting edges
		
		List<String> tokensList = Arrays.asList(wordTokens);
		sentenceGraph.getVerticies().forEach(vertex -> {
			int index = tokensList.indexOf(vertex.getData());
			//the before vertex
			if (index > 0) {
				String beforeToken = tokensList.get(index - 1);
				Vertex<String> beforeVertex = sentenceGraph.getVertexByValue(beforeToken);
				if (beforeVertex != null) {
					sentenceGraph.addEdge(beforeVertex, vertex, 1);
				} else {
					//all tokens should have a vertex or be known there
					appender.appendOut("Can't vind vertex for token: " + beforeToken);
				}
			}
			//the after vertex
			if (index < tokensList.size() - 1) {
				String afterToken = tokensList.get(index + 1);
				Vertex<String> afterVertex = sentenceGraph.getVertexByValue(afterToken);
				if (afterVertex != null) {
					sentenceGraph.addEdge(vertex, afterVertex, 1);
				} else {
					//all tokens should have a vertex or be known there
					appender.appendOut("Can't vind vertex for token: " + afterToken);
				}
			}
		});
		
		return sentenceGraph;
	}
	
	
	private String[] getFilteredWordTokens(String fullText) {
		String[] wordTokens = NLPUtil.getInstance().getTokenizer().tokenize(fullText);
		String[] posTags = NLPUtil.getInstance().getPosTagger().tag(wordTokens);

		//need a variable size list for the filtering
		List<String> filteredTokens = new ArrayList<>();
		for (int i = 0; i < wordTokens.length; i++) {
			PartOfSpeech pos = null;
			try {
				pos = PartOfSpeech.get(StringUtils.strip(posTags[i], " -*^&%#?!.,:[]\\|<>"));
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (StringUtils.isAlpha(wordTokens[i]) && (pos == null
					|| (!pos.isPreposition() && WordNetUtils.getInstance().isParsablePOS(pos)))) {
				filteredTokens.add(wordTokens[i]);
			}
		}
		
		return filteredTokens.toArray(new String[filteredTokens.size()]);
	}

}
