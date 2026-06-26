package com.docube.model;

public final class IngestOptions {

    private final boolean skipEmptyLines;
    private final boolean skipDuplicates;

    public IngestOptions() {
        this(true, false);
    }

    public IngestOptions(boolean skipEmptyLines, boolean skipDuplicates) {
        this.skipEmptyLines = skipEmptyLines;
        this.skipDuplicates = skipDuplicates;
    }

    public static IngestOptions defaults() {
        return new IngestOptions();
    }

    public boolean isSkipEmptyLines() {
        return skipEmptyLines;
    }

    public boolean isSkipDuplicates() {
        return skipDuplicates;
    }

    public IngestOptions withSkipEmptyLines(boolean skipEmptyLines) {
        return new IngestOptions(skipEmptyLines, skipDuplicates);
    }

    public IngestOptions withSkipDuplicates(boolean skipDuplicates) {
        return new IngestOptions(skipEmptyLines, skipDuplicates);
    }
}
