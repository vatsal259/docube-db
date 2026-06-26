# docube-db

**docube-db** is a small Java library for **fast fuzzy search over lists of text codes** — invoice numbers, shipment IDs, serial numbers, part codes, anything that shares prefixes.

Keep a list of valid strings on disk. When someone types or scans a code that is *almost* right, docube finds the closest matches **without scanning the whole list every time**.

No server. No database install. Just files in a folder.

```
Your list:   INV-2024-IND-88492
User typed:  INV-2024-IND-8849Z
Result:      INV-2024-IND-88492
```

---

## Why I built this

Back in my second year of college (2021) I read about how Elasticsearch uses **inverted indexes**, and I was hooked.

Think of the **index at the back of a textbook**. Instead of flipping through every page to find "photosynthesis", you look it up in the index and it tells you the exact pages. An inverted index is the same idea for search: it keeps a list of *word → which documents contain it*. Type a word, jump straight to the matching documents — no reading everything.

But that back-of-book index only works because text is made of **words**. It splits on spaces and punctuation. It's perfect for "which docs mention *invoice*?" — but useless for "which code looks almost like `INV-2024-IND-8849Z`?" There are no words here, just one long code with a single typo.

So I kept wondering: what's the same shortcut, but for **whole codes that share a beginning**? That's docube-db. Same instinct — *don't read everything, keep an index that jumps you near the answer* — but the index is a **tree of code-pieces (a trie)** instead of a word list. Codes that start the same way sit on the same branch, so a search only walks that branch.

| | Inverted index (Elasticsearch) | docube-db |
|---|---|---|
| What it indexes | words in your text | small **pieces of the whole code** |
| The shortcut | word → documents that contain it | shared **prefix** → codes on that branch |
| Great for | "find documents containing *invoice*" | "find the code almost like this one" |

Both win the same way: never read the whole dataset. They just use a different shaped index. docube-db is small, file-based, and prefix-first. Hope you like it.

---

## The problem

A spreadsheet has 500,000 invoice numbers. A scanner misreads one character. You need the right row — fast, offline, on a laptop.

Brute force (compare to every line) is slow. Exact lookup fails on typos. docube sits in the middle: **structure to prune, fuzzy scoring to rank**.

---

## How it works in one picture

Codes become paths through a **trie of fixed-size chunks**:

```
INV-2024-IND-88492  →  [INV-][2024][-IND][-884][92]
```

Search = **walk the trie to prune** down to a few candidates, then **score the survivors** with Levenshtein and return the best.

A query starting with `SHP-` never looks at `INV-` rows.

---

## Layout

Single Maven module (`docube-db`, one jar). Each search mode lives in its own **sub-package** for isolation.

| Package | Contents |
|---------|----------|
| `com.docube` | Public API + wiring: `DocubeDB`, `DocubeFile`, `SearchStrategies` |
| `com.docube.model` | Value types: `SearchHit`, `SearchMode`, `SearchOptions`, `IngestOptions`, `IngestResult` |
| `com.docube.normalize` | Input cleanup: `Normalizer`, `DefaultNormalizer`, `IdentityNormalizer` |
| `com.docube.scoring` | Similarity: `StringScorer`, `ChunkScorer`, `SimilarityScorer`, `LevenshteinScorer` |
| `com.docube.chunk` | `Chunker` — splits a code into fixed-size chunks |
| `com.docube.trie` | `ChunkTrie`, `TrieNode`, `TrieWalkResult`, `TrieRecallStrategy`, `TrieRecallContext` |
| `com.docube.storage` | File persistence: `FileMeta`, `LineIndex`, `RecordReader` |
| `com.docube.mode.exact` | `EXACT` — literal full-string match |
| `com.docube.mode.prefixstrict` | `PREFIX_STRICT` — exact prefix recall |
| `com.docube.mode.treeuntilbreak` | `TREE_UNTIL_BREAK` — fuzzy peek at first mismatch |
| `com.docube.mode.beamwalk` | `BEAM_WALK` — keep top-N fuzzy paths every step |

---

## Quick start

