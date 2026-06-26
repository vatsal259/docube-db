package com.docube.storage;

import java.io.IOException;

@FunctionalInterface
public interface RecordReader {
    String readLine(int lineNo) throws IOException;
}
