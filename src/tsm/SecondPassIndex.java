/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

/**
 *
 * @author riya
 */
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.payloads.DelimitedPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.FloatEncoder;
import org.apache.lucene.analysis.payloads.NumericPayloadTokenFilter;
import org.apache.lucene.analysis.payloads.PayloadEncoder;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.analysis.standard.UAX29URLEmailTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsEnum;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.Version;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;
import org.tartarus.snowball.ext.PorterStemmer;
import static tsm.WordVecIndexer.FIELD_BAG_OF_WORDS;
import static tsm.WordVecIndexer.FIELD_ID;
import static tsm.WordVecIndexer.PAYLOAD_DELIM;


class TotalSimPerTerm{
    String term;
    float prob_in_doc;  // for storing probability in document (sum of t' P(t,t'|d) )
    float prob_in_ng;   // for storing total neighbouring frequency (sum of t' P(t,t'|C) )
    List  coll_list; // for storing the terms added in collection
    List  coll_prob;  // for storing the probability with respect to neighbour terms
    float nts_d; // normalizer term
    float ntf_d; // term frequency / doc_length
    
    public TotalSimPerTerm(String term1){
            this.term=term1;
    }
}


public class SecondPassIndex {
    
    Properties prop;
    File indexDir;
    File wvIndexDir;
    WordVecs wordvecs;
    IndexWriter writer;
    PerFieldAnalyzerWrapper wrapper;
    int indexingPass;
    Compute_Yass_Distance yass_obj;
    float lambda;
    
    static final public String FIELD_ID = "id";
    static final public String FIELD_BAG_OF_WORDS = "words";  // Baseline
    static final public String FIELD_TERM = "term";
    static final public String FIELD_PROB = "prob";
    
   
    public  SecondPassIndex(String propFile) throws FileNotFoundException, IOException
    {
        prop = new Properties();
        prop.load(new FileReader(propFile));        
        String indexPath = prop.getProperty("word.index");
        String wvIndexPath = prop.getProperty("wv.index");
        wvIndexDir = new File(wvIndexPath);
        indexDir = new File(indexPath);
        
        IndexWriterConfig iwcfg = new IndexWriterConfig(Version.LUCENE_4_9,new WhitespaceAnalyzer(Version.LUCENE_4_9));
        iwcfg.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
        
         writer = new IndexWriter(FSDirectory.open(wvIndexDir), iwcfg);
         lambda = Float.parseFloat(prop.getProperty("comb_lambda","0.5"));
     
    }
    public void expandIndex() throws Exception {
        
        wordvecs = new WordVecs(prop);
        yass_obj = new Compute_Yass_Distance();
        
        if (wordvecs.wordvecmap != null)
            wordvecs.loadPrecomputedNNs();
        
        System.out.println("word-to-vec read and NNfile loaded");
        
        int end = Integer.parseInt(prop.getProperty("wv_expand.end.docid", "-1"));
       
        
        try (IndexReader reader = DirectoryReader.open(FSDirectory.open(indexDir))) {
            int maxDoc = reader.maxDoc();
            end = Math.min(end, maxDoc);
            if (end == -1)
                end = maxDoc;

            for (int i = 0; i < end; i++) {
                System.out.println("DocId: " + i);                
                expandDoc(reader, i);
            }
        }
        
        writer.close();
    }
    public void expandDoc(IndexReader reader, int docId) throws IOException
    {
        ArrayList<TotalSimPerTerm> tsim = new ArrayList<>();
        HashMap<String,Integer> uterm = new HashMap<String,Integer>();
        Document doc = reader.document(docId);
        BytesRef term;
        int docLen = 0;
        
        Terms terms = reader.getTermVector(docId, FIELD_BAG_OF_WORDS);
        if (terms == null || terms.size() == 0)
            return;
        
        TermsEnum termsEnum = terms.iterator(null);
        
        while (termsEnum.next() != null) {
            DocsEnum docsEnum = termsEnum.docs(null, null);

            while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                //get the term frequency in the document
                docLen += docsEnum.freq();
            }
        }
        
