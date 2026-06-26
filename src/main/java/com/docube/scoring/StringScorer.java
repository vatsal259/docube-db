package com.docube.scoring;

public interface StringScorer {
    double score(String query, String record);
}