```java
DocubeDB db = new DocubeDB("/data/codes");

// Create a collection (chunkSize = 4 chars) and load a file in one step
DocubeFile invoices = db.createFileFromRecords("invoices", 4, Path.of("invoices.txt"));

// Default fuzzy search
List<SearchHit> hits = invoices.search("INV-2024-IND-8849Z", 5, 0.85, SearchMode.TREE_UNTIL_BREAK);

SearchHit best = hits.get(0);
System.out.println(best.getText() + " @ " + best.getScore());
```

**File format:** UTF-8 text, one code per line. Empty lines skipped by default.

---

## Loading data (ingestion)

All paths normalize each value and update the trie automatically.

| Method | Best for |
|--------|----------|
| `createFileFromRecords(name, chunkSize, file)` | First-time load — create + ingest in one step |
| `ingest(file)` | Bulk append from an export (indexes saved once at the end) |
| `insert(code)` | Add one code (writes to disk immediately) |

```java
DocubeFile file = db.openFile("invoices");

IngestResult result = file.ingest(Path.of("new-invoices.txt"));
System.out.println(result.getInsertedCount() + " added");

// Skip duplicates (vs existing catalog and within the same file)
file.ingest(Path.of("batch.txt"), IngestOptions.defaults().withSkipDuplicates(true));

file.insert("INV-2024-IND-88500");
```

`IngestResult` reports `insertedCount`, `skippedEmptyCount`, `skippedDuplicateCount`.

---

## Search modes

All modes share one pipeline: **normalize → chunk → trie recall → Levenshtein rerank → filter & sort**. They differ only in the recall step.

| Mode | What it does | Use when |
|------|--------------|----------|
| `EXACT` | Literal match, no trie, no fuzzing. Score 1.0 or nothing. | Strict gate, validation |
| `PREFIX_STRICT` | Walk trie exactly; return everything under the matched prefix. No fuzzing. | Autocomplete, "starts with…" |
| `TREE_UNTIL_BREAK` | Walk exactly until a chunk mismatches, then peek fuzzy siblings once, then rank. **Default.** | Scanner/OCR typos, one mistake |
| `BEAM_WALK` | Keep several "maybe" paths open at every chunk, then rank. | Multiple typos, scrambled input |

```java
// Autocomplete-style prefix listing
invoices.search("INV-2024", 20, 0.0, SearchMode.PREFIX_STRICT);

// Harder noise — keep 4 candidate paths per step
invoices.search("ACBD-EFGH", 5, 0.7, SearchMode.BEAM_WALK,
        SearchOptions.defaults().withBeamWidth(4));

// Strict equality
invoices.search("INV-2024-IND-88492", 1, 0.0, SearchMode.EXACT);
```

> **Note:** `EXACT` compares the **raw** query. Every other mode and all stored values are **normalized** (trimmed, uppercased, whitespace stripped, dashes unified). For exact match, pass the normalized form.

---

## Tunables

| Parameter | Where | Default | Effect |
|-----------|-------|---------|--------|
| `chunkSize` | set at `createFile` | — | Trie granularity (chars per chunk) |
| `minChunkScore` | `SearchOptions` | `0.25` | How fuzzy a trie chunk match may be |
| `beamWidth` | `SearchOptions` | `3` | Paths kept per step (BEAM_WALK) |
| `minScore` | `search(...)` | caller | Drop full-string matches below this |
| `limit` | `search(...)` | caller | Max results |

---

## What you store

| Piece | What it is |
|-------|------------|
| DB root | A folder, e.g. `/data/codes` |
| Collection | One named list inside the root, e.g. `invoices` |
| Record | One normalized code per line |
| Indexes | `.data`, `.tree.idx`, `.offset.json`, `.meta.json` per collection (built automatically) |

---

## Build

```bash
mvn clean install
```

Requires JDK 21. Jackson is the only runtime dependency.

---

## Good fit / bad fit

**Works well when:**

- Strings share prefixes (region, year, product line, type code)
- Input is nearly correct — typos, not random text
- You need offline, explainable matching
- List size is thousands to low millions

**Not the right tool when:**

- You need full-text or semantic search
- Data is tens of millions+ (the trie is held in memory and serialized as one file)
- You need concurrent writers (single-writer assumption)
