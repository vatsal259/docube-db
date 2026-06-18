package com.docube.model;

public final class SearchOptions {

    private static final SearchOptions DEFAULTS = new SearchOptions(0.25, 3);

    private final double minChunkScore;
    private final int beamWidth;

    public SearchOptions(double minChunkScore, int beamWidth) {
        if (minChunkScore < 0.0 || minChunkScore > 1.0) {
            throw new IllegalArgumentException("minChunkScore must be between 0.0 and 1.0");
        }
        if (beamWidth <= 0) {
            throw new IllegalArgumentException("beamWidth must be positive");
        }
        this.minChunkScore = minChunkScore;
        this.beamWidth = beamWidth;
    }

    public static SearchOptions defaults() {
        return DEFAULTS;
    }

    public double getMinChunkScore() {
        return minChunkScore;
    }

    public int getBeamWidth() {
        return beamWidth;
    }

    public SearchOptions withMinChunkScore(double minChunkScore) {
        return new SearchOptions(minChunkScore, beamWidth);
    }

    public SearchOptions withBeamWidth(int beamWidth) {
        return new SearchOptions(minChunkScore, beamWidth);
    }
}
