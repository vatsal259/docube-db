package com.docube;

import com.docube.mode.beamwalk.BeamWalkRecall;
import com.docube.mode.prefixstrict.PrefixStrictRecall;
import com.docube.mode.treeuntilbreak.TreeUntilBreakRecall;
import com.docube.model.SearchMode;
import com.docube.trie.TrieRecallStrategy;

import java.util.EnumMap;
import java.util.Map;

final class SearchStrategies {

    private static final Map<SearchMode, TrieRecallStrategy> BY_MODE = new EnumMap<>(SearchMode.class);

    static {
        BY_MODE.put(SearchMode.TREE_UNTIL_BREAK, new TreeUntilBreakRecall());
        BY_MODE.put(SearchMode.BEAM_WALK, new BeamWalkRecall());
        BY_MODE.put(SearchMode.PREFIX_STRICT, new PrefixStrictRecall());
    }

    private SearchStrategies() {
    }

    static TrieRecallStrategy forMode(SearchMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (mode == SearchMode.EXACT) {
            throw new IllegalArgumentException("EXACT mode does not use trie recall");
        }
        TrieRecallStrategy strategy = BY_MODE.get(mode);
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported recall mode: " + mode);
        }
        return strategy;
    }
}
