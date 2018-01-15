/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package tsm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import static java.lang.Float.max;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

/**
 *
 * @author riya
 */
public class Combine_WV_Other_Sim {
    
    HashMap<String, List<distance_storage>> nearestWordMap;
    HashMap<String, List<WordVec>> nearestWordVecsMap;
    
    float lambda;
    Properties prop;
    int k;
    
    public Combine_WV_Other_Sim(String str) throws FileNotFoundException, IOException
    {
        prop=new Properties();
        prop.load(new FileReader(str));
        lambda=Float.parseFloat(prop.getProperty("comb_lambda"));
        k = Integer.parseInt(prop.getProperty("yash.numnearest", "30"));
    }
    public void loadPrecomputedNN_Other() throws FileNotFoundException, IOException {
        nearestWordMap = new HashMap<>();
        
        String NNDumpPath = prop.getProperty("NNDumpPath_Yass");
        if (NNDumpPath == null) {
            System.out.println("NNDumpPath not specified in configuration...");
            return;
        }
        System.out.println("Reading from the NN dump at: "+ NNDumpPath);
        File NNDumpFile = new File(NNDumpPath);
        
        try (FileReader fr = new FileReader(NNDumpFile);
            BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line, " \t:");
                List<String> tokens = new ArrayList<>();
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    tokens.add(token);
                }
                List<distance_storage> nns = new LinkedList();
                int len = tokens.size();
                for (int i=1; i < len-1; i+=2) {
                    nns.add(new distance_storage(tokens.get(i), Float.parseFloat(tokens.get(i+1))));
                }
                nearestWordMap.put(tokens.get(0), nns);
            }
            System.out.println("NN dump has been reloaded");
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public void loadPrecomputedNN_WV() throws FileNotFoundException, IOException {
        nearestWordVecsMap = new HashMap<>();
        String NNDumpPath = prop.getProperty("NNDumpPath_Wv");
        if (NNDumpPath == null) {
            System.out.println("NNDumpPath not specified in configuration...");
            return;
        }
        System.out.println("Reading from the NN dump at: "+ NNDumpPath);
        File NNDumpFile = new File(NNDumpPath);
        
        try (FileReader fr = new FileReader(NNDumpFile);
            BufferedReader br = new BufferedReader(fr)) {
            String line;
            
            while ((line = br.readLine()) != null) {
                StringTokenizer st = new StringTokenizer(line, " \t:");
                List<String> tokens = new ArrayList<>();
                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    tokens.add(token);
                }
                List<WordVec> nns = new LinkedList();
                int len = tokens.size();
                for (int i=1; i < len-1; i+=2) {
                    nns.add(new WordVec(tokens.get(i), Float.parseFloat(tokens.get(i+1))));
                }
                nearestWordVecsMap.put(tokens.get(0), nns);
            }
            System.out.println("NN dump has been reloaded");
            br.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    public void combine_two_union() throws FileNotFoundException
    {
        int i=0,j;
        String NNDumpPath = prop.getProperty("NNDumpPath_Combine");
        if(NNDumpPath!=null) {
            File f = new File(NNDumpPath);
        }
        else {
            System.out.println("Null found");
            return;
        }

        System.out.println("Dumping the NN in: "+ NNDumpPath);
        PrintWriter pout = new PrintWriter(NNDumpPath);

        System.out.println("Precomputing NNs for each word");
        int m;
        for(String str : nearestWordMap.keySet())
        {
            List<Combine_Similarity> distList = new ArrayList<>();
            if(nearestWordVecsMap.get(str)!=null)
            {
                List<WordVec> nns=nearestWordVecsMap.get(str);
                List<distance_storage> nn=nearestWordMap.get(str);
                Set<String> hash_Set = new HashSet<String>();
                if(nns.size() > nn.size())
                    m=nns.size();
                else
                    m=nn.size();
                 
                for(i=0;i<m;i++)
                {
                    if(i<nns.size())
                    {
                        WordVec wv=nns.get(i);
                        hash_Set.add(wv.word);
                    }
                    if(i<nn.size())
                    {
                        distance_storage ds=nn.get(i);
                        hash_Set.add(ds.word);
                    }
                    
                   
                }
                for(String s:hash_Set)
                {
                    float val=0;
                    float val1=0;
                    
                   for(i=0;i<nns.size();i++)
                    {
                         WordVec wv=nns.get(i);
                         if(wv.word.equals(s))
                         {
                             val=wv.querySim;
                             break;
                         }
                    }
                    for(i=0;i<nn.size();i++)
                    {
                         distance_storage ds=nn.get(i);
                         if(ds.word.equals(s))
                         {
                             val1=ds.yass_similarity;
                             break;
                         }
                    }
                    Combine_Similarity cs=new Combine_Similarity(s,val1,val,lambda);
                    distList.add(cs);
                }
                Collections.sort(distList);
                distList=distList.subList(0, Math.min(k, distList.size()));
                
                if (distList != null) {
                    pout.print(str + "\t");
                    for (j = 0; j < distList.size(); j++) {
                        Combine_Similarity nnc = distList.get(j);
                        pout.print(nnc.word + ":" + nnc.comb_sim + "\t");
                    }
                    pout.print("\n");
                }
                
                
                
            }
            else
            {
                 
                List<distance_storage> nns=nearestWordMap.get(str);
                if (nns != null) {
                    pout.print(str + "\t");
                    for (j = 0; j < nns.size(); j++) {
                        distance_storage nnc = nns.get(j);
                        pout.print(nnc.word + ":" + nnc.yass_similarity + "\t");
                    }
                    pout.print("\n");
                }
                
            }
            System.out.println("Precomputing NNs for " + str +" done!");
            
        }
        pout.close();
    }
    
    public static void main(String[] args) {
        if (args.length == 0) {
            args = new String[1];
            args[0] = "/user1/faculty/cvpr/irlab/riya_java/tweet.index.properties";
        }
        
        try {
            //System.out.println(args[0]);
            Combine_WV_Other_Sim qe = new Combine_WV_Other_Sim(args[0]);
            System.out.println("Prop-file read");
            qe.loadPrecomputedNN_Other();
            qe.loadPrecomputedNN_WV();
            qe.combine_two_union();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    
}
