package lt.mif.vu.crosscorr.processors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;

import lt.mif.vu.crosscorr.OutputAppender;
import lt.mif.vu.crosscorr.nlp.NLPUtil;
import lt.mif.vu.crosscorr.nlp.PartOfSpeech;
import lt.mif.vu.crosscorr.stanfordnlp.StanfordNLPUtils;
import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.utils.PrintUtils;
import lt.mif.vu.crosscorr.utils.graph.Graph;
import lt.mif.vu.crosscorr.utils.graph.Vertex;
import lt.mif.vu.crosscorr.wordnet.WordNetUtils;

public abstract class EVectorProcessor implements Runnable {

	private List<String> inputDocs;
	private OutputAppender appender;
	private StanfordNLPUtils nlpProcessor;
	private static final int FREQ_TERMS = 5;

	public EVectorProcessor(List<String> inputDocs, OutputAppender appender) {
		this.inputDocs = inputDocs;
		this.appender = appender;
		this.nlpProcessor = StanfordNLPUtils.getInstance();
	}

	public abstract void runFinished();

	@Override
	public void run() {

		appender.appendOut("Processing " + inputDocs.size() + " input docs...\n");
		// unite all of the texts
		String allText = inputDocs.stream().reduce("", String::concat);
		
		Arrays.stream(NLPUtil.getInstance().getSentenceDetector().sentDetect(allText))
			.forEach(sentence -> processTextToGraph(sentence));
	}

	private void processTextToGraph(String text) {
		
		appender.appendOut("Total text length: " + text.length() + "\n");

		// find terms groups and group sizes
		String[] wordTokens = getFilteredWordTokens(text);
		Graph<String> sentenceGraph = assembleConnectionGraph(wordTokens);
		
		appender.appendOut("Graph complete! \n"
				+ "Vertices: " + sentenceGraph.getVerticies().size()
				+ "\nEdges: " + sentenceGraph.getEdges().size()
				+ "\n\n\n");
		
		String graphSentiment = resolveGraphToSentiment(sentenceGraph);
		
		
	}

	private String resolveGraphToSentiment(Graph<String> sentenceGraph) {
		//the last and first vertices are the last and first sentence tokens
		//due to graph implementations
		
		List<Vertex<String>> verticies = sentenceGraph.getVerticies();
		
		//do front-to-back calc
		Integer ftbSentiment = getClosestAccumulatedSentiment(verticies);
		//and back-to-front
		List<Vertex<String>> copyList = new ArrayList<>(verticies);
		Collections.reverse(copyList);
		Integer btfSentiment = getClosestAccumulatedSentiment(copyList);
		
		String graphSentiment = "";
		//introducing sentiment bias
		switch (GlobalConfig.SELECTED_ALGORITHM) {
		case BACK_TO_FRONT:
			
			break;
		case FRONT_TO_BACK:
			
			break;
		case STANFROD_NLP:
			
			break;
		default:
			throw new RuntimeException("Unknown Algorithm class!");
		}
		
		return graphSentiment;
	}

	private Integer getClosestAccumulatedSentiment(List<Vertex<String>> verticies) {
		double accumSentimentAverage = 0.0;
		for (int i = 0; i < verticies.size(); i++) {
			Vertex<String> vertex = verticies.get(i);
			String sentiments = nlpProcessor.analyzeString(vertex.getData());
			accumSentimentAverage += StanfordNLPUtils.sentimentClassIndex(sentiments);
			if (i > 0) { 
				accumSentimentAverage /= 2.0;
			}
		}
		return (int) Math.round(accumSentimentAverage);
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

	private Map<String, Double> getRelativeWordFrequencies(String[] wordTokens) {
		Map<String, Double> relativeFreqMap = new HashMap<>();
		List<String> wordTokensList = Arrays.asList(wordTokens);
		
		Stream.of(wordTokens).forEach(token -> {			
			double freq = Collections.frequency(wordTokensList, token);
			relativeFreqMap.put(token, freq / (double) wordTokens.length);
		});

		return relativeFreqMap;
	}

}
