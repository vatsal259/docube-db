package com.docube.model;

import java.util.Collections;
import java.util.List;

public final class SearchHit {

    private final int lineNo;
    private final String text;
    private final double score;
    private final List<Double> chunkScores;

    public SearchHit(int lineNo, String text, double score, List<Double> chunkScores) {
        this.lineNo = lineNo;
        this.text = text;
        this.score = score;
        this.chunkScores = chunkScores == null
                ? List.of()
                : Collections.unmodifiableList(chunkScores);
    }

    public SearchHit(int lineNo, String text, double score) {
        this(lineNo, text, score, List.of());
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getText() {
        return text;
    }

    public double getScore() {
        return score;
    }

    public List<Double> getChunkScores() {
        return chunkScores;
    }

    @Override
    public String toString() {
        return "SearchHit{lineNo=" + lineNo + ", text='" + text + "', score=" + score + '}';
    }
}
