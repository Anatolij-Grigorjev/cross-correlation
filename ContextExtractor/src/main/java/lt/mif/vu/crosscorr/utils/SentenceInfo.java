package lt.mif.vu.crosscorr.utils;

import lombok.Data;

/**
 * A holder bean for information about a sentence, including the 
 * sentence itself, the relevance score it got from algorithm 
 * applications and the original array index of it assigned by the sentence
 * detector
 * @author anatolij
 *
 */
@Data
public class SentenceInfo {
	
	private String sentence;
	private Double sentenceScore;
	private Integer originalIndex;

}
