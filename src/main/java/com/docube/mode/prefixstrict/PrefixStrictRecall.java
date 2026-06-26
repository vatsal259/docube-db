package com.docube.mode.prefixstrict;

import com.docube.trie.ChunkTrie;
import com.docube.trie.TrieRecallContext;
import com.docube.trie.TrieRecallStrategy;
import com.docube.trie.TrieWalkResult;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PrefixStrictRecall implements TrieRecallStrategy {

    @Override
    public Set<Integer> recall(ChunkTrie trie, List<String> queryChunks, TrieRecallContext context) {
        TrieWalkResult walk = trie.walkExact(queryChunks);
        if (!walk.isFullExactMatch() && walk.getBreakDepth() == 0) {
            return Set.of();
        }
        Set<Integer> candidates = new LinkedHashSet<>();
        ChunkTrie.collectLineNos(walk.getNode(), candidates);
        return candidates;
    }
}
