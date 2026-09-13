package com.paodekuai.analyzer.vision;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

public final class StableFrameGate {
    public static final class Result { public final boolean stable;public final double meanDifference;public final int consecutiveStable;Result(boolean s,double d,int n){stable=s;meanDifference=d;consecutiveStable=n;} }
    private final double threshold;private final int required;private Mat prev;private int count;
    public StableFrameGate(){this(4.0,2);} public StableFrameGate(double t,int r){threshold=t;required=r;}
    private static Mat view(Mat frame){
        Mat roi=frame.submat(50,Math.min(750,frame.rows()),120,Math.min(1160,frame.cols()));Mat gray=new Mat(),small=new Mat();
        Imgproc.cvtColor(roi,gray,Imgproc.COLOR_BGR2GRAY);Imgproc.resize(gray,small,new Size(260,175));roi.release();gray.release();return small;
    }
    public Result update(Mat frame){
        Mat cur=view(frame);double diff;
        if(prev==null){diff=999;count=0;} else {Mat d=new Mat();Core.absdiff(cur,prev,d);diff=Core.mean(d).val[0];d.release();if(diff<=threshold)count++;else count=0;prev.release();}
        prev=cur;return new Result(count>=required,diff,count);
    }
    public void reset(){if(prev!=null)prev.release();prev=null;count=0;}
}
