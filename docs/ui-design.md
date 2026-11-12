# Graph explorer: UI design

Status: increments 2, 3, 4 (the details panel) and the expand half of 5 are built, as is the focus part of 7 (see Increments), and so is querying in the browser (Leads, Ask, Advanced), built and published on 10 and 11 November 2026; the rest is design. This document describes the first real user interface of VIP,
replacing the current demo viewer that dumps the whole dataset on one canvas.

## Who it is for and what they do

An investigator or journalist arrives with a name. They want to answer, in under a minute:
who is this person connected to, through what, and where is the proof. They then follow one
of those connections and ask the same question again. The interface is a trail, not a map.

Three things must always be true on screen:

1. The person or organization in focus is unmistakable.
2. Only the immediate neighbourhood is shown, so the picture stays readable.
3. The sources behind any node or edge are one click away, with their verification status.

## User flow

1. **Search.** The user types into a single search box. Suggestions appear after two
   characters, matching names, first and last names, and aliases. Matching ignores
   diacritics and case, so "brnabic" finds "Ana Brnabić". Cyrillic input is transliterated
   to Latin before matching, so "Брнабић" finds the same person.
2. **Focus.** Choosing a suggestion renders the ego graph: the focus node in the centre and
   every node directly connected to it in a ring around it. A depth toggle switches between
   one hop (default) and two hops. Two hops render the second ring fainter.
3. **Inspect.** A single click on any node or edge selects it and fills the details panel:
   label, type, every known property, and the list of sources with domain and reachability
   status. An edge's details list the relationship's dated states, the newest marked as the latest
   documented state; a relationship is never called current. Nothing else on the canvas changes.
4. **Expand.** A double click, or the Expand button in the details panel, adds that node's
   neighbours to the canvas without removing what is there. The layout animates so the
   expanded node moves outward and its neighbours gather round it.
5. **Recentre.** The Focus button in the details panel makes the selected node the new centre
   and rebuilds the ego graph around it. The previous focus is appended to the trail.
6. **Follow the trail.** A breadcrumb strip at the bottom lists every focus node visited in
   this session. Clicking a crumb recentres on it. The trail is encoded in the URL hash so a
   link reproduces the same view for a colleague.

## Screen layout

```
+----------------------------------------------------------------------------------+
| [ Search a person or organization...                     ]  Depth: (1) ( ) 2      |
+-------------------------------------------------------------+--------------------+
|                                                             | Ana Brnabić        |
|                   ( Milenijum Tim )                         | Person             |
|                        |  owner                             |--------------------|
|   ( Peta Gimnazija )---( ANA BRNABIĆ )---( Ivan Bošnjak )   | Born 1975-09-28    |
|          classmate     |  business partner                  | Party: SNS         |
|                   ( SNS )                                   |--------------------|
|                     member                                  | Sources (2)        |
|                                                             | ✓ istinomer.rs     |
|                                                             | ✗ sr.wikipedia.org |
|                                                             |   HTTP 403         |
|                                                             |--------------------|
|                                                             | [Focus] [Expand]   |
+-------------------------------------------------------------+--------------------+
| Trail:  Siniša Mali  >  Peta Gimnazija  >  Ana Brnabić                            |
+----------------------------------------------------------------------------------+
```

On narrow screens the details panel becomes a sheet that slides up over the canvas.

## Visual encoding

- Node shape and colour by type, as today: person ellipse, organization rectangle, party
  diamond, academic rounded rectangle. Unknown types grey.
- The focus node is larger with a thick outline. First-ring nodes full opacity, second-ring
  nodes at reduced opacity. Nodes added by Expand are full opacity.
- Edge labels hidden by default, shown on hover and on selection, to keep the canvas clean.
- Layout: concentric around the focus node for the ego view; on Expand, run an incremental
  force layout with existing nodes locked so the picture does not jump.
- Ended relationships (the latest state has an end date) are drawn dashed, with the end in the
  label, for example "owns 100% (until 2022-07)".
- Weak evidence (no reachable source) is planned as a lighter, thinner line, since dashed now
  means ended.

## Data

The dataset is small for the foreseeable future, so the first version is fully client-side.
The build exports a single `graph.json` that the browser loads once and indexes in memory.

```json
{
  "nodes": [
    { "id": "ana_brnabic", "uri": "...", "label": "Ana Brnabić", "type": "person",
      "aliases": ["Ана Брнабић"], "properties": { "birthDate": "1975-09-28" },
      "sources": [ { "url": "https://...", "domain": "istinomer.rs", "status": "ok" } ] }
  ],
  "edges": [
    { "id": "c1", "source": "ana_brnabic", "target": "ivan_bosnjak",
      "label": "business partner", "connectionUri": "...", "comment": "...",
      "sources": [ ... ] }
  ]
}
```

Adjacency is computed in the browser from the edge list. Search runs over a folded index
(lower-cased, diacritics stripped, Cyrillic transliterated to Latin) built at load time.

