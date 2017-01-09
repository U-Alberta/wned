package ca.ualberta.entitylinking.kb.wikipedia.wikixmlj;

public class Pair<L,R> {
    private L l;
    private R r;
    public Pair(L l, R r){
        this.l = l;
        this.r = r;
    }
    
    public L getValue1(){ return l; }
    public R getValue2(){ return r; }
    public void setValue1(L l){ this.l = l; }
    public void setValue2(R r){ this.r = r; }
    
    public static void main(String[] args) {
    	Pair<String, String> p = new Pair<String, String>("Hello", "Hello world");

    	System.out.println(p.getValue1() + "\t" + p.getValue2());
    }
}