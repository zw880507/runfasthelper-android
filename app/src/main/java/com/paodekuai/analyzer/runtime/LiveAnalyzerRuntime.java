package com.paodekuai.analyzer.runtime;

import android.content.Context;
import com.paodekuai.analyzer.advisor.AdvisorWorker;
import com.paodekuai.analyzer.audit.AuditLogger;
import com.paodekuai.analyzer.decoder.LiveEventDecoder;
import com.paodekuai.analyzer.model.CanonicalFrame;
import com.paodekuai.analyzer.model.PublicActionEvent;
import com.paodekuai.analyzer.overlay.OverlayController;
import com.paodekuai.analyzer.prefs.AppSettings;
import com.paodekuai.analyzer.tracker.GameTracker;
import com.paodekuai.analyzer.tracker.RuleEngine;
import com.paodekuai.analyzer.vision.*;
import org.opencv.core.Mat;
import java.util.*;

/** Stateful production runtime: WAITING -> TRACKING -> ROUND_END -> WAITING. */
public final class LiveAnalyzerRuntime implements AutoCloseable {
    public enum Phase { INITIALIZING, WAITING_FOR_GAME, TRACKING, ROUND_END, ERROR }
    public interface Listener { void onStatus(Phase phase,String text); }

    private final AppSettings settings; private final Listener listener; private final OverlayController overlay;
    private final HandRecognizer initial,current; private final TableActionRecognizer table; private final UIStateClassifier ui=new UIStateClassifier(); private final StableFrameGate gate=new StableFrameGate(4.0,2);
    private final AdvisorWorker advisor=new AdvisorWorker(); private final AuditLogger audit;
    private GameTracker tracker; private LiveEventDecoder decoder; private String lastAdviceKey; private long roundEndAtNs; private int roundNumber; private int resultFrames;
    private Phase phase=Phase.INITIALIZING;

    public LiveAnalyzerRuntime(Context c,TemplateBank handBank,TemplateBank tableBank,AppSettings settings,OverlayController overlay,Listener listener) throws Exception {
        this.settings=settings;this.overlay=overlay;this.listener=listener;this.initial=new HandRecognizer(handBank);this.current=new HandRecognizer(handBank);this.table=new TableActionRecognizer(tableBank,.55);this.audit=new AuditLogger(c,settings.keepAudit);
        transition(Phase.WAITING_FOR_GAME,"等待牌局");
    }