When the dataset grows past a few thousand nodes, the same UI switches to a backend: a Fuseki
SPARQL endpoint behind `/api`, which the Vite dev proxy already points at. Two queries cover
the UI: search by folded label, and neighbourhood of a URI at depth one or two. The JSON
contract above stays the same, so the UI does not change.

## Changes required in the converter

The current converter produces nodes and edges with only id, label and type. It must also:

- merge the records of one relationship (same kind, parties, roles) into one edge carrying the
  latest state and a `history` of all states;
- carry every `hasDocumentURL` and `hasSource` of a node onto the node, and of a connection
  onto the edge it was collapsed into;
- keep the connection's URI and comment on the edge, so the details panel can show them;
- collect aliases from all `foaf:name` values and the Cyrillic and Latin spellings;
- keep a connection as a small node when it has more than two participants, since a
  hyper-edge cannot be drawn as one line;
- merge the veracity report into the export so each source carries its last known status.

## Sources panel

This panel is the reason the project exists and deserves the most care. For each source: the
domain in bold, the full URL on hover, the reachability status with HTTP code, the date of the
last check, and the archived copy. The archived copy is the primary link, because its address
embeds the capture date and cannot be edited after the fact; the live URL is secondary. Sources from the same domain are grouped so the two-domain rule is visible.
An entity or edge that violates the rule shows a warning banner at the top of the panel.

## Increments

Each increment is a thin vertical slice that ends green, following the Baby Steps approach.

1. **Converter export.** Nodes and edges carry sources, aliases, connection URI and comment.
   Kotlin unit tests.
2. **Search.** Search box with typeahead over the folded index. Unit tests for folding and
   matching in Vitest, including Cyrillic input and diacritics.
3. **Ego graph, depth one.** Choosing a result renders the focus node and its neighbours with
   the concentric layout.
4. **Details panel.** Built. The focus node, or a clicked connection, shows facts, connections, sources with quotes and archive links, and links to the raw data. Relationship history and ended-relationship marking built.
5. **Expand and recentre.** Double click expands; Focus button recentres; trail appears.
6. **Depth two toggle.** Second ring at reduced opacity.
7. **Deep links.** Trail and expanded set in the URL hash. Built so far: the path (`?from=&to=`) and the focus (`?focus=`).
8. **Veracity status export.** Build writes the veracity report as JSON and the converter
   merges it, so dashed edges and status marks reflect the last check.

Increments one and two deliver value on their own and can be reviewed independently.

## Querying in the browser

The rules in `rules/`, and any question a reporter can phrase as a graph pattern, run in the
visitor's browser over the same data the map draws. Nothing is computed on a server, and nothing
leaves the browser except what the visitor chooses to hand to their own AI assistant.

### Decision

