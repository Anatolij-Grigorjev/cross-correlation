package lt.mif.vu.crosscorr.utils.model;

import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;


public class HomogenousPair<T> extends Pair<T, T> {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -2597885830504247871L;
	private ImmutablePair<T, T> pair;
	
	public HomogenousPair(T left, T right) {
		pair = new ImmutablePair<T, T>(left, right);
	}
	
	public HomogenousPair(List<T> items) {
		if (items.size() >= 2) {
			pair = new ImmutablePair<T, T>(items.get(0), items.get(1));
		}
	}
	

	@Override
	public T setValue(T value) {
		throw new NotImplementedException();
	}

	@Override
	public T getLeft() {
		return pair.left;
	}

	@Override
	public T getRight() {
		return pair.right;
	}
	
	/**
	 * Tries to calculate the sum of the elements of the pair.
	 * if the elements are collections, this is the sum of their sizes
	 * if the elements are arrays, this is the size of their lengths
	 * If the elements are numbers, this is their sum
	 * Otherwise 0
	 * 
	 * @return determined sum of pair
	 */
	public int pairSize() {
		if (pair.left instanceof Collection) {
			return ((Collection) pair.left).size() + ((Collection) pair.right).size();
		} else if (pair.left.getClass().isArray()) {
			return ((double[]) pair.left).length + ((double[]) pair.right).length;
		} else if (pair.left instanceof Number) {
			return ((Number) pair.left).intValue() + ((Number) pair.right).intValue();
		} else {
			return 0;
		}
	}
	
	/**
	 * <code>true</code> if one of the elements in this pair is the one provided
	 * 
	 * @param value
	 * @return
	 */
	public boolean contains(T value) {
		return ObjectUtils.equals(pair.left, value) 
				|| ObjectUtils.equals(pair.right, value);
	}

}
