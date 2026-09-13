package com.paodekuai.analyzer.decoder;

import com.paodekuai.analyzer.model.CanonicalFrame;
import com.paodekuai.analyzer.model.PublicActionEvent;
import com.paodekuai.analyzer.tracker.RuleEngine;
import com.paodekuai.analyzer.vision.HandRecognizer;
import com.paodekuai.analyzer.vision.TableActionRecognizer;

import java.util.*;
import java.util.function.Predicate;

/** Conservative two-player visual event decoder, ported from Windows v0.8.0. */
public final class LiveEventDecoder {
    private final HandRecognizer hand;
    private final TableActionRecognizer table;
    private final double minHandConf,minTableConf,localTableSingleFrameConf;
    private final Predicate<String> localActionValidator;
    private final Predicate<String> remoteEarlyCommitValidator;
    private final Predicate<String> localFollowupImpliesRemotePassValidator;

    private String lastHand,lastTable,lastUi;
    private Integer lastExpectedPlayer;
    private String lastRemoteAction,lastLocalAction;
    private boolean remoteEpochCleared;
    private String lastEmittedSignature;
    private PendingPass pendingPass;
    private LocalCandidate localTableCandidate;
    private PublicActionEvent pendingLocalEvent;
    private final Map<String,RemoteCandidate> remoteCandidates=new HashMap<>();

    public static final class Debug {
        public String handCards,trustedHand,tableRaw,tableZone,tableReason,uiState;
        public double handConf,tableConf;
        public Integer expectedPlayer;
        public boolean canPass,frameStable;
    }
    public final Debug debug=new Debug();

    private static final class PendingPass {int player;double ts;String source;PendingPass(int p,double t,String s){player=p;ts=t;source=s;}}
    private static final class LocalCandidate {String cards;int count;LocalCandidate(String c,int n){cards=c;count=n;}}
    private static final class RemoteCandidate {String cards;double firstTs,lastTs,maxConf;int count;RemoteCandidate(String c,double t,double q){cards=c;firstTs=t;lastTs=t;maxConf=q;count=1;}}

    public LiveEventDecoder(HandRecognizer hand, TableActionRecognizer table,
                            Predicate<String> localActionValidator,
                            Predicate<String> remoteEarlyCommitValidator,
                            Predicate<String> localFollowupImpliesRemotePassValidator) {
        this.hand=hand;this.table=table;this.minHandConf=.68;this.minTableConf=.58;this.localTableSingleFrameConf=.64;
        this.localActionValidator=localActionValidator;this.remoteEarlyCommitValidator=remoteEarlyCommitValidator;this.localFollowupImpliesRemotePassValidator=localFollowupImpliesRemotePassValidator;
    }

    public void reset(String initialHand){lastHand=initialHand;lastTable=null;lastUi=null;lastExpectedPlayer=null;lastRemoteAction=null;lastLocalAction=null;remoteEpochCleared=false;lastEmittedSignature=null;pendingPass=null;localTableCandidate=null;pendingLocalEvent=null;remoteCandidates.clear();}
    public String trustedHand(){return lastHand;}

    public void confirmEvent(PublicActionEvent e){
        if(e.player==0&&!"PASS".equals(e.action)&&lastHand!=null){lastLocalAction=e.action;if(RuleEngine.contains(lastHand,e.action))lastHand=RuleEngine.subtract(lastHand,e.action);}
        if(e.player==1&&!"PASS".equals(e.action)){lastRemoteAction=e.action;remoteCandidates.clear();}
        if(e.player==0)pendingLocalEvent=null;pendingPass=null;
    }
    public void rejectEvent(PublicActionEvent e){String sig=sig(e);if(sig.equals(lastEmittedSignature))lastEmittedSignature=null;if(e.player==1&&!"PASS".equals(e.action)){remoteCandidates.clear();}if(e.player==0)pendingLocalEvent=null;pendingPass=null;}

