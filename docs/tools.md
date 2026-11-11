# Tools

*Srpska verzija: [tools.sr.md](tools.sr.md)*

The validators, the red-flag rules, the viewer and the build. Back to the [README](../README.md).

## What is in the repository

| Path | Role |
|---|---|
| `schema.ttl` | PoderVocab 2.0 ontology with Serbian labels, plus VIP additions and SHACL shapes |
| `data/entities/` | One Turtle file per person or organisation: type, names, identifiers, identity sources |
| `data/cases/` | One Turtle file per investigated case: the connections between entities, with evidence |
| `archives.ttl` | Archive snapshot per cited document, maintained by the build |
| `app/` | Kotlin validation and conversion tools |
| `rules/` | Red-flag patterns as SPARQL queries, one per file |
| `web/` | Browser graph viewer, published at <https://veze-i-poznanstva.github.io/vip/> |

The `web/` module shows the data to readers and is published as a site on every green push to
`main`. It is not part of the validation pipeline; the validators in `app/` decide what counts.

## Quick start

Requirements: Java 17 or newer. Gradle is bundled through the wrapper.

```bash
# RDF syntax validation
./gradlew :app:validate -Pfile=schema.ttl

# SHACL validation of data against the shapes in the schema
./gradlew :app:validateShacl -Pshapes=schema.ttl -Pdata=data

# Best practices check
./gradlew :app:checkBestPractices -Pfile=schema.ttl

# OWL consistency check
./gradlew :app:checkConsistency -Pfile=schema.ttl

# Schema metrics
./gradlew :app:analyzeSchema -Pfile=schema.ttl

# Veracity validation: two domains per entity, every URL reachable
./gradlew :app:validateVeracity -Pfile=data

# Data layout: entities under entities/, connections under cases/, one home file per entity
./gradlew :app:checkDataLayout -Pdata=data

# Red-flag rules over the data (leads for a reporter; never fails)
./gradlew :app:checkRedFlags

# Run every validation (schema tasks default to schema.ttl, data tasks to data/)
./gradlew :app:validateAll
```

To see the graph in your browser, export the data and start the viewer. Java is the only
requirement, because the build downloads its own Node.js:

```bash
./gradlew :app:ttlToCytoscape   # refresh the viewer's data from data/
./gradlew :app:exportQueryData  # publish data.ttl, schema.ttl, rules.json and red-flags.json for the viewer's query engine
./gradlew :web:npmDev           # start the viewer; stop it with Ctrl+C
```

Then open <http://localhost:5173/> (Serbian), or <http://localhost:5173/?lang=en> for English. Type a name
into the search box, click a node to make it the centre, and double-click to add its connections. Rest the pointer on a
node to read its description and open its Wikipedia article, if it has one; the details panel also
links its Wikidata item. The viewer draws one edge per relationship, labelled with its latest
documented state (for example "owns 90%"); the panel shows the history, every state with its
period, share, comment and sources. An ended relationship is drawn dashed with "until" and the end
in the label. A missing end date means unknown, and the panel says so; the viewer never says
"current".

Schema tools take one file (`-Pfile`, default `schema.ttl`); data tools take a directory tree of
Turtle files or a single file (`-Pdata`, default `data`). Every tool loads with Apache Jena,
prints a report and exits with status 0 on success or 1 on failure, so they can be chained in
scripts and CI. The schema metrics report is informational and never fails.

## The validators

### Veracity validator

The validator that embodies the sourcing rule of the initiative. Given the data and the
vocabulary, it finds every agent and every connection the vocabulary entails, collects the URLs
of their Documentation nodes (ignoring documents with status removed) and checks:

1. There are at least two URLs, and they come from at least two different domains. A
   `www.` prefix is ignored, so `www.istinomer.rs` and `istinomer.rs` count as one domain.
