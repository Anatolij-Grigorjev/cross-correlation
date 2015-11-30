package lt.mif.vu.crosscorr.wordnet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import lombok.Getter;
import lt.mif.vu.crosscorr.nlp.PartOfSpeech;
import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.IndexWord;
import net.didion.jwnl.data.POS;
import net.didion.jwnl.data.relationship.RelationshipFinder;
import net.didion.jwnl.dictionary.Dictionary;

public class WordNetUtils {

	private static final Set<PartOfSpeech> PARSABLE_POS = new HashSet<PartOfSpeech>(
			Arrays.asList(PartOfSpeech.ADJECTIVE,
					PartOfSpeech.ADJECTIVE_COMPARATIVE,
					PartOfSpeech.ADJECTIVE_SUPERLATIVE,
					PartOfSpeech.ADVERB,
					PartOfSpeech.ADVERB_COMPARATIVE,
					PartOfSpeech.ADVERB_SUPERLATIVE,
					PartOfSpeech.ADVERB_WH,
					PartOfSpeech.NOUN,
					PartOfSpeech.NOUN_PLURAL,
					PartOfSpeech.NOUN_PROPER_PLURAL,
					PartOfSpeech.NOUN_PROPER_SINGULAR,
					PartOfSpeech.VERB,
					PartOfSpeech.VERB_MODAL,
					PartOfSpeech.VERB_PARTICIPLE_PAST,
					PartOfSpeech.VERB_PARTICIPLE_PRESENT,
					PartOfSpeech.VERB_PAST_TENSE,
					PartOfSpeech.VERB_SINGULAR_PRESENT_NONTHIRD_PERSON,
					PartOfSpeech.VERB_SINGULAR_PRESENT_THIRD_PERSON));
	@Getter
	private Dictionary dictionary;
	@Getter
	private RelationshipFinder relationFinder;
	private static WordNetUtils instance;

	private WordNetUtils() {
		try {
			JWNL.initialize(
					WordNetUtils.class.getClassLoader().getResourceAsStream("properties.xml"));
			this.dictionary = Dictionary.getInstance();
			this.relationFinder = RelationshipFinder.getInstance();
		} catch (JWNLException e) {
			e.printStackTrace();
		}
	}

	public IndexWord getIndexWord(String word, String pos) {
		try {
			PartOfSpeech partOfSpeech = PartOfSpeech.get(pos);
			return getIndexWord(word, partOfSpeech);
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	private POS toWNPOS(PartOfSpeech partOfSpeech) {

		switch (partOfSpeech) {
		case ADJECTIVE:
		case ADJECTIVE_COMPARATIVE:
		case ADJECTIVE_SUPERLATIVE:
			return POS.ADJECTIVE;
		case ADVERB:
		case ADVERB_COMPARATIVE:
		case ADVERB_SUPERLATIVE:
		case ADVERB_WH:
			return POS.ADVERB;
		case NOUN:
		case NOUN_PLURAL:
		case NOUN_PROPER_PLURAL:
		case NOUN_PROPER_SINGULAR:
			return POS.NOUN;
		case VERB:
		case VERB_MODAL:
		case VERB_PARTICIPLE_PAST:
		case VERB_PARTICIPLE_PRESENT:
		case VERB_PAST_TENSE:
		case VERB_SINGULAR_PRESENT_NONTHIRD_PERSON:
		case VERB_SINGULAR_PRESENT_THIRD_PERSON:
			return POS.VERB;
		default:
			return POS.NOUN;
		}
	}

	public boolean isParsablePOS(PartOfSpeech pos) {
		return PARSABLE_POS.contains(pos);
	}

	public IndexWord getIndexWord(String word, PartOfSpeech pos) {
		try {
			return dictionary.lookupIndexWord(toWNPOS(pos), word);
		} catch (JWNLException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static WordNetUtils getInstance() {
		if (instance == null) {
			instance = new WordNetUtils();
		}
		return instance;
	}

}
