# VIP: Mapping Networks of Power with Verified Sources

*Srpska verzija: [PROCITAJME.md](PROCITAJME.md)*

**Want to add data with an AI agent?** Start with the
[agent quickstart prompt](docs/agent-prompt.md): paste it to your coding agent, or to a browser chat that will first
help you install a desktop version, and it sets up the tools, forks the repository and guides you through adding sourced connections, in Serbian.

## Purpose

VIP is a civic initiative. Its goal is to build an open, machine-checkable knowledge graph of
relationships between people of influence: politicians, officials, businesspeople, and the
organizations, companies and parties they are connected to. The graph is meant to support
criminal and journalistic investigation by making connections explicit, traceable and
verifiable rather than scattered across news archives and registries.

VIP aggregates what reputable media and official registries report. It does not fact-check them
and it is not legal proof; its job is to report them correctly and show where each claim comes from.

Three principles drive the design:

1. **Every claim is sourced.** An entity or a connection enters the graph only if it is backed
   by at least two public documents hosted on two different domains, and those documents must
   actually be reachable. This rule is enforced automatically by the veracity validator.
2. **Data is structured, not prose.** Facts are expressed in RDF using PoderVocab, the
   vocabulary created for Poderopedia. Reusing an established vocabulary keeps the data
   interoperable with other investigative datasets.
3. **Data is validated before it is trusted.** The toolkit checks syntax, logical consistency,
   SHACL shape constraints and modelling best practices, so that errors are caught at ingest
   rather than discovered during an investigation.

