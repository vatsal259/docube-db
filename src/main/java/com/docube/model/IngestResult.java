package com.docube.model;

public final class IngestResult {

    private final int insertedCount;
    private final int skippedEmptyCount;
    private final int skippedDuplicateCount;

    public IngestResult(int insertedCount, int skippedEmptyCount, int skippedDuplicateCount) {
        this.insertedCount = insertedCount;
        this.skippedEmptyCount = skippedEmptyCount;
        this.skippedDuplicateCount = skippedDuplicateCount;
    }

    public int getInsertedCount() {
        return insertedCount;
    }

    public int getSkippedEmptyCount() {
        return skippedEmptyCount;
    }

    public int getSkippedDuplicateCount() {
        return skippedDuplicateCount;
    }


    public int getTotalProcessed() {
        return insertedCount + skippedEmptyCount + skippedDuplicateCount;
    }

    @Override
    public String toString() {
        return "IngestResult{inserted=" + insertedCount
                + ", skippedEmpty=" + skippedEmptyCount
                + ", skippedDuplicate=" + skippedDuplicateCount + '}';
    }
}
