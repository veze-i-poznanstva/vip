# AGENTS.md

Guidance for AI agents working in this repository, whichever tool runs them. `CLAUDE.md` imports this
file, so edit only this one.

**Contributors:** work on your own fork and follow `docs/agent-prompt.md`. There `gh` is fine, on a
branch, under your own account. The capitalised rule below and the commit rules under "How to work
here" apply only on the maintainer's machine.

**NO `gh` TO ACT ON THE VIP REPOSITORIES (THIS ONE, THE w3id FORK, ANYTHING UNDER
`veze-i-poznanstva`). ONLY SSH WITH THE GHDL KEY (`~/.ssh/id_ed25519_ghdl`, configured for
everything under `~/code/ghdl/`). THE `gh` TOOL MUST NEVER PUSH, COMMENT OR OPEN PULL REQUESTS HERE. THE ONLY IDENTITY IN THESE REPOSITORIES IS
`danluddlum <350449005+danluddlum@users.noreply.github.com>`.**

README.md is a short front page; detail
lives in docs/ (`why-connections`, `data-guide`, `tools`, `privacy`). Keep this file in step with them.

## What this is

VIP ("Veze i poznanstva", connections and acquaintances) is a civic dataset of connections
between people and organisations in Serbia, built to support criminal and journalistic
investigation. It is RDF/Turtle on the PoderVocab ontology (`schema.ttl`, prefix `:` =
`http://poderopedia.com/vocab/`) plus the project's own additions (prefix `vip:` =
`https://w3id.org/vip/vocab#`). Instance data uses `rs:` = `https://w3id.org/vip/rs/`.
Kotlin tools validate it; a Svelte viewer in `web/` is published to GitHub Pages on green pushes to main. Licence: DACL 0.1
(`LICENSE.md`); `schema.ttl`'s PoderVocab terms keep Poderopedia's licence.

## The sourcing rule

Every agent and every connection carries `:Documentation` nodes with at least two document URLs
from two different domains (www ignored), each reachable live or through a Wayback Machine
snapshot recorded in `archives.ttl`. A dead source is kept with `:hasDocumentStatus vip:removed`
and no longer counts. Only genuine `web.archive.org/web/<14 digits>/` addresses count as archives.
Editorial standard: VIP aggregates what reputable media and official registries report; it does
not fact-check them and needs no legal proof. Trust the source, but aggregate it correctly: the
record must say what the source says, with its attribution, and nothing more.
The build proves form and reachability, not that: read each source before citing it, quote the
passage on the document's `rdfs:comment` (the build enforces this for connections), and check that it means this person (same-named people
get separate URIs, `rs:Ivan_Bosnjak_Milenijum_Tim` and `rs:Ivan_Bosnjak_Zrenjanin` are two men).
A citation found wrong stays with `vip:removed` and a comment saying why. Corrections: `?ispravka=1`
opens the page. VIP is a secondary source: a wrong source is corrected at the source first, then VIP
follows; a record that misstates its source is our error and is fixed directly.

Same-named people get separate URIs told apart by public facts. `vip:wikipediaArticle` is the
article about the entity, never a page it is only cited in; `vip:wikidataItem` is its Wikidata entity
IRI (`http://www.wikidata.org/entity/Q…`), an identifier, never a source. The build writes it from the
article (`linkWikidata`); contributors never look it up; without an article the `rs:` URI is the
identifier. Never record a JMBG or personal tax number; the shapes refuse `:hasTaxId` on a person, while a
company's registration and tax numbers are public registry data.

Connections carry `:connectionStartDate`/`:connectionEndDate` at the source's precision (`xsd:gYear`,
`xsd:gYearMonth`, `xsd:date`); never invent 1 January; absent means unknown; one connection per
role when periods differ. A changed attribute (such as a share) is a new dated record for the same
parties, the old one ending where the new one starts; the export merges the records of one
relationship into one edge with the latest state and its history, and draws ended relationships
dashed. A company's shares that may apply at the same time total at most 100
(`:OwnershipTotalShape`); a share is a decimal in (0, 100]. Rules drop known non-overlap and
report `overlapping` or `unknown`.

## Data layout

- `data/entities/<slug>.ttl`: one file per person or organisation, named after the URI local name
  lowercased with hyphens (`rs:Sinisa_Mali` -> `sinisa-mali.ttl`). Type, names, identifiers,
  identity sources.