    public List<PublicActionEvent> observe(CanonicalFrame cf,Integer expectedPlayer,String uiState,boolean canPass,boolean frameStable){
        List<PublicActionEvent> out=new ArrayList<>();
        HandRecognizer.Observation h=frameStable?hand.recognizeCurrent(cf.image):null;
        String handCards=(h!=null&&h.meanConfidence>=minHandConf)?h.cards:null;
        String preferred=expectedPlayer==null?"remote":(expectedPlayer==0?"local":"remote");
        TableActionRecognizer.Observation t=table.recognizeZone(cf.image,preferred);
        String tableCards=(t.cards!=null&&t.confidence>=minTableConf)?t.cards:null;
        debug.handCards=handCards;debug.handConf=h==null?0:h.meanConfidence;debug.trustedHand=lastHand;debug.tableRaw=t.cards;debug.tableConf=t.confidence;debug.tableReason=t.reason;debug.tableZone=t.zone;debug.uiState=uiState;debug.expectedPlayer=expectedPlayer;debug.canPass=canPass;debug.frameStable=frameStable;

        boolean enteringRemote=expectedPlayer!=null&&expectedPlayer==1&&(lastExpectedPlayer==null||lastExpectedPlayer!=1);

        if(lastHand!=null&&handCards!=null&&!handCards.equals(lastHand)){
            boolean reduction=handCards.length()<lastHand.length()&&isSubmultiset(handCards,lastHand);
            if(reduction&&!"my_turn".equals(uiState)){
                String d=handDelta(lastHand,handCards);
                if(!d.isEmpty()&&localActionValidator.test(d)){
                    if(expectedPlayer!=null&&expectedPlayer==1&&localFollowupImpliesRemotePassValidator.test(d)){
                        emitOnce(out,new PublicActionEvent(1,"PASS",.99,"local_followup_implies_remote_pass",cf.timestampSeconds));
                        PublicActionEvent ev=new PublicActionEvent(0,d,Math.min(h.meanConfidence,.99),"hand_delta_after_implied_pass",cf.timestampSeconds);out.add(ev);pendingLocalEvent=ev;
                    } else if(expectedPlayer==null||expectedPlayer==0){PublicActionEvent ev=new PublicActionEvent(0,d,Math.min(h.meanConfidence,.99),"hand_delta",cf.timestampSeconds);emitOnce(out,ev);if(!out.isEmpty())pendingLocalEvent=ev;}
                }
            }
        } else if(handCards!=null&&lastHand==null) lastHand=handCards;

        // Last-card fallback from local lane only.
        if(expectedPlayer!=null&&expectedPlayer==1&&tableCards==null&&lastHand!=null&&lastHand.length()==1){
            TableActionRecognizer.Observation lt=table.recognizeZone(cf.image,"local");String lc=(lt.cards!=null&&lt.confidence>=Math.max(minTableConf,.80))?lt.cards:null;
            if(lc!=null&&!lc.equals(lastLocalAction)&&localTablePlausible(lc)&&localFollowupImpliesRemotePassValidator.test(lc)){
                emitOnce(out,new PublicActionEvent(1,"PASS",.99,"local_table_implies_remote_pass",cf.timestampSeconds));PublicActionEvent ev=new PublicActionEvent(0,lc,Math.min(lt.confidence,.98),"local_table_after_implied_pass",cf.timestampSeconds);out.add(ev);pendingLocalEvent=ev;
            }
        }

        if(frameStable&&expectedPlayer!=null&&expectedPlayer==0&&lastHand!=null&&lastHand.length()==1&&handCards==null&&tableCards!=null&&!tableCards.equals(lastTable)&&sameMultiset(tableCards,lastHand)){
            PublicActionEvent ev=new PublicActionEvent(0,lastHand,Math.min(t.confidence,.95),"final_card_table_crosscheck",cf.timestampSeconds);emitOnce(out,ev);if(!out.isEmpty())pendingLocalEvent=ev;
        }

        if(frameStable&&out.isEmpty()&&expectedPlayer!=null&&expectedPlayer==0&&!"my_turn".equals(uiState)&&tableCards!=null&&!tableCards.equals(lastTable)&&localTablePlausible(tableCards)){
            int n=(localTableCandidate!=null&&localTableCandidate.cards.equals(tableCards))?localTableCandidate.count+1:1;localTableCandidate=new LocalCandidate(tableCards,n);
            if(t.confidence>=localTableSingleFrameConf||n>=2){PublicActionEvent ev=new PublicActionEvent(0,tableCards,Math.min(t.confidence,.95),"local_table_fallback",cf.timestampSeconds);emitOnce(out,ev);if(!out.isEmpty())pendingLocalEvent=ev;lastTable=tableCards;localTableCandidate=null;pendingPass=null;}
        } else if(expectedPlayer!=null&&expectedPlayer==0&&"my_turn".equals(uiState)) localTableCandidate=null;

        if(expectedPlayer!=null&&expectedPlayer==1&&tableCards!=null&&((!tableCards.equals(lastTable)&&!tableCards.equals(lastRemoteAction))||remoteEpochCleared)){
            double now=cf.timestampSeconds;remoteCandidates.entrySet().removeIf(e->now-e.getValue().lastTs>.8);RemoteCandidate c=remoteCandidates.get(tableCards);if(c==null){c=new RemoteCandidate(tableCards,now,t.confidence);remoteCandidates.put(tableCards,c);}else{c.lastTs=now;c.count++;c.maxConf=Math.max(c.maxConf,t.confidence);}double span=c.lastTs-c.firstTs;boolean handoff="my_turn".equals(uiState)||"modal".equals(uiState)||"result".equals(uiState);boolean strong;
            if(c.cards.length()==1) strong=(handoff&&c.maxConf>=.90)||(c.count>=3&&span>=.24&&c.maxConf>=.72);
            else if(handoff) strong=(c.count>=1&&c.maxConf>=.85)||(c.count>=2&&c.maxConf>=.65);
            else strong=c.count>=2&&span>=.12&&c.maxConf>=.80;
            boolean allow=handoff||remoteEarlyCommitValidator.test(c.cards);if(strong&&allow){emitOnce(out,new PublicActionEvent(1,c.cards,Math.min(c.maxConf,.98),handoff?"remote_handoff_consensus":"remote_temporal_consensus",cf.timestampSeconds));if(!out.isEmpty())remoteCandidates.clear();}
        }

        if(expectedPlayer==null||expectedPlayer!=1){if(tableCards!=null)lastTable=tableCards;remoteEpochCleared=false;}else{if(enteringRemote)remoteEpochCleared=tableCards==null;else if("table".equals(uiState)&&tableCards==null)remoteEpochCleared=true;}

        if(!out.isEmpty())pendingPass=null;
        else if(lastUi!=null&&uiState!=null&&!uiState.equals(lastUi)){
            if(expectedPlayer!=null&&expectedPlayer==0&&"my_turn".equals(lastUi)&&"table".equals(uiState)&&canPass&&handCards!=null&&handCards.equals(lastHand))pendingPass=new PendingPass(0,cf.timestampSeconds,"turn_transition_hand_unchanged");
            else if(expectedPlayer!=null&&expectedPlayer==1&&"table".equals(lastUi)&&"my_turn".equals(uiState)&&canPass)pendingPass=new PendingPass(1,cf.timestampSeconds,"turn_transition_no_new_table_action");
        }
        if(pendingPass!=null&&out.isEmpty()){
            if(expectedPlayer!=null&&pendingPass.player==expectedPlayer&&cf.timestampSeconds-pendingPass.ts>=.45){emitOnce(out,new PublicActionEvent(pendingPass.player,"PASS",.78,pendingPass.source+"_delayed",cf.timestampSeconds));pendingPass=null;}else if(expectedPlayer==null||pendingPass.player!=expectedPlayer)pendingPass=null;
        }
        if(uiState!=null)lastUi=uiState;lastExpectedPlayer=expectedPlayer;return out;
    }

