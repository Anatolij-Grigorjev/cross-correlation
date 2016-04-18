package lt.mif.vu.crosscorr.processors;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import lt.mif.vu.crosscorr.OutputAppender;
import lt.mif.vu.crosscorr.nlp.NLPUtil;
import lt.mif.vu.crosscorr.nlp.PartOfSpeech;
import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.utils.MathUtils;
import lt.mif.vu.crosscorr.utils.PrintUtils;
import lt.mif.vu.crosscorr.utils.model.SentenceInfo;
import lt.mif.vu.crosscorr.utils.model.TFIDF;
import lt.mif.vu.crosscorr.wordnet.WordNetUtils;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.PointerType;
import net.didion.jwnl.data.relationship.Relationship;
import net.didion.jwnl.data.relationship.RelationshipList;
import opennlp.tools.util.InvalidFormatException;

public abstract class CVectorProcessor implements Runnable {

	/**
	 * Largest search depth during wordnet hypernims searches.
	 */
	private static final int MAX_SEARCH_DEPTH = 4;
	/**
	 * Symbol used to separate text into pargraphs
	 */
	private static String PARAGRAPH_SEPARATOR = System.getProperty("line.separator");
	/**
	 * The separator that allows a searched replaceable to be replaced
	 * accounting for needed depth addition
	 */
	private static final String SEPARATOR = "@";
	private OutputAppender appender;
	private List<String> inputDocs;
	private NLPUtil nlpUtils;
	private WordNetUtils wordNetUtils;
	/**
	 * Stores depth facotrs needed to reach the replacers from tokens. for every
	 * one of them tokens, the depth is individual and sometimes might not be
	 * required, so the term gets a score reduction based on what portion
	 * required what depth
	 */
	private Map<String, List<Integer>> termWieghtReductionFactorMap;
	/**
	 * Known term replacements, when invoked and used also checked against 
	 * known replacement depth
	 */
	private Map<String, String> replaceables;
	
	private List<SentenceInfo> result;

	public CVectorProcessor(List<String> input, OutputAppender appender)
			throws InvalidFormatException, IOException {
		this.inputDocs = input;
		this.appender = appender;
		this.nlpUtils = NLPUtil.getInstance();
		this.wordNetUtils = WordNetUtils.getInstance();
		this.termWieghtReductionFactorMap = new HashMap<>();
		this.replaceables = new HashMap<>();
	}

