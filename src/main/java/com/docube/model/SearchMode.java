package com.docube.model;

public enum SearchMode {

    TREE_UNTIL_BREAK,

    BEAM_WALK,

    PREFIX_STRICT,

    EXACT;

    public boolean usesTrieRecall() {
        return this != EXACT;
    }
}