    private boolean localTablePlausible(String cards){return cards!=null&&lastHand!=null&&isSubmultiset(cards,lastHand)&&localActionValidator.test(cards);}
    private void emitOnce(List<PublicActionEvent> out,PublicActionEvent e){String s=sig(e);if(!s.equals(lastEmittedSignature)){out.add(e);lastEmittedSignature=s;}}
    private static String sig(PublicActionEvent e){return e.player+"|"+e.action+"|"+e.source;}
    private static boolean isSubmultiset(String after,String before){Map<Integer,Integer>a=RuleEngine.counts(after),b=RuleEngine.counts(before);for(var e:a.entrySet())if(e.getValue()>b.getOrDefault(e.getKey(),0))return false;return true;}
    private static boolean sameMultiset(String a,String b){return RuleEngine.counts(a).equals(RuleEngine.counts(b));}
    private static String handDelta(String before,String after){if(!isSubmultiset(after,before))return "";Map<Integer,Integer>b=RuleEngine.counts(before),a=RuleEngine.counts(after);StringBuilder out=new StringBuilder();List<Integer> rs=new ArrayList<>(b.keySet());rs.sort(Collections.reverseOrder());for(int r:rs){int n=b.get(r)-a.getOrDefault(r,0);for(int i=0;i<n;i++)out.append(RuleEngine.text(r));}return out.toString();}
}
