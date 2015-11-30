package lt.mif.vu.crosscorr.utils;

import java.util.Collection;
import java.util.Objects;

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
	
}
