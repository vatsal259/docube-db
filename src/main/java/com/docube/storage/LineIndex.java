package com.docube.storage;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class LineIndex {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Long> offsets = new ArrayList<>();

    public void addLineStart(long byteOffset) {
        offsets.add(byteOffset);
    }

    public int size() {
        return offsets.size();
    }

    long getLineStart(int lineNo) {
        if (lineNo < 1 || lineNo > offsets.size()) {
            throw new IllegalArgumentException("Invalid line number: " + lineNo);
        }
        return offsets.get(lineNo - 1);
    }

    public void save(Path offsetPath) throws IOException {
        if (offsetPath.getParent() != null) {
            Files.createDirectories(offsetPath.getParent());
        }
        MAPPER.writeValue(offsetPath.toFile(), offsets);
    }

    public static LineIndex loadOrRebuild(Path offsetPath, Path dataPath, int expectedLineCount) throws IOException {
        if (Files.exists(offsetPath)) {
            LineIndex index = new LineIndex();
            long[] loaded = MAPPER.readValue(offsetPath.toFile(), long[].class);
            for (long offset : loaded) {
                index.offsets.add(offset);
            }
            if (index.size() == expectedLineCount) {
                return index;
            }
        }
        return rebuild(dataPath);
    }

    public static LineIndex rebuild(Path dataPath) throws IOException {
        LineIndex index = new LineIndex();
        if (!Files.exists(dataPath) || Files.size(dataPath) == 0) {
            return index;
        }

        byte[] bytes = Files.readAllBytes(dataPath);
        index.offsets.add(0L);
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] == '\n' && i + 1 < bytes.length) {
                index.offsets.add((long) i + 1);
            }
        }
        return index;
    }

    public String readLine(Path dataPath, int lineNo) throws IOException {
        long start = getLineStart(lineNo);
        try (InputStream in = Files.newInputStream(dataPath)) {
            long skipped = in.skip(start);
            if (skipped != start) {
                throw new IllegalStateException("Could not seek to line " + lineNo);
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    break;
                }
                if (b != '\r') {
                    buffer.write(b);
                }
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    public long appendLine(Path dataPath, String line) throws IOException {
        long offset = Files.exists(dataPath) ? Files.size(dataPath) : 0L;
        Files.writeString(
                dataPath,
                line + '\n',
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
        );
        return offset;
    }
}
