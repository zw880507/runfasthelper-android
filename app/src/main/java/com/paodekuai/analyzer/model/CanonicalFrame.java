package com.paodekuai.analyzer.model;

import org.opencv.core.Mat;

public final class CanonicalFrame {
    public final Mat image;
    public final double timestampSeconds;
    public CanonicalFrame(Mat image, double timestampSeconds) {
        this.image = image;
        this.timestampSeconds = timestampSeconds;
    }
}
