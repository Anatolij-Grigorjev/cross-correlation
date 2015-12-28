package lt.mif.vu.crosscorr.utils;

public enum Algorithm {

	FRONT_TO_BACK {
		
		@Override
		public String toString() {
			return "Front-origin bias";
		}
		
	}, BACK_TO_FRONT {
		
		@Override
		public String toString() {
			return "Back-origin bias";
		}
		
	}, STANFROD_NLP {
		
		@Override
		public String toString() {
			return "Stanford NLP bias";
		}
		
	}
	
}
