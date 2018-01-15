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
public class Probability {
    
    float term_prob;
    float doc_prob;
    float coll_prob;
    
    public Probability(float t, float d, float c)
    {
        this.term_prob=t;
        this.doc_prob=d;
        this.coll_prob=c;
    }
    
    public String getProb()
    {
        String str=Float.toString(term_prob);
        str=str+"|";
        str=str+Float.toString(doc_prob);
        str=str+"|";
        str=str+Float.toString(coll_prob);
        return str;
    }
    
}
