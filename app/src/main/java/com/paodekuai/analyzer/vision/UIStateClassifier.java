package com.paodekuai.analyzer.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public final class UIStateClassifier {
    public enum State { TABLE, MY_TURN, MODAL, RESULT }
    public static final class Observation {
        public final State state; public final double myTurnScore,modalScore,resultScore;
        Observation(State s,double a,double b,double c){state=s;myTurnScore=a;modalScore=b;resultScore=c;}
    }

    public Observation classify(Mat frame){
        double my=ratio(frame,340,425,955,515,new Scalar(85,110,110),new Scalar(125,255,255));
        double modal=ratio(frame,360,250,930,545,new Scalar(90,55,55),new Scalar(125,255,190));
        double result=ratio(frame,0,250,900,610,new Scalar(85,0,120),new Scalar(130,130,255));
        State s=modal>0.22?State.MODAL:(result>0.25?State.RESULT:(my>0.015?State.MY_TURN:State.TABLE));
        return new Observation(s,my,modal,result);
    }
    private static double ratio(Mat frame,int x0,int y0,int x1,int y1,Scalar lo,Scalar hi){
        Rect r=new Rect(x0,y0,Math.max(1,Math.min(frame.cols(),x1)-x0),Math.max(1,Math.min(frame.rows(),y1)-y0));
        Mat roi=new Mat(frame,r),hsv=new Mat(),mask=new Mat();Imgproc.cvtColor(roi,hsv,Imgproc.COLOR_BGR2HSV);Core.inRange(hsv,lo,hi,mask);
        double v=Core.countNonZero(mask)/(double)(mask.rows()*mask.cols());roi.release();hsv.release();mask.release();return v;
    }
}
