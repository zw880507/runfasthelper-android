package com.paodekuai.analyzer.advisor;

import com.paodekuai.analyzer.tracker.GameTracker;import com.paodekuai.analyzer.tracker.RuleEngine;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicLong;import java.util.function.Consumer;

/** Async wrapper around the Android two-player SO-ISMCTS engine. */
public final class AdvisorWorker implements AutoCloseable {
    public static final class Candidate {public final String action;public final double score;public final int visits,availability;Candidate(String a,double s,int v,int av){action=a;score=s;visits=v;availability=av;}}
    public static final class Result {public final String stateKey;public final List<Candidate> top;public final long elapsedMs;public final int iterations,treeNodes,reservoir,samplingAttempts,rolloutCutoffs;Result(String k,List<Candidate>t,SoIsmctsEngine.Result r){stateKey=k;top=t;elapsedMs=r.elapsedMs;iterations=r.iterations;treeNodes=r.treeNodes;reservoir=r.reservoir;samplingAttempts=r.attempts;rolloutCutoffs=r.cutoffs;}}
    public static final class Snapshot {
        public final String ownHand,stateKey;public final int opponentCards,currentPlayer,firstPlayer;public final RuleEngine.Play target;public final Integer targetOwner;public final List<GameTracker.Hist> history;
        public Snapshot(String h,int o,RuleEngine.Play t,Integer owner,int cp,int fp,String k,List<GameTracker.Hist> hist){ownHand=h;opponentCards=o;target=t;targetOwner=owner;currentPlayer=cp;firstPlayer=fp;stateKey=k;history=List.copyOf(hist);}
    }
    private final ExecutorService exec=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"pdk-soismcts");t.setDaemon(true);return t;});private final AtomicLong generation=new AtomicLong();
    public void cancelPending(){generation.incrementAndGet();}
    public void submit(Snapshot s,int budgetMs,int topK,Consumer<Result> cb){long g=generation.incrementAndGet();exec.submit(()->{SoIsmctsEngine.Snapshot q=new SoIsmctsEngine.Snapshot(s.ownHand,s.opponentCards,s.target,s.targetOwner,s.currentPlayer,s.firstPlayer,s.history);SoIsmctsEngine.Result raw=new SoIsmctsEngine(q,31L*s.stateKey.hashCode()+7).search(10000,budgetMs,.9);if(generation.get()!=g)return;List<Candidate>x=new ArrayList<>();for(int i=0;i<Math.min(topK,raw.actions.size());i++){SoIsmctsEngine.Candidate a=raw.actions.get(i);x.add(new Candidate(a.action,a.score,a.visits,a.availability));}cb.accept(new Result(s.stateKey,x,raw));});}
    @Override public void close(){generation.incrementAndGet();exec.shutdownNow();}
}
