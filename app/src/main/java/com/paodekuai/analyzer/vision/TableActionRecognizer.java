package com.paodekuai.analyzer.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

public final class TableActionRecognizer {
    public static final Rect REMOTE_ROI=new Rect(260,235,840,100);
    public static final Rect LOCAL_ROI=new Rect(260,350,840,170);
    public static final class Observation {
        public final String cards;public final double confidence;public final int glyphs;public final String reason;public final String zone;
        Observation(String c,double conf,int g,String r,String z){cards=c;confidence=conf;glyphs=g;reason=r;zone=z;}
    }
    private final TemplateBank bank;private final double minConfidence;
    public TableActionRecognizer(TemplateBank b,double minConfidence){bank=b;this.minConfidence=minConfidence;}

    public Observation recognizeZone(Mat frame,String zone){
        boolean local="local".equals(zone);List<Rect> comps=components(frame,local?LOCAL_ROI:REMOTE_ROI,local);
        if(comps.isEmpty())return new Observation(null,0,0,"no card-rank group",zone);
        StringBuilder cards=new StringBuilder();double sum=0;
        for(Rect c:comps){Mat p=patch(frame,c);TemplateBank.Result r=bank.classify(p);cards.append(r.label);sum+=r.confidence;p.release();}
        double mean=sum/comps.size();if(mean<minConfidence || cards.indexOf("?")>=0)return new Observation(null,mean,comps.size(),"low confidence",zone);
        return new Observation(cards.toString(),mean,comps.size(),"resolved",zone);
    }

    private static List<Rect> components(Mat frame,Rect box,boolean local){
        Rect safe=new Rect(box.x,box.y,Math.min(box.width,frame.cols()-box.x),Math.min(box.height,frame.rows()-box.y));
        Mat roi=new Mat(frame,safe),gray=new Mat();Imgproc.cvtColor(roi,gray,Imgproc.COLOR_BGR2GRAY);
        Mat dark=new Mat();Imgproc.threshold(gray,dark,105,255,Imgproc.THRESH_BINARY_INV);
        List<Mat> ch=new ArrayList<>();Core.split(roi,ch);Mat b=ch.get(0),g=ch.get(1),r=ch.get(2);
        Mat g125=new Mat(),b125=new Mat();Core.multiply(g,new Scalar(1.25),g125);Core.multiply(b,new Scalar(1.25),b125);
        Mat m1=new Mat(),m2=new Mat(),m3=new Mat(),red=new Mat();Core.compare(r,g125,m1,Core.CMP_GT);Core.compare(r,b125,m2,Core.CMP_GT);Core.compare(r,new Scalar(100),m3,Core.CMP_GT);Core.bitwise_and(m1,m2,red);Core.bitwise_and(red,m3,red);
        Mat mask=new Mat();Core.bitwise_or(dark,red,mask);Mat kernel=Imgproc.getStructuringElement(Imgproc.MORPH_RECT,new Size(2,2));Imgproc.morphologyEx(mask,mask,Imgproc.MORPH_CLOSE,kernel);
        Mat labels=new Mat(),stats=new Mat(),cent=new Mat();int n=Imgproc.connectedComponentsWithStats(mask,labels,stats,cent);
        List<Rect> raw=new ArrayList<>();int ymin=local?5:12,ymax=local?70:52,hmin=local?14:24,hmax=local?44:40,amax=local?800:700;
        for(int i=1;i<n;i++){
            int x=(int)stats.get(i,Imgproc.CC_STAT_LEFT)[0],y=(int)stats.get(i,Imgproc.CC_STAT_TOP)[0],w=(int)stats.get(i,Imgproc.CC_STAT_WIDTH)[0],h=(int)stats.get(i,Imgproc.CC_STAT_HEIGHT)[0],a=(int)stats.get(i,Imgproc.CC_STAT_AREA)[0];
            if(!(y>=ymin&&y<=ymax&&h>=hmin&&h<=hmax&&w>=5&&w<=34&&a>=55&&a<=amax))continue;
            int xa=Math.max(0,x-5),xb=Math.min(roi.cols(),x+w+5),ya=Math.max(0,y-5),yb=Math.min(roi.rows(),y+h+6);
            Mat rr=roi.submat(ya,yb,xa,xb),hsv=new Mat(),white=new Mat();Imgproc.cvtColor(rr,hsv,Imgproc.COLOR_BGR2HSV);Core.inRange(hsv,new Scalar(0,0,150),new Scalar(179,70,255),white);double wr=Core.countNonZero(white)/(double)(white.rows()*white.cols());rr.release();hsv.release();white.release();
            if(wr>=0.28)raw.add(new Rect(x+box.x,y+box.y,w,h));
        }
        raw.sort(Comparator.comparingInt(z->z.x));
        List<List<Rect>> groups=new ArrayList<>();for(Rect c:raw){Rect tail=null;if(!groups.isEmpty()){List<Rect> prev=groups.get(groups.size()-1);tail=prev.get(prev.size()-1);}if(tail==null||c.x-(tail.x+tail.width)>5){List<Rect> q=new ArrayList<>();q.add(c);groups.add(q);}else groups.get(groups.size()-1).add(c);}
        List<Rect> merged=new ArrayList<>();for(List<Rect> grp:groups){int x=Integer.MAX_VALUE,y=Integer.MAX_VALUE,xx=0,yy=0;for(Rect z:grp){x=Math.min(x,z.x);y=Math.min(y,z.y);xx=Math.max(xx,z.x+z.width);yy=Math.max(yy,z.y+z.height);}if(xx-x>=5&&xx-x<=38&&yy-y>=14&&yy-y<=(local?45:42))merged.add(new Rect(x,y,xx-x,yy-y));}
        List<List<Rect>> chains=new ArrayList<>();for(int i=0;i<merged.size();i++){List<Rect> chain=new ArrayList<>();chain.add(merged.get(i));for(int j=i+1;j<merged.size();j++){Rect c=merged.get(j);int dx=c.x+0-chain.get(chain.size()-1).x;if(dx>=22&&dx<=(local?75:58))chain.add(c);else if(dx>(local?75:58))break;}chains.add(chain);}
        List<Rect> best=Collections.emptyList();double bestTie=-1e18;int anchor=local?650:930;for(List<Rect> chain:chains){double center=(chain.get(0).x+last(chain).x+last(chain).width)/2.0;double tie=-Math.abs(center-anchor);if(chain.size()>best.size()||(chain.size()==best.size()&&tie>bestTie)){best=chain;bestTie=tie;}}
        roi.release();gray.release();dark.release();g125.release();b125.release();m1.release();m2.release();m3.release();red.release();mask.release();kernel.release();labels.release();stats.release();cent.release();for(Mat z:ch)z.release();return best;
    }
    private static Rect last(List<Rect> x){return x.get(x.size()-1);}
    private static Mat patch(Mat frame,Rect c){int pad=5;int x=Math.max(0,c.x-pad),y=Math.max(0,c.y-pad),xx=Math.min(frame.cols(),c.x+c.width+pad),yy=Math.min(frame.rows(),c.y+c.height+pad);Mat p=frame.submat(y,yy,x,xx),g=new Mat(),out=new Mat();Imgproc.cvtColor(p,g,Imgproc.COLOR_BGR2GRAY);Imgproc.resize(g,out,new Size(32,40),0,0,Imgproc.INTER_AREA);p.release();g.release();return out;}
}
