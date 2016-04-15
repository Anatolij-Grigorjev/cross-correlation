package lt.mif.vu.crosscorr.utils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;

import lt.mif.vu.crosscorr.nlp.NLPUtil;
import opennlp.tools.sentdetect.SentenceDetectorME;

public class GlobalIdfCalculator {
	
	private GlobalIdfCalculator() { }
	
	private static String[] textSentences;
	private static int totalCount;
	private static Map<String, Double> wordsIdfCache;
	
	public static void init(String text1, String text2) {
		SentenceDetectorME sentenceDetector = NLPUtil.getInstance().getSentenceDetector();
		textSentences = sentenceDetector.sentDetect(text1 + text2);
		totalCount = textSentences.length;
		if (wordsIdfCache == null) {
			wordsIdfCache = new HashMap<>();
		}
	}
	
	/**
	 * Global idf is the inverse document frequency for the data present in the
	 * tool. This takes all sentences of both texts as the document corpus and
	 * counts in how many sentences does the word appear. Calculated words are 
	 * cached until the calculator is cleared.
	 * @param word
	 * @return
	 */
	public static double getGlobalIdf(String word) {
		if (textSentences == null) {
			throw new RuntimeException("Global IDF CALC NOT INITIALIZED!");
		}
		//direct cache hit
		if (wordsIdfCache.containsKey(word)) {
			return wordsIdfCache.get(word);
		}
		long withWord = Arrays.stream(textSentences)
		.filter(sentence -> {
			int matches = StringUtils.countMatches(sentence, word);
			return matches > 0;
		}).count();
		
		double idf = (double)totalCount / (double)withWord;
		wordsIdfCache.put(word, idf);
		
		return idf;
	}
	
	
	public static void clear() {
		textSentences = null;
		totalCount = 0;
		wordsIdfCache.clear();
	}
	

}
