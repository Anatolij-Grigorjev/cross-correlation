package lt.mif.vu.crosscorr.processors;

import java.util.List;

import lt.mif.vu.crosscorr.OutputAppender;

public abstract class EVectorProcessor implements Runnable {
	
	
	private List<String> inputDocs;
	private OutputAppender appender;


	public EVectorProcessor(List<String> inputDocs, OutputAppender appender) {
		this.inputDocs = inputDocs;
		this.appender = appender;
	}
	
	public abstract void runFinished();
	
	@Override
	public void run() {
		// TODO Auto-generated method stub

	}

}
