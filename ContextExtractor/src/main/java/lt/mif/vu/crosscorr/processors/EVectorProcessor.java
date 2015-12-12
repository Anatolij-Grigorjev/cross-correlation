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
import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.utils.PrintUtils;
import lt.mif.vu.crosscorr.utils.graph.Graph;
import lt.mif.vu.crosscorr.utils.graph.Vertex;
import lt.mif.vu.crosscorr.wordnet.WordNetUtils;

public abstract class EVectorProcessor implements Runnable {

	private List<String> inputDocs;
	private OutputAppender appender;
	private static final int FREQ_TERMS = 5;
	private Graph<String> wordsGraph;

	public EVectorProcessor(List<String> inputDocs, OutputAppender appender) {
		this.inputDocs = inputDocs;
		this.appender = appender;
		this.wordsGraph = new Graph<>();
	}

	public abstract void runFinished();

	@Override
	public void run() {

		appender.appendOut("Processing " + inputDocs.size() + " input docs...\n");
		// unite all of the texts
		String allText = inputDocs.stream().reduce("", String::concat);
		appender.appendOut("Total text length: " + allText.length() + "\n");

		// find terms groups and group sizes
		String[] wordTokens = getFilteredWordTokens(allText);
		Map<String, Double> relativeFreq = getRelativeWordFrequencies(wordTokens);
		if (GlobalConfig.LOG_EVECTOR_VERBOSE) {
			appender.appendOut("\n\nRelative frequencies: \n");
			appender.appendOut(PrintUtils.printWordRelevance(relativeFreq));
		}

		Map<String, Double> highestFreqTerms = relativeFreq.entrySet().stream()
				.sorted((entry1, entry2) -> {
					return -1 * entry1.getValue().compareTo(entry2.getValue());
				})
				.limit(FREQ_TERMS)
				.collect(Collectors.toMap(entry -> {
						return entry.getKey();
					} , entry -> {
						return entry.getValue();
					})
				);

		appender.appendOut("\nThe highest " + FREQ_TERMS + " term frequencies: \n");
		appender.appendOut(PrintUtils.printWordRelevance(highestFreqTerms));
		
		assembleConnectionGraph(wordTokens);
		
		appender.appendOut("Graph complete! \n"
				+ "Vertices: " + wordsGraph.getVerticies().size()
				+ "\nEdges: " + wordsGraph.getEdges().size()
				+ "\n");
	}

	private void assembleConnectionGraph(String[] wordTokens) {
		if (GlobalConfig.LOG_EVECTOR_VERBOSE) {
			appender.appendOut("Making a graph of " + wordTokens.length + " tokens...\n\n");
		}
		//creating all initial vertices (skips duplicates)
		Arrays.stream(wordTokens).forEach(word -> {
			Vertex<String> vertex = new Vertex<String>("Vertex-" + word, word) {
				
				@SuppressWarnings("rawtypes")
				@Override
				public boolean equals(Object obj) {
					if (obj instanceof Vertex) {
						Vertex other = (Vertex) obj;
						return other.getName().equals(this.getName()) 
								&& other.getData().equals(this.getData());
					} else {
						return obj.equals(this);
					}
				};
			};
			
			wordsGraph.addVertex(vertex);
		});
		
		//start connecting edges
		
		List<String> tokensList = Arrays.asList(wordTokens);
		wordsGraph.getVerticies().forEach(vertex -> {
			int index = tokensList.indexOf(vertex.getData());
			//the before vertex
			if (index > 0) {
				String beforeToken = tokensList.get(index - 1);
				Vertex<String> beforeVertex = wordsGraph.getVertexByValue(beforeToken);
				if (beforeVertex != null) {
					wordsGraph.addEdge(beforeVertex, vertex, 1);
				} else {
					//all tokens should have a vertex or be known there
					appender.appendOut("Can't vind vertex for token: " + beforeToken);
				}
			}
			//the after vertex
			if (index < tokensList.size() - 1) {
				String afterToken = tokensList.get(index + 1);
				Vertex<String> afterVertex = wordsGraph.getVertexByValue(afterToken);
				if (afterVertex != null) {
					wordsGraph.addEdge(vertex, afterVertex, 1);
				} else {
					//all tokens should have a vertex or be known there
					appender.appendOut("Can't vind vertex for token: " + afterToken);
				}
			}
		});
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
