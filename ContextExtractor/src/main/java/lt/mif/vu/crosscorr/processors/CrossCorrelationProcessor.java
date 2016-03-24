package lt.mif.vu.crosscorr.processors;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.commons.lang.ArrayUtils;

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
		int sizeBound = Math.min(eVectors.getLeft().size(), eVectors.getRight().size());
		int delayBound = sizeBound / 2;
		//first with signals swapped
		double[] crossCorr1 = IntStream.range(-1* delayBound, 0)
		.mapToDouble(d -> MathUtils.getCrossCorrelationAt(eVectors.getRight(), eVectors.getLeft(), sizeBound, d))
		.toArray();
		double[] crossCorr2 = IntStream.rangeClosed(0, delayBound)
		.mapToDouble(d -> MathUtils.getCrossCorrelationAt(eVectors.getLeft(), eVectors.getRight(), sizeBound, d))
		.toArray();
		
		double[] crossCorr = ArrayUtils.addAll(crossCorr1, crossCorr2);
		
		runFinished(crossCorr);
	}

	public abstract void runFinished(double[] resultCorr);

}
