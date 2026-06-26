/* docube-db live demo
 * Faithful JS port of the recall + rerank pipeline:
 *   normalize -> chunk -> trie recall -> Levenshtein rerank -> filter & sort
 * Mirrors: DefaultNormalizer, Chunker, ChunkTrie, the four recall modes,
 * and LevenshteinScorer from the Java source.
 */

const MIN_CHUNK_SCORE = 0.25; // SearchOptions default

/* ---------- DefaultNormalizer ---------- */
function normalize(input) {
  if (!input) return input || "";
  let s = input.trim().toUpperCase().replace(/\s+/g, "");
  return s.replace(/[\u2013\u2014\u2212]/g, "-");
}

/* ---------- Chunker ---------- */
function chunkText(text, chunkSize) {
  const chunks = [];
  for (let i = 0; i < text.length; i += chunkSize) {
    chunks.push(text.slice(i, i + chunkSize));
  }
  return chunks;
}

/* ---------- LevenshteinScorer ---------- */
function levenshtein(a, b) {
  let prev = new Array(b.length + 1);
  let curr = new Array(b.length + 1);
  for (let j = 0; j <= b.length; j++) prev[j] = j;
  for (let i = 1; i <= a.length; i++) {
    curr[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const cost = a[i - 1] === b[j - 1] ? 0 : 1;
      curr[j] = Math.min(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost);
    }
    [prev, curr] = [curr, prev];
  }
  return prev[b.length];
}
function similarity(a, b) {
  if (a === b) return 1.0;
  const maxLen = Math.max(a.length, b.length);
  if (maxLen === 0) return 1.0;
  return 1.0 - levenshtein(a, b) / maxLen;
}

/* ---------- ChunkTrie ---------- */
function newNode() {
  return { children: new Map(), lineNos: [] };
}
function buildTrie(records, chunkSize) {
  const root = newNode();
  records.forEach((rec, lineNo) => {
    if (!rec) return;
    const chunks = chunkText(rec, chunkSize);
    let node = root;
    for (const c of chunks) {
      if (!node.children.has(c)) node.children.set(c, newNode());
      node = node.children.get(c);
    }
    node.lineNos.push(lineNo);
  });
  return root;
}
function collectLineNos(node, set) {
  for (const n of node.lineNos) set.add(n);
  for (const child of node.children.values()) collectLineNos(child, set);
}
function walkExact(root, queryChunks) {
  let node = root;
  for (let depth = 0; depth < queryChunks.length; depth++) {
    const c = queryChunks[depth];
    if (node.children.has(c)) {
      node = node.children.get(c);
    } else {
      return { node, breakDepth: depth, full: false };
    }
  }
  return { node, breakDepth: queryChunks.length, full: true };
}

/* ---------- Recall strategies ---------- */
function recallTreeUntilBreak(root, queryChunks) {
  const walk = walkExact(root, queryChunks);
  const set = new Set();
  if (walk.full) {
    collectLineNos(walk.node, set);
    return set;
  }
  const queryChunk = queryChunks[walk.breakDepth];
  for (const [edge, child] of walk.node.children) {
    if (similarity(queryChunk, edge) > MIN_CHUNK_SCORE) collectLineNos(child, set);
  }
  return set;
}
function recallPrefixStrict(root, queryChunks) {
  const walk = walkExact(root, queryChunks);
  const set = new Set();
  if (walk.full) collectLineNos(walk.node, set);
  return set;
}
function recallBeamWalk(root, queryChunks, beamWidth) {
  let beam = [{ node: root, score: 0 }];
  for (let depth = 0; depth < queryChunks.length; depth++) {
    const queryChunk = queryChunks[depth];
    let next = [];
    for (const entry of beam) {
      if (entry.node.children.size === 0) {
        next.push(entry);
        continue;
      }
      for (const [edge, child] of entry.node.children) {
        const edgeScore = similarity(queryChunk, edge);
        if (edgeScore > MIN_CHUNK_SCORE) next.push({ node: child, score: entry.score + edgeScore });
      }
    }
    if (next.length === 0) break;
    next.sort((a, b) => b.score - a.score);
    if (next.length > beamWidth) next = next.slice(0, beamWidth);
    beam = next;
  }
  const set = new Set();
  for (const entry of beam) collectLineNos(entry.node, set);
  return set;
}

