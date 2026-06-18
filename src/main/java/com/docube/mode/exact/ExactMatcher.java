package com.docube.mode.exact;

import com.docube.model.SearchHit;
import com.docube.storage.RecordReader;

import java.io.IOException;
import java.util.Optional;

public final class ExactMatcher {

    public Optional<SearchHit> find(RecordReader reader, int lineCount, String query) throws IOException {
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("value must be non-empty");
        }
        for (int lineNo = 1; lineNo <= lineCount; lineNo++) {
            String record = reader.readLine(lineNo);
            if (record.equals(query)) {
                return Optional.of(new SearchHit(lineNo, record, 1.0));
            }
        }
        return Optional.empty();
    }
}
