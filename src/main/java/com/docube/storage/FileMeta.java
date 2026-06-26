package com.docube.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class FileMeta {

    private final String name;
    private final int chunkSize;
    private int lineCount;
    private final String createdAt;

    @JsonCreator
    public FileMeta(
            @JsonProperty("name") String name,
            @JsonProperty("chunkSize") int chunkSize,
            @JsonProperty("lineCount") int lineCount,
            @JsonProperty("createdAt") String createdAt
    ) {
        this.name = name;
        this.chunkSize = chunkSize;
        this.lineCount = lineCount;
        this.createdAt = createdAt;
    }

    public static FileMeta create(String name, int chunkSize, String createdAt) {
        return new FileMeta(name, chunkSize, 0, createdAt);
    }

    public String getName() {
        return name;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getLineCount() {
        return lineCount;
    }

    public void setLineCount(int lineCount) {
        this.lineCount = lineCount;
    }

    public String getCreatedAt() {
        return createdAt;
    }
}
