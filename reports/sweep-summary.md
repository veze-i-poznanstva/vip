# Review sweep, 30 October 2026: what was done with the findings

Four read-only reviewers (bug hunter, TDD coverage, documentation audit, security) reviewed the
finished work: veracity validator with archive fallback, Wayback client, source archiver, data
set loader and layout check, Cytoscape export, SHACL validator, build and CI, data and docs.
Their reports are the other files in this folder. This file records the reconciliation.

## Fixed, each test-first (80 -> 107 tests)

- Archive records are evidence only if they are genuine `web.archive.org/web/<14 digits>/`
  addresses; anything else is an error. A malformed record is an error, not a crash. The
  recorded archive is used without asking the archive (pinned), so CI does not depend on it.
- Wayback lookups apply the same page guard as saves. A host move with the same path is accepted
  (pkspartner -> cubepartner is a real permanent redirect); an error page or home page is not.
  A snapshot whose status is not 2xx counts as no snapshot. Fragments are ignored; a bad percent
  sequence no longer throws.
- URL checker: a redirect from a deep page onto the home page is dead; five redirects are really
  followed; only http/https; `HostPolicy.PublicHostsOnly` refuses loopback, private, link-local,
  multicast and unique-local hosts at every hop (tests use `AnyHost`); domains compare lowercase
  without a trailing dot; each URL is checked once per run.
- Export: only vocabulary-declared predicates become edges, so a bare made-up triple between two
  agents is never drawn; node ids are injective (hash suffix on collision).
- Layout check: unknown `rdf:type` reported; vocabulary statements (rdfs/owl/sh) in data files
  reported and excluded from inference; undeclared predicate between two declared entities
  reported; rules 3 and 4 apply only to agents and connections (FinancialInformation etc. are left
  alone); messages use paths relative to the data root; parse errors name the file; symlinks are
  not followed; path separators are platform-independent.
- SHACL validation extracted to `validateShacl(shapes, data)` with four tests.
- Best-practices checker exits 1 on errors.
- Build: OWL API, TopBraid SHACL, HermiT and Openllet removed (unused); data tasks read `-Pdata`
  only, so `-Pfile=schema.ttl` can no longer make veracity run against the schema; task
  descriptions corrected. Archiver closes its output stream and writes the `vip:` prefix.
- CI workflow: actions pinned to commit SHAs, `permissions: contents: read`, `timeout-minutes: 30`.
- Docs: CLAUDE.md rewritten; README and PROCITAJME corrected (namespaces, counts, libraries,
  example output, redirect and host rules, layout rules, practical contributor notes, licence
  scope); five BIO terms given Spanish text; schema licence header clarified.

## Deliberately not changed (design decisions confirmed)

- Subdomains are separate domains (`sr.` and `en.wikipedia.org` satisfy the two-domain rule).
  Encyclopaedia language editions are edited by different communities; acceptable for now.
- Identity sources on a person count as that person's two sources; connection sources are
  separate. This is the sourcing rule as designed.
- `vip:archived` document status is kept, unused, for a document that exists only in an archive.
- Licence steward URL: README points at the repository the maintainer named; LICENSE.md is the
  licence's own text and is left verbatim.

## Deferred, with a recommendation

- Refactor every `main()` into `run(args, out, err, deps): Int` so the CLIs can be tested offline
  (missing archives file silently ignored; schema stream leak; stack traces). One increment.
- Strip control characters from contributed strings before printing (GitHub Actions `::`
  commands, terminal escapes). Small; do with the `run()` refactor.
- Cap Save Page Now captures per local run and document "read the URLs before running verify on
  a stranger's branch". Policy question for the maintainer.
- IDN hosts and non-ASCII paths are sent raw by HttpURLConnection; a `.срб` source cannot pass
  live today. Needs IDN.toASCII and path encoding plus tests.
- `Outcome.alreadyArchived` miscounts and is never printed; inject the pause so pacing is testable.
- A property linking an organisation to its `:FinancialInformation` is missing from the
  vocabulary; add `vip:hasFinancialInformation` when financial data is first recorded.
- Deterministic label choice in the export when an entity has several names (prefer `@sr`).
- Gradle: `distributionSha256Sum` in the wrapper, dependency verification metadata, remove the
  unused `gradle/libs.versions.toml`; `npm ci` instead of `npm install` in the web build.
- Characterisation tests for the remaining surviving mutants listed in tdd-coverage.md.

## Second pass, 30 October 2026 (evening): the deferred list

Every item under "Deferred, with a recommendation" above is now done, test-first (107 -> 221
tests): `run()` for every CLI with a `<Tool>CliTest`, `String.printable()` for contributed
strings, a per-run capture cap of 25 with a `deferred` list and an injected pause, IDN hosts and
non-ASCII paths through `asciiForm`, `Outcome.alreadyArchived` counting records that existed
before the run for documents still cited, `vip:hasFinancialInformation`, deterministic label
choice (sr, en, alphabetical), wrapper checksum, dependency verification (Node.js trusted, see
the metadata file), version catalog removed, `npm ci`. The P2 mutant rows were pinned by a
tests-only agent; of the oddities it exposed, four were fixed as bugs:

- Wayback lookup on a non-JSON answer, on a snapshot without a url, and a timestamp that is not
  fourteen digits now fail as `ArchiveException` instead of Gson, null-pointer and string-index
  errors.
- `extractLabelFromUri` keeps acronyms whole (`NALED`, not `N A L E D`); camel case still splits.

Pinned as current behaviour, not changed (design or not reachable in practice):

- `record` called twice for one URL keeps both archive URLs; the archiver never does that.
- An entity URI ending in a slash gets the file name `.ttl`; such a URI is wrong before naming.
- An untyped participant is inferred an agent by the veracity validator (reported as lacking
  documentation) while the converter ignores it; the layout check reports it first anyway.
- Two connections with the same label between the same pair draw one edge; a connection's own
  label keeps its case while type and role labels are lowercased.
- The order of inaccessible URLs in one error message follows Jena's statement order.
