package com.docube.trie;

public final class TrieWalkResult {

    private final TrieNode node;
    private final int breakDepth;
    private final int walkedDepth;

    public TrieWalkResult(TrieNode node, int breakDepth, int walkedDepth) {
        this.node = node;
        this.breakDepth = breakDepth;
        this.walkedDepth = walkedDepth;
    }

    public TrieNode getNode() {
        return node;
    }

    public int getBreakDepth() {
        return breakDepth;
    }

    public int getWalkedDepth() {
        return walkedDepth;
    }

    public boolean isFullExactMatch() {
        return breakDepth < 0;
    }
}
