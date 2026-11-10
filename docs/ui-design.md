# Graph explorer: UI design

Status: increments 2, 3, 4 (the details panel) and the expand half of 5 are built, as is the focus part of 7 (see Increments); the rest is design. This document describes the first real user interface of VIP,
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

## Open questions

- **Cyrillic and Latin.** Decided for now: folding in the UI transliterates Serbian Cyrillic to
  Latin, so both scripts find the same label. Names of foreign origin whose Cyrillic spelling is
  not a letter-for-letter transliteration still need an alias in the data.
- **Connections with many participants.** A board of directors is one connection with many
  participants. Rendering it as a node is correct but visually different from pairwise edges.
  Needs a visual treatment.
- **Status.** The viewer is published as the project's site; the increments above remain open.
- **Testing the canvas.** Cytoscape rendering is hard to unit test. Keep graph construction
  (nodes, edges, adjacency, folding, trail state) in plain TypeScript modules with Vitest
  tests, and leave the Svelte components as thin rendering shells.
