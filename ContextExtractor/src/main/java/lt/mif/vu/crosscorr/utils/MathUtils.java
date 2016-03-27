package lt.mif.vu.crosscorr.utils;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

public class MathUtils {

	
	private MathUtils() {
	}
	
	
	public static Double calcDispersion(Collection<Double> values) {
		double mean = avg(values);
		double sum = 0;
		for (Double score: values) {
			sum += Math.pow((score - mean), 2);
		}
		sum /= (double)values.size();
		double disp = Math.sqrt(sum);
		
		return disp;
	}
	
	public static Double sqr(Double a) {
		return Math.pow(a,  2);
	}
	
	public static Double avg(Collection<? extends Number> nums) {
		return nums.stream()
		.filter(Objects::nonNull)
		.mapToDouble(Number::doubleValue)
		.average()
		.orElse(0);
	}


	public static double dampen(Double toDampen, Double dampener,
			Integer dampeningFactor) {
		
		Double dampened = toDampen;
		for (int i = 0; i < dampeningFactor; i++) {
			dampened += dampener;
			dampened /= 2.0;
		}
		
		return dampened;
	}
	
	
	 public static long gcd(long a, long b)
	 {
	     while (b > 0)
	     {
	         long temp = b;
	         b = a % b; // % is remainder
	         a = temp;
	     }
	     return a;
	 }
	 
	 public static long lcm(long a, long b)
	 {
	     return a * (b / gcd(a, b));
	 }
	 
	 private static double getSignalSliceAvg(List<Double> sig, int N, int delay, boolean offset) {
		 double multiple = 1.0 / (double)(N - delay + 1);
		 int substart = offset? delay : 0;
		 double sum = sig.subList(substart, N - delay).stream().mapToDouble(Double::doubleValue).sum();
		 return sum * multiple;
	 }
	 
	 public static double getCrossCorrelationAt(List<Double> sig1, List<Double> sig2, int N, int delay) {
		 double sig1Slice = getSignalSliceAvg(sig1, N, delay, false);
		 double sig2Slice = getSignalSliceAvg(sig2, N, delay, true);
		 
		 double numerator = IntStream.range(0, N - delay).mapToDouble(j -> 
			 (sig1.get(j) - sig1Slice) * (sig2.get(j + delay) - sig2Slice)
		 ).sum();
		 
		 double denom1 = IntStream.range(0,  N - delay).mapToDouble(j ->
		 	(sig1.get(j) - sig1Slice) * (sig1.get(j) - sig1Slice)
		 ).sum();
		 double denom2 = IntStream.range(0,  N - delay).mapToDouble(j ->
		 	(sig2.get(delay + j) - sig2Slice) * (sig2.get(delay + j) - sig2Slice)
		 ).sum();
		 
		 double denominator = Math.sqrt(denom1 * denom2);
		 
		 return numerator / denominator;
	 }
	
}
