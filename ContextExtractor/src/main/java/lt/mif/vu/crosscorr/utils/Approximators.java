package lt.mif.vu.crosscorr.utils;

import java.util.function.Function;

import lombok.Getter;

public enum Approximators {
	
	CEIL(Math::ceil, "Positive (ceil)"),
	FLOOR(Math::floor, "Negative (floor)"),
	ROUND(Math::round, "Neutral (round)")
	;
	
	private Function<Double, Number> approxFunction;
	@Getter
	private String description;
	
	private Approximators(Function<Double, Number> approx, String description) {
		this.approxFunction = approx;
		this.description = description;
	}
	
	public Number approximate(double val) {
		return this.approxFunction.apply(val);
	}
	
	@Override
	public String toString() {
		return description;
	}
	
}
