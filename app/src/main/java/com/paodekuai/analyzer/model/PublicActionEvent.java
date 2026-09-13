package com.paodekuai.analyzer.model;

public final class PublicActionEvent {
    public final int player;
    public final String action;
    public final double confidence;
    public final String source;
    public final double timestamp;

    public PublicActionEvent(int player, String action, double confidence, String source, double timestamp) {
        this.player = player;
        this.action = action;
        this.confidence = confidence;
        this.source = source;
        this.timestamp = timestamp;
    }

    @Override public String toString() {
        return "P" + player + " " + action + " conf=" + String.format("%.2f", confidence) + " src=" + source;
    }
}