/* ---------- Search pipeline ---------- */
function search(records, root, rawQuery, opts) {
  const { mode, chunkSize, minScore, limit, beamWidth } = opts;

  if (mode === "EXACT") {
    const q = normalize(rawQuery);
    const hits = [];
    records.forEach((rec, i) => {
      if (rec === q) hits.push({ lineNo: i, text: rec, score: 1.0 });
    });
    return hits.slice(0, limit);
  }

  const nq = normalize(rawQuery);
  if (!nq) return [];
  const queryChunks = chunkText(nq, chunkSize);

  let candidates;
  if (mode === "PREFIX_STRICT") candidates = recallPrefixStrict(root, queryChunks);
  else if (mode === "BEAM_WALK") candidates = recallBeamWalk(root, queryChunks, beamWidth);
  else candidates = recallTreeUntilBreak(root, queryChunks);

  const scored = [];
  for (const lineNo of candidates) {
    const rec = records[lineNo];
    const sc = mode === "PREFIX_STRICT" ? Math.max(similarity(nq, rec), 0.0001) : similarity(nq, rec);
    if (sc >= minScore) scored.push({ lineNo, text: rec, score: sc });
  }
  // Match DocubeFile: score desc, then lineNo asc as tie-break.
  scored.sort((a, b) => b.score - a.score || a.lineNo - b.lineNo);
  return scored.slice(0, limit);
}

/* ---------- Dataset ---------- */
const RAW_DATASET = [
  "INV-2024-IND-88490", "INV-2024-IND-88491", "INV-2024-IND-88492",
  "INV-2024-IND-88493", "INV-2024-IND-88500", "INV-2024-IND-90017",
  "INV-2024-US-10233", "INV-2024-US-10234", "INV-2024-EU-44120",
  "INV-2023-IND-77310", "INV-2023-IND-77311", "INV-2023-US-55012",
  "SHP-EU-2024-00730", "SHP-EU-2024-00731", "SHP-EU-2024-00732",
  "SHP-US-2024-11920", "SHP-US-2024-11921", "SHP-IN-2024-00045",
  "SHP-IN-2023-99812", "SHP-AP-2024-30021",
  "SRL-AC-99B-1004", "SRL-AC-99B-1005", "SRL-AC-99B-1006",
  "SRL-AC-77A-2210", "SRL-DC-12X-0098", "SRL-DC-12X-0099",
  "PRT-MTR-4490-A", "PRT-MTR-4490-B", "PRT-MTR-4491-A",
  "PRT-GBX-1120-C", "PRT-GBX-1121-C", "PRT-BRG-5500-Z",
];
const RECORDS = RAW_DATASET.map(normalize);

/* ---------- Per-mode plain-language explainers + diagrams ---------- */
function pillOn(x, y, w, label) {
  return `<rect x="${x}" y="${y}" width="${w}" height="30" rx="8" class="dg-node--on"/>` +
    `<text x="${x + w / 2}" y="${y + 19}" text-anchor="middle" class="dg-label--on">${label}</text>`;
}
function pillOff(x, y, w, label) {
  return `<rect x="${x}" y="${y}" width="${w}" height="30" rx="8" class="dg-node--off"/>` +
    `<text x="${x + w / 2}" y="${y + 19}" text-anchor="middle" class="dg-label--off">${label}</text>`;
}

const svgExact = `<svg viewBox="0 0 360 180" role="img" aria-label="Exact match diagram">
  <text x="180" y="20" text-anchor="middle" class="dg-cap">What you type</text>
  ${pillOn(70, 30, 220, "INV-2024-IND-88492")}
  <text x="180" y="86" text-anchor="middle" class="dg-label--on" style="font-size:20px">=</text>
  <text x="180" y="106" text-anchor="middle" class="dg-cap">every character must match</text>
  ${pillOn(60, 118, 210, "INV-2024-IND-88492")}
  <circle cx="300" cy="133" r="13" fill="#0066cc"/>
  <path d="M293 133 l5 5 l9 -10" stroke="#fff" stroke-width="2.5" fill="none" stroke-linecap="round"/>
</svg>`;

