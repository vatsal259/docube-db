package com.docube.mode.treeuntilbreak;

import com.docube.scoring.ChunkScorer;
import com.docube.trie.ChunkTrie;
import com.docube.trie.TrieNode;
import com.docube.trie.TrieRecallContext;
import com.docube.trie.TrieRecallStrategy;
import com.docube.trie.TrieWalkResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class TreeUntilBreakRecall implements TrieRecallStrategy {

    @Override
    public Set<Integer> recall(ChunkTrie trie, List<String> queryChunks, TrieRecallContext context) {
        TrieWalkResult walk = trie.walkExact(queryChunks);
        Set<Integer> candidates = new LinkedHashSet<>();

        if (walk.isFullExactMatch()) {
            ChunkTrie.collectLineNos(walk.getNode(), candidates);
            return candidates;
        }

        int breakDepth = walk.getBreakDepth();
        String queryChunk = queryChunks.get(breakDepth);
        TrieNode breakNode = walk.getNode();
        ChunkScorer scorer = context.getChunkScorer();
        double minChunkScore = context.getMinChunkScore();

        for (Map.Entry<String, TrieNode> entry : breakNode.getChildren().entrySet()) {
            if (scorer.scoreChunks(queryChunk, entry.getKey()) > minChunkScore) {
                ChunkTrie.collectLineNos(entry.getValue(), candidates);
            }
        }

        return candidates;
    }
}