2. Every URL is reachable, live or through an archived copy. The checker sends a HEAD request
   and, if the site refuses that, a GET; it follows up to five redirects, including relative and
   cross-protocol ones, identifies itself with a descriptive User-Agent, and times out after ten
   seconds. When the live page is blocked or gone, the validator falls back to the snapshot
   recorded in `archives.ttl`, or asks the Wayback Machine whether one exists. A page served only
   from the archive is reported as a warning with the snapshot date; a page that neither the web
   nor any archive has is an error.

An entity with no documentation at all is reported as an error, which is how under-sourced
records surface. A page counts as reachable when HEAD or GET answers 2xx after at most five
redirects; a redirect onto an error page (a path such as `/404.php` or `/not-found`) or onto the
site's home page means the page is gone. Only `http` and `https` URLs on public hosts are
fetched, so a cited address can never make the build probe a private network. An archive record
is accepted only if it is a genuine Wayback Machine snapshot address; anything else is an error.
Each URL is checked once per run however many entities cite it.

Implementation: `VeracityValidation` holds the rules and `VeracityValidator` is the
command-line entry point. The HTTP check is injected as a function, so unit tests run offline
against a stub and the live check is used only from the command line.

Example output:

```
=== Veracity Validation Report ===

Checking: data
Vocabulary: schema.ttl
Archive records: archives.ttl
Rules:
  1. Every agent and every connection must have documentation with at least 2 URLs from different domains
  2. Every URL must be reachable, live or through an archived copy

Warnings (1):
  1. https://w3id.org/vip/rs/SNS: https://www.sns.org.rs/ not reachable live (HTTP 403); using archived copy from 2025-10-23 at http://web.archive.org/web/20251023082323/https://sns.org.rs/

✗ Validation FAILED

Errors found:
  1. https://w3id.org/vip/rs/Entity has only 1 URL
  2. https://w3id.org/vip/rs/Other has URLs from only 1 domain(s): istinomer.rs
  3. https://w3id.org/vip/rs/Third has inaccessible URLs: https://broken.url (HTTP 404, no archived copy)

Total errors: 3
```

### Source archiver

Keeps the archive records complete. For every cited URL not marked removed and without a record it first asks the
Wayback Machine for an existing snapshot and only then requests a new capture through Save Page
Now, pausing between captures to respect the archive's rate limit. Records are written to
`archives.ttl`.

```bash
./gradlew :app:archiveSources   # locally: archive what is missing, then commit archives.ttl
./gradlew :app:checkArchives    # on CI: fail if anything is missing, without contacting the archive
```

`verify` runs the archiver locally and the check on CI, so a contribution that cites a new source
cannot pass CI until that source has been archived and the record committed.

The Wikidata step works the same way for `vip:wikidataItem`. For every entity with a
`vip:wikipediaArticle` and no recorded item it asks the Wikipedia API, anonymously, which Wikidata
item the article belongs to, and writes the `vip:wikidataItem` line into the entity file in place,
right after the article line. Commit the changed files. An entity without an article needs
nothing: its VIP URI is its identifier.

```bash
./gradlew :app:linkWikidata     # locally: write each entity's Wikidata item into its file, then commit
./gradlew :app:checkWikidata    # on CI: fail if a recorded item does not match the article, or an article's item is not recorded
```

`verify` runs the linker locally and the check on CI. The check also fails when an item recorded
without an article does not exist on Wikidata; an article not yet linked to Wikidata is only noted.
If Wikipedia cannot be reached, that is a warning, not a failure. Both tasks take `-Pdata`
(default `data`) and exit 1 on failure.

Reachability is checked live only for URLs in data files changed since the base, by default
`origin/main`, so a build's load on other sites stays small. Pick another base with
`-PverifyBase=<ref>` or check every URL with `-PverifyFull`. If the base cannot be found, every URL
is checked. A weekly CI run checks every URL, because sources die without anyone changing a file.
Everything else, including the two-domain rule, always covers the whole dataset.

### Data layout check

