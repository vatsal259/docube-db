package com.docube.trie;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrieNode {

    private final List<Integer> lineNos;
    private final Map<String, TrieNode> children;

    public TrieNode() {
        this.lineNos = new ArrayList<>();
        this.children = new LinkedHashMap<>();
    }

    @JsonCreator
    public TrieNode(
            @JsonProperty("lineNos") List<Integer> lineNos,
            @JsonProperty("children") Map<String, TrieNode> children
    ) {
        this.lineNos = lineNos == null ? new ArrayList<>() : new ArrayList<>(lineNos);
        this.children = children == null ? new LinkedHashMap<>() : new LinkedHashMap<>(children);
    }

    public List<Integer> getLineNos() {
        return lineNos;
    }

    public Map<String, TrieNode> getChildren() {
        return children;
    }
}