	@Override
	public void run() {
		try {
			long start = System.currentTimeMillis();
			List<String> newDocs = new ArrayList<String>();
			// transform docs into an equal list of docs with synset-based terms
			// that repeat a lot
			// this needs to happen sequentially, because otherwise we lose
			// index-based association between source docs and resulting ones
			for (int i = 0; i < inputDocs.size(); i++) {
				String document = inputDocs.get(i);
				String newDocument = transformDocument(document, this::getNewTokensDoc);
				appender.appendOut("New old doc: " + newDocument);
				//this transform will ensure the same-sized sentence model
				String newOldDoc = transformDocument(document, this::addWhiteSpaceTokenDoc);
				inputDocs.set(i, newOldDoc);
				newDocs.add(newDocument);
			}

			// this work with paragraphs allows to correctly asses deviation of
			// TF*IDF across paragraphs, as opposed to being across documents

			// because are assembling a set of all terms, this doesn't need to
			// be sequential
			Set<String> allTermsDictionary = newDocs.parallelStream()
					.map(doc -> {
						try {
							return nlpUtils.getTokenizer().tokenize(doc);
						} catch (Exception e) {
							e.printStackTrace();
							return new String[0];
						}
					})
					.flatMap(Arrays::stream)
					.filter(StringUtils::isAlpha)
					.collect(Collectors.toSet());
			appender.appendOut("Total batch terms: " + allTermsDictionary.size() + "\n");
			// we create a chunk made of all paragraphs instead of all docs
			// for second TF*IDF
			List<String> allParagraphs = new ArrayList<String>();
			for (String document : newDocs) {
				String[] paragraphs = document.split(PARAGRAPH_SEPARATOR);
				allParagraphs.addAll(Arrays.asList(paragraphs));
			}
			appender.appendOut("Total paragraphs: " + allParagraphs.size());
			List<String> allSentences = allParagraphs.stream()
			.map(nlpUtils.getSentenceDetector()::sentDetect)
			.flatMap(Arrays::stream)
			.collect(Collectors.toList());
			appender.appendOut("Total sentences: " + allSentences.size());
			// calculate per-document TF*IDF. same process for paragraphs,
			// just different separator
			Map<String, List<Double>> parsTermsTFIDF = getChunksTFIDFMap(allParagraphs,
					allTermsDictionary);
			Map<String, List<Double>> sentTermsTFIDF = getChunksTFIDFMap(allSentences,
					allTermsDictionary);
			if (GlobalConfig.LOG_CVECTOR_VERBOSE) {
				appender.appendOut("DOCS TF*IDF:\n" + PrintUtils.printMapLines(parsTermsTFIDF) + "\n");
				appender.appendOut("PARS TF*IDF:\n" + PrintUtils.printMapLines(sentTermsTFIDF) + "\n");
			}
			// count up the major topic detection
			// deviation and dispersion equations
			// these influence the conditions, by which the majoring topics are
			// selected in text
			Map<String, Double> parsTermsDisp = getTFIDFDisp(parsTermsTFIDF);
			Map<String, List<Double>> parsTermsDev = getTFIDFDev(parsTermsTFIDF, parsTermsDisp);
			Map<String, Double> sentTermsDisp = getTFIDFDisp(sentTermsTFIDF);
			Map<String, List<Double>> sentTermsDev = getTFIDFDev(sentTermsTFIDF, sentTermsDisp);
			//modified map to print the entries nice n pretty
			Map<String, Double> wordRelevanceScores = new HashMap<String, Double>();
			
			appender.appendOut("Pre-score reduction factors: \n" + PrintUtils.printMapLines(termWieghtReductionFactorMap));
			
			if (GlobalConfig.LOG_CVECTOR_VERBOSE) {
				appender.appendOut("Paragraph z-values: " + PrintUtils.printMapLines(parsTermsDev));
				appender.appendOut("Sentence z-values: " + PrintUtils.printMapLines(sentTermsDev));
			}
			
			// doesn't matter which of the 2 maps to run since only the shared
			// tokens are explored
			for (Entry<String, List<Double>> docToken : parsTermsDev.entrySet()) {
				String token = docToken.getKey();
				List<Double> scores = docToken.getValue();
				// only if the tokens intersect
				if (sentTermsDev.containsKey(token)) {
					List<Double> sentScores = sentTermsDev.get(token);
					double docsSum = scores.stream().mapToDouble(Math::abs).max().orElse(0);
					double parsSum = sentScores.stream().mapToDouble(Math::abs).max().orElse(0);
					// total amount of term scores
					int size = scores.size() + sentScores.size();
					double scoreReduceSum = termWieghtReductionFactorMap.containsKey(token) ?
							termWieghtReductionFactorMap.get(token).stream().mapToDouble(Number::doubleValue).sum() 
							: 0;
					int reduceSize = termWieghtReductionFactorMap.containsKey(token)? size - termWieghtReductionFactorMap.get(token).size() : size;
					//got all things calculated, final score
					Double wordScore = Math.abs(docsSum - parsSum) * (size / (scoreReduceSum + reduceSize));
					
					wordRelevanceScores.put(token, wordScore);
				}
			}
			
			if (GlobalConfig.LOG_CVECTOR_VERBOSE) {
				appender.appendOut("The word relevance scores: \n" + PrintUtils.printWordRelevance(wordRelevanceScores));
			}
			//sentence scores according to sentence index
			String[] newDocSentences = nlpUtils.getSentenceDetector().sentDetect(newDocs.stream().collect(Collectors.joining()));
			//list of holder objects that would help maintain the original index after standard sorting
			List<SentenceInfo> infoList = new ArrayList<SentenceInfo>(newDocSentences.length);
			for (int i = 0; i < newDocSentences.length; i++) {
				String sentence = newDocSentences[i];
				Double sentenceScore = 0.0;
				for (String term: wordRelevanceScores.keySet()) {
					int occurences = StringUtils.countMatches(sentence, term);
					sentenceScore += (occurences * wordRelevanceScores.get(term));
				}
				
				SentenceInfo info = new SentenceInfo();
				info.setSentence(sentence);
				info.setOriginalIndex(i);
				info.setSentenceScore(sentenceScore);
				
				infoList.add(info);
			}
			//sorting needs to be done according to a score, so need to tie scores to actual doc sentences
			Collections.sort(infoList, (info1, info2) -> {
				//-1 because we want this sorted in descending order
				return -1 * info1.getSentenceScore().compareTo(info2.getSentenceScore());
			});
			
			String[] docsSentences = nlpUtils.getSentenceDetector().sentDetect(inputDocs.stream().collect(Collectors.joining()));
			appender.appendOut("\nActual sentences count: " + docsSentences.length + "\t Model sentences count: " + newDocSentences.length + "\n");
			
			result = infoList.subList(0, infoList.size() / 3); //keep 1/n of the list
			//and resort it by index to keep the structure flow
			Collections.sort(result, (inf1, inf2) -> inf1.getOriginalIndex().compareTo(inf2.getOriginalIndex()));
			
			appender.appendOut("\nREPRESENTATIVE SENTENCES (" + result.size() + "):\n");
			//print a nth of the list and save to file
			File file = new File("sentences-" + System.currentTimeMillis() + ".txt");
			try (BufferedWriter bw = new BufferedWriter(new FileWriter(file))) {
				for (int i = 0; i < result.size(); i++) {
					SentenceInfo info = infoList.get(i);
					String line = info.getOriginalIndex() + ". " + docsSentences[info.getOriginalIndex()] + " (SCORE: " + info.getSentenceScore() + ")\n";
					appender.appendOut(line);
					bw.write(line);
				}
			}
			
			appender.appendOut("\n\n------------------------------------------------\nRun finished after " + (System.currentTimeMillis() - start) + "ms");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			runFinished(result);
		}
	}

