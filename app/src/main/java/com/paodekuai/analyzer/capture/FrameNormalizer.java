package com.paodekuai.analyzer.capture;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

/** Normalizes arbitrary landscape phone captures to the Windows reference 1296x772 coordinate space. */
public final class FrameNormalizer {
    public static final int W=1296,H=772;
    public Mat normalize(Mat src){
        if(src.empty()) return new Mat();
        int sw=src.cols(), sh=src.rows();
        // MediaProjection may follow device rotation. Vision is calibrated for landscape.
        Mat work=src, rotated=null;
        if(sh>sw){rotated=new Mat(); org.opencv.core.Core.rotate(src,rotated,org.opencv.core.Core.ROTATE_90_CLOCKWISE); work=rotated; sw=work.cols(); sh=work.rows();}
        double target=W/(double)H, cur=sw/(double)sh;
        Rect crop;
        if(cur>target){int cw=(int)Math.round(sh*target); crop=new Rect(Math.max(0,(sw-cw)/2),0,Math.min(cw,sw),sh);}
        else {int ch=(int)Math.round(sw/target); crop=new Rect(0,Math.max(0,(sh-ch)/2),sw,Math.min(ch,sh));}
        Mat roi=new Mat(work,crop), out=new Mat(); Imgproc.resize(roi,out,new Size(W,H),0,0,Imgproc.INTER_AREA); roi.release(); if(rotated!=null)rotated.release(); return out;
    }
}
