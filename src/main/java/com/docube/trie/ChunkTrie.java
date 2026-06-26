package com.docube.trie;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ChunkTrie {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final TrieNode root;

    public ChunkTrie() {
        this.root = new TrieNode();
    }

    public ChunkTrie(TrieNode root) {
        this.root = root == null ? new TrieNode() : root;
    }

    public TrieNode getRoot() {
        return root;
    }

    public void insert(List<String> chunks, int lineNo) {
        TrieNode node = root;
        for (String chunk : chunks) {
            node = node.getChildren().computeIfAbsent(chunk, key -> new TrieNode());
        }
        node.getLineNos().add(lineNo);
    }

    public static ChunkTrie load(Path path) throws IOException {
        if (!Files.exists(path)) {
            return new ChunkTrie();
        }
        TrieNode root = MAPPER.readValue(path.toFile(), TrieNode.class);
        return new ChunkTrie(root);
    }

    public void save(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        MAPPER.writeValue(path.toFile(), root);
    }

    public static void collectLineNos(TrieNode node, Set<Integer> out) {
        out.addAll(node.getLineNos());
        for (TrieNode child : node.getChildren().values()) {
            collectLineNos(child, out);
        }
    }

    public int countIndexedLines() {
        Set<Integer> lineNos = new LinkedHashSet<>();
        collectLineNos(root, lineNos);
        return lineNos.size();
    }

    public TrieWalkResult walkExact(List<String> queryChunks) {
        TrieNode node = root;
        for (int depth = 0; depth < queryChunks.size(); depth++) {
            TrieNode child = node.getChildren().get(queryChunks.get(depth));
            if (child == null) {
                return new TrieWalkResult(node, depth, depth);
            }
            node = child;
        }
        return new TrieWalkResult(node, -1, queryChunks.size());
    }
}