The project is inspired by [Poderopedia](https://www.poderopedia.org/), the Chilean platform
that pioneered mapping networks of power for investigative journalism.

The example data in `data/`, one Turtle file per person or organisation and one per investigated
case, describes Serbian public figures and their shared schools,
parties and business ties, sourced from Serbian fact-checking and investigative outlets such as
Istinomer, KRIK, Cenzolovka and the Media Ownership Monitor.

## How facts are recorded

Seven conventions apply to every record. The validators enforce their form; whether a source really says what a record claims takes a person reading it.

- **Evidence is a Documentation node, never a bare URL.** Each source is a `:Documentation`
  resource with its URL, a document type (news article, encyclopedia article, registry record,
  official document, fact-checking profile, web page, video recording) and a status (published,
  archived, removed). It is attached to the person, organization or connection it supports
  through `:hasDocumentation`. A source that no longer resolves keeps its record with status
  removed, so the history is preserved, but it no longer counts as evidence.
- **Every source has an archived copy.** Pages get deleted or quietly edited under pressure, so
  each cited URL is captured in the Internet Archive's Wayback Machine and the snapshot address
  and date are recorded in `archives.ttl`, keyed by the document URL. That file is maintained by
  the build, never by hand. The snapshot address embeds the capture time, so it is the stable
  reference to show and to check; the original URL remains the identity of the citation.
- **Relationships are connection resources, never bare properties.** Party membership is a
  `:PoliticalConnection` between the person and the party. Employment and board seats are a
  `:WorkConnection` with one or more work roles and optional start and end dates. Schooling is
  an `:AlumniEducationalConnection`. Company ownership is a `vip:OwnershipConnection` with an
  optional share. Each connection carries its own documentation, so the evidence for "X sits on
  the board of Y" is separate from the evidence that X and Y exist.
- **Time is part of the claim.** A connection carries `:connectionStartDate` and
  `:connectionEndDate` as precisely as its source states them: `xsd:gYear`, `xsd:gYearMonth` or
  `xsd:date`, never an invented 1 January. A missing date means unknown, not ongoing. Roles held
  at different times are separate connections. The shapes refuse an end before a start.
- **Every term has labels in English, Spanish and Serbian.** Every class, property and
  individual in the vocabulary and every SHACL message carries `@en`, `@es` and `@sr` text, so
  user interfaces and validation reports can be rendered in any of the three.
- **Same name, separate records.** Each person has one URI and a name that says who it is, such
  as `Ivan Bošnjak (Zrenjanin)`. Tell people apart by public facts: birth year, office, employer.
  `vip:wikipediaArticle` names the article about the entity itself, not one that only mentions
  it. Never record a JMBG or a personal tax number.
- **Three namespaces.** PoderVocab terms use the empty prefix `:` for
  `http://poderopedia.com/vocab/`; the project's own vocabulary additions use `vip:` for
  `https://w3id.org/vip/vocab#`; Serbian instance data uses `rs:` for `https://w3id.org/vip/rs/`,
  so `rs:Sinisa_Mali` is the person and other countries get their own segment.

The vocabulary is extended where PoderVocab had gaps. The additions are marked in `schema.ttl`
under the heading "VIP additions": the ownership connection and its properties, the
godparenthood connection (kumstvo, a kinship-grade bond in Serbian society that PoderVocab
lacks), the link from an organisation to its financial information, governance work roles such
as founder and chair of the board, the document types and the document statuses.

## What is in the repository

| Path | Role |
|---|---|
| `schema.ttl` | PoderVocab 2.0 ontology with Serbian labels, plus VIP additions and SHACL shapes |
| `data/entities/` | One Turtle file per person or organisation: type, names, identifiers, identity sources |
| `data/cases/` | One Turtle file per investigated case: the connections between entities, with evidence |
| `archives.ttl` | Archive snapshot per cited document, maintained by the build |
| `app/` | Kotlin validation and conversion tools |
| `rules/` | Red-flag patterns as SPARQL queries, one per file |
| `web/` | Browser graph viewer, **demo only** |

The `web/` module exists to demonstrate the data visually. It is not part of the validation
pipeline and carries no guarantees. The validators in `app/` are the product.

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
./gradlew :web:npmDev           # start the viewer; stop it with Ctrl+C
```

Then open <http://localhost:5173/>, or <http://localhost:5173/?lang=sr> for Serbian. Type a name
into the search box, click a node to make it the centre, and double-click to add its connections. Rest the pointer on a
node to read its description and open its Wikipedia article, if it has one.

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
repeating the hierarchy. Reports each violation with its focus node and message.

### OWL consistency checker

Runs a reasoner over the ontology to find logical inconsistencies, unsatisfiable classes and
circular hierarchies.

### Best practices checker

Reports missing labels and comments, naming-convention violations, orphan classes, language-tag
inconsistencies and deprecated elements.

### Schema metrics

Counts classes, properties and individuals, measures hierarchy depth and reports complexity
metrics such as restrictions and disjointness axioms.

## The vocabulary

PoderVocab models agents and the connections between them. People and organizations are
`foaf:Person` and `foaf:Organization`. Organizations are specialised into company, political,
governmental, non-governmental, non-profit, international, religious and academic kinds.
Connections are first-class resources with their own taxonomy: friendship, classmate,
educational, work role, company acquisition and merger, ownership, membership, financier,
political, sentimental and news-event connections, among others. Provenance is carried by
`SourcedEntity`, `SourceReliability` and `Documentation`. Labels and comments are in English,
Spanish and Serbian.

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
comment lines explain it in English and Serbian; the unit tests run each rule against a fixture
that must match and one that must not.

## Web viewer (demo)

The `web/` module is a Svelte application that renders the data as a Cytoscape graph. People,
companies, parties and academic institutions are drawn as differently shaped nodes and
connections as labelled edges. Convert data for it with:

```bash
./gradlew :app:ttlToCytoscape -Pinput=data -Poutput=web/public/test-data-cytoscape.json -Pschema=schema.ttl
```

The converter is driven by the vocabulary: agents become nodes, each connection becomes an edge
between every pair of its participants, labelled by the connection's own label, its work roles
or its type, and direct properties between agents become edges labelled by the property.
Documentation and plain URLs never appear in the graph.

Then run the viewer with `npm install` and `npm run dev` inside `web/`, or through Gradle with
`./gradlew :web:npmDev`. The viewer speaks English and Serbian; the switch in the header or
`?lang=sr` in the address picks the language. The viewer is a demonstration of what the data looks
like. Its graph rendering is not tested and it is not intended for production use.

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
export of the data to Cytoscape JSON, and the build of the demo viewer against that fresh export. Only two orderings
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
./gradlew :web:npmDev          # run the demo viewer locally
```

Key libraries: Apache Jena 4.10.0 (RDF, RDFS and OWL reasoning, SHACL), Gson 2.10.1, Kotlin 1.9.21.

## Roadmap: graph explorer

The viewer is becoming a search-driven explorer: type a name, see the person with their
immediate connections, expand a node by clicking it and follow the trail through the graph, with
the sources of every claim one click away. The design is written up in
[docs/ui-design.md](docs/ui-design.md). Built so far: the search box with suggestions (matching
ignores case, diacritics and script, so "brnabic" and "Брнабић" both find Ana Brnabić), the
one-hop view around the chosen node, and double-click to add a node's connections. The graph
logic lives in `web/src/lib/graph.ts` with Vitest tests; the Svelte components stay thin. Still
to come: sources and aliases in the export, the details panel with sources, recentring and the
trail, depth two, deep links.

## License

SPDX-License-Identifier: LicenseRef-DACL-0.1

The code, the example data and the documentation written for this project are released under
the [Degrowth-Aligned Commons License (DACL) 0.1](LICENSE.md) or any later version published
by its stewards at [codeberg.org/degrowth-boundary-commons/dacl](https://codeberg.org/degrowth-boundary-commons/dacl).
DACL is a source-available, share-alike commons license with purpose restrictions. It is not an OSI-approved open source license. Read the license before reusing
the work.

Scope: `schema.ttl` is a revision of PoderVocab, the vocabulary created by Poderopedia. It is
not this project's work and is not relicensed here; it remains under whatever terms its
authors chose. The project's own additions to the schema, the `vip:` terms, the SHACL shapes and
the Serbian labels, are offered under DACL.

## Contributing data

Data lives in `data/` in two kinds of file:

- `data/entities/<slug>.ttl`, one per person or organisation: its type, names, identifiers,
  a short description and the sources that establish who or what it is. The file is named after
  the URI, so `rs:Sinisa_Mali` is `sinisa-mali.ttl`. Many of these files are ten lines long, and
  that is fine: the file is still the one place to look, and learning more about an entity later
  changes only that file.
- `data/cases/<case>.ttl`, one per investigated case: the connections between entities, each
  with dates, roles and evidence. `milenijum-tim.ttl` holds the construction group's ownership and
  work ties; `peta-beogradska-gimnazija.ttl` holds the school class that reached the government;
  `alo-media-system.ttl` holds the ownership of the tabloid Alo's publisher.
  A reviewer reads one case file and sees a whole story with its sources.

Working with an AI coding agent? Paste the quickstart prompt in
[docs/agent-prompt.md](docs/agent-prompt.md) as your first message: it explains the journey,
installs the tools, forks the repository and walks you through adding people and connections with
verified sources, asking your permission at every step, in Serbian. Bring a case, or let it pick a
Serbian politician and follow the links.

To contribute a case:

1. For every person or organisation not yet under `data/entities/`, add a file there with the
   same prefixes as the existing ones and a header comment. Entities that already exist are
   referenced by URI from your case, never declared again.
2. Create `data/cases/<case>.ttl` with a header comment, in Serbian and English, saying what the
   case covers, and declare the connections in it.
3. Give every new entity and every connection evidence as `:Documentation` nodes: at least two
   documents from two different domains, each reachable. Prefer primary sources: official
   registries, court records, published investigations and fact-checking archives. A one-line
   `rdfs:comment` on a document saying what it supports helps the reviewer.
4. Run `./gradlew verify`. Locally this also archives every newly cited page through the Wayback
   Machine and updates `archives.ttl`; commit that file together with your data. On CI the same
   step only checks, so an unarchived source fails the build.
5. Propose the new entity files, the case file and `archives.ttl` together.

The build enforces all of this: the data layout check, the SHACL shapes, the archive records and
the veracity validator.

Practical notes:

- **Prefixes.** Copy the prefix block from any existing file. Instance URIs are `rs:` plus the
  name in Latin script with underscores, `rs:Sinisa_Mali`, `rs:Milenijum_Tim`; connections are
  named after their ends, `rs:SinisaMali_SNS`. The entity file is the local name lowercased with
  hyphens, `sinisa-mali.ttl`.
- **Minimum fields.** A person needs `foaf:firstName` and `:firstLastName`; an organisation needs
  `foaf:name`; a connection needs its participants and documentation. A document needs
  `:hasDocumentURL`, `:hasDocumentType` and `:hasDocumentStatus vip:published`.
- **Blocked sources.** Some sites (Istinomer, the SNS site) refuse automated clients. Cite them
  anyway: the archiver records a Wayback snapshot and the validator reports a warning, not an
  error, as long as the snapshot exists.
- **Archive rate limiting.** The Internet Archive throttles after many requests. The archiver
  keeps what it managed, exits 1 and asks you to rerun in a few minutes; records already written
  are kept. It also requests at most 25 new captures per run and lists the rest for the next
  run, so one branch cannot flood the archive. Read the cited addresses before running `verify`
  on a stranger's branch: the archiver will ask the Wayback Machine to fetch each of them.
- **Dead sources.** Never delete a citation. Set `:hasDocumentStatus vip:removed` and add an
  `rdfs:comment` saying what happened; the validator then ignores it as evidence and the archiver
  skips it. Replace it with a live source so the entity still has two domains.
- **Fast checks.** `./gradlew :app:test :app:checkDataLayout :app:validateShacl` needs no network.
  `./gradlew :app:validateData` runs the archiver locally; with the environment variable `CI`
  set to `true` it only checks, as on the build server.
