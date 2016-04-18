package lt.mif.vu.crosscorr.utils.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SentencesDataPoint {
	
	private String sentence1;
	private String sentence2;
	private double relatednessScore;
	
}
