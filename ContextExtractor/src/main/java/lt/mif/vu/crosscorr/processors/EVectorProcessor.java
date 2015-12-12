package lt.mif.vu.crosscorr.processors;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
		Map<String, Double> relativeFreq = getRelativeWordFrequencies(allText);
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
		
		//we will now construct graph word chains only using tokens from relative frequency map
		//these are known parts of speech because of the way the frequency checker works
		
		//creating all initial vertices (skips duplicates)
		relativeFreq.keySet().forEach(word -> wordsGraph.addVertex(new Vertex<>(word)));
		
		
	}

	private Map<String, Double> getRelativeWordFrequencies(String fullText) {

		String[] wordTokens = NLPUtil.getInstance().getTokenizer().tokenize(fullText);
		String[] posTags = NLPUtil.getInstance().getPosTagger().tag(wordTokens);

		Map<String, Double> relativeFreqMap = new HashMap<>();
		List<String> wordTokensList = Arrays.asList(wordTokens);
		for (int i = 0; i < wordTokens.length; i++) {
			PartOfSpeech pos = null;
			try {
				pos = PartOfSpeech.get(StringUtils.strip(posTags[i], " -*^&%#?!.,:[]\\|<>"));
			} catch (Exception e) {
				e.printStackTrace();
			}
			if (StringUtils.isAlpha(wordTokens[i]) && (pos == null
					|| (!pos.isPreposition() && WordNetUtils.getInstance().isParsablePOS(pos)))) {
				String token = wordTokens[i];
				double freq = Collections.frequency(wordTokensList, token);
				relativeFreqMap.put(token, freq / (double) wordTokens.length);
			}
		}

		return relativeFreqMap;
	}

}