	private String transformDocument(String document, Function<String[], String> paragraphTransformer) {
		// the document needs to be separated into paragraphs with all
		// of them
		// happening individually and with same separator
		String[] docParagraphs = document.split(PARAGRAPH_SEPARATOR);
		appender.appendOut("Got " + docParagraphs.length + " paragraphs in doc!\n");
		StringBuilder newDocBuilder = new StringBuilder();
		Long tokensSum = 0l;
		
		for (int j = 0; j < docParagraphs.length; j++) {
			String[] docTokens = nlpUtils.getTokenizer().tokenize(docParagraphs[j]);
			tokensSum += docTokens.length;
			if (docTokens.length > 0) {
				if (GlobalConfig.LOG_CVECTOR_VERBOSE) {
					appender.appendOut("Got " + docTokens.length + " tokens! From paragraph #"
							+ (j + 1) + "\n");
				}
				String newDocumentParagraph = paragraphTransformer.apply(docTokens);
				// process one paragraph at a time and put the separator
				// after,
				// maintaining document structure
				newDocBuilder.append(newDocumentParagraph);
				newDocBuilder.append(PARAGRAPH_SEPARATOR);
			} else {
				if (GlobalConfig.LOG_CVECTOR_VERBOSE) {
					appender.appendOut("Skipping empty paragraph...\n");
				}
			}
		}
		appender.appendOut("Got " + tokensSum + " tokens from doc!\n");
		String newDocument = newDocBuilder.toString();
		return newDocument;
	}
	
	private String addWhiteSpaceTokenDoc(String[] tokens) {
		StringBuilder newParBuilder = new StringBuilder();
		for (String token: tokens) {
			newParBuilder.append(token);
			newParBuilder.append(' ');
		}
		return newParBuilder.toString();
	}
	

