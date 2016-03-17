package lt.mif.vu.crosscorr.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SignalUtils {
	private SignalUtils() {}

	public static List<Double> createSignalFromSeq(List<Double> seq, int signalSize) {
		List<Double> signal = new ArrayList<>(signalSize);
		int fullAdds = signalSize / seq.size();
		//add the whole signal, looping values to feign periodicity
		for (int i = 0; i < fullAdds; i++) { signal.addAll(seq); }
		int remainder = signalSize % seq.size();
		signal.addAll(seq.subList(0, remainder));
		//create array twice that for complex-0 storage
		double[] sigArr = new double[signal.size() * 2];
		Arrays.fill(sigArr, 0.0);
		for (int i = 0; i < signal.size(); i++) {
			sigArr[i * 2] = (double) signal.get(i);
		}
		//copy contents of new expanded array to the 2x scaled one for FFT
		Double[] outputSpectrum = new Double[sigArr.length * 2];
		Arrays.fill(outputSpectrum, 0.0);
		System.arraycopy(sigArr, 0, outputSpectrum, 0, sigArr.length);
		
		return Arrays.asList(outputSpectrum);
	}
	
	public static List<Double> timeReverse(List<Double> createSignalFromSeq) {
		// TODO Auto-generated method stub
		return null;
	}
	
}