Keeps the data layout coherent. People and organisations are declared, that is given their type,
only under `data/entities/`, one per file, and the file is named after the entity's URI, so
`rs:Sinisa_Mali` lives in `sinisa-mali.ttl`. Connections are declared only under `data/cases/`.
Every entity has exactly one home file, and everything a file refers to must be declared
somewhere: participants in some file, types and predicates in the vocabulary. A mistyped URI
therefore cannot create a ghost participant, a misspelt class is not silently ignored, and a
made-up relationship between two entities is refused with a pointer to use a connection with
evidence. Data files may not contain vocabulary statements (subclass, domain, range, OWL, SHACL),
since those would change what every other check means. Turtle files anywhere else under `data/`
are reported.

### RDF validator

Parses the file and reports syntax errors, triple counts and namespaces.

### SHACL validator

Validates instance data against the SHACL shapes shipped in `schema.ttl`: PersonShape,
OrganizationShape, ConnectionShape, DocumentationShape and FinancialInformationShape. The data
is validated together with what the vocabulary entails (subclasses, subproperties, domains and
ranges), so a `foaf:Person` satisfies a constraint that asks for a `foaf:Agent` without the data
repeating the hierarchy. Each citation of a connection must quote its supporting passage in `rdfs:comment`. Reports each
violation with its focus node and message. Two checks cover ownership: `vip:ownershipShare` is one
decimal percentage above 0 and at most 100, and for each company the shares of records that may
apply at the same time total at most 100%. Records known to follow one another are not summed; an
undated record is never known to be disjoint.

### OWL consistency checker

Runs a reasoner over the ontology to find logical inconsistencies, unsatisfiable classes and
circular hierarchies.

### Best practices checker

Reports missing labels and comments, naming-convention violations, orphan classes, language-tag
inconsistencies and deprecated elements.

### Schema metrics

Counts classes, properties and individuals, measures hierarchy depth and reports complexity
metrics such as restrictions and disjointness axioms.

## Red-flag rules

Patterns that deserve a reporter's second look are written as SPARQL queries in `rules/`, one
file per pattern, with a bilingual explanation at the top of each file:

| Rule | Pattern |
|---|---|
| `nepotism.rq` | A spouse, partner, kum or family member of someone who leads an organisation holds a job there |
| `owner-in-office.rq` | A person who owns a company also holds a position in a government organisation |
| `schoolmates-in-power.rq` | Two people from the same school are both tied, through work or politics, to the same organisation |

```bash
./gradlew :app:checkRedFlags     # run every rule over data/ and print the matches
```

Dates decide what is reported. A case whose dates show the two periods never met is dropped. The
rest carry a status: `overlapping` when all four dates are recorded and the years meet, `unknown`
when any is missing. Most leads are `unknown` until the data has its dates, so an empty result
says little either.

The runner loads the data with what the vocabulary entails, so a rule written against
`bio:participant` sees every specialised participant property. Matches are leads, not findings:
the task prints them and always exits 0. To add a rule, drop a `.rq` file into `rules/` whose first
comment lines explain it: the title line as "English / Serbian", the English paragraph, a blank
comment line, then the Serbian paragraph; the unit tests run each rule against a fixture
that must match and one that must not.

## Web viewer

The `web/` module is a Svelte application that renders the data as a Cytoscape graph. People,
companies, parties and academic institutions are drawn as differently shaped nodes and
connections as labelled edges. Convert data for it with:

```bash
./gradlew :app:ttlToCytoscape -Pinput=data -Poutput=web/public/test-data-cytoscape.json -Pschema=schema.ttl
./gradlew :app:exportQueryData -Pdata=data -Pschema=schema.ttl -Prules=rules -Poutput=web/public
```

