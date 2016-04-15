package lt.mif.vu.crosscorr.processors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import javax.xml.ws.Holder;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import lt.mif.vu.crosscorr.nlp.NLPUtil;
import lt.mif.vu.crosscorr.nlp.PartOfSpeech;
import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.utils.GlobalIdfCalculator;
import lt.mif.vu.crosscorr.utils.HomogenousPair;
import lt.mif.vu.crosscorr.utils.MathUtils;
import lt.mif.vu.crosscorr.utils.SentenceInfo;
import lt.mif.vu.crosscorr.utils.WordCorrelationHelper;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerME;

public abstract class CrossCorrelationProcessor implements Runnable {

	private HomogenousPair<List<SentenceInfo>> cVectors;
	private HomogenousPair<List<Double>> eVectors;

	

	public CrossCorrelationProcessor(List<Double> evector1, List<Double> evector2,
			List<SentenceInfo> cVector1, List<SentenceInfo> cVector2) {
		cVectors = new HomogenousPair<List<SentenceInfo>>(cVector1, cVector2);
		eVectors = new HomogenousPair<List<Double>>(evector1, evector2);
		
	}

	@Override
	public void run() {
		double[] crossCorr = performEVectorCorrelation();
		double[] cvectorCross = performCVectorCorrelation();
		CrossCorrResults corrResults = new CrossCorrResults();
		corrResults.setEVectorCrossCorr(crossCorr);
		corrResults.setCVectorCrossCorr(cvectorCross);
		runFinished(corrResults);
	}

	private double[] performCVectorCorrelation() {
		Map<HomogenousPair<SentenceInfo>, Double> sentencePairScores = new HashMap<>();
		cVectors.getLeft().forEach(sentenceInfoLeft -> {
			cVectors.getRight().forEach(sentenceInfoRight -> {
				
				double similarity = 0.5 * (
						simToIdfRelation(sentenceInfoLeft.getSentence(), sentenceInfoRight.getSentence())
						+
						simToIdfRelation(sentenceInfoRight.getSentence(), sentenceInfoLeft.getSentence())
						);
				
				HomogenousPair<SentenceInfo> key = new HomogenousPair<>(sentenceInfoLeft, sentenceInfoRight);
				sentencePairScores.put(key, similarity);
				
				
				
//				sim(T_1, T_2) = \frac{1}{2}
//				\big(
//				\frac{\sum_{w \in \{T_1\}}^{}(maxSim(w, T_2)*idf(w))}{\sum_{w \in \{T_1\}}^{}idf(w)}
//				+
//				\frac{\sum_{w \in \{T_2\}}^{}(maxSim(w, T_1)*idf(w))}{\sum_{w \in \{T_2\}}^{}idf(w)}
//				)
				
//				maxSim - according to Wu And Palmer:
//				http://stackoverflow.com/questions/17750234/ws4j-returns-infinity-for-similarity-measures-that-should-return-1
			});
		});
		
		List<Double> maxValues = new ArrayList<>();
		Holder<Double> currMax = new Holder<Double>(0.0);
		Holder<SentenceInfo> noteablePairing = new Holder<>();
		Set<SentenceInfo> noTouchy = new HashSet<>();
		cVectors.getLeft().forEach(sentenceInfoLeft -> {
			currMax.value = 0.0;
			noteablePairing.value = null;
			cVectors.getRight().forEach(sentenceInfoRight -> {
				if (!noTouchy.contains(sentenceInfoRight)) {
					HomogenousPair<SentenceInfo> pair = new HomogenousPair<>(sentenceInfoLeft, sentenceInfoRight);
					Double value = sentencePairScores.get(pair);
					if (value > currMax.value) {
						currMax.value = value;
						noteablePairing.value = sentenceInfoRight;
					}
				}
			});
			maxValues.add(currMax.value);
			noTouchy.add(noteablePairing.value);
		});
		
		return maxValues.stream().mapToDouble(Double::doubleValue).toArray();
	}

