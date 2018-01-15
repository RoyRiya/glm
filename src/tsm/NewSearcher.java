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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.search.payloads.AveragePayloadFunction;
import org.apache.lucene.search.payloads.PayloadFunction;
import org.apache.lucene.search.similarities.AfterEffectB;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.BasicModelBE;
import org.apache.lucene.search.similarities.DFRSimilarity;
import org.apache.lucene.search.similarities.LMDirichletSimilarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.search.similarities.MultiSimilarity;
import org.apache.lucene.search.similarities.NormalizationH1;
import org.apache.lucene.search.similarities.PerFieldSimilarityWrapper;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;
import trec.TRECQuery;
import trec.TRECQueryParser;


class Block implements Comparable<Block>
{
    String doc_id;
    float score;

    @Override
    public int compareTo(Block that) {
      
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        return this.score > that.score? -1 : this.score == that.score? 0 : 1;
    }
    
    public Block(String id)
    {
        doc_id=id;
    }
}


public class NewSearcher {

    IndexReader reader;
    IndexSearcher searcher;
    Properties prop;   // retrieve.properties
    Properties iprop; // init.properties
    int numWanted;      // number of result to be retrieved
    HashMap<Integer, Float> docScorePredictionMap;
    WordVecIndexer wvIndexer;
    String runName;     // name of the run
    float gamma,alpha, beta;   // mu < 1; lambda + alpha < 1
    String stop_file;
    HashMap<String,Integer>  stop_map;
    
    
    
    public NewSearcher(String rpropFile) throws FileNotFoundException, IOException
    {
        prop = new Properties();
        prop.load(new FileReader(rpropFile));
        stop_file=prop.getProperty("stopfile");
        gamma= Float.parseFloat(prop.getProperty("retrieve.gamma"));
        alpha = Float.parseFloat(prop.getProperty("retrieve.alpha"));
        beta = Float.parseFloat(prop.getProperty("retrieve.beta"));
        stop_map=new HashMap<>();
        String wvIndex_dir = prop.getProperty("wv.index");
        
        String line;

        try {
            int count=1;
            FileReader fr = new FileReader(stop_file);
            BufferedReader br = new BufferedReader(fr);
            while ( (line = br.readLine()) != null ) {
                stop_map.put(line.trim(),count);
                count=count+1;
            }
            br.close(); fr.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
        
        System.out.println("Running queries against index: " + wvIndex_dir);
        try {
            File indexDir;
            indexDir = new File(wvIndex_dir);
            reader = DirectoryReader.open(FSDirectory.open(indexDir));
            searcher = new IndexSearcher(reader);
            numWanted = Integer.parseInt(prop.getProperty("retrieve.num_wanted", "1000"));
            runName = prop.getProperty("retrieve.runname", "glm_word2vec");
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
        List<TRECQuery> constructQueries() throws Exception {
        String queryFile = prop.getProperty("query.file");
        TRECQueryParser parser = new TRECQueryParser(queryFile);
        parser.parse();
        return parser.queries;
    }
        
        public float calculate_score(String st)
        {
            String[] s = st.split("[\\|\\s]+");
            float val = (float)Math.log(1 + (gamma*Float.valueOf(s[0])) + (alpha*Float.valueOf(s[1])) + (beta*Float.valueOf(s[2])));
            return val;
        }
        public ScoreDoc[] retrieve(Query query) throws Exception {

        ScoreDoc[] hits = null;
        TopDocs topDocs = null;

        TopScoreDocCollector collector = TopScoreDocCollector.create(numWanted,true);

        searcher.search(query,collector);
        topDocs = collector.topDocs();
        hits = topDocs.scoreDocs;
        if(hits == null)
            System.out.println("Nothing found");

        return hits;
    }
        public void retrieveAll() throws Exception {
 
        int i;
        ScoreDoc[] hits = null;
        String resultsFile = prop.getProperty("retrieve.wv_results_file");
        FileWriter fw = new FileWriter(resultsFile);

        List<TRECQuery> queries = constructQueries();
        for (TRECQuery query : queries) {
            
            HashMap<String,Integer> strn = query.analysing_query(new WhitespaceAnalyzer(Version.LUCENE_4_9),3, stop_map);
            System.out.println("Retrieved results for query: " + query.id);
            HashMap<String,HashMap<String,String>> hash = new HashMap<String,HashMap<String,String>>();
            Set<String> s1 = new HashSet<String>();
            List<Block> list = new ArrayList<>();
            for(String st : strn.keySet())
            {
                Query q = query.getQuery(st);
                hits=retrieve(q);
                HashMap<String,String> doc_details = new HashMap<String,String>();
                if(hits.length!=0)
                {
               
                    for (i = 0; i < hits.length;i++) 
                    {
                        int docId = hits[i].doc;
                        Document d = searcher.doc(docId);
                        if(doc_details.get(d.get("id"))==null)
                        {
                            doc_details.put(d.get("id"),d.get("prob"));
                            s1.add(d.get("id"));
                        }
                    }
                    hash.put(st, doc_details);
                }
            }
            for(String id : s1)
            {
                Block b = new Block(id);
                float sc=0.0f;
                for(String qterm : hash.keySet())
                {
                    HashMap h=hash.get(qterm);
                    if(h.get(id)!=null)
                    {
                        sc+=calculate_score(h.get(id).toString());
                    }
                }
                b.score=sc;
                list.add(b);
            }
            Collections.sort(list);
            StringBuffer buff = new StringBuffer();
            for(i=0;i<list.size();i++)
            {
                buff.append(query.id).append("\tQ0\t").
                append(list.get(i).doc_id).append("\t").
                append((i)).append("\t").
                append(list.get(i).score).append("\t").
                append(runName).append("\n");
            }
            fw.write(buff.toString());
        }
        fw.close();
    }
     
        public static void main(String[] args) {
        if (args.length < 2) {
            args = new String[2];
            //args[0] ="/home/riya/IR/glm/tweet.index.properties";
            args[0] ="/home/riya/IR/glm/tweet.retrieve.properties";
        }
        
        try {
            
            NewSearcher searcher = new NewSearcher(args[0]);
            
            searcher.retrieveAll();
        }
        
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
        
    
    
    
}