    public synchronized void onFrame(Mat frame,double ts){
        if(frame==null||frame.empty())return;
        try{
            StableFrameGate.Result stab=gate.update(frame); UIStateClassifier.Observation uio=ui.classify(frame);String uiName=name(uio.state);
            if(phase==Phase.ROUND_END){if(System.nanoTime()-roundEndAtNs>1_500_000_000L && (uio.state==UIStateClassifier.State.TABLE||uio.state==UIStateClassifier.State.MY_TURN)){resetForNextRound();}else return;}
            if(tracker==null){
                if(!settings.autoDetect||!stab.stable)return;
                HandRecognizer.Observation h=initial.recognizeInitial16(frame);if(h==null||h.count!=16||h.meanConfidence<.70)return;
                int first=settings.firstPlayer>=0?settings.firstPlayer:(uio.state==UIStateClassifier.State.MY_TURN?0:1);
                startRound(h.cards,first,h.meanConfidence); return;
            }
            GameTracker.State before=tracker.state(); Integer expected=before.winner==null?before.currentPlayer:null; boolean canPass=before.currentTarget!=null;
            List<PublicActionEvent> events=decoder.observe(new CanonicalFrame(frame,ts),expected,uiName,canPass,stab.stable);
            boolean changed=false;
            for(PublicActionEvent e:events){
                try{GameTracker.State after=tracker.observe(e.player,e.action);decoder.confirmEvent(e);changed=true;audit.log("ACCEPT P"+e.player+" "+e.action+" conf="+fmt(e.confidence)+" src="+e.source+" left="+Arrays.toString(after.cardsLeft));
                    if(after.winner==null&&after.currentPlayer==0&&after.currentTarget!=null&&RuleEngine.legalResponses(after.ownHand,after.currentTarget).isEmpty()){
                        PublicActionEvent forced=new PublicActionEvent(0,"PASS",1.0,"rule_forced_pass",ts);after=tracker.observe(0,"PASS");decoder.confirmEvent(forced);audit.log("ACCEPT P0 PASS conf=1.00 src=rule_forced_pass left="+Arrays.toString(after.cardsLeft));changed=true;
                    }
                }catch(Exception ex){decoder.rejectEvent(e);audit.log("REJECT P"+e.player+" "+e.action+" src="+e.source+" err="+ex.getMessage());}
            }
            if(changed){advisor.cancelPending();lastAdviceKey=null;}
            GameTracker.State st=tracker.state(); if(st.winner!=null){finishRound(st);return;}
            if(uio.state==UIStateClassifier.State.RESULT || uio.state==UIStateClassifier.State.MODAL){resultFrames++; if(resultFrames>=8){quarantineRound("result_without_tracker_winner"); return;}} else resultFrames=0;
            updateOverlay(st,uiName); maybeAdvise(st,uiName);
        }catch(Throwable e){audit.log("RUNTIME_ERROR "+e);transition(Phase.ERROR,e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()));}
    }

    private void startRound(String hand,int first,double conf){roundNumber++;resultFrames=0;tracker=new GameTracker(hand,16,first);final GameTracker[] holder={tracker};decoder=new LiveEventDecoder(current,table,s->RuleEngine.classify(s)!=null,s->remoteEarlyOk(holder[0],s),s->localFollowupImpliesRemotePass(holder[0],s));decoder.reset(hand);lastAdviceKey=null;advisor.cancelPending();transition(Phase.TRACKING,"R"+roundNumber+" 已识别 16 张 · first=P"+first);audit.log("ROUND_START R"+roundNumber+" hand="+hand+" conf="+fmt(conf)+" first=P"+first);overlay.status("已识别 16 张","等待出牌 · R"+roundNumber);}
    private void finishRound(GameTracker.State st){advisor.cancelPending();lastAdviceKey=null;audit.log("ROUND_END R"+roundNumber+" winner=P"+st.winner+" left="+Arrays.toString(st.cardsLeft));roundEndAtNs=System.nanoTime();transition(Phase.ROUND_END,"R"+roundNumber+" 结束 · winner=P"+st.winner);overlay.status("本局结束","winner=P"+st.winner+" · 等待下一局");}
    private void resetForNextRound(){tracker=null;decoder=null;lastAdviceKey=null;resultFrames=0;gate.reset();transition(Phase.WAITING_FOR_GAME,"等待下一局");overlay.status("识别中","等待新牌局");}


    private void quarantineRound(String reason){advisor.cancelPending();lastAdviceKey=null;audit.log("ROUND_QUARANTINE R"+roundNumber+" reason="+reason);roundEndAtNs=System.nanoTime();transition(Phase.ROUND_END,"R"+roundNumber+" 未闭环，已隔离并等待下一局");overlay.status("本局未闭环","已隔离，不影响下一局");}
    private void maybeAdvise(GameTracker.State st,String uiName){
        if(!settings.advisorEnabled||st.currentPlayer!=0||!"my_turn".equals(uiName))return;String key=stateKey(st);if(key.equals(lastAdviceKey))return;lastAdviceKey=key;
        RuleEngine.Play target=st.currentTarget;int first=tracker.history().isEmpty()?st.currentPlayer:tracker.history().get(0).player;AdvisorWorker.Snapshot snap=new AdvisorWorker.Snapshot(st.ownHand,st.cardsLeft[1],target,st.targetOwner,st.currentPlayer,first,key,new ArrayList<>(tracker.history()));overlay.status("计算中","你:"+st.cardsLeft[0]+" 对手:"+st.cardsLeft[1]);
        advisor.submit(snap,settings.searchBudgetMs,settings.topK,r->{synchronized(LiveAnalyzerRuntime.this){if(tracker==null)return;GameTracker.State now=tracker.state();if(!r.stateKey.equals(stateKey(now))||now.currentPlayer!=0){audit.log("ADVICE_STALE key="+r.stateKey);return;}String text=formatAdvice(r);audit.log("ADVICE key="+r.stateKey+" "+text.replace('\n',' '));overlay.advice(now.currentTarget==null?null:now.currentTarget.cards,String.valueOf(now.cardsLeft[0]),now.cardsLeft[1],text);}});
    }
    private void updateOverlay(GameTracker.State st,String uiName){if(st.currentPlayer==0){if(!"my_turn".equals(uiName))overlay.status("等待界面稳定","你:"+st.cardsLeft[0]+" 对手:"+st.cardsLeft[1]);}else overlay.status("对手回合","你:"+st.cardsLeft[0]+" · 对手:"+st.cardsLeft[1]);}
    private String formatAdvice(AdvisorWorker.Result r){if(r.top.isEmpty())return "暂无合法建议";StringBuilder b=new StringBuilder();for(int i=0;i<r.top.size();i++){AdvisorWorker.Candidate c=r.top.get(i);b.append(i==0?"★ ":(i+1)+". ").append(c.action).append("  score=").append((int)Math.round(c.score*100)).append("%  n=").append(c.visits);if(i+1<r.top.size())b.append('\n');}b.append("\n").append(r.elapsedMs).append("ms · iter=").append(r.iterations).append(" · worlds=").append(r.reservoir);return b.toString();}
    private static boolean remoteEarlyOk(GameTracker t,String action){if(t==null||t.state().currentPlayer!=1)return false;RuleEngine.Play p=RuleEngine.classify(action);if(p==null)return false;RuleEngine.Play target=t.state().currentTarget;if(target!=null){Integer c=RuleEngine.compare(p,target);if(c==null||c!=1)return false;}if(p.length()==t.state().cardsLeft[1])return true;return RuleEngine.legalResponses(t.state().ownHand,p).isEmpty();}
    private static boolean localFollowupImpliesRemotePass(GameTracker t,String action){if(t==null||t.state().currentPlayer!=1||t.state().targetOwner==null||t.state().targetOwner!=0||t.state().currentTarget==null)return false;RuleEngine.Play p=RuleEngine.classify(action);if(p==null)return false;Integer c=RuleEngine.compare(p,t.state().currentTarget);return c==null||c!=1;}
    private static String stateKey(GameTracker.State s){return s.ownHand+"|"+Arrays.toString(s.cardsLeft)+"|"+(s.currentTarget==null?"-":s.currentTarget.cards)+"|"+s.currentPlayer;}
    private static String name(UIStateClassifier.State s){return switch(s){case MY_TURN->"my_turn";case MODAL->"modal";case RESULT->"result";default->"table";};}
    private static String fmt(double x){return String.format(Locale.ROOT,"%.2f",x);} private void transition(Phase p,String s){phase=p;if(listener!=null)listener.onStatus(p,s);audit.log("STATE "+p+" "+s);}
    public synchronized Phase phase(){return phase;}
    @Override public synchronized void close(){advisor.close();audit.close();gate.reset();tracker=null;decoder=null;}
}
