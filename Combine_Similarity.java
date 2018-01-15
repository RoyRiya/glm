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
public class Combine_Similarity implements Comparable<Combine_Similarity> {
    
    String word;
    float y_sim;
    float wv_sim;
    float comb_sim;

    @Override
    public int compareTo(Combine_Similarity that) {
        
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        return this.comb_sim > that.comb_sim? -1 : this.comb_sim == that.comb_sim? 0 : 1;
    
    }
    
    public Combine_Similarity(String str,float val,float val1,float lambda)
    {
        word=str;
        y_sim=val;
        wv_sim=val1;
        comb_sim=lambda*val1+(1-lambda)*val;
    }
    
}
