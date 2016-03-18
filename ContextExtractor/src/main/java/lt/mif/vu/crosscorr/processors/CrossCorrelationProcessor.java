package lt.mif.vu.crosscorr.processors;

import static lt.mif.vu.crosscorr.utils.SignalUtils.createSignalFromSeq;
import static lt.mif.vu.crosscorr.utils.SignalUtils.timeReverse;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import lt.mif.vu.crosscorr.utils.HomogenousPair;
import lt.mif.vu.crosscorr.utils.SentenceInfo;

public abstract class CrossCorrelationProcessor implements Runnable {

	private HomogenousPair<List<SentenceInfo>> cVectors;
	private HomogenousPair<List<Double>> eVectors;

	private DoubleFFT_1D transformer;

	public CrossCorrelationProcessor(List<Double> evector1, List<Double> evector2,
			List<SentenceInfo> cVector1, List<SentenceInfo> cVector2) {
		cVectors = new HomogenousPair<List<SentenceInfo>>(cVector1, cVector2);
		eVectors = new HomogenousPair<List<Double>>(evector1, evector2);
		// size of signals is pseudo-looped to sum of their lengths
		// after that, for algorithm size will be doubled to store 0 complex
		// values
		// for FFT size is doubled again for library requirements
		this.transformer = new DoubleFFT_1D(2 * eVectors.pairSize());
	}

	@Override
	public void run() {
		int signalLength = eVectors.pairSize();
		// create 2 signal length evector sequences for DFT
		List<Double> eVectorLSignal = createSignalFromSeq(eVectors.getLeft(), signalLength);
		List<Double> eVectorRSignal = timeReverse(
				createSignalFromSeq(eVectors.getRight(), signalLength));
		HomogenousPair<List<Double>> signalPair = new HomogenousPair<List<Double>>(eVectorLSignal,
				eVectorRSignal);

		HomogenousPair<double[]> transformedSpectra = transformLoopedSignals(signalPair);

		// perform multiply
		double[] left = transformedSpectra.getLeft();
		double[] right = transformedSpectra.getRight();

		double[] multiple = new double[left.length];
		for (int i = 0; i < left.length; i++) {
			multiple[i] = left[i] * right[i];
		}

		// inverse is the cross-correlation graph
		transformer.complexInverse(multiple, true);
		runFinished(multiple);
	}

	public abstract void runFinished(double[] resultCorr);

	private HomogenousPair<double[]> transformLoopedSignals(HomogenousPair<List<Double>> signals) {

		List<double[]> processedSignals = Stream.of(signals.getLeft(), signals.getRight()).map(
				signal -> {
					double[] outputSpectrum = signal.stream().mapToDouble(
							Double::doubleValue).toArray();
					// perform DFT for all them signals
					this.transformer.realForwardFull(outputSpectrum);

					return outputSpectrum;
				}).collect(Collectors.toList());

		return new HomogenousPair<>(processedSignals);
	}

}
