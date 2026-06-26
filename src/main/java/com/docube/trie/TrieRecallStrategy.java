package com.docube.trie;

import java.util.List;
import java.util.Set;

public interface TrieRecallStrategy {

    Set<Integer> recall(ChunkTrie trie, List<String> queryChunks, TrieRecallContext context);
}
