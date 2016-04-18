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

import lt.mif.vu.crosscorr.OutputAppender;
import lt.mif.vu.crosscorr.nlp.NLPUtil;
import lt.mif.vu.crosscorr.nlp.PartOfSpeech;
import lt.mif.vu.crosscorr.utils.GlobalConfig;
import lt.mif.vu.crosscorr.utils.GlobalIdfCalculator;
import lt.mif.vu.crosscorr.utils.MathUtils;
import lt.mif.vu.crosscorr.utils.WordCorrelationHelper;
import lt.mif.vu.crosscorr.utils.model.HomogenousPair;
import lt.mif.vu.crosscorr.utils.model.SentenceInfo;
import lt.mif.vu.crosscorr.utils.model.SentencesDataPoint;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.tokenize.TokenizerME;

public abstract class CrossCorrelationProcessor implements Runnable {

	private HomogenousPair<List<SentenceInfo>> cVectors;
	private HomogenousPair<List<Double>> eVectors;
	private OutputAppender appender;

	

	public CrossCorrelationProcessor(List<Double> evector1, List<Double> evector2,
			List<SentenceInfo> cVector1, List<SentenceInfo> cVector2
			, OutputAppender appender) {
		cVectors = new HomogenousPair<List<SentenceInfo>>(cVector1, cVector2);
		eVectors = new HomogenousPair<List<Double>>(evector1, evector2);
		this.appender = appender;
	}

	@Override
	public void run() {
		double[] crossCorr = performEVectorCorrelation();
		appender.appendOut("Done with eVector!\n");
		List<SentencesDataPoint> cvectorCross = performCVectorCorrelation();
		appender.appendOut("Done with cVector!\n");
		CrossCorrResults corrResults = new CrossCorrResults();
		corrResults.setEVectorCrossCorr(crossCorr);
		corrResults.setCVectorCrossCorr(cvectorCross);
		appender.appendOut("Done!\nBuilding graphs...");
		runFinished(corrResults);
	}

	private List<SentencesDataPoint> performCVectorCorrelation() {
		appender.appendOut("Fetching cVector correlation...\n");
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
		appender.appendOut("Got Wu-Palmer scores for " + sentencePairScores.size() + " sentence pairs!\n");
		List<SentencesDataPoint> maxValues = new ArrayList<>();
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
			if (currMax.value > 0.0 && noteablePairing.value != null) {
				maxValues.add(
						new SentencesDataPoint(
								sentenceInfoLeft.getSentence()
								, noteablePairing.value.getSentence()
								, currMax.value
						)
				);
			}
			//already used up pairing
			noTouchy.add(noteablePairing.value);
		});
		
		return maxValues;
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
		appender.appendOut("Fetching eVector correlation...\n");
		appender.appendOut("Inflating " + eVectors.getLeft().size()
				+ " sentiments with " + cVectors.getLeft().size() 
				+ " topical sentences\n");
		inflateEViaC(eVectors.getLeft(), cVectors.getLeft());
		appender.appendOut("Inflating " + eVectors.getRight().size()
				+ " sentiments with " + cVectors.getRight().size() 
				+ " topical sentences\n");
		inflateEViaC(eVectors.getRight(), cVectors.getRight());
		int sizeBound = Math.min(eVectors.getLeft().size(), eVectors.getRight().size());
		appender.appendOut("Sentiments size bound: " + sizeBound + "\n");
		int delayBound = sizeBound / 2;
		//first with signals swapped
		appender.appendOut("Correlating negative delay...\n");
		double[] crossCorr1 = IntStream.range(-1* delayBound, 0)
		.mapToDouble(d -> MathUtils.getCrossCorrelationAt(eVectors.getRight(), eVectors.getLeft(), sizeBound, Math.abs(d)))
		.toArray();
		appender.appendOut("Correlating positive delay...\n");
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
