package com.docube;

import com.docube.chunk.Chunker;
import com.docube.mode.exact.ExactMatcher;
import com.docube.model.IngestOptions;
import com.docube.model.IngestResult;
import com.docube.model.SearchHit;
import com.docube.model.SearchMode;
import com.docube.model.SearchOptions;
import com.docube.normalize.DefaultNormalizer;
import com.docube.normalize.Normalizer;
import com.docube.scoring.LevenshteinScorer;
import com.docube.scoring.StringScorer;
import com.docube.storage.FileMeta;
import com.docube.storage.LineIndex;
import com.docube.trie.ChunkTrie;
import com.docube.trie.TrieRecallContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class DocubeFile {

    private static final ObjectMapper META_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final FileMeta meta;
    private final Path dataPath;
    private final Path metaPath;
    private final Path treePath;
    private final Path offsetPath;
    private final ChunkTrie trie;
    private final LineIndex lineIndex;
    private final Normalizer normalizer;
    private final StringScorer stringScorer;

    DocubeFile(
            Path dbRoot,
            FileMeta meta,
            ChunkTrie trie,
            LineIndex lineIndex,
            Normalizer normalizer,
            StringScorer stringScorer
    ) {
        this.meta = meta;
        this.dataPath = dbRoot.resolve(meta.getName() + ".data");
        this.metaPath = dbRoot.resolve(meta.getName() + ".meta.json");
        this.treePath = dbRoot.resolve(meta.getName() + ".tree.idx");
        this.offsetPath = dbRoot.resolve(meta.getName() + ".offset.json");
        this.trie = trie;
        this.lineIndex = lineIndex;
        this.normalizer = normalizer == null ? DefaultNormalizer.INSTANCE : normalizer;
        this.stringScorer = stringScorer == null ? new LevenshteinScorer() : stringScorer;
    }

    static DocubeFile create(
            Path dbRoot,
            String name,
            int chunkSize,
            Normalizer normalizer,
            StringScorer stringScorer
    ) throws IOException {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        validateName(name);

        Files.createDirectories(dbRoot);
        FileMeta meta = FileMeta.create(name, chunkSize, Instant.now().toString());
        DocubeFile file = new DocubeFile(
                dbRoot,
                meta,
                new ChunkTrie(),
                new LineIndex(),
                normalizer,
                stringScorer
        );

        if (!Files.exists(file.dataPath)) {
            Files.createFile(file.dataPath);
        }
        file.saveMeta();
        file.trie.save(file.treePath);
        file.lineIndex.save(file.offsetPath);
        return file;
    }

    static DocubeFile open(
            Path dbRoot,
            String name,
            Normalizer normalizer,
            StringScorer stringScorer
    ) throws IOException {
        validateName(name);
        Path metaPath = dbRoot.resolve(name + ".meta.json");
        if (!Files.exists(metaPath)) {
            throw new IllegalArgumentException("File not found: " + name);
        }
        FileMeta meta = META_MAPPER.readValue(metaPath.toFile(), FileMeta.class);
        if (!meta.getName().equals(name)) {
            throw new IllegalStateException("Metadata name mismatch for collection: " + name);
        }

        Path dataPath = dbRoot.resolve(name + ".data");
        Path treePath = dbRoot.resolve(name + ".tree.idx");
        Path offsetPath = dbRoot.resolve(name + ".offset.json");

        ChunkTrie trie = ChunkTrie.load(treePath);
        if (meta.getLineCount() > 0 && trie.countIndexedLines() != meta.getLineCount()) {
            trie = rebuildTrieFromData(dataPath, meta);
            trie.save(treePath);
        }

        LineIndex lineIndex = LineIndex.loadOrRebuild(offsetPath, dataPath, meta.getLineCount());
        if (!Files.exists(offsetPath) || lineIndex.size() != meta.getLineCount()) {
            lineIndex.save(offsetPath);
        }

        return new DocubeFile(dbRoot, meta, trie, lineIndex, normalizer, stringScorer);
    }

    public FileMeta getMeta() {
        return meta;
    }


    public int insert(String record) throws IOException {
        int lineNo = appendRecord(requireNormalized(record));
        persist();
        return lineNo;
    }


    public Optional<SearchHit> findExact(String query) throws IOException {
        return new ExactMatcher().find(this::readLine, meta.getLineCount(), query);
    }


    public IngestResult ingest(Path recordFile) throws IOException {
        return ingest(recordFile, IngestOptions.defaults());
    }


    public IngestResult ingest(Path recordFile, IngestOptions options) throws IOException {
        if (recordFile == null) {
            throw new IllegalArgumentException("recordFile must not be null");
        }
        if (!Files.isRegularFile(recordFile)) {
            throw new IllegalArgumentException("record file not found: " + recordFile);
        }
        IngestOptions opts = options == null ? IngestOptions.defaults() : options;

        Set<String> seen = opts.isSkipDuplicates() ? loadExistingRecords() : Set.of();
        int inserted = 0;
        int skippedEmpty = 0;
        int skippedDuplicate = 0;

        try (BufferedReader reader = Files.newBufferedReader(recordFile, StandardCharsets.UTF_8)) {
            String line;
            int sourceLineNo = 0;
            while ((line = reader.readLine()) != null) {
                sourceLineNo++;
                if (line.isEmpty()) {
                    if (opts.isSkipEmptyLines()) {
                        skippedEmpty++;
                        continue;
                    }
                    throw new IllegalArgumentException(
                            "Empty line at " + recordFile + ":" + sourceLineNo
                    );
                }

                String normalized = normalizeRecord(line, recordFile, sourceLineNo);
                if (opts.isSkipDuplicates()) {
                    if (seen.contains(normalized)) {
                        skippedDuplicate++;
                        continue;
                    }
                    seen.add(normalized);
                }

                appendRecord(normalized);
                inserted++;
            }
        }

        if (inserted > 0) {
            persist();
        }

        return new IngestResult(inserted, skippedEmpty, skippedDuplicate);
    }

    public List<SearchHit> search(String query) throws IOException {
        return search(query, Integer.MAX_VALUE, 0.0, SearchMode.TREE_UNTIL_BREAK);
    }

    public List<SearchHit> search(String query, int limit, double minScore, SearchMode mode) throws IOException {
        return search(query, limit, minScore, mode, SearchOptions.defaults());
    }


    public String prepareSearchQuery(String query, SearchMode mode) {
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("query must be non-empty");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (mode == SearchMode.EXACT) {
            return query;
        }
        return requireNormalized(query);
    }

    public List<SearchHit> search(
            String query,
            int limit,
            double minScore,
            SearchMode mode,
            SearchOptions options
    ) throws IOException {
        validateSearchParams(query, limit, minScore, mode);

        SearchOptions opts = options == null ? SearchOptions.defaults() : options;

        if (mode == SearchMode.EXACT) {
            List<SearchHit> hits = new ArrayList<>();
            findExact(query).ifPresent(hits::add);
            return hits;
        }

        String normalizedQuery = prepareSearchQuery(query, mode);
        Set<Integer> candidateLineNos = recallCandidates(normalizedQuery, mode, opts);
        List<SearchHit> hits = rerankCandidates(normalizedQuery, candidateLineNos, minScore);

        hits.sort(Comparator.comparingDouble(SearchHit::getScore).reversed()
                .thenComparingInt(SearchHit::getLineNo));

        if (hits.size() > limit) {
            return new ArrayList<>(hits.subList(0, limit));
        }
        return hits;
    }


    String readLine(int lineNo) throws IOException {
        return lineIndex.readLine(dataPath, lineNo);
    }

    private int appendRecord(String normalized) throws IOException {
        int lineNo = meta.getLineCount() + 1;
        long offset = lineIndex.appendLine(dataPath, normalized);
        lineIndex.addLineStart(offset);

        List<String> chunks = Chunker.chunkText(normalized, meta.getChunkSize());
        trie.insert(chunks, lineNo);
        meta.setLineCount(lineNo);
        return lineNo;
    }

    private void persist() throws IOException {
        saveMeta();
        trie.save(treePath);
        lineIndex.save(offsetPath);
    }

    private Set<String> loadExistingRecords() throws IOException {
        Set<String> existing = new HashSet<>();
        for (int lineNo = 1; lineNo <= meta.getLineCount(); lineNo++) {
            existing.add(readLine(lineNo));
        }
        return existing;
    }

    private String normalizeRecord(String raw, Path sourceFile, int sourceLineNo) {
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException("value must be non-empty");
        }
        String normalized = normalizer.normalize(raw);
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    "Line normalizes to empty at " + sourceFile + ":" + sourceLineNo
            );
        }
        return normalized;
    }

    private static ChunkTrie rebuildTrieFromData(Path dataPath, FileMeta meta) throws IOException {
        ChunkTrie trie = new ChunkTrie();
        if (!Files.exists(dataPath)) {
            return trie;
        }

        try (BufferedReader reader = Files.newBufferedReader(dataPath, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    throw new IllegalStateException(
                            "Empty line at position " + (lineNo + 1) + " in " + dataPath
                    );
                }
                lineNo++;
                List<String> chunks = Chunker.chunkText(line, meta.getChunkSize());
                trie.insert(chunks, lineNo);
            }
            if (lineNo != meta.getLineCount()) {
                throw new IllegalStateException(
                        "Line count mismatch: meta=" + meta.getLineCount() + ", data=" + lineNo
                );
            }
        }
        return trie;
    }

    private String requireNormalized(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("value must be non-empty");
        }
        String normalized = normalizer.normalize(value);
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("value must be non-empty after normalization");
        }
        return normalized;
    }

    private Set<Integer> recallCandidates(String normalizedQuery, SearchMode recallMode, SearchOptions options) {
        List<String> queryChunks = Chunker.chunkText(normalizedQuery, meta.getChunkSize());
        TrieRecallContext context = TrieRecallContext.from(
                stringScorer, options.getMinChunkScore(), options.getBeamWidth());
        return SearchStrategies.forMode(recallMode).recall(trie, queryChunks, context);
    }

    private List<SearchHit> rerankCandidates(String normalizedQuery, Set<Integer> candidateLineNos, double minScore)
            throws IOException {
        List<SearchHit> hits = new ArrayList<>();
        for (int lineNo : candidateLineNos) {
            String record = readLine(lineNo);
            double score = stringScorer.score(normalizedQuery, record);
            if (score >= minScore) {
                hits.add(new SearchHit(lineNo, record, score));
            }
        }
        return hits;
    }

    private static void validateSearchParams(String query, int limit, double minScore, SearchMode mode) {
        if (query == null || query.isEmpty()) {
            throw new IllegalArgumentException("query must be non-empty");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        validateThreshold(minScore, "minScore");
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
    }

    private static void validateThreshold(double value, String name) {
        if (value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0.0 and 1.0");
        }
    }

    private void saveMeta() throws IOException {
        META_MAPPER.writeValue(metaPath.toFile(), meta);
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must be non-empty");
        }
        if (name.contains("/") || name.contains("\\") || name.contains("..")) {
            throw new IllegalArgumentException("invalid file name: " + name);
        }
    }
}