	private double simToIdfRelation(String block1, String block2) {
		TokenizerME tokenizer = NLPUtil.getInstance().getTokenizer();
		String[] block1Tokens = tokenizer.tokenize(block1);
		IntStream block1Filter = getMajorPOSFilter(block1Tokens);
		
		double globalIdfSum = block1Filter.mapToDouble(index -> 
			GlobalIdfCalculator.getGlobalIdf(block1Tokens[index])
		).sum();
		
		//refresh the start of stream
		block1Filter = getMajorPOSFilter(block1Tokens);
		double idfMaxSimSum = block1Filter.mapToDouble(index -> {
			double idf = GlobalIdfCalculator.getGlobalIdf(block1Tokens[index]);
			double maxSim = getMaxSimilarity(block1Tokens[index], block2);
			
			return idf * maxSim;
		}).sum();
		
		return idfMaxSimSum / globalIdfSum;
	}

	private double getMaxSimilarity(String word1, String block2) {
		TokenizerME tokenizer = NLPUtil.getInstance().getTokenizer();
		String[] blockTokens = tokenizer.tokenize(block2);
		IntStream majorPOSFilter = getMajorPOSFilter(blockTokens);
		WordCorrelationHelper corr = WordCorrelationHelper.getInstance();
		
		return majorPOSFilter
				.mapToDouble(index -> corr.getWordsCorr(word1, blockTokens[index]))
				.max()
				.orElse(0.0);
	}

	private IntStream getMajorPOSFilter(String[] tokens) {
		POSTaggerME posTagger = NLPUtil.getInstance().getPosTagger();
		
		String[] textPOS = posTagger.tag(tokens);
		
		return IntStream.range(0, tokens.length).filter(index -> {
			PartOfSpeech pos = null;
			try {
				pos = PartOfSpeech.get(
						StringUtils.strip(textPOS[index], " -$*|,./\\")
				);
				//only using similarity measures on "major" tag words, such as 
				//nouns, verbs, adjectives and adverbs
				return pos != null && pos.isMajorTag();
			} catch (Exception e) {
//				e.printStackTrace();
				return false;
			}
		});
	}

	private double[] performEVectorCorrelation() {
		inflateEViaC(eVectors.getLeft(), cVectors.getLeft());
		inflateEViaC(eVectors.getRight(), cVectors.getRight());
		int sizeBound = Math.min(eVectors.getLeft().size(), eVectors.getRight().size());
		int delayBound = sizeBound / 2;
		//first with signals swapped
		double[] crossCorr1 = IntStream.range(-1* delayBound, 0)
		.mapToDouble(d -> MathUtils.getCrossCorrelationAt(eVectors.getRight(), eVectors.getLeft(), sizeBound, Math.abs(d)))
		.toArray();
		double[] crossCorr2 = IntStream.rangeClosed(0, delayBound)
		.mapToDouble(d -> MathUtils.getCrossCorrelationAt(eVectors.getLeft(), eVectors.getRight(), sizeBound, d))
		.toArray();
		
		double[] crossCorr = ArrayUtils.addAll(crossCorr1, crossCorr2);
		return crossCorr;
	}

	private void inflateEViaC(List<Double> eVector, List<SentenceInfo> cVector) {
		cVector.forEach(cvec -> {
			int ind = cvec.getOriginalIndex();
			double emotion = eVector.get(ind);
			IntStream.rangeClosed(ind - GlobalConfig.CONTEXT_RESONANCE, ind + GlobalConfig.CONTEXT_RESONANCE)
				.forEach(i -> {
					// no need to add an extra time for the index itself
					// also need to avoid under/over-flows 
					if (i > 0 && i < eVector.size() && i != ind) {
						eVector.add(i, emotion);
					}
				});
		});
	}

	public abstract void runFinished(CrossCorrResults resultCorr);

}
