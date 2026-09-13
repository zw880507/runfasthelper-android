package com.paodekuai.analyzer.video;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.paodekuai.analyzer.decoder.LiveEventDecoder;
import com.paodekuai.analyzer.model.CanonicalFrame;
import com.paodekuai.analyzer.model.PublicActionEvent;
import com.paodekuai.analyzer.tracker.GameTracker;
import com.paodekuai.analyzer.tracker.RuleEngine;
import com.paodekuai.analyzer.vision.*;

import org.opencv.android.Utils;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OfflineVideoAnalyzer {
    public interface Progress { void onProgress(double fraction,String status); }
    public static final class RoundResult {
        public final int index;public final String initialHand;public final int firstPlayer;public final List<PublicActionEvent> events;public final Integer winner;public final int[] cardsLeft;public final int rejected;
        RoundResult(int i,String h,int f,List<PublicActionEvent> e,Integer w,int[] left,int rej){index=i;initialHand=h;firstPlayer=f;events=e;winner=w;cardsLeft=left;rejected=rej;}
    }
    public static final class Report {
        public final List<RoundResult> rounds;public final long analyzedFrames;public final double seconds;
        Report(List<RoundResult> r,long f,double s){rounds=r;analyzedFrames=f;seconds=s;}
        public int acceptedEvents(){int n=0;for(RoundResult r:rounds)n+=r.events.size();return n;}
        public String text(){StringBuilder b=new StringBuilder();b.append("分析完成\n轮数: ").append(rounds.size()).append("\n公开动作: ").append(acceptedEvents()).append("\n帧数: ").append(analyzedFrames).append("\n\n");for(RoundResult r:rounds){b.append("=== R").append(r.index).append(" ===\n");b.append("初始手牌: ").append(r.initialHand).append("  first=P").append(r.firstPlayer).append("\n");for(PublicActionEvent e:r.events)b.append(String.format(Locale.ROOT,"%6.1fs  P%d %-8s  %.2f  %s\n",e.timestamp,e.player,e.action,e.confidence,e.source));b.append("winner=P").append(r.winner).append(" left=").append(Arrays.toString(r.cardsLeft)).append(" rejected=").append(r.rejected).append("\n\n");}return b.toString();}
    }

    private final Context context;private final HandRecognizer initial,current;private final TableActionRecognizer table;private final UIStateClassifier ui=new UIStateClassifier();private final AtomicBoolean cancelled=new AtomicBoolean(false);
    public OfflineVideoAnalyzer(Context context,TemplateBank handBank,TemplateBank tableBank){this.context=context;initial=new HandRecognizer(handBank);current=new HandRecognizer(handBank);table=new TableActionRecognizer(tableBank,.55);}
    public void cancel(){cancelled.set(true);}

    public Report analyze(Uri uri,double fps,Progress cb) throws Exception {
        cancelled.set(false);MediaMetadataRetriever mmr=new MediaMetadataRetriever();mmr.setDataSource(context,uri);long durationMs=Long.parseLong(Objects.requireNonNullElse(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION),"0"));if(durationMs<=0)throw new IllegalArgumentException("无法读取视频时长");
        long stepUs=(long)(1_000_000.0/fps),durationUs=durationMs*1000L;StableFrameGate gate=new StableFrameGate(4.0,2);List<RoundResult> rounds=new ArrayList<>();long frameCount=0;int roundIndex=1;
        GameTracker tracker=null;LiveEventDecoder decoder=null;List<PublicActionEvent> accepted=new ArrayList<>();int rejected=0;String initialHand=null;int firstPlayer=1;final GameTracker[] holder=new GameTracker[1];
        for(long us=0;us<=durationUs&&!cancelled.get();us+=stepUs){
            Bitmap bmp=mmr.getFrameAtTime(us,MediaMetadataRetriever.OPTION_CLOSEST);if(bmp==null)continue;Mat rgba=new Mat(),bgr=new Mat(),frame=new Mat();Utils.bitmapToMat(bmp,rgba);Imgproc.cvtColor(rgba,bgr,Imgproc.COLOR_RGBA2BGR);Imgproc.resize(bgr,frame,new Size(1296,772),0,0,Imgproc.INTER_AREA);bmp.recycle();rgba.release();bgr.release();frameCount++;double ts=us/1_000_000.0;StableFrameGate.Result stab=gate.update(frame);UIStateClassifier.Observation uio=ui.classify(frame);String uiName=switch(uio.state){case MY_TURN->"my_turn";case MODAL->"modal";case RESULT->"result";default->"table";};
            if(tracker==null){if(stab.stable){HandRecognizer.Observation h=initial.recognizeInitial16(frame);if(h!=null&&h.meanConfidence>=.70){initialHand=h.cards;firstPlayer=uio.state==UIStateClassifier.State.MY_TURN?0:1;tracker=new GameTracker(initialHand,16,firstPlayer);holder[0]=tracker;accepted=new ArrayList<>();rejected=0;decoder=new LiveEventDecoder(current,table,
                        s->RuleEngine.classify(s)!=null,
                        s->remoteEarlyOk(holder[0],s),
                        s->localFollowupImpliesRemotePass(holder[0],s));decoder.reset(initialHand);if(cb!=null)cb.onProgress(us/(double)durationUs,"R"+roundIndex+" 开始: "+initialHand+" first=P"+firstPlayer);}}
                frame.release();continue;
            }
            Integer expected=tracker.state().winner==null?tracker.state().currentPlayer:null;boolean canPass=tracker.state().currentTarget!=null;List<PublicActionEvent> events=decoder.observe(new CanonicalFrame(frame,ts),expected,uiName,canPass,stab.stable);
            for(PublicActionEvent e:events){try{GameTracker.State st=tracker.observe(e.player,e.action);decoder.confirmEvent(e);accepted.add(e);if(st.winner==null&&st.currentPlayer==0&&st.currentTarget!=null&&RuleEngine.legalResponses(st.ownHand,st.currentTarget).isEmpty()){PublicActionEvent forced=new PublicActionEvent(0,"PASS",1.0,"rule_forced_pass",ts);GameTracker.State fs=tracker.observe(0,"PASS");decoder.confirmEvent(forced);accepted.add(forced);}}
                catch(Exception ex){decoder.rejectEvent(e);rejected++;}}
            if(tracker.state().winner!=null){GameTracker.State st=tracker.state();rounds.add(new RoundResult(roundIndex,initialHand,firstPlayer,new ArrayList<>(accepted),st.winner,st.cardsLeft.clone(),rejected));if(cb!=null)cb.onProgress(us/(double)durationUs,"R"+roundIndex+" 完成: winner=P"+st.winner+" events="+accepted.size());roundIndex++;tracker=null;holder[0]=null;decoder=null;gate.reset();}
            if(cb!=null&&frameCount%10==0)cb.onProgress(us/(double)durationUs,"分析中 " + String.format(Locale.ROOT,"%.1f%%",100.0*us/durationUs));frame.release();
        }
        mmr.release();if(cancelled.get())throw new InterruptedException("analysis cancelled");return new Report(rounds,frameCount,durationMs/1000.0);
    }

    private static boolean remoteEarlyOk(GameTracker t,String action){if(t==null||t.state().currentPlayer!=1)return false;RuleEngine.Play p=RuleEngine.classify(action);if(p==null)return false;RuleEngine.Play target=t.state().currentTarget;if(target!=null){Integer c=RuleEngine.compare(p,target);if(c==null||c!=1)return false;}if(p.length()==t.state().cardsLeft[1])return true;return RuleEngine.legalResponses(t.state().ownHand,p).isEmpty();}
    private static boolean localFollowupImpliesRemotePass(GameTracker t,String action){if(t==null||t.state().currentPlayer!=1||t.state().targetOwner==null||t.state().targetOwner!=0||t.state().currentTarget==null)return false;RuleEngine.Play p=RuleEngine.classify(action);if(p==null)return false;Integer c=RuleEngine.compare(p,t.state().currentTarget);return c==null||c!=1;}
}
