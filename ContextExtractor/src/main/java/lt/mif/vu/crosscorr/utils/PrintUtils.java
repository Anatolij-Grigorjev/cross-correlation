package lt.mif.vu.crosscorr.utils;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class PrintUtils {
	
	public static String printSentences(String[] sentences) {
		StringBuilder builder = new StringBuilder();
		for(int i = 0; i < sentences.length; i++) {
			builder.append(i);
			builder.append(". ");
			builder.append(sentences[i]);
			builder.append('\n');
		}
		
		return builder.toString();
	}
	
	
	public static String printWordRelevance(Map<String, Double> wordRelevanceMap) {
		Map entries = wordRelevanceMap.entrySet().stream()
				.filter(entry -> !entry.getValue().isNaN())
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue));
		
		return printMapLines(entries);
	}

	public static <K, V> String printEntryLine(Entry<K, V> entry) {
		StringBuilder builder = new StringBuilder();
		builder.append(entry.getKey());
		builder.append(" = ");
		builder.append(entry.getValue());
		builder.append("\n");
		
		return builder.toString();
	}

	public static <K, V> String printMapLines(Map<K, V> map) {
		StringBuilder builder = new StringBuilder();
		builder.append("{\n");
		map.entrySet().forEach(entry -> builder.append(printEntryLine(entry)));
		builder.append("}\n");
		return builder.toString();
	}


	public static <K, V> String printGroupingMap(Map<K, V> map) {
		if (map == null) {
			return "<no map>";
		}
		StringBuilder builder = new StringBuilder();
		map.forEach((key, val) -> {
			if (key == null) {
				builder.append("null");
			} else {
				builder.append(key.toString());
			}
			builder.append(": ");
			if (val instanceof Number) {
				builder.append(val);
			} else if (val instanceof Collection) {
				builder.append(((Collection) val).size());
			} else {
				builder.append(val);
			}
			builder.append("\n");
			
		});
		
		return builder.toString();
	}

}
