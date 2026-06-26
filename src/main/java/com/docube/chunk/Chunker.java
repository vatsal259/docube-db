package com.docube.chunk;

import java.util.ArrayList;
import java.util.List;

public final class Chunker {

    private Chunker() {
    }

    public static List<String> chunkText(String text, int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text must be non-empty");
        }
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            chunks.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return chunks;
    }
}
