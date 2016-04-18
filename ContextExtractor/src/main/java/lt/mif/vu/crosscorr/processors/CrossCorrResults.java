package lt.mif.vu.crosscorr.processors;

import java.util.List;

import lombok.Data;
import lt.mif.vu.crosscorr.utils.model.SentencesDataPoint;

@Data
public class CrossCorrResults {
	
	private double[] eVectorCrossCorr;
	private List<SentencesDataPoint> cVectorCrossCorr;

}
