package lt.mif.vu.crosscorr.stanfordnlp;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations.SentimentClass;
import lt.mif.vu.crosscorr.utils.GlobalConfig;

public class StanfordNLPUtils {

	//"Very negative", "Negative", "Neutral", "Positive", "Very positive"
	private static final Map<Integer, String> SENTIMENT_CLASSES = new HashMap<>();
	static {
		SENTIMENT_CLASSES.put(0, "Very negative");
		SENTIMENT_CLASSES.put(1, "Negative");
		SENTIMENT_CLASSES.put(2, "Neutral");
		SENTIMENT_CLASSES.put(3, "Positive");
		SENTIMENT_CLASSES.put(4, "Very positive");
	}
	
	StanfordCoreNLP sentimentAnalyzer;
	
	private static StanfordNLPUtils instance;
	
	private StanfordNLPUtils() {
		Properties requiredAnnotators = new Properties();
		requiredAnnotators.setProperty("annotators", "tokenize, ssplit, pos, parse, sentiment");
		this.sentimentAnalyzer = new StanfordCoreNLP(requiredAnnotators);
	}
	
	
	public String analyzeString(String text) {
		Annotation annotation = new Annotation(text);
		this.sentimentAnalyzer.annotate(annotation);
		
		String sentenceSentiments = annotation.get(SentencesAnnotation.class).stream()
			.map(sentence -> sentence.get(SentimentClass.class))
			.filter(senClass -> !("Neutral".equals(senClass)))
			.findFirst().orElse("Neutral");
		
		return sentenceSentiments;
	}
	
	
	public static String closestSentimentClass(double value) {
		int roundedClass = (int) GlobalConfig.APPROXIMATOR.approximate(value);
		
		if (roundedClass > 4 || roundedClass < 0) {
			throw new RuntimeException("Unkown sentiment class: " + roundedClass);
		}
		
		return SENTIMENT_CLASSES.get(roundedClass);
	}
	
	public static int sentimentClassIndex(String sentiment) {
		
		if (SENTIMENT_CLASSES.containsValue(sentiment)) {
			
			return SENTIMENT_CLASSES.entrySet().stream()
				.filter(entry -> entry.getValue().equals(sentiment))
				.mapToInt(Map.Entry::getKey)
				.findFirst()
				.getAsInt();
				
			
		} else {
			throw new RuntimeException("No index found for sentiment class: " + sentiment);
		}
		
	}


	public static StanfordNLPUtils getInstance() {
		if (instance == null) {
			instance = new StanfordNLPUtils();
		}
		
		return instance;
	}
	
	
}