const svgPrefix = `<svg viewBox="0 0 360 180" role="img" aria-label="Prefix match diagram">
  <text x="180" y="20" text-anchor="middle" class="dg-cap">Jump to the matching start…</text>
  ${pillOn(125, 30, 110, "INV-2024")}
  <path class="dg-edge--on" d="M155 60 C120 85, 70 95, 55 118"/>
  <path class="dg-edge--on" d="M180 60 C170 85, 150 95, 140 118"/>
  <path class="dg-edge--on" d="M205 60 C215 85, 240 95, 250 118"/>
  ${pillOn(20, 118, 72, "88490")}
  ${pillOn(105, 118, 72, "88491")}
  ${pillOn(215, 118, 72, "88492")}
  <text x="180" y="170" text-anchor="middle" class="dg-cap">…return every code under it</text>
</svg>`;

const svgTree = `<svg viewBox="0 0 360 180" role="img" aria-label="Tree until break diagram">
  <text x="180" y="20" text-anchor="middle" class="dg-cap">Follow the matching path…</text>
  ${pillOn(140, 28, 80, "INV-")}
  <path class="dg-edge--off" d="M180 58 L180 86"/>
  <line x1="172" y1="74" x2="188" y2="90" class="dg-x"/>
  <line x1="188" y1="74" x2="172" y2="90" class="dg-x"/>
  <text x="232" y="84" class="dg-cap">2025? wrong turn</text>
  <path class="dg-edge--peek" d="M150 58 C110 78, 80 84, 70 100"/>
  <path class="dg-edge--peek" d="M210 58 C250 78, 280 84, 290 100"/>
  ${pillOn(34, 100, 72, "2024")}
  ${pillOn(254, 100, 72, "2023")}
  <text x="180" y="170" text-anchor="middle" class="dg-cap">…peek at the closest neighbors once</text>
</svg>`;

const svgBeam = `<svg viewBox="0 0 360 180" role="img" aria-label="Beam walk diagram">
  <text x="180" y="20" text-anchor="middle" class="dg-cap">Keep a few best guesses…</text>
  ${pillOn(150, 28, 60, "start")}
  <path class="dg-edge--on" d="M165 58 C110 75, 70 82, 55 96"/>
  <path class="dg-edge--on" d="M178 58 C160 76, 140 84, 132 96"/>
  <path class="dg-edge--on" d="M195 58 C215 76, 235 84, 245 96"/>
  <path class="dg-edge--off" d="M205 58 C260 75, 300 82, 312 96"/>
  ${pillOn(22, 96, 66, "path 1")}
  ${pillOn(100, 96, 66, "path 2")}
  ${pillOn(214, 96, 66, "path 3")}
  ${pillOff(292, 96, 56, "drop")}
  <path class="dg-edge--on" d="M55 126 L55 142"/>
  <path class="dg-edge--on" d="M133 126 L133 142"/>
  <path class="dg-edge--on" d="M247 126 L247 142"/>
  <text x="180" y="170" text-anchor="middle" class="dg-cap">…only the top few survive each step</text>
</svg>`;

const MODE_INFO = {
  EXACT: {
    tag: "EXACT",
    title: "Perfect match only",
    desc: "docube checks for one code that matches your text exactly — no guessing, no fuzzy neighbours. You get a single hit or nothing.",
    analogy: "like a password check — it's either exactly right, or it's rejected.",
    viz: svgExact,
  },
  PREFIX_STRICT: {
    tag: "PREFIX_STRICT",
    title: "Everything that starts the same",
    desc: "docube walks straight to the branch that matches the beginning of your code, then returns every code sitting under it. No typo tolerance.",
    analogy: "like typing the first letters of a contact's name and seeing everyone that starts that way.",
    viz: svgPrefix,
  },
  TREE_UNTIL_BREAK: {
    tag: "TREE_UNTIL_BREAK · default",
    title: "Follow, then peek when it breaks",
    desc: "docube follows the path that matches your code piece by piece. The moment a piece is wrong, it peeks at the closest neighbouring branches once, then scores those candidates.",
    analogy: "like driving a familiar route, and only when you hit a wrong turn do you glance at the streets next door.",
    viz: svgTree,
  },
  BEAM_WALK: {
    tag: "BEAM_WALK",
    title: "Keep a few best guesses alive",
    desc: "docube explores several promising paths at the same time, keeping only the top few at every step. It can recover even when there are multiple typos.",
    analogy: "like solving a maze by sending a few scouts down the most promising paths at once.",
    viz: svgBeam,
  },
};

