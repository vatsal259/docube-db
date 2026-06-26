package com.docube.mode.beamwalk;

import com.docube.scoring.ChunkScorer;
import com.docube.trie.ChunkTrie;
import com.docube.trie.TrieNode;
import com.docube.trie.TrieRecallContext;
import com.docube.trie.TrieRecallStrategy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BeamWalkRecall implements TrieRecallStrategy {

    private record BeamEntry(TrieNode node, double pathScore) {
    }

    @Override
    public Set<Integer> recall(ChunkTrie trie, List<String> queryChunks, TrieRecallContext context) {
        ChunkScorer scorer = context.getChunkScorer();
        double minChunkScore = context.getMinChunkScore();
        int beamWidth = context.getBeamWidth();

        List<BeamEntry> beam = List.of(new BeamEntry(trie.getRoot(), 0.0));

        for (int depth = 0; depth < queryChunks.size(); depth++) {
            String queryChunk = queryChunks.get(depth);
            List<BeamEntry> nextBeam = new ArrayList<>();

            for (BeamEntry entry : beam) {
                Map<String, TrieNode> children = entry.node.getChildren();
                if (children.isEmpty()) {
                    nextBeam.add(entry);
                    continue;
                }
                for (Map.Entry<String, TrieNode> child : children.entrySet()) {
                    double edgeScore = scorer.scoreChunks(queryChunk, child.getKey());
                    if (edgeScore > minChunkScore) {
                        nextBeam.add(new BeamEntry(child.getValue(), entry.pathScore() + edgeScore));
                    }
                }
            }

            if (nextBeam.isEmpty()) {
                break;
            }

            nextBeam.sort(Comparator.comparingDouble(BeamEntry::pathScore).reversed());
            if (nextBeam.size() > beamWidth) {
                nextBeam = new ArrayList<>(nextBeam.subList(0, beamWidth));
            }
            beam = nextBeam;
        }

        Set<Integer> candidates = new LinkedHashSet<>();
        for (BeamEntry entry : beam) {
            ChunkTrie.collectLineNos(entry.node(), candidates);
        }
        return candidates;
    }
}
