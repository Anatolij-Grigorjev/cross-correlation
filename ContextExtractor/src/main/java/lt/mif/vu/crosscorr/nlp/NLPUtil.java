package lt.mif.vu.crosscorr.nlp;

import java.io.IOException;
import java.io.InputStream;

import lombok.Getter;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;
import opennlp.tools.util.InvalidFormatException;

public class NLPUtil implements AutoCloseable {
	
	private static NLPUtil instance;
	
	
	private InputStream sentenceModelStream;
	private InputStream posTaggerStream;
	private InputStream tokenizerStream;
	
	private SentenceModel sentenceModel;
	@Getter
	private SentenceDetectorME sentenceDetector;
	
	private TokenizerModel tokenizerModel;
	@Getter
	private TokenizerME tokenizer;
	
	private POSModel posModel;
	@Getter
	private POSTaggerME posTagger;

	private NLPUtil() throws InvalidFormatException, IOException {
		this.sentenceModelStream = NLPUtil.class.getClassLoader().getResourceAsStream("en-sent.bin");
		this.posTaggerStream = NLPUtil.class.getClassLoader().getResourceAsStream("en-pos-maxent.bin");
		this.tokenizerStream = NLPUtil.class.getClassLoader().getResourceAsStream("en-token.bin");
		
		this.sentenceModel = new SentenceModel(sentenceModelStream);
		this.sentenceDetector = new SentenceDetectorME(sentenceModel);
		
		this.tokenizerModel = new TokenizerModel(tokenizerStream);
		this.tokenizer = new TokenizerME(tokenizerModel);
		
		this.posModel = new POSModel(posTaggerStream);
		this.posTagger = new POSTaggerME(posModel);
	}

	@Override
	public void close() throws Exception {
		sentenceModelStream.close();
		posTaggerStream.close();
		tokenizerStream.close();
	}

	public static NLPUtil getInstance() {
		if (instance == null) {
			try {
				instance = new NLPUtil();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return instance;
	}
	
	

}