- `data/cases/<case>.ttl`: one file per investigation, holding only connections. A case that cuts
  across investigations may be a folder, `data/cases/javne-funkcije/<body>.ttl`, one file per public
  body; the layout check reads only the top-level folder.
- `archives.ttl`: machine-maintained archive records, keyed by document URL.
- `rules/<name>.rq`: one red-flag pattern per file (nepotism, owner-in-office, schoolmates-in-power).
- `checkDataLayout` enforces: one home file per entity; everything referenced is declared (in a
  file or in the vocabulary, including types and predicates); agents only under `entities/`,
  connections only under `cases/`; one entity per entity file, correctly named; no stray files;
  no vocabulary statements in data files.

## Commands

```bash
./gradlew verify                 # the single gate; CI only calls this
./gradlew :app:test              # unit tests, offline (local HTTP servers, stubs)
./gradlew :app:validateSchema    # validate, checkConsistency, checkBestPractices, analyzeSchema
./gradlew :app:validateData      # checkDataLayout, validateShacl, archiveSources|checkArchives, linkWikidata|checkWikidata, validateVeracity
./gradlew :app:checkDataLayout -Pdata=data
./gradlew :app:validateShacl -Pshapes=schema.ttl -Pdata=data
./gradlew :app:validateVeracity -Pdata=data -Pschema=schema.ttl -Parchives=archives.ttl   # live checks only for files changed since -PverifyBase (origin/main); -PverifyFull checks all
./gradlew :app:archiveSources    # locally: record Wayback snapshots for new citations; commit archives.ttl
./gradlew :app:checkArchives     # what CI runs (CI=true): fail if a citation has no record, never contacts the archive
./gradlew :app:linkWikidata      # locally: write each entity's Wikidata item from its Wikipedia article into the entity file; commit the files
./gradlew :app:checkWikidata     # what CI runs (CI=true): fail if a recorded item does not match the article, or an article's item is not recorded
./gradlew :app:ttlToCytoscape -Pinput=data -Poutput=web/public/test-data-cytoscape.json -Pschema=schema.ttl
./gradlew :app:checkRedFlags     # every rules/*.rq over the data; prints leads, always exits 0
./gradlew :app:exportQueryData   # web/public/data.ttl (records plus entailments), schema.ttl, rules.json, red-flags.json for the viewer's query engine
```

Schema tasks take `-Pfile` (default `schema.ttl`); data tasks take `-Pdata` (default `data`,
a directory tree or one file). Every tool exits 1 on failure except `analyzeSchema`.

## Code map (`app/src/main/kotlin/vip/`)

- `Vocab.kt` URIs the tools depend on (PODER, VIP, RS namespaces).
- `DataSet.kt` loads a directory tree of Turtle files (no symlinks, errors name the file).
- `DataLayoutCheck.kt` the layout rules above; CLI `DataLayoutCheckKt`.
- `VeracityValidation.kt` the sourcing rule, `checkUrlOverHttp` (HEAD then GET, five manual
  redirects, error-page and home-page redirects are dead, `HostPolicy.PublicHostsOnly` refuses
  loopback/private hosts; tests use `HostPolicy.AnyHost`), archive fallback; CLI `VeracityValidator.kt`.
- `Wayback.kt` Internet Archive client: availability lookup, Save Page Now, rate-limit errors,
  separate save timeout, refuses snapshots of a different page (error page, home page) but accepts
  a host move with the same path.
- `SourceArchiver.kt` which URLs need records, lookup then save, writes `archives.ttl`, `--check`.
- `Wikidata.kt` Wikipedia/Wikidata client (`ItemLookup`, `WikimediaClient`): item of an article, item exists; tests on a local server.
- `WikidataLinker.kt` fills `vip:wikidataItem` into entity files from the article (text insertion after the article line) and checks recorded items; CLI with `--check`.
- `SHACLValidator.kt` `validateShacl(shapes, data)` over the RDFS-inferred graph; CLI.
- `TtlToCytoscape.kt` vocabulary-driven export: agents are nodes, connections are edges between
  participants, only vocabulary-declared direct properties become edges, node ids are injective.
  The records of one relationship (same kind, parties and roles) become one edge carrying the latest
  state and a `history` of all states; nothing else is merged. Nodes and connection edges carry
  sources (quote, archive snapshot), aliases, facts, dates, roles, share and the file and line of the
  record (`CytoscapeEvidence.kt`: Source, Fact, RecordLocator).
