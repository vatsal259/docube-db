package com.docube.scoring;

public interface ChunkScorer {
    double scoreChunks(String queryChunk, String recordChunk);
}
