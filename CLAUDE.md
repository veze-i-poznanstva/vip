# CLAUDE.md

**NO `gh` TO ACT ON THE VIP REPOSITORIES (THIS ONE, THE w3id FORK, ANYTHING UNDER
`veze-i-poznanstva`). ONLY SSH WITH THE GHDL KEY (`~/.ssh/id_ed25519_ghdl`, configured for
everything under `~/code/ghdl/`). THE `gh` TOOL ON THIS MACHINE IS SIGNED IN AS OTHER ACCOUNTS AND
MUST NEVER PUSH, COMMENT OR OPEN PULL REQUESTS HERE. THE ONLY IDENTITY IN THESE REPOSITORIES IS
`danluddlum <350449005+danluddlum@users.noreply.github.com>`.**

Guidance for Claude Code when working in this repository. Keep it in step with README.md.

## What this is

VIP ("Veze i poznanstva", connections and acquaintances) is a civic dataset of connections
between people and organisations in Serbia, built to support criminal and journalistic
investigation. It is RDF/Turtle on the PoderVocab ontology (`schema.ttl`, prefix `:` =
`http://poderopedia.com/vocab/`) plus the project's own additions (prefix `vip:` =
`https://w3id.org/vip/vocab#`). Instance data uses `rs:` = `https://w3id.org/vip/rs/`.
Kotlin tools validate it; a Svelte viewer in `web/` is a demo only. Licence: DACL 0.1
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
passage on the document's `rdfs:comment`, and check that it means this person (same-named people
get separate URIs, `rs:Ivan_Bosnjak_Milenijum_Tim` and `rs:Ivan_Bosnjak_Zrenjanin` are two men).
A citation found wrong stays with `vip:removed` and a comment saying why.

Same-named people get separate URIs told apart by public facts. `vip:wikipediaArticle` is the
article about the entity, never a page it is only cited in. Never record a JMBG or personal tax number.

Connections carry `:connectionStartDate`/`:connectionEndDate` at the source's precision (`xsd:gYear`,
`xsd:gYearMonth`, `xsd:date`); never invent 1 January; absent means unknown; one connection per
role when periods differ. Rules drop known non-overlap and report `overlapping` or `unknown`.

## Data layout

- `data/entities/<slug>.ttl`: one file per person or organisation, named after the URI local name
  lowercased with hyphens (`rs:Sinisa_Mali` -> `sinisa-mali.ttl`). Type, names, identifiers,
  identity sources.
- `data/cases/<case>.ttl`: one file per investigation, holding only connections.
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
./gradlew :app:validateData      # checkDataLayout, validateShacl, archiveSources|checkArchives, validateVeracity
./gradlew :app:checkDataLayout -Pdata=data
./gradlew :app:validateShacl -Pshapes=schema.ttl -Pdata=data
./gradlew :app:validateVeracity -Pdata=data -Pschema=schema.ttl -Parchives=archives.ttl
./gradlew :app:archiveSources    # locally: record Wayback snapshots for new citations; commit archives.ttl
./gradlew :app:checkArchives     # what CI runs (CI=true): fail if a citation has no record, never contacts the archive
./gradlew :app:ttlToCytoscape -Pinput=data -Poutput=web/public/test-data-cytoscape.json -Pschema=schema.ttl
./gradlew :app:checkRedFlags     # every rules/*.rq over the data; prints leads, always exits 0
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
- `SHACLValidator.kt` `validateShacl(shapes, data)` over the RDFS-inferred graph; CLI.
- `TtlToCytoscape.kt` vocabulary-driven export: agents are nodes, connections are edges between
  participants, only vocabulary-declared direct properties become edges, node ids are injective.
- `RDFValidator.kt`, `OWLConsistencyChecker.kt` (Jena OWL reasoner), `BestPracticesChecker.kt`
  (exits 1 on errors), `SchemaMetrics.kt`.
- `RedFlags.kt` loads `rules/*.rq` (name, leading comment = bilingual explanation, SPARQL SELECT),
  runs them over the RDFS-inferred data, prints bindings; `RedFlagsTest` has a matching and a
  non-matching fixture per rule. Add a rule = add a file + two tests.
- Every CLI is `main = exitProcess(runXxx(args, out, err, deps))`; `runXxx` is tested in
  `<Tool>CliTest.kt`. Contributed strings are printed through `String.printable()` (`Printing.kt`),
  which turns control characters into spaces.

Web (`web/src/`): `lib/i18n.ts` translation store (`$t`, `language`, `resolveLanguage`), dictionaries
in `locales/<code>.json`, Vitest tests in `lib/i18n.test.ts`; `./gradlew :web:npmTest` runs them and
`:web:build` depends on them.

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
