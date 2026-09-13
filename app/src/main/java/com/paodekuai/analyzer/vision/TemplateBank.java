package com.paodekuai.analyzer.vision;

import android.content.res.AssetManager;
import org.opencv.core.*;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.*;
import java.util.*;

public final class TemplateBank {
    public static final class Result {
        public final String label;
        public final double confidence;
        public final double distance;
        Result(String label, double confidence, double distance) {
            this.label = label; this.confidence = confidence; this.distance = distance;
        }
    }

    private final Map<String, List<Mat>> templates = new HashMap<>();
    private final boolean force32x40;

    private TemplateBank(boolean force32x40) { this.force32x40 = force32x40; }

    public static TemplateBank load(AssetManager assets, String root, boolean force32x40) throws IOException {
        TemplateBank bank = new TemplateBank(force32x40);
        String[] labels = assets.list(root);
        if (labels == null) throw new IOException("No templates under assets/" + root);
        for (String label : labels) {
            String dir = root + "/" + label;
            String[] files = assets.list(dir);
            if (files == null) continue;
            Arrays.sort(files);
            List<Mat> arr = new ArrayList<>();
            for (String name : files) {
                if (!name.endsWith(".png")) continue;
                try (InputStream in = assets.open(dir + "/" + name)) {
                    byte[] bytes = readAll(in);
                    MatOfByte mob = new MatOfByte(bytes);
                    Mat m = Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_GRAYSCALE);
                    if (m.empty()) throw new IOException("Failed to decode " + dir + "/" + name);
                    arr.add(m);
                }
            }
            if (!arr.isEmpty()) bank.templates.put(label, arr);
        }
        if (bank.templates.isEmpty()) throw new IOException("Template bank empty: " + root);
        return bank;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] b = new byte[8192]; int n;
        while ((n = in.read(b)) >= 0) out.write(b,0,n);
        return out.toByteArray();
    }

    public Result classify(Mat patchGray) {
        Mat q = new Mat();
        if (force32x40) Imgproc.resize(patchGray, q, new Size(32,40), 0,0, Imgproc.INTER_AREA);
        else patchGray.copyTo(q);
        Imgproc.equalizeHist(q,q);

        String bestLabel = "?";
        double best = Double.POSITIVE_INFINITY;
        double second = Double.POSITIVE_INFINITY;
        for (Map.Entry<String,List<Mat>> e : templates.entrySet()) {
            double labelBest = Double.POSITIVE_INFINITY;
            for (Mat src : e.getValue()) {
                Mat t;
                if (src.rows()!=q.rows() || src.cols()!=q.cols()) {
                    t = new Mat(); Imgproc.resize(src,t,q.size(),0,0,Imgproc.INTER_AREA);
                } else t = src;
                Mat diff = new Mat(); Core.absdiff(q,t,diff);
                double s = Core.mean(diff).val[0];
                diff.release();
                if (t != src) t.release();
                if (s < labelBest) labelBest=s;
            }
            if (labelBest < best) {
                second = best; best = labelBest; bestLabel=e.getKey();
            } else if (labelBest < second) second=labelBest;
        }
        double margin = Double.isFinite(second) ? Math.max(0, second-best) : 0;
        double conf = force32x40 ? clamp(0.48 + margin/42.0) : clamp(0.55 + margin/45.0);
        q.release();
        return new Result(bestLabel,conf,best);
    }

    private static double clamp(double v) { return Math.max(0,Math.min(1,v)); }
}
