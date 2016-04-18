package lt.mif.vu.crosscorr.utils;

import lt.mif.vu.crosscorr.utils.enums.Algorithm;
import lt.mif.vu.crosscorr.utils.enums.Approximators;

/**
 * System configuration access
 */
public class GlobalConfig {
	private GlobalConfig() {}
	
	public static Boolean LOG_CVECTOR_VERBOSE = Boolean.FALSE;
	public static Boolean LOG_EVECTOR_VERBOSE = Boolean.FALSE;
	public static Algorithm SELECTED_ALGORITHM = Algorithm.FRONT_TO_BACK;
	public static Integer DAMPENING_FACTOR = 1;
	public static Integer CONTEXT_RESONANCE = 2;
	public static Approximators APPROXIMATOR = Approximators.ROUND;
	
}