- `RDFValidator.kt`, `OWLConsistencyChecker.kt` (Jena OWL reasoner), `BestPracticesChecker.kt`
  (exits 1 on errors), `SchemaMetrics.kt`.
- `RedFlags.kt` loads `rules/*.rq` (name, leading comment = bilingual explanation, SPARQL SELECT),
  runs them over the RDFS-inferred data, prints bindings; `RedFlagsTest` has a matching and a
  non-matching fixture per rule. Add a rule = add a file + two tests. The leading comment is the title
  line "English / Serbian", the English paragraph, a blank comment line, then the Serbian paragraph.
- `QueryExport.kt` publishes `data.ttl` (records plus RDFS entailments, so a reasoner-free engine sees
  what the rules see; written the same on every build: blank nodes relabelled from where they hang and
  what they say, statements sorted, bare anonymous types left out), `schema.ttl`, `rules.json` (title and explanation by language, query text) and
  `red-flags.json` (the leads the build finds, for the viewer's equivalence test) into `web/public/`;
  `npmTest` and `npmBuild` depend on it; CLI `QueryExportKt`.
- Every CLI is `main = exitProcess(runXxx(args, out, err, deps))`; `runXxx` is tested in
  `<Tool>CliTest.kt`. Contributed strings are printed through `String.printable()` (`Printing.kt`),
  which turns control characters into spaces.

Web (`web/src/`): `lib/i18n.ts` translation store (`$t`, `language`, `resolveLanguage`), dictionaries
in `locales/<code>.json`, Vitest tests in `lib/i18n.test.ts`; `./gradlew :web:npmTest` runs them and
`:web:build` depends on them. Querying in the browser (`docs/ui-design.md`, "Querying in the browser"):
`lib/query.ts` runs SPARQL `SELECT` with Comunica's pure-TypeScript lite build over an N3 store
(row cap, time limit, `QueryError` kinds, `labelsOf`/`textsOf`); `lib/leads.ts` turns rule rows into
sentences (`leads.<rule>` templates with `[optional]` segments) and map views; `lib/ask.ts` builds the
prompt for the visitor's own assistant from the terms the data uses and extracts the query from the
answer; `lib/advanced.ts` carries a query in `?upit=` (base64url); `lib/engine.ts` loads the published
files once. `QueryPanel.svelte` (tabs Tragovi, Pitaj, Napredno) is imported lazily by `App.svelte`, so
the engine is a separate chunk; `scripts/write-prompts.ts` (vite-node) publishes the assistant brief as
`dist/prompt.<language>.txt`, which the Ask links tell the assistant to read from the published site
(`PUBLISHED_SITE` in `lib/ask.ts`, never the page the visitor is on); `scripts/check-bundle.mjs`
fails the build when that chunk passes 400 KB gzipped. `lib/equivalence.test.ts` proves every rule gives the build's `red-flags.json` leads.
Node 22 (the Gradle plugin downloads it; Comunica's dependencies need it).

Libraries: Apache Jena 4.10.0, Gson 2.10.1, Kotlin 1.9.21, Java 17.

## How to work here

- Baby Steps TDD: one test, minimal code, refactor, run `CI=true ./gradlew :app:test`.
- Two languages everywhere a reader looks, English and Serbian (Latin): documents are paired
  (`README.md`/`PROCITAJME.md`, `docs/<name>.md`/`docs/<name>.sr.md`; `DocumentationTranslationsTest`
  fails when a pair is missing or the heading outlines differ), viewer text goes through `$t` with
  a key in every `web/src/locales/*.json` (parity test), data headers are bilingual, the vocabulary
  is trilingual. Change both languages in the same step. Tool output is English, by decision.
- Tests never touch the network: `com.sun.net.httpserver.HttpServer` on localhost, stub
  `UrlChecker`, `ArchiveLookup`, `FakeWayback`.
- The sandbox may refuse Save Page Now; the user runs `./gradlew :app:archiveSources` themselves.
- Trunk-based: commit to `main` only when the user says "cp"; no attribution lines.
- Explorer (`docs/ui-design.md`): search, one-hop view and expand are built in `web/src/lib/graph.ts`
  (fold, buildIndex, search, egoGraph, expand; Vitest) and `App.svelte`; the rest is planned.
