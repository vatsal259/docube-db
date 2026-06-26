package com.docube.trie;

import com.docube.scoring.ChunkScorer;
import com.docube.scoring.StringScorer;

public final class TrieRecallContext {

    private final ChunkScorer chunkScorer;
    private final double minChunkScore;
    private final int beamWidth;

    public TrieRecallContext(ChunkScorer chunkScorer, double minChunkScore, int beamWidth) {
        if (chunkScorer == null) {
            throw new IllegalArgumentException("chunkScorer must not be null");
        }
        if (minChunkScore < 0.0 || minChunkScore > 1.0) {
            throw new IllegalArgumentException("minChunkScore must be between 0.0 and 1.0");
        }
        if (beamWidth <= 0) {
            throw new IllegalArgumentException("beamWidth must be positive");
        }
        this.chunkScorer = chunkScorer;
        this.minChunkScore = minChunkScore;
        this.beamWidth = beamWidth;
    }

    public static TrieRecallContext of(ChunkScorer chunkScorer, double minChunkScore, int beamWidth) {
        return new TrieRecallContext(chunkScorer, minChunkScore, beamWidth);
    }

    public static TrieRecallContext from(StringScorer scorer, double minChunkScore, int beamWidth) {
        return new TrieRecallContext(scorer::score, minChunkScore, beamWidth);
    }


    public static TrieRecallContext scoringNotUsed() {
        return new TrieRecallContext((q, r) -> 0.0, 0.0, 1);
    }

    public ChunkScorer getChunkScorer() {
        return chunkScorer;
    }

    public double getMinChunkScore() {
        return minChunkScore;
    }

    public int getBeamWidth() {
        return beamWidth;
    }
}