        termsEnum = terms.iterator(null); 
        while ((term = termsEnum.next()) != null) { 
            String termStr = term.utf8ToString();
            DocsEnum docsEnum = termsEnum.docs(null, null); // enumerate through documents, in this case only one
            while (docsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                //get the term frequency in the document
                int tf = docsEnum.freq();
                float ntf = tf/(float)docLen;
                TotalSimPerTerm tobj = new TotalSimPerTerm(termStr);
                tobj.ntf_d = ntf;
                tsim.add(tobj);
            }
        }
        int i, j, len = tsim.size();
        float prob, sim,temp_sim,totalSim;
        for (i = 0; i < len; i++) {
            TotalSimPerTerm tf_i=tsim.get(i);
            totalSim = 0.0f;
            temp_sim=0.0f;
            for (j = 0; j < len; j++) {
                if (i==j)
                    continue;
                TotalSimPerTerm tf_j = tsim.get(j);
                temp_sim=lambda * (this.wordvecs.getSim(tf_i.term, tf_j.term));
                char[] X = tf_i.term.toCharArray();
                char[] Y = tf_j.term.toCharArray();
                if(X[0]==Y[0])
                    temp_sim+= (1-lambda) * (this.yass_obj.yass_similarity(X, Y));
                totalSim += temp_sim;
            }
            tf_i.nts_d=totalSim;
            System.out.println("term "+tf_i.term);
            System.out.println("term-tf "+tf_i.ntf_d);
            System.out.println("total-prob "+tf_i.nts_d);
        }
        
        for (i = 0; i < len; i++) {
            
            TotalSimPerTerm tf_i =tsim.get(i);
            prob=0.0f;
            sim=0.0f;
            for (j = 0; j < len; j++) {
                if (i==j)
                    continue;
                
                TotalSimPerTerm tf_j = tsim.get(j);
                //System.out.println("word vec sim "+wordvecs.getSim(tf_i.term,tf_j.term));
                sim = lambda*(wordvecs.getSim(tf_i.term, tf_j.term));
                char[] X = tf_i.term.toCharArray();
                char[] Y = tf_j.term.toCharArray();
                if(X[0]==Y[0])
                    sim+= (1-lambda) * (yass_obj.yass_similarity(X, Y));
                
                System.out.println("sim prob "+sim/tf_i.nts_d);
                prob+= ((tf_j.ntf_d)* (sim/tf_i.nts_d));
            }
            tf_i.prob_in_doc=prob;
            System.out.println("prob in doc of term "+tf_i.prob_in_doc);
            
        }
        
        final int K = Integer.parseInt(prop.getProperty("wvexpand.numnearest", "3"));
        final float thresh = Float.parseFloat(prop.getProperty("wvexpand.thresh", "0.6"));
        
        for (i = 0; i < len; i++) {
           
            TotalSimPerTerm ts_i =tsim.get(i);
            ts_i.coll_list = new ArrayList<>();
            ts_i.coll_prob = new ArrayList<>();
            
            List<WordVec> nn_tf_i = wordvecs.getPrecomputedNNs(ts_i.term, K, thresh);
            if (nn_tf_i == null || nn_tf_i.size() == 0) {
                continue;
            }

           // Add the term itself in the list (A word is also a neighbor
           // of itself.
           
            nn_tf_i.add(new WordVec(ts_i.term, 1.0f));
    
            float normalizer = 0.0f;
            for (WordVec nn : nn_tf_i) {
                normalizer += nn.querySim;
            }
            
            // Expand the current document by NN words (including itself)
            float pp=0.0f;
            for (WordVec nn : nn_tf_i) {
                // We can do this since it's postional indexing... no need
                // to add only one occurrence of term with its frequency
                // No need to incorporate the collection freq here because
                // it will any way be taken care of during retrieval.
                //probNN+= (float)(nn.querySim/normalizer);
                
                float probNN = (float)(nn.querySim/normalizer);
                ts_i.coll_list.add(nn.word);
                ts_i.coll_prob.add(probNN);
                pp+=probNN;
            }
            ts_i.prob_in_ng=pp;
        }
        for(i=0;i<tsim.size();i++)
        {
            TotalSimPerTerm ts = tsim.get(i);
            Term termInstance = new Term(FIELD_BAG_OF_WORDS, ts.term);                              
            float termFreq = reader.totalTermFreq(termInstance);
            Fields fields = MultiFields.getFields(reader);
            Terms tm = fields.terms(FIELD_BAG_OF_WORDS);
            float vocSize = tm.getSumTotalTermFreq();
            float val1 = (termFreq/vocSize);
            //System.out.println(" term-freq by collection-size "+val1);
            float val2 =  ts.prob_in_doc/val1;
            float val3 = ts.prob_in_ng/val1;
            float val4 = ts.ntf_d/val1;
            ts.prob_in_doc=val2;
            ts.prob_in_ng=val3;
            ts.ntf_d=val4;
            uterm.put(ts.term,1);
        }
        len=tsim.size();
        
