package com.paodekuai.analyzer.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import java.util.*;

public final class HandRecognizer {
    public static final class Observation {
        public final String cards;
        public final int count;
        public final double meanConfidence;
        public final double meanDistance;
        public final List<Integer> starts;
        Observation(String cards,int count,double meanConfidence,double meanDistance,List<Integer> starts){
            this.cards=cards;this.count=count;this.meanConfidence=meanConfidence;this.meanDistance=meanDistance;this.starts=starts;
        }
    }

    private final TemplateBank bank;
    public HandRecognizer(TemplateBank bank){this.bank=bank;}

    private static List<Double> cluster(List<Integer> values,int gap){
        Collections.sort(values); List<List<Integer>> groups=new ArrayList<>();
        for(int x:values){
            if(groups.isEmpty() || x-groups.get(groups.size()-1).get(groups.get(groups.size()-1).size()-1)>gap){
                List<Integer> g=new ArrayList<>();g.add(x);groups.add(g);
            } else groups.get(groups.size()-1).add(x);
        }
        List<Double> out=new ArrayList<>();
        for(List<Integer> g:groups){double s=0;for(int x:g)s+=x;out.add(s/g.size());}
        return out;
    }

    public List<Integer> detectVerticalCardEdges(Mat frame){
        Mat roi=frame.submat(550,Math.min(772,frame.rows()),0,frame.cols());
        Mat gray=new Mat(),edges=new Mat(),lines=new Mat();
        Imgproc.cvtColor(roi,gray,Imgproc.COLOR_BGR2GRAY);
        Imgproc.Canny(gray,edges,50,120);
        Imgproc.HoughLinesP(edges,lines,1,Math.PI/180,75,95,12);
        List<Integer> xs=new ArrayList<>();
        for(int i=0;i<lines.rows();i++){
            double[] z=lines.get(i,0); if(z==null || z.length<4)continue;
            int x1=(int)Math.round(z[0]),y1=(int)Math.round(z[1]),x2=(int)Math.round(z[2]),y2=(int)Math.round(z[3]);
            if(Math.abs(x1-x2)<=2 && Math.abs(y2-y1)>=95)xs.add((int)Math.round((x1+x2)/2.0));
        }
        List<Double> clustered=cluster(xs,4); List<Integer> out=new ArrayList<>();
        for(double x:clustered)out.add((int)Math.round(x));
        gray.release();edges.release();lines.release();roi.release();return out;
    }

    public List<Integer> detect16Starts(Mat frame){
        List<Integer> edges=detectVerticalCardEdges(frame),best=new ArrayList<>();
        for(int st:edges){
            List<Integer> chain=new ArrayList<>();chain.add(st);int cur=st;
            for(int k=0;k<15;k++){
                Integer nxt=null;double bd=1e9;
                for(int x:edges){int d=x-cur;if(d>=55 && d<=64){double dd=Math.abs(d-59.7);if(dd<bd){bd=dd;nxt=x;}}}
                if(nxt==null)break;chain.add(nxt);cur=nxt;
            }
            if(chain.size()>best.size())best=chain;
        }
        return best.size()>=16?new ArrayList<>(best.subList(0,16)):Collections.emptyList();
    }

    public List<Integer> detectCurrentStarts(Mat frame,int minCards,int maxCards){
        List<Integer> edges=detectVerticalCardEdges(frame),best=new ArrayList<>();
        for(int i=0;i<edges.size();i++){
            List<Integer> chain=new ArrayList<>();int cur=edges.get(i);chain.add(cur);
            for(int j=i+1;j<edges.size();j++){
                int x=edges.get(j),gap=x-cur;
                if(gap>=48 && gap<=100){chain.add(x);cur=x;if(chain.size()>=maxCards)break;}
                else if(gap>100)break;
            }
            if(chain.size()>best.size())best=chain;
        }
        if(best.size()<minCards)return Collections.emptyList();
        return new ArrayList<>(best.subList(0,Math.min(maxCards,best.size())));
    }

    public Observation recognizeInitial16(Mat frame){return recognize(frame,detect16Starts(frame));}
    public Observation recognizeCurrent(Mat frame){return recognize(frame,detectCurrentStarts(frame,1,16));}

    private Observation recognize(Mat frame,List<Integer> starts){
        if(starts.isEmpty())return null;
        StringBuilder cards=new StringBuilder();double cs=0,ds=0;
        for(int x:starts){
            int xa=Math.max(0,x+5),xb=Math.min(frame.cols(),x+51);
            if(xb<=xa || frame.rows()<630)return null;
            Mat p=frame.submat(575,630,xa,xb),g=new Mat();Imgproc.cvtColor(p,g,Imgproc.COLOR_BGR2GRAY);
            TemplateBank.Result r=bank.classify(g);cards.append(r.label);cs+=r.confidence;ds+=r.distance;
            p.release();g.release();
        }
        return new Observation(cards.toString(),starts.size(),cs/starts.size(),ds/starts.size(),starts);
    }
}
