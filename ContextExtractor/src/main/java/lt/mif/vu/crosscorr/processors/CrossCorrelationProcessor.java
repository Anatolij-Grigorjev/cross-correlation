package lt.mif.vu.crosscorr.processors;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.commons.lang.ArrayUtils;

import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.utils.HomogenousPair;
import lt.mif.vu.crosscorr.utils.MathUtils;
import lt.mif.vu.crosscorr.utils.SentenceInfo;

public abstract class CrossCorrelationProcessor implements Runnable {

	private HomogenousPair<List<SentenceInfo>> cVectors;
	private HomogenousPair<List<Double>> eVectors;

	

	public CrossCorrelationProcessor(List<Double> evector1, List<Double> evector2,
			List<SentenceInfo> cVector1, List<SentenceInfo> cVector2) {
		cVectors = new HomogenousPair<List<SentenceInfo>>(cVector1, cVector2);
		eVectors = new HomogenousPair<List<Double>>(evector1, evector2);
		
	}

	@Override
	public void run() {
		double[] crossCorr = performEVectorCorrelation();
		double[] cvectorCross = performCVectorCorrelation();
		CrossCorrResults corrResults = new CrossCorrResults();
		corrResults.setEVectorCrossCorr(crossCorr);
		corrResults.setCVectorCrossCorr(cvectorCross);
		runFinished(corrResults);
	}

	private double[] performCVectorCorrelation() {
		
		cVectors.getLeft().forEach(sentenceInfoLeft -> {
			cVectors.getRight().forEach(sentenceScoreRight -> {
				
//				sim(T_1, T_2) = \frac{1}{2}
//				\big(
//				\frac{\sum_{w \in \{T_1\}}^{}(maxSim(w, T_2)*idf(w))}{\sum_{w \in \{T_1\}}^{}idf(w)}
//				+
//				\frac{\sum_{w \in \{T_2\}}^{}(maxSim(w, T_1)*idf(w))}{\sum_{w \in \{T_2\}}^{}idf(w)}
//				)
				
//				maxSim - according to Wu And Palmer:
//				http://stackoverflow.com/questions/17750234/ws4j-returns-infinity-for-similarity-measures-that-should-return-1
			});
		});
		
		// TODO Auto-generated method stub
		return null;
	}

	private double[] performEVectorCorrelation() {
		inflateEViaC(eVectors.getLeft(), cVectors.getLeft());
		inflateEViaC(eVectors.getRight(), cVectors.getRight());
		int sizeBound = Math.min(eVectors.getLeft().size(), eVectors.getRight().size());
		int delayBound = sizeBound / 2;
		//first with signals swapped
		double[] crossCorr1 = IntStream.range(-1* delayBound, 0)
		.mapToDouble(d -> MathUtils.getCrossCorrelationAt(eVectors.getRight(), eVectors.getLeft(), sizeBound, Math.abs(d)))
		.toArray();
		double[] crossCorr2 = IntStream.rangeClosed(0, delayBound)
		.mapToDouble(d -> MathUtils.getCrossCorrelationAt(eVectors.getLeft(), eVectors.getRight(), sizeBound, d))
		.toArray();
		
		double[] crossCorr = ArrayUtils.addAll(crossCorr1, crossCorr2);
		return crossCorr;
	}

	private void inflateEViaC(List<Double> eVector, List<SentenceInfo> cVector) {
		cVector.forEach(cvec -> {
			int ind = cvec.getOriginalIndex();
			double emotion = eVector.get(ind);
			IntStream.rangeClosed(ind - GlobalConfig.CONTEXT_RESONANCE, ind + GlobalConfig.CONTEXT_RESONANCE)
				.forEach(i -> {
					// no need to add an extra time for the index itself
					// also need to avoid under/over-flows 
					if (i > 0 && i < eVector.size() && i != ind) {
						eVector.add(i, emotion);
					}
				});
		});
	}

	public abstract void runFinished(CrossCorrResults resultCorr);

}