- **Engine.** Comunica's RDF/JS lite build, pure TypeScript, over an N3.js in-memory store. The
  whole query panel with the engine is one lazily loaded chunk, 339 KB gzipped as measured in the
  build (the lite build's own figure of 225 KB predates its SPARQL 1.2 parser). No WebAssembly: the
  engine is readable, debuggable and runs in Vitest without a binary. The lite build has no
  `SERVICE` keyword, so a query cannot call out to another endpoint. Its dependencies need Node 22,
  which the Gradle plugin downloads.
- **One definition of every rule.** The rules stay SPARQL files in `rules/`. The build's
  `checkRedFlags` keeps reporting on the command line; the browser runs the same text.
- **Leads are computed in the client.** This replaces the earlier idea of a precomputed leads
  file: with the engine in the browser there is nothing left to duplicate.

### Published files

The build writes, beside the graph JSON and from the same commit:

- `data.ttl`: every data file merged, plus the RDFS entailments the rules depend on (types along
  `rdfs:subClassOf`, participants along `rdfs:subPropertyOf`), because the engine does no
  reasoning.
- `schema.ttl` as it is, for labels, comments and the prompt.
- `rules.json`: the name, the bilingual explanation and the query text of every rule.
- `red-flags.json`: the leads the build itself finds, so the viewer's tests can prove the browser
  engine gives exactly the same.

### Leads panel

"Leads (Tragovi)". On opening it loads the engine and the data and runs every rule. It shows each rule's
name and explanation in the viewer's language, its match count, and each match as a sentence built
from node labels ("Siniša Mali owns Brigham Holding & Finance and held office at Grad Beograd as
mayor; overlap unknown"). Clicking a match opens the path view between the two agents, or the ego
view of the person. The panel shows "a lead for a reporter, not a finding" once, at its foot. No query is
visible here. The sentence comes from a template per rule in the dictionaries (`leads.<rule>`,
with `[optional]` segments for variables a row may lack); a rule without a template lists its
variables and values. A row with two people opens the path between them, any other row the
neighbourhood of its first agent.

### Ask

"Ask (Pitaj)". A box for a question in Serbian or English. The page does not interpret the question;
it builds a prompt and hands it to the visitor's own assistant.

1. The prompt contains: the task (write one SPARQL 1.1 `SELECT` query over this vocabulary); the
   vocabulary summary generated from `schema.ttl` in the viewer's language, limited to the
   classes, properties and individuals the records actually use (about twenty-five classes, twenty-five
   properties and twenty roles, document types and statuses), each with its label, parent or
   domain and range, and comment; the prefixes; two rules as worked examples; the conventions (a
   missing end date is unknown, never current; dates come at three precisions and are compared on
   the year; kinship, kumstvo and public office are connections; entailments are materialised;
   `rs:` names); the output format (a single fenced `sparql` block, with variables for the agents
   to show); and the question. About 14,000 characters.
2. The build publishes the brief, the prompt without the question, as `prompt.sr.txt` and
   `prompt.en.txt` beside the page. "Open in Claude" and "Open in ChatGPT" carry a short
   instruction in the address: read the brief in full, then answer the question with one SPARQL
   `SELECT` query. The brief named is always the one on the published site,
   `https://veze-i-poznanstva.github.io/vip/prompt.<language>.txt`, whatever page the visitor is
   on, because the assistant must be able to fetch it and cannot reach a local preview or a fork's
   copy. An assistant that cannot open links gets the whole prompt from
   "Copy the prompt". A link is still prefilled only while its text encodes to 8,000 characters or
   fewer. The page itself sends nothing.
3. A paste field takes the assistant's answer. The page extracts the first fenced code block, or
   the whole text when there is no fence, runs it with the same runner as Advanced, shows the rows
   as sentences and highlights the agents on the map. "Show query" expands the SPARQL; a parse or
   run error is shown in words together with the engine's message.
4. The same caveat line as the leads, and a note that the question goes to the assistant the
   visitor opened, not to the project.

### Advanced

"Advanced (Napredno)", reachable from a link under an answer and from the address `?upit=<base64url>`. A
plain text area to paste or edit SPARQL, "Run", the rules loadable as templates, a results table
with "show on map" per row, and "Share", which writes the query into the address. Meant for
journalists who learn the patterns, for developers, and for agents that generate queries.

### Limits

- A result cap of 500 rows and a time limit of five seconds, each with a readable message; rows
  beyond the cap are not read, and a query's own `LIMIT` is respected within it.
- The query is evaluated on the page's main thread. The time limit is checked when a row arrives
  and by a timer, so a single long synchronous evaluation (for example a regular expression with
  catastrophic backtracking in a pasted query) cannot be interrupted and can freeze the tab until
  it ends. A query only runs when the visitor presses Run. The recommended remedy, not yet built,
  is to evaluate in a Web Worker that the page can terminate at the deadline.
- `SELECT` only; `CONSTRUCT`, `ASK` and updates are refused in the interface.
- The whole dataset sits in the browser. At tens of thousands of triples the Turtle is still a
  few megabytes; revisit if it grows past that.
- Vite needs the known `process.env` workaround for Comunica; it is documented in the build.
- The lazy chunk stays under 400 KB gzipped; `web/scripts/check-bundle.mjs` fails the build above
  that, and fails it too if the chunk is no longer separate.

### Increments

Thin, test-first slices, each ending green. All seven were built on 10 and 11 November 2026; the
list stays as the record of what each proves.

1. **Export.** A Gradle task writes `data.ttl` with the entailments, `schema.ttl`,
   `rules.json` and `red-flags.json`. Kotlin tests: a sibling connection comes out typed as a family connection; a
   parent comes out as a participant; the JSON carries both explanations.
2. **Query module.** `web/src/lib/query.ts`: load Turtle, run, cap, time limit, rows with node
   ids. Vitest: a join, a property path, the `SUBSTR` year filter, a syntax error message, an
   empty result, the cap.
3. **Equivalence.** Vitest: every rule over the published `data.ttl` gives exactly the bindings
   the build's `exportQueryData` wrote to `red-flags.json`.
4. **Leads panel.** Sentences from bindings, click to path or ego, locale keys, loading and
   error states.
5. **Prompt builder and paste-back.** The prompt from the schema in both languages (test: every
   class and property the data uses present, the unused vocabulary absent), the links and the
   copy, code-block extraction (test: fenced, unfenced, text before and after), run and display.
6. **Advanced.** Text area, templates, share address, results table.
7. **Docs and budget.** The tools guide gains the task; the bundle size is asserted in the web
   build.

### Not now

- A direct call to a model with the visitor's own key.
- An MCP server over the dataset for agents.
- `llms.txt` on the site, cheap once the files are published.

## Open questions

- **Cyrillic and Latin.** Decided for now: folding in the UI transliterates Serbian Cyrillic to
  Latin, so both scripts find the same label. Names of foreign origin whose Cyrillic spelling is
  not a letter-for-letter transliteration still need an alias in the data.
- **Connections with many participants.** A board of directors is one connection with many
  participants. Rendering it as a node is correct but visually different from pairwise edges.
  Needs a visual treatment.
- **Status.** The viewer is published as the project's site; querying in the browser (all seven increments of Leads, Ask and Advanced) is built and published, 10 and 11 November 2026.
- **Testing the canvas.** Cytoscape rendering is hard to unit test. Keep graph construction
  (nodes, edges, adjacency, folding, trail state) in plain TypeScript modules with Vitest
  tests, and leave the Svelte components as thin rendering shells.
