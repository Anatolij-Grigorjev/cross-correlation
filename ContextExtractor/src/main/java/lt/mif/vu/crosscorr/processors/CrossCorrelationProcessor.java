package lt.mif.vu.crosscorr.processors;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;
import lt.mif.vu.crosscorr.utils.HomogenousPair;
import lt.mif.vu.crosscorr.utils.SentenceInfo;
import static lt.mif.vu.crosscorr.utils.SignalUtils.*;

public class CrossCorrelationProcessor implements Runnable {
	
	private HomogenousPair<List<SentenceInfo>> cVectors;
	private HomogenousPair<List<Double>> eVectors;
	
	private DoubleFFT_1D transformer;

	public CrossCorrelationProcessor(
			List<Double> evector1
			, List<Double> evector2
			, List<SentenceInfo> cVector1
			, List<SentenceInfo> cVector2
	) {
		cVectors = new HomogenousPair<List<SentenceInfo>>(cVector1, cVector2);
		eVectors = new HomogenousPair<List<Double>>(evector1, evector2);
		//size of signals is pseudo-looped to sum of their lengths
		//after that, for algorithm size will be doubled to store 0 complex values
		//for FFT size is doubled again for library requirements
		this.transformer = new DoubleFFT_1D(2 * eVectors.pairSize());
	}

	@Override
	public void run() {
		int signalLength = eVectors.pairSize();
		//create 2 signal length evector sequences for DFT
		List<Double> eVectorLSignal = createSignalFromSeq(eVectors.getLeft(), signalLength);
		List<Double> eVectorRSignal = timeReverse(createSignalFromSeq(eVectors.getRight(), signalLength));
		HomogenousPair<List<Double>> signalPair = 
				new HomogenousPair<List<Double>>(eVectorLSignal, eVectorRSignal);
		
		HomogenousPair<double[]> transformedSpectra = transformLoopedSignals(signalPair);
		
	}


	private HomogenousPair<double[]> transformLoopedSignals(HomogenousPair<List<Double>> signals) {
		
		List<double[]> processedSignals = Stream.of(signals.getLeft(), signals.getRight()).map(signal -> {
			double[] outputSpectrum = signal.stream().mapToDouble(Double::doubleValue).toArray();
			//perform DFT for all them signals
			this.transformer.realForwardFull(outputSpectrum);
			
			return outputSpectrum;
		}).collect(Collectors.toList());
		
		return new HomogenousPair<>(processedSignals);
	}

}