        for(i=0;i<len;i++)
        {
            TotalSimPerTerm ts = tsim.get(i);
            List l = ts.coll_list;
            List l1= ts.coll_prob;
            
            Term termInstance = new Term(FIELD_BAG_OF_WORDS, ts.term);                              
            float termFreq = reader.totalTermFreq(termInstance);
            Fields fields = MultiFields.getFields(reader);
            Terms tm = fields.terms(FIELD_BAG_OF_WORDS);
            float vocSize = tm.getSumTotalTermFreq();
            float val1 = (termFreq/vocSize);
            
            for(j=0;j<l.size();j++)
            {
                if(uterm.get(l.get(j))==null)
                {
                    TotalSimPerTerm ts1= new TotalSimPerTerm(l.get(j).toString());
                    ts1.ntf_d=0;
                    ts1.prob_in_doc=0;
                    ts1.prob_in_ng=(float)l1.get(j)/val1;
                    tsim.add(ts1);
                }
                 
            }   
         }
        
        for(i=0;i<tsim.size();i++)
        {
            TotalSimPerTerm ts =tsim.get(i);
            if(uterm.get(ts.term)==null)
            {
                Document newdoc = new Document();
                newdoc.add(new Field(FIELD_ID, doc.get(FIELD_ID), Field.Store.YES, Field.Index.NOT_ANALYZED));
                newdoc.add(new Field(FIELD_TERM,ts.term,Field.Store.YES,Field.Index.ANALYZED,Field.TermVector.YES));
                Probability ps = new Probability(ts.ntf_d,ts.prob_in_doc,ts.prob_in_ng);
                newdoc.add(new Field(FIELD_PROB,ps.getProb(),Field.Store.YES,Field.Index.ANALYZED));
                writer.addDocument(newdoc);
            }
            else if(uterm.get(ts.term)==1)
            {
                Document newdoc = new Document();
                newdoc.add(new Field(FIELD_ID, doc.get(FIELD_ID), Field.Store.YES, Field.Index.NOT_ANALYZED));
                newdoc.add(new Field(FIELD_TERM,ts.term,Field.Store.YES,Field.Index.ANALYZED,Field.TermVector.YES));
                Probability ps = new Probability(ts.ntf_d,ts.prob_in_doc,ts.prob_in_ng);
                newdoc.add(new Field(FIELD_PROB,ps.getProb(),Field.Store.YES,Field.Index.ANALYZED));
                uterm.put(ts.term,uterm.get(ts.term)+1);
                //System.out.println("term prob"+ts);

                writer.addDocument(newdoc);
         
            }
           
        }
        
        System.out.println("Doc done");
        
    }
    public static void main(String[] args) throws IOException, Exception {
        if (args.length == 0) {
            args = new String[1];
            System.out.println("Usage: java WordVecIndexer <prop-file>");
            args[0] = "/home/riya/IR/glm/tweet.index.properties";
        }

            SecondPassIndex indexer = new SecondPassIndex(args[0]);
            indexer.expandIndex();                
 
        
    }    
    
    
}
