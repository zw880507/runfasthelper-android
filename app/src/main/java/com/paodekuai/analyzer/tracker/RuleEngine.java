package com.paodekuai.analyzer.tracker;

import java.util.*;

public final class RuleEngine {
    public enum Type { SINGLE,PAIR,TRIPLE,TRIPLE_SINGLE,TRIPLE_PAIR,TRIPLE_TWO_SINGLE,STRAIGHT,CONSECUTIVE_PAIRS,AIRPLANE,AIRPLANE_SINGLE,AIRPLANE_PAIR,FOUR_TWO_SINGLE,FOUR_TWO_PAIR,BOMB }
    public static final class Play {
        public final Type type; public final String cards; public final int mainRank,chainLen;
        public Play(Type t,String c,int m,int k){type=t;cards=sortText(c);mainRank=m;chainLen=k;}
        public int length(){return cards.length();}
        @Override public String toString(){return cards;}
    }
    private static final String DESC="2AKQJT9876543";
    public static int rank(char c){return switch(c){case '3'->3;case '4'->4;case '5'->5;case '6'->6;case '7'->7;case '8'->8;case '9'->9;case 'T'->10;case 'J'->11;case 'Q'->12;case 'K'->13;case 'A'->14;case '2'->15;default->-1;};}
    public static char text(int r){return switch(r){case 3->'3';case 4->'4';case 5->'5';case 6->'6';case 7->'7';case 8->'8';case 9->'9';case 10->'T';case 11->'J';case 12->'Q';case 13->'K';case 14->'A';case 15->'2';default->'?';};}
    public static String sortText(String s){char[] a=s.toUpperCase(Locale.ROOT).toCharArray();Character[] x=new Character[a.length];for(int i=0;i<a.length;i++)x[i]=a[i];Arrays.sort(x,(p,q)->Integer.compare(rank(q),rank(p)));StringBuilder b=new StringBuilder();for(char c:x)b.append(c);return b.toString();}
    public static Map<Integer,Integer> counts(String s){Map<Integer,Integer> m=new TreeMap<>();for(char c:s.toCharArray()){int r=rank(c);if(r>0)m.put(r,m.getOrDefault(r,0)+1);}return m;}
    private static boolean consecutive(List<Integer> xs){for(int i=1;i<xs.size();i++)if(xs.get(i)!=xs.get(i-1)+1)return false;return true;}
    public static Play classify(String raw){
        if(raw==null||raw.isBlank())return null;String cards=sortText(raw);int n=cards.length();Map<Integer,Integer> cnt=counts(cards);List<Integer> ranks=new ArrayList<>(cnt.keySet());Collections.sort(ranks);
        if(n==1)return new Play(Type.SINGLE,cards,rank(cards.charAt(0)),1);
        if(n==2&&cnt.size()==1)return new Play(Type.PAIR,cards,ranks.get(0),1);
        if(n==3&&cnt.size()==1)return new Play(Type.TRIPLE,cards,ranks.get(0),1);
        if(n==4&&cnt.size()==1)return new Play(Type.BOMB,cards,ranks.get(0),1);
        if(n==4){for(var e:cnt.entrySet())if(e.getValue()==3)return new Play(Type.TRIPLE_SINGLE,cards,e.getKey(),1);}
        if(n==5){Integer tr=null;for(var e:cnt.entrySet())if(e.getValue()==3)tr=e.getKey();if(tr!=null){Map<Integer,Integer> r=new TreeMap<>(cnt);r.put(tr,r.get(tr)-3);if(r.get(tr)==0)r.remove(tr);if(r.size()==1&&r.values().iterator().next()==2)return new Play(Type.TRIPLE_PAIR,cards,tr,1);int sum=r.values().stream().mapToInt(Integer::intValue).sum();if(sum==2)return new Play(Type.TRIPLE_TWO_SINGLE,cards,tr,1);}}
        if(n>=5&&cnt.size()==n&&ranks.get(ranks.size()-1)<=14&&consecutive(ranks))return new Play(Type.STRAIGHT,cards,ranks.get(ranks.size()-1),n);
        if(n%2==0&&n/2>=2&&ranks.get(ranks.size()-1)<=14&&consecutive(ranks)&&cnt.values().stream().allMatch(v->v==2))return new Play(Type.CONSECUTIVE_PAIRS,cards,ranks.get(ranks.size()-1),ranks.size());
        if(n%3==0&&n/3>=2&&ranks.get(ranks.size()-1)<=14&&consecutive(ranks)&&cnt.values().stream().allMatch(v->v==3))return new Play(Type.AIRPLANE,cards,ranks.get(ranks.size()-1),ranks.size());
        if(n%4==0){int k=n/4;if(k>=2){List<Integer> cand=new ArrayList<>();for(var e:cnt.entrySet())if(e.getValue()>=3&&e.getKey()<=14)cand.add(e.getKey());Collections.sort(cand);for(int i=0;i+k<=cand.size();i++){List<Integer> core=cand.subList(i,i+k);if(!consecutive(core))continue;Map<Integer,Integer> rem=new TreeMap<>(cnt);for(int r:core){rem.put(r,rem.get(r)-3);if(rem.get(r)==0)rem.remove(r);}int sum=rem.values().stream().mapToInt(Integer::intValue).sum();if(sum==k)return new Play(Type.AIRPLANE_SINGLE,cards,core.get(core.size()-1),k);}}}
        if(n%5==0){int k=n/5;if(k>=2){List<Integer> cand=new ArrayList<>();for(var e:cnt.entrySet())if(e.getValue()>=3&&e.getKey()<=14)cand.add(e.getKey());Collections.sort(cand);for(int i=0;i+k<=cand.size();i++){List<Integer> core=cand.subList(i,i+k);if(!consecutive(core))continue;Map<Integer,Integer> rem=new TreeMap<>(cnt);for(int r:core){rem.put(r,rem.get(r)-3);if(rem.get(r)==0)rem.remove(r);}if(rem.size()==k&&rem.values().stream().allMatch(v->v==2))return new Play(Type.AIRPLANE_PAIR,cards,core.get(core.size()-1),k);}}}
        return null;
    }
    public static Integer compare(Play a,Play b){
        if(a==null||b==null)return null;if(a.type==Type.BOMB&&b.type!=Type.BOMB)return 1;if(b.type==Type.BOMB&&a.type!=Type.BOMB)return -1;
        boolean compat=(a.type==b.type)||((a.type==Type.TRIPLE_PAIR||a.type==Type.TRIPLE_TWO_SINGLE)&&(b.type==Type.TRIPLE_PAIR||b.type==Type.TRIPLE_TWO_SINGLE));if(!compat||a.length()!=b.length()||a.chainLen!=b.chainLen)return null;return Integer.compare(a.mainRank,b.mainRank);
    }
    public static boolean contains(String hand,String play){Map<Integer,Integer> h=counts(hand),p=counts(play);for(var e:p.entrySet())if(h.getOrDefault(e.getKey(),0)<e.getValue())return false;return true;}
    public static String subtract(String hand,String play){Map<Integer,Integer> h=counts(hand);for(var e:counts(play).entrySet()){int n=h.getOrDefault(e.getKey(),0)-e.getValue();if(n<0)throw new IllegalArgumentException("hand does not contain "+play);if(n==0)h.remove(e.getKey());else h.put(e.getKey(),n);}StringBuilder b=new StringBuilder();List<Integer> rs=new ArrayList<>(h.keySet());rs.sort(Collections.reverseOrder());for(int r:rs)for(int i=0;i<h.get(r);i++)b.append(text(r));return b.toString();}