The second task publishes what the viewer's query panels need: `data.ttl` (the records plus the
RDFS entailments, so the browser engine sees what the rules see without reasoning), `schema.ttl`,
`rules.json` (each rule's title and explanation by language and its query text) and
`red-flags.json` (the leads the build finds; the viewer's test suite runs every rule in the browser
engine and must get exactly these). `./gradlew :web:npmTest` depends on it. The web build also
writes the assistant brief for the Ask panel as `dist/prompt.en.txt` and `dist/prompt.sr.txt`
(`web/scripts/write-prompts.ts`) and fails when the lazily loaded query chunk passes 400 KB gzipped
(`web/scripts/check-bundle.mjs`).

The converter is driven by the vocabulary: agents become nodes, each connection becomes an edge
between every pair of its participants, labelled by the connection's own label, its work roles
or its type, and direct properties between agents become edges labelled by the property.
Documentation and plain URLs never become nodes, but they travel with the record: every node and
every connection edge carries its sources (address, domain, type, status, the quoted passage and
the Wayback snapshot from `archives.ttl`, read from the optional fourth argument or `-Parchives`),
nodes also carry aliases and facts such as birth date, and connection edges carry their kind, roles,
ownership share, start and end exactly as written, and the repository file and line where the
record lives. Connections are never merged: two periods between the same pair are two edges.

Then run the viewer with `npm install` and `npm run dev` inside `web/`, or through Gradle with
`./gradlew :web:npmDev`. The viewer speaks English and Serbian; the switch in the header or
`?lang=sr` in the address picks the language. Its logic is tested; the graph drawing itself is not.

## Languages

Everything a reader meets exists in English and in Serbian, Latin script:

- **Documentation.** `README.md` is paired with `PROCITAJME.md`; every other document is
  `docs/<name>.md` paired with `docs/<name>.sr.md`. A unit test fails the build when a
  translation is missing or its heading outline differs from the original, so the two versions
  cannot drift apart unnoticed. Data files carry their header comments in both languages. The
  licence text stays in its original English.
- **Vocabulary.** Every term and every SHACL message in `schema.ttl` carries `@en`, `@es` and
  `@sr` text, as described under "How facts are recorded".
- **Viewer.** The interface text lives in `web/src/locales/<code>.json`, one file per language
  with the same keys; a Vitest test fails when a key is missing in any language. The language is
  taken from `?lang=` in the address, then from the last choice made on that browser, then from
  the browser's own language. To add a language, add its dictionary and its code to
  `supportedLanguages` in `web/src/lib/i18n.ts`, and the translated documents beside the
  originals.

Not localised yet: the reports of the command-line tools, which are English, and the connection
labels the converter writes into the viewer's data, which take the vocabulary's English label.

## Build automation

Everything that gates a release runs from one Gradle task:

```bash
./gradlew verify
```

It runs the unit tests, every schema check (`:app:validateSchema`), every data check
(`:app:validateData`: data layout, SHACL, archive records and veracity), the red-flag rules, the
export of the data to Cytoscape JSON, and the build of the viewer against that fresh export. Only two orderings
matter and both are enforced: archiving before veracity, export before the viewer build; the
rest may run in parallel. The pipeline fails
if any step fails, so a red `verify` means the data or the code is not fit to publish.

Continuous integration is deliberately thin: the workflow in `.github/workflows/verify.yml`
installs Java and calls `./gradlew verify`. All logic belongs in the build, never in CI
configuration, so the same command gives the same answer on a laptop and on the server.

Smaller entry points for day-to-day work:

```bash
./gradlew :app:test            # unit tests only, no network needed
./gradlew :app:validateSchema  # syntax, OWL consistency, best practices, metrics
./gradlew :app:validateData    # data layout, SHACL shapes, archive records, veracity of sources
./gradlew :web:npmDev          # run the viewer locally
```

Key libraries: Apache Jena 4.10.0 (RDF, RDFS and OWL reasoning, SHACL), Gson 2.10.1, Kotlin 1.9.21.

## Roadmap: graph explorer

The viewer is becoming a search-driven explorer: type a name, see the person with their
immediate connections, expand a node by clicking it and follow the trail through the graph, with
the sources of every claim one click away. The design is written up in
[docs/ui-design.md](ui-design.md). Built so far: the search box with suggestions (matching
ignores case, diacritics and script, so "brnabic" and "Брнабић" both find Ana Brnabić), the
one-hop view around the chosen node, and double-click to add a node's connections. The graph
logic lives in `web/src/lib/graph.ts` with Vitest tests; the Svelte components stay thin. Still
to come: sources and aliases in the export, the details panel with sources, recentring and the
trail, depth two, deep links.