/* ---------- UI wiring ---------- */
const LIMIT = 8;
const BEAM_WIDTH = 3;

const els = {
  q: document.getElementById("q"),
  mode: document.getElementById("mode"),
  chunk: document.getElementById("chunk"),
  minScore: document.getElementById("minScore"),
  results: document.getElementById("results"),
  meta: document.getElementById("meta"),
  datasetDump: document.getElementById("datasetDump"),
  datasetCount: document.getElementById("datasetCount"),
  explainerViz: document.getElementById("explainerViz"),
  explainerTag: document.getElementById("explainerTag"),
  explainerTitle: document.getElementById("explainerTitle"),
  explainerDesc: document.getElementById("explainerDesc"),
  explainerAnalogy: document.getElementById("explainerAnalogy"),
};

let trie = buildTrie(RECORDS, 4);
let lastChunkSize = 4;

function highlight(query, text) {
  // char-level diff highlight vs normalized query
  const nq = normalize(query);
  let out = "";
  const len = Math.max(nq.length, text.length);
  for (let i = 0; i < text.length; i++) {
    const same = i < nq.length && nq[i] === text[i];
    out += same ? text[i] : `<mark>${text[i]}</mark>`;
  }
  return out;
}

function run() {
  const mode = els.mode.value;
  const chunkSize = clamp(parseInt(els.chunk.value, 10) || 4, 1, 8);
  const minScore = clamp(parseFloat(els.minScore.value) || 0, 0, 1);
  const limit = LIMIT;
  const beamWidth = BEAM_WIDTH;

  if (chunkSize !== lastChunkSize) {
    trie = buildTrie(RECORDS, chunkSize);
    lastChunkSize = chunkSize;
  }

  const t0 = performance.now();
  const hits = search(RECORDS, trie, els.q.value, { mode, chunkSize, minScore, limit, beamWidth });
  const ms = (performance.now() - t0).toFixed(2);

  render(hits, ms, mode);
  updateExplainer(mode);
}

function updateExplainer(mode) {
  const info = MODE_INFO[mode];
  if (!info) return;
  els.explainerViz.innerHTML = info.viz;
  els.explainerTag.textContent = info.tag;
  els.explainerTitle.textContent = info.title;
  els.explainerDesc.textContent = info.desc;
  els.explainerAnalogy.textContent = info.analogy;
}

function render(hits, ms, mode) {
  els.meta.innerHTML = `<b>${hits.length}</b> result${hits.length === 1 ? "" : "s"} · ${mode} · ${RECORDS.length} codes scanned in <b>${ms} ms</b>`;
  els.results.innerHTML = "";
  if (hits.length === 0) {
    els.results.innerHTML = `<li class="empty">No matches above the minScore threshold.</li>`;
    return;
  }
  hits.forEach((h, i) => {
    const pct = Math.round(h.score * 100);
    const li = document.createElement("li");
    li.className = "result" + (i === 0 ? " result--top" : "");
    li.innerHTML = `
      <span class="result__rank">${i + 1}</span>
      <span class="result__text">${highlight(els.q.value, h.text)}</span>
      <span class="result__bar"><span class="bar"><span class="bar__fill" style="width:${pct}%"></span></span></span>
      <span class="result__score">${h.score.toFixed(2)}</span>`;
    els.results.appendChild(li);
  });
}

function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }

[els.q, els.mode, els.chunk, els.minScore].forEach((el) => {
  el.addEventListener("input", run);
  el.addEventListener("change", run);
});

els.datasetCount.textContent = RECORDS.length;
els.datasetDump.textContent = RECORDS.join("\n");
run();