    public static List<Play> generatePlays(String hand){
        Map<Integer,Integer> cnt=counts(hand);Map<String,Play> out=new LinkedHashMap<>();
        java.util.function.Consumer<String> add=s->{Play p=classify(s);if(p!=null)out.put(p.type+":"+p.length()+":"+p.chainLen+":"+p.mainRank+":"+p.cards,p);};
        for(var e:cnt.entrySet()){int r=e.getKey(),n=e.getValue();add.accept(rep(r,1));if(n>=2)add.accept(rep(r,2));if(n>=3)add.accept(rep(r,3));if(n>=4)add.accept(rep(r,4));}
        for(var e:cnt.entrySet()){int tr=e.getKey();if(e.getValue()<3)continue;for(var q:cnt.entrySet()){if(q.getKey()==tr)continue;if(q.getValue()>=1)add.accept(rep(tr,3)+rep(q.getKey(),1));if(q.getValue()>=2)add.accept(rep(tr,3)+rep(q.getKey(),2));}Map<Integer,Integer> rem=new TreeMap<>(cnt);rem.put(tr,rem.get(tr)-3);if(rem.get(tr)==0)rem.remove(tr);for(String wings:selectMultiset(rem,2))add.accept(rep(tr,3)+wings);}
        List<Integer> singles=new ArrayList<>(),pairs=new ArrayList<>(),triples=new ArrayList<>();for(var e:cnt.entrySet()){if(e.getKey()<=14){singles.add(e.getKey());if(e.getValue()>=2)pairs.add(e.getKey());if(e.getValue()>=3)triples.add(e.getKey());}}
        for(List<Integer> seg:windows(singles,5))add.accept(join(seg,1));for(List<Integer> seg:windows(pairs,2))add.accept(join(seg,2));
        for(List<Integer> core:windows(triples,2)){String cc=join(core,3);add.accept(cc);Map<Integer,Integer> rem=new TreeMap<>(cnt);for(int r:core){rem.put(r,rem.get(r)-3);if(rem.get(r)==0)rem.remove(r);}for(String w:selectMultiset(rem,core.size()))add.accept(cc+w);List<Integer> pairCand=new ArrayList<>();for(var e:rem.entrySet())if(e.getValue()>=2)pairCand.add(e.getKey());for(List<Integer> comb:combinations(pairCand,core.size()))add.accept(cc+join(comb,2));}
        List<Play> a=new ArrayList<>(out.values());a.sort(Comparator.comparingInt(Play::length).thenComparing(p->p.type.name()).thenComparingInt(p->p.mainRank).thenComparing(p->p.cards));return a;
    }
    public static List<Play> legalResponses(String hand,Play target){List<Play> all=generatePlays(hand);if(target==null)return all;List<Play> o=new ArrayList<>();for(Play p:all){Integer c=compare(p,target);if(c!=null&&c==1)o.add(p);}return o;}
    private static String rep(int r,int n){return String.valueOf(text(r)).repeat(n);}private static String join(List<Integer> rs,int copies){StringBuilder b=new StringBuilder();for(int r:rs)b.append(rep(r,copies));return b.toString();}
    private static List<List<Integer>> windows(List<Integer> ranks,int min){List<Integer> x=new ArrayList<>(ranks);Collections.sort(x);List<List<Integer>> o=new ArrayList<>();for(int i=0;i<x.size();i++)for(int j=i+min;j<=x.size();j++){List<Integer>s=new ArrayList<>(x.subList(i,j));if(consecutive(s))o.add(s);else break;}return o;}
    private static List<String> selectMultiset(Map<Integer,Integer> cnt,int total){List<Integer> rs=new ArrayList<>(cnt.keySet());List<String> o=new ArrayList<>();recSelect(rs,cnt,0,total,new StringBuilder(),o);return o;}
    private static void recSelect(List<Integer> rs,Map<Integer,Integer> cnt,int i,int left,StringBuilder acc,List<String> out){if(left==0){out.add(acc.toString());return;}if(i>=rs.size())return;int r=rs.get(i),max=Math.min(cnt.get(r),left),len=acc.length();for(int take=0;take<=max;take++){for(int k=0;k<take;k++)acc.append(text(r));recSelect(rs,cnt,i+1,left-take,acc,out);acc.setLength(len);}}
    private static List<List<Integer>> combinations(List<Integer>x,int k){List<List<Integer>>o=new ArrayList<>();combRec(x,k,0,new ArrayList<>(),o);return o;}private static void combRec(List<Integer>x,int k,int i,List<Integer>a,List<List<Integer>>o){if(a.size()==k){o.add(new ArrayList<>(a));return;}for(int j=i;j<x.size();j++){a.add(x.get(j));combRec(x,k,j+1,a,o);a.remove(a.size()-1);}}
}