	private Map<String, List<Double>> getTFIDFDev(Map<String, List<Double>> termsTFIDF,
			Map<String, Double> termsDisp) {

		Map<String, List<Double>> termsDev = new HashMap<>();

		for (Entry<String, List<Double>> entry : termsTFIDF.entrySet()) {
			List<Double> values = entry.getValue();
			Double disp = termsDisp.get(entry.getKey());
			Double mean = MathUtils.avg(values);

			List<Double> devs = new ArrayList<Double>();
			for (Double tfIdf : values) {
				Double dev = (tfIdf - mean) / (Double.MIN_VALUE + disp);
				devs.add(dev);
			}
			termsDev.put(entry.getKey(), devs);
		}

		return termsDev;
	}

	private Map<String, Double> getTFIDFDisp(Map<String, List<Double>> termsTFIDF) {
		Map<String, Double> termsDisp = new HashMap<String, Double>();
		for (Entry<String, List<Double>> entry : termsTFIDF.entrySet()) {
			List<Double> tfIdfs = entry.getValue();
			Double valuesDisp = MathUtils.calcDispersion(tfIdfs);
			termsDisp.put(entry.getKey(), valuesDisp);
		}

		return termsDisp;
	}

	private Map<String, List<Double>> getChunksTFIDFMap(List<String> textChunks,
			Set<String> allTermsDictionary) {
		int termsCount = allTermsDictionary.size();
		Map<String, List<Double>> termDocumentTFIDF = new HashMap<String, List<Double>>();
		int docsCount = textChunks.size();
		for (String term : allTermsDictionary) {
			long occursInDocs = textChunks.parallelStream().filter(document -> document.contains(term)).count();
			//small customized list to print the scores nice n pretty
			List<Double> perDocScores = new ArrayList<Double>();;
			for (String document : textChunks) {
				int occurences = StringUtils.countMatches(document, term);
				perDocScores.add(new TFIDF(occurences, termsCount, docsCount, occursInDocs).getValue());
			}
			termDocumentTFIDF.put(term, perDocScores);
		}
		return termDocumentTFIDF;
	}

