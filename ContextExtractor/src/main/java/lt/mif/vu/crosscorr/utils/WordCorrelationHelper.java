package lt.mif.vu.crosscorr.utils;

import edu.cmu.lti.lexical_db.ILexicalDatabase;
import edu.cmu.lti.lexical_db.NictWordNet;
import edu.cmu.lti.ws4j.RelatednessCalculator;
import edu.cmu.lti.ws4j.impl.WuPalmer;

public class WordCorrelationHelper {
	
	private ILexicalDatabase db;
    private RelatednessCalculator wuPalmer;
    private static WordCorrelationHelper instance;
    
    private WordCorrelationHelper() {
    	db = new NictWordNet();
    	wuPalmer = new WuPalmer(db);
    }
    
    public static WordCorrelationHelper getInstance() {
    	if (instance == null) {
    		instance = new WordCorrelationHelper();
    	}
    	return instance;
    }
	
    public double getWordsCorr(String word1, String word2) {
    	double relatednessOfWords = wuPalmer.calcRelatednessOfWords(word1, word2);
    	//might have eclipsed from bad calc
    	return MathUtils.clamp(relatednessOfWords, 0.0, 1.0);
    }
    
}
