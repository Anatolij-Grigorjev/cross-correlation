package lt.mif.vu.crosscorr.utils;

/**
 * Wrapper object that consists all necessary information for calculating tfidf value for each term.
 * Use method getValue() to get the actual tfidf value.
 * @author Aniket
 * @see 
 * 	<a href="https://code.google.com/p/java-intelligent-tutor/source/browse/trunk/itjava/src/itjava/model/TFIDF.java">The original source on Google code</a>
 *
 */
public class TFIDF implements Comparable<TFIDF>{
        private Number numOfOccurrences;
        private Number totalTermsInDocument;
        private Number totalDocuments;
        private Number numOfDocumentsWithTerm;
        
        public TFIDF(Number occ, Number totTerms, Number totDocs, Number docsWithTerms) {
                numOfOccurrences = occ;
                totalTermsInDocument = totTerms;
                totalDocuments = totDocs;
                numOfDocumentsWithTerm = docsWithTerms;
        }
        
        /**
         * Calculates the tf-idf value of the current term. For description of tf-idf 
         * refer to <a href="http://en.wikipedia.org/wiki/tf-idf">^ wikipedia.org/tfidf</a> <br />
         * Because there can be many cases where the current term is not present in any other 
         * document in the repository, Float.MIN_VALUE is added to the denominator to avoid
         * DivideByZero exception
         * @return the caluclated TF*IDF of a term, using log10
         */
        public Double getValue(){
                double tf = numOfOccurrences.doubleValue() / (Double.MIN_VALUE + totalTermsInDocument.doubleValue());
                double idf = (double) (totalDocuments.doubleValue() / (Double.MIN_VALUE + numOfDocumentsWithTerm.doubleValue()));
                return (tf * idf);
        }
        
        public int getNumOfOccurrences() {
                return this.numOfOccurrences.intValue();
        }
        
        public String toString() {
//                return this.getValue().toString();
              return "numOfOccurrences : " + this.numOfOccurrences.intValue() + "\n"
                      + "totalTermsInDocument : " + this.totalTermsInDocument.intValue() + "\n"
                      + "numOfDocumentsWithTerm : " + this.numOfDocumentsWithTerm.intValue() + "\n"
                      + "totalDocuments : " + this.totalDocuments.intValue() + "\n"
                      + "TFIDF : " + this.getValue();
                        
        }
        
        @Override
        public int compareTo(TFIDF o) {
                return (int) ((this.getValue() - o.getValue()) * 100);
        }
        
}
