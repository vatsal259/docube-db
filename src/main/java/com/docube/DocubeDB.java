package com.docube;

import com.docube.model.IngestOptions;
import com.docube.normalize.DefaultNormalizer;
import com.docube.normalize.Normalizer;
import com.docube.scoring.LevenshteinScorer;
import com.docube.scoring.StringScorer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class DocubeDB {

    private final Path dbRoot;
    private final Normalizer normalizer;
    private final StringScorer stringScorer;

    public DocubeDB(String dbRootPath) {
        this(Path.of(dbRootPath));
    }

    public DocubeDB(Path dbRoot) {
        this(dbRoot, DefaultNormalizer.INSTANCE, new LevenshteinScorer());
    }

    public DocubeDB(Path dbRoot, Normalizer normalizer, StringScorer stringScorer) {
        this.dbRoot = dbRoot;
        this.normalizer = normalizer == null ? DefaultNormalizer.INSTANCE : normalizer;
        this.stringScorer = stringScorer == null ? new LevenshteinScorer() : stringScorer;
    }

    public DocubeFile createFile(String name, int chunkSize) throws IOException {
        Path metaPath = dbRoot.resolve(name + ".meta.json");
        if (Files.exists(metaPath)) {
            throw new IllegalArgumentException("File already exists: " + name);
        }
        return DocubeFile.create(dbRoot, name, chunkSize, normalizer, stringScorer);
    }


    public DocubeFile createFileFromRecords(String name, int chunkSize, Path recordFile) throws IOException {
        return createFileFromRecords(name, chunkSize, recordFile, IngestOptions.defaults());
    }


    public DocubeFile createFileFromRecords(
            String name,
            int chunkSize,
            Path recordFile,
            IngestOptions options
    ) throws IOException {
        DocubeFile file = createFile(name, chunkSize);
        file.ingest(recordFile, options);
        return file;
    }

    public DocubeFile openFile(String name) throws IOException {
        return DocubeFile.open(dbRoot, name, normalizer, stringScorer);
    }

    public List<String> listFiles() throws IOException {
        if (!Files.exists(dbRoot)) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dbRoot)) {
            stream.filter(path -> path.getFileName().toString().endsWith(".meta.json"))
                    .forEach(path -> {
                        String fileName = path.getFileName().toString();
                        names.add(fileName.substring(0, fileName.length() - ".meta.json".length()));
                    });
        }
        names.sort(String::compareTo);
        return names;
    }

    public Path getDbRoot() {
        return dbRoot;
    }
}