	private String getNewTokensDoc(String[] docTokens) {
		StringBuilder newDocBuilder = new StringBuilder();
		// pos tags for the doc tokens. needed later for wordnet comparison
		String[] posTags = nlpUtils.getPosTagger().tag(docTokens);
		// run doc tokens, building doc, getting synsets and gauging depth in
		// the process
		//TODO: Skip prepositions, you embarrassing yourself
		for (int i = 0; i < docTokens.length; i++) {
			String token = docTokens[i];
			String tag = posTags[i];
			PartOfSpeech pos = null;
			try {
				pos = PartOfSpeech.get(tag);
			} catch (Exception e) {
//				e.printStackTrace();
			}
			if (StringUtils.isAlpha(token) && (pos == null || pos.isMajorTag())) {
				// this is a word! lets see if it should be replaced
				if (replaceables.containsKey(token) 
						&& !replaceables.get(token).equalsIgnoreCase(token)) {
					// this is a know replaceable. appending replacement instead
					// accounting for possible depth
					String replaceable = replaceables.get(token);
					// depth to the replacement
					String finalReplacement = replaceable;
					int depth = 1;
					if (replaceable.contains(SEPARATOR)) {
						String[] split = replaceable.split(SEPARATOR);
						finalReplacement = split[0];
						depth = Integer.parseInt(split[1]);
					}
					if (depth != 1) 
						if (termWieghtReductionFactorMap.containsKey(finalReplacement)) {
							termWieghtReductionFactorMap.get(finalReplacement).add(depth);
						} else {
							termWieghtReductionFactorMap.put(finalReplacement,
									new ArrayList<Integer>(Arrays.asList(depth)));
						}
					if (GlobalConfig.LOG_CVECTOR_VERBOSE) {
						appender.appendOut("Replacing token " + token + " with replacer "
								+ finalReplacement + " and depth " + depth + "\n");
					}
					newDocBuilder.append(finalReplacement);
				} else {
					// wordnet indexing for word we search
					IndexWord indexWord = wordNetUtils.getIndexWord(token, posTags[i]);
					boolean loopAppended = false;
					// looping known replacers because this is faster if list is
					// empty
					for (String replacerComp : replaceables.values()) {
						// replacers might be ugly with their depth, keep it in
						// mind, but purify it
						String[] replacerSplit = replacerComp.split(SEPARATOR);
						// true replacer token
						String replacer = replacerSplit[0];
						// try for synset of the current token
						if (indexWord != null) {
							// the replacers all come from the same text
							// so they should always have an index in the
							// document
							int posTagIndex = Arrays.asList(docTokens).indexOf(replacer);
							//this is also the worng replaceable, form another paragraph or someshit
							if (posTagIndex < 0) continue;
							IndexWord replacerIndexWord = wordNetUtils.getIndexWord(replacer,
									posTags[posTagIndex]);
							if (replacerIndexWord == null) {
								// wordnet did not find an indexword for the
								// potential replacer
								// means we take our token further
								continue;
							}
							try {
								int relationship = wordNetUtils.getRelationFinder().getImmediateRelationship(indexWord,
										replacerIndexWord);
								if (relationship > -1 
										&& !token.equalsIgnoreCase(replacer)) {
									// found in the common synset, so not only
									// do we replace, we dont even
									// define a depth reduction
									newDocBuilder.append(replacer);
									// make sure we dont have to do this search
									// in the future
									if (GlobalConfig.LOG_CVECTOR_VERBOSE) {
										appender.appendOut("Token " + token
												+ " is replaced with synset member " + replacer + "\n");
									}
									replaceables.put(token, replacer);
									// set post-loop flag
									loopAppended = true;
									break;
								} else {
									// relationship not immediate, lets try
									// searching depths
									RelationshipList relationships = wordNetUtils.getRelationFinder().findRelationships(indexWord.getSense(1),
											replacerIndexWord.getSense(1),
											PointerType.HYPERNYM,
											MAX_SEARCH_DEPTH);
									// could not find similarity at even this
									// depth, time to pack it in...
									if (relationships.isEmpty()) {
										continue;
									} else {
										if (!token.equalsIgnoreCase(replacer)) {
											// begrudgingly adding term under great
											// duress - will be depth to pay
											newDocBuilder.append(replacer);
											loopAppended = true;
											int depth = ((Relationship) relationships.get(0)).getDepth();
											// add the depth to the depths this
											// keyword has had during its time
											if (termWieghtReductionFactorMap.containsKey(replacer)) {
												termWieghtReductionFactorMap.get(replacer).add(depth);
											} else {
												termWieghtReductionFactorMap.put(replacer,
														new ArrayList<Integer>(Arrays.asList(depth)));
											}
											// when we search the term again, we
											// know what to do without all this
											// depth search
											if (GlobalConfig.LOG_CVECTOR_VERBOSE) {
												appender.appendOut("Token " + token
														+ " is replaced with hypernim member " + replacer
														+ " at depth " + depth + "\n");
											}
											replaceables.put(token, replacer + SEPARATOR + depth);
											break;
										}
									}
								}
							} catch (JWNLException e) {
								e.printStackTrace();
								continue;
							}
						}
					}
					// none of the members of the loop were a suitable
					// candidate, this is a root word then, just append as-is
					if (!loopAppended) {
						newDocBuilder.append(token);
						// set it as replacer for itself to get the map ball
						// rolling
						// form here others will grab it for synonyms
						// and deep relations
						if (GlobalConfig.LOG_CVECTOR_VERBOSE) {
							appender.appendOut("Root replacer: " + token + "\n");
						}
						replaceables.put(token, token);
					}
				}
			} else {
				// not a word, so no use wordnetting, just move it along
				newDocBuilder.append(token);
			}
			// a space is appended between all tokens.
			// this loses doc syntax, but is good for TFIDF
			newDocBuilder.append(' ');
		}
		return newDocBuilder.toString();
	}

	/**
	 * The delegate to be implemented by target class, this fires after all 
	 * processing is complete
	 */
	public abstract void runFinished(List<SentenceInfo> result);

}
