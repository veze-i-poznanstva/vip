# Documentation audit

Scope: README.md, PROCITAJME.md, CLAUDE.md, LICENSE.md, header comments of `data/entities/*.ttl` and
`data/cases/*.ttl`, the "VIP additions" block of `schema.ttl`, KDoc in `app/src/main/kotlin/vip/*.kt`,
task descriptions in `app/build.gradle.kts`. Everything was checked against the code and the build as
they stand in the working tree on 2026-10-30. Read-only: nothing was built, run or edited.

Counts used below were taken with grep over `schema.ttl`: 64 named `owl:Class` declarations (60 `a owl:Class`
at line start plus `bio:Birth`, `bio:Death`, `bio:IndividualEvent`, `bio:Event`, `bio:Relationship` declared
with `rdf:type`; the anonymous union class at line 949 is not counted), 91 property declarations (87 at line
start plus `foaf:firstName`, `rdfs:comment`, `foaf:based_near`, `foaf:givenName`, whose type sits on the next
line), 26 individuals (13 `:WorkRole`, 7 `:DocumentType`, 3 `:DocumentStatus`, 3 `:Gender`), 5 `sh:NodeShape`
with 13 property shapes and 13 `sh:message` literals. Data: 20 entity files, 2 case files, 68 distinct cited
URLs of which 10 are `vip:removed`, 58 records in `archives.ttl` (58 = 68 - 10, consistent).

## Findings, ranked

### HIGH

**H1. `validateAll -Pfile=schema.ttl` silently redirects the data checks to the ontology.**
README.md:105, PROCITAJME.md:104, CLAUDE.md:42:
```
./gradlew :app:validateAll -Pfile=schema.ttl
```
`-P` sets a build-wide project property. `validateAll` depends on `validateData`, and both
`validateVeracity` (app/build.gradle.kts:118) and `archiveSources` (app/build.gradle.kts:133) read
`project.findProperty("file")` with default `"data"`. With `-Pfile=schema.ttl` the veracity validator and the
archiver run against `schema.ttl`, find no agents and no Documentation nodes, and pass vacuously, so the
"run every validation" command does not validate the data at all. The defaults already point at
`schema.ttl` and `data`, so the property is unnecessary.
Proposed wording (all three files):
```
# Run every validation (schema and data; defaults are schema.ttl and data/)
./gradlew :app:validateAll
```
Consider adding a sentence after the block: "Do not pass `-Pfile` to `validateAll` or `verify`: the property is
shared by the schema tools (default `schema.ttl`) and the data tools (default `data`)." A build-side fix
(separate `-Pschema`/`-Pdata` property names) would remove the trap; that is a code change and out of scope here.

**H2. "All 156 classes, properties and individuals ... carry `@en`, `@es` and `@sr`" is false on both counts.**
README.md:54-56 and PROCITAJME.md:54-56:
> All 156 classes, properties and individuals in the vocabulary and all SHACL messages carry `@en`, `@es` and `@sr` text
The vocabulary has 64 classes, 91 properties and 26 individuals (181 terms). Five terms inherited from the BIO
vocabulary have no Spanish text: `bio:IndividualEvent` label and comment (schema.ttl:166-167), and the comments
of `bio:Relationship` (519), `bio:participant` (566) and `bio:relationship` (576). The 13 SHACL messages do carry
all three languages. Either add the five Spanish strings to `schema.ttl` (schema change, out of scope) and drop the
hard-coded number, or state the truth. Proposed wording:
> **Every term has labels in English, Spanish and Serbian.** Every class, property and individual in the
> vocabulary and every SHACL message carries `@en`, `@es` and `@sr` text, so user interfaces and validation
> reports can be rendered in any of the three. (Serbian: "**Svaki pojam ima oznake na engleskom, španskom i
> srpskom.** Svaka klasa, svojstvo i individua u rečniku i svaka SHACL poruka nose tekst sa oznakama `@en`,
> `@es` i `@sr` ...")
Do not quote a count; `./gradlew :app:analyzeSchema` prints the live numbers.

**H3. "Key libraries" names four libraries the code never uses, and misattributes the reasoner and the SHACL engine.**
README.md:258-259, PROCITAJME.md:254-255:
> Key libraries: Apache Jena 4.10.0, OWL API 5.5.0, TopBraid SHACL 1.4.2, HermiT and Openllet reasoners, Kotlin 1.9.21.
`app/src/main/kotlin/vip/*.kt` imports only `org.apache.jena.*`, `com.google.gson.*` and the JDK. The OWL
consistency check uses Jena's built-in OWL reasoner (`ReasonerRegistry.getOWLReasoner()`,
OWLConsistencyChecker.kt:29); SHACL validation uses Jena SHACL (`org.apache.jena.shacl.ShaclValidator`,
SHACLValidator.kt:5-8), not TopBraid. OWL API, HermiT, Openllet and TopBraid SHACL are declared in
app/build.gradle.kts:15-22 but unused (a supply-chain and build-time cost worth a separate decision).
Proposed wording:
> Key libraries: Apache Jena 4.10.0 (RDF parsing, RDFS/OWL inference, SHACL), Gson 2.10.1 (Cytoscape JSON and
> the Wayback availability API), Kotlin 1.9.21. HTTP is done with the JDK's `HttpURLConnection`; no HTTP client
> library is used.
Same fix in CLAUDE.md:69-74 (see H4).

**H4. CLAUDE.md describes a project that no longer exists.**
CLAUDE.md is the AI assistant's map of the codebase and is wrong in most sections:
- Line 7 "VIP (RDF/OWL Validation and Analysis Tools) is a Kotlin-based validation toolkit for RDF/OWL data" – the
  project is a sourced civic dataset of connections in Serbia; the toolkit is its gate. Propose: "VIP ('Veze i
  poznanstva') is a civic dataset of connections between people and organisations in Serbia, kept as RDF
  (PoderVocab plus additions under `https://w3id.org/vip/vocab#`, instances under `https://w3id.org/vip/rs/`), with
  a Kotlin toolkit that validates syntax, OWL consistency, SHACL shapes, data layout, archive records and veracity
  of sources. See README.md for the rules; this file is the code map."
- Line 42 `validateAll -Pfile=schema.ttl` – see H1.
- Lines 53-59 "Key Components" lists six classes; missing `DataLayoutCheck` (five layout rules), `DataSet`/`loadDataSet`
  (directory-or-file loading), `SourceArchiver` (archive records, `--check` mode), `Wayback`/`WaybackClient`
  (availability API and Save Page Now), `Vocab` (term URIs), `TtlToCytoscape` (viewer export).
- Line 55 and 73 "HermiT/Openllet reasoners" and line 74 "**OkHttp3** - HTTP client for URL accessibility checks" –
  neither is used; see H3. Replace line 74 with "**JDK HttpURLConnection** – HEAD-then-GET reachability check with
  manual redirect following (VeracityValidation.kt), Wayback client (Wayback.kt)".
- Line 77 "defines PoderVocab vocabulary with 65 classes, 84 properties, and 18 SHACL shapes" – now 64 classes,
  91 properties, 26 individuals, 5 node shapes (18 only if the 13 property shapes are counted, which is what
  Jena's `numShapes()` reports; say so or drop the number).
- Line 78 "Multilingual support with English and Spanish labels/comments" – Serbian is the third language and
  the reason the project exists.
- Line 79 "Veracity validation requires entities to have URLs from at least 2 different domains" – add "and every
  URL reachable live or through an archived copy recorded in `archives.ttl` (documents with status `vip:removed`
  are ignored)".
- Lines 61-67 "Validator Pattern ... accepts file path" – data tools accept a directory tree or a single file;
  `SchemaMetrics` and `BestPracticesChecker` never exit 1 on findings (see M4).
- Missing entirely: `archives.ttl` is machine-maintained; `validateData` picks `checkArchives` when `CI=true`
  and `archiveSources` otherwise (app/build.gradle.kts:176-178); tests run with `workingDir` = repo root and load
  the real `schema.ttl` (app/build.gradle.kts:41-45); no network in tests (local `HttpServer` only).
Also `.claude/CLAUDE.md` is the untouched init template ("<!-- Add information about your project here -->");
fill it or delete it so it does not shadow the real file in a reader's mind.

### MEDIUM

**M1. The instance namespace is never stated and the example prefix is wrong.**
README.md:168 and 288, PROCITAJME.md:165 and 283: "`ex:Sinisa_Mali` lives in `sinisa-mali.ttl`". The data uses
`rs:` = `https://w3id.org/vip/rs/` (Vocab.kt:9, every data file line 3); `ex:` appears only in unit tests.
Neither README nor PROCITAJME ever gives the three project namespaces, yet both use `:PoliticalConnection`,
`vip:OwnershipConnection` and `rs:` terms. Proposed: replace `ex:Sinisa_Mali` with `rs:Sinisa_Mali` in all four
places, and add to "How facts are recorded" (after the list):
> Three namespaces appear in every file: `:` is PoderVocab (`http://poderopedia.com/vocab/`), `vip:` is this
> project's vocabulary additions (`https://w3id.org/vip/vocab#`) and `rs:` is the Serbian instance data
> (`https://w3id.org/vip/rs/`; other countries would get their own segment under `https://w3id.org/vip/`).

**M2. "Contributing data" cannot be followed end to end without reading the existing files.**
README.md:296-313 / PROCITAJME.md:291-308. Missing, in the order a contributor hits them:
1. The prefix block to copy (step 1 says "the same prefixes as the existing ones"). Propose a code block with
   the nine `@prefix` lines from data/entities/ana-brnabic.ttl:1-9.
2. How to pick a URI: people `rs:Firstname_Lastname` in Latin script without diacritics (`rs:Sinisa_Mali`),
   organisations by their common short name (`rs:Milenijum_Tim`, `rs:NALED`, `rs:SNS`), connections
   `rs:<Person>_<Counterpart>` in CamelCase (`rs:AnaBrnabic_NALED`). This is the convention the data follows;
   nothing enforces it except the file-name rule.
3. The file-name rule as a rule, not an example: "the URI's local name, lower-cased, `_` replaced by `-`, plus
   `.ttl`" (DataLayoutCheck.kt:69-71).
4. What a connection needs to pass SHACL and veracity: at least two participants through `bio:participant` or
   one of its sub-properties (`:personParticipant`/`:politicalParticipant` for `:PoliticalConnection`,
   `:hasStudent`/`:hasAcademicOrganization` for `:AlumniEducationalConnection`, `bio:participant` plus
   `:hasEconomicOrganizationParticipant` and `:hasWorkRole` for `:WorkConnection`, `vip:owner`/`vip:ownedCompany`
   for `vip:OwnershipConnection`), one `:hasDocumentation` with at least two Documentation nodes from two domains.
5. What an entity needs: `foaf:Person` requires `foaf:firstName` and `:firstLastName`; `foaf:Organization`
   requires `foaf:name` (schema.ttl:1135-1175); plus the two-domain documentation.
6. How to reference an existing entity: "use its `rs:` URI from the file name in `data/entities/`; do not add
   `a foaf:Person` or any other `rdf:type` for it in the case file, that is what the layout check calls a second
   home" (DataLayoutCheck.kt:12-13, 33-34).
7. Cloudflare or bot-blocked sources: the validator treats a 403 as unreachable and falls back to the archive, so a
   blocked page passes with a warning if a snapshot exists. If the archive itself cannot capture the page,
   `archiveSources` fails and `verify` cannot pass: cite a copy on another host or mark the document removed.
   This is nowhere in the docs.
8. Archive rate limiting: `WaybackClient` raises "the archive is rate limiting us (HTTP 429); wait a few minutes
   and retry" (Wayback.kt:83); the archiver keeps going, writes the records it did get to `archives.ttl`, and
   exits 1 (SourceArchiver.kt:113-119), so a re-run only does the leftovers. Say that.
9. Marking a dead source: set `:hasDocumentStatus vip:removed` and keep the node; it then counts neither as
   evidence (VeracityValidation.kt:60) nor as something to archive (SourceArchiver.kt:35), so the entity needs two
   other live documents. The data has the precedent at data/cases/milenijum-tim.ttl:70-71 (with a
   `rdfs:comment` saying why), which is worth quoting in the README. `vip:archived` exists as a status
   (schema.ttl:1125) but is used nowhere and never explained; either document when to use it or state that the
   archive copy is recorded in `archives.ttl` and the status stays `published`.
10. Running only the fast checks: `./gradlew :app:test :app:validateSchema :app:checkDataLayout :app:validateShacl`
    needs no network; `CI=true ./gradlew :app:validateData` reproduces CI (no Save Page Now, veracity still
    fetches every URL). Neither recipe nor the `CI=true` switch is documented (the behaviour is in
    app/build.gradle.kts:176-178).

**M3. The example veracity output is stale.**
README.md:136-147, PROCITAJME.md:134-145 show `http://example.org/...` subjects and
`has inaccessible URLs: https://broken.url (HTTP 404)`. The current report prints `Checking:`, `Vocabulary:`,
`Archive records:` and `Rules:` lines, an optional `Warnings (n):` block, and inaccessible URLs as
`https://broken.url (HTTP 404, no archived copy)` followed by `Total errors: n`
(VeracityValidator.kt:21-27, 39-55; VeracityValidation.kt:94-96). Proposed example:
```
=== Veracity Validation Report ===

Checking: data
Vocabulary: schema.ttl
Archive records: archives.ttl
Rules:
  1. Every agent and every connection must have documentation with at least 2 URLs from different domains
  2. Every URL must be reachable, live or through an archived copy

Warnings (1):
  1. https://w3id.org/vip/rs/Milenijum_Tim: https://www.millenniumteam.rs/ not reachable live (HTTP 403); using archived copy from 2025-07-11 at http://web.archive.org/web/20250711.../https://www.millenniumteam.rs/

✗ Validation FAILED

Errors found:
  1. https://w3id.org/vip/rs/Stojan_Vujko has only 1 URL
  2. https://w3id.org/vip/rs/SNS has URLs from only 1 domain(s): krik.rs
  3. https://w3id.org/vip/rs/NALED has inaccessible URLs: https://naled.rs/o-nama (HTTP 404, no archived copy)

Total errors: 3
```

**M4. "Every tool ... exits with status 0 on success or 1 on failure" and "loads the data (a directory ... or a single file)" overstate.**
README.md:108-110, PROCITAJME.md:107-109. Directory loading (`loadDataSet`) exists only in the data tools
(`validateShacl`, `validateVeracity`, `checkDataLayout`, `archiveSources`, `checkArchives`, `ttlToCytoscape`); the
four schema tools read exactly one file. `SchemaMetrics` has no failure condition and `BestPracticesChecker`
prints errors but always exits 0 (BestPracticesChecker.kt:256-292, no `exitProcess` after the report), so
`validateSchema` cannot go red on a best-practice error. Proposed wording:
> The data tools load a directory of Turtle files or a single file; the schema tools load one file. Every
> validator prints a report and exits with status 1 when its rule is broken (the metrics and best-practices
> reports are informational and always exit 0), so the checks can be chained in scripts and CI.
If the best-practices checker is meant to gate, that is a code fix; flag it.

**M5. "For every live cited URL without a record" misdescribes the archiver's filter.**
README.md:151, PROCITAJME.md:149. The archiver does not test reachability; it takes every `:hasDocumentURL` of a
Documentation node whose status is not `vip:removed` and that has no `vip:hasArchiveURL` in `archives.ttl`
(SourceArchiver.kt:27-41). Proposed: "For every cited URL whose document is not marked removed and that has no
record yet, it first asks ...". Also worth stating: the pause (6 s) is applied between new captures only, not
between availability lookups (SourceArchiver.kt:56).

**M6. "It runs, in dependency order: the unit tests, every schema check, every data check, the export ... and the build"**
README.md:239-243, PROCITAJME.md:235-239. Gradle orders only what is declared: `npmBuild` depends on
`:app:ttlToCytoscape` (web/build.gradle.kts:12) and `validateVeracity` must run after the archive task
(app/build.gradle.kts:123). Tests are not ordered before the validators and `org.gradle.parallel=true`
(gradle.properties) lets tasks run concurrently. Proposed: "It runs the unit tests, every schema check
(`:app:validateSchema`), every data check (`:app:validateData`: data layout, SHACL, archive records, then
veracity), the export of the data to Cytoscape JSON and, after it, the build of the demo viewer against that
fresh export. Tasks may run in parallel; the pipeline fails if any step fails."

**M7. Licence steward URL differs between README and LICENSE.md.**
README.md:273-274 and PROCITAJME.md:268-269 link `codeberg.org/degrowth-boundary-commons/dacl`; LICENSE.md:96
and :615 say `codeberg.org/degrowth-commons/dacl/`. One is wrong. Check which resolves and use it in all three
places. (Not verified here: no network use in this audit.)

**M8. Licence scope of `schema.ttl` is ambiguous about the shapes and the translations.**
README.md:278-280 says `schema.ttl` is PoderVocab's and "The project's own additions to the schema are offered
under DACL"; schema.ttl:1017-1021 says "Everything below this header is offered under the project's license".
Below that header sit not only the VIP terms but also the whole SHACL shapes block (schema.ttl:1131-1226), while
the Serbian labels and comments the project added to every PoderVocab term sit above it. README.md:68 calls the
file "PoderVocab 2.0 ontology: classes, properties and SHACL shapes", which reads as if the shapes were
Poderopedia's. Proposed README wording:
> Scope: `schema.ttl` is a revision of PoderVocab, the vocabulary created by Poderopedia. PoderVocab's classes
> and properties are not this project's work and are not relicensed here; they remain under whatever terms their
> authors chose. The project's own work in that file, the terms under the "VIP additions" heading, the SHACL
> shapes and the Serbian labels and comments, is offered under DACL.
And in schema.ttl:1019 make the header explicit: "Everything below this header (the VIP terms and the SHACL
shapes), and the Serbian labels and comments throughout, is offered under the project's license."

**M9. Gradle task descriptions and one KDoc lag the layout work.**
- app/build.gradle.kts:113 `validateVeracity`: "checks if items have URLs from multiple domains" – add "and that
  every URL is reachable live or through an archived copy in archives.ttl".
- app/build.gradle.kts:154 `checkDataLayout`: "Checks the one-file-per-case layout: every entity declared in
  exactly one file, nothing referenced that no file declares" – propose "Checks the data layout: entities only
  under entities/ (one per file, named after the URI), connections only under cases/, one home file per entity,
  every reference declared somewhere, no Turtle files elsewhere".
- app/build.gradle.kts:174 `validateData`: "(SHACL shapes, archive records, veracity of sources)" – add
  "data layout," first.
- app/src/main/kotlin/vip/DataSet.kt:9 "Instance data as contributed: one Turtle file per case" – propose
  "Instance data as contributed: one Turtle file per entity under entities/ and one per case under cases/, all
  loaded into one [merged] model."

**M10. "Three conventions apply to every record" introduces four bullets.**
README.md:35 ("Three conventions"), PROCITAJME.md:35 ("Tri pravila"). Change to "Four conventions" / "Četiri
pravila", or move the labels bullet (which is about the vocabulary, not about records) to "The vocabulary".

### LOW

**L1. Redirect limit and the error-page heuristic.** README.md:122-125 / PROCITAJME.md:121-123 "follows up to five
redirects". `MAX_REDIRECTS = 5` bounds the number of requests, so at most four redirects are followed
(VeracityValidation.kt:160, 178-200). The checker also treats a redirect whose target path ends in `404`,
`not-found`/`not_found` or `error` as unreachable (VeracityValidation.kt:163, 195); the data relies on this
(data/cases/milenijum-tim.ttl:71) but the README does not mention it. Propose "follows up to four redirects,
including relative and cross-protocol ones, and treats a redirect to an error page (`/404`, `/not-found`,
`/error`) as a dead link".

**L2. OWL consistency checker overclaims.** README.md:188-189 "unsatisfiable classes". The code lists classes with
no individuals as "Potentially empty class" and checks for a class being its own superclass
(OWLConsistencyChecker.kt:44-64, 87-109); it does no satisfiability reasoning. Propose "logical
inconsistencies reported by Jena's OWL reasoner, classes with no individuals, and self-referential hierarchies".

**L3. Organisation kinds list is incomplete.** README.md:204-205 lists eight kinds; the schema also has
`:ProgrammaticOrganization` (used by `rs:NALED`) and `:RestrictedAccessOrganization`, and "governmental" is
`:GovernmentOrganization`. Add "programmatic" or say "among others".

**L4. Undocumented task properties.** `validateVeracity` also takes `-Pschema` and `-Parchives`
(app/build.gradle.kts:119-120); `archiveSources`/`checkArchives` take `-Pfile` and `-Parchives`
(:133-134, :146-147); `checkDataLayout` takes `-Pschema` (:160). One line under Quick start would do.

**L5. Leftover "test-data" name.** README.md:219 and app/build.gradle.kts:196 default the export to
`web/public/test-data-cytoscape.json` and web/src/App.svelte:24 fetches it. It is the real dataset now. A rename
is a code change; until then the README could say "(the file name is historical)". No other `test-data.ttl`
reference survives in the docs.

**L6. PROCITAJME wording.** Substance mirrors README throughout (every section, every number, every command,
including the H1 and H2 defects). Minor: "build" is left untranslated at lines 45, 158, 304, 307 while the
heading at 227 says "izgradnja"; pick one. "fact-checking" at lines 30 and 300 is untranslated while the schema's
own Serbian label says "provera činjenica" (schema.ttl:1105); propose "medija za proveru činjenica" and "arhive
redakcija za proveru činjenica". The addition "(na engleskom)" at 261 is a good, deliberate divergence.

**L7. README.md:130** "An entity with no documentation at all is reported" – it is an error that fails the build
(VeracityValidation.kt:71-73). Say "reported as an error".

**L8. Case-file header points to a section by title.** data/cases/*.ttl:18 "videti README.md / PROCITAJME.md,
odeljak 'Doprinos podacima'" – the README section is titled "Contributing data"; fine for Serbian readers, but
give both titles or none.

## What is correct and good

- The veracity rules in README.md:116-128 match the code: two URLs from two domains after stripping `www.`
  (VeracityValidation.kt:36-43, 77-84), removed documents ignored (:60), HEAD then GET with a descriptive
  User-Agent and 10 s timeouts (:159-161, 169-176), fallback first to the snapshot recorded in `archives.ttl`
  and then to the Wayback availability API (:92), archive-only pages as warnings with the snapshot date (:94),
  nothing anywhere as an error (:96). The HTTP check and archive lookup are injected (:31-34), as stated.
- The data layout paragraph (README.md:164-172) covers all five rules in DataLayoutCheck.kt:10-21: single home,
  no dangling references, entities/ vs cases/, one entity per file named after it, nothing elsewhere; and it
  correctly says vocabulary terms are recognised through `schema.ttl`.
- Archive semantics are right: `archives.ttl` keyed by document URL, machine-maintained, lookup before Save Page
  Now, pause between captures, `checkArchives` never contacts the archive, `verify` picks archive vs check by
  `CI` (app/build.gradle.kts:176-178; the GitHub runner sets `CI=true`).
- All task names and the properties shown in Quick start exist with those defaults (except the `validateAll`
  property, H1). `verify` and the CI workflow are described exactly (`.github/workflows/verify.yml` is a thin
  wrapper; root build.gradle.kts:6-17).
- The five SHACL shapes named in README.md:180-181 are the five in schema.ttl; RDFS inference before validation
  is described exactly as SHACLValidator.kt:40-48 does it.
- The licence section states DACL's purpose restriction, share-alike, non-OSI status and the SPDX id, and that
  `schema.ttl` is not relicensed (README.md:268-280); LICENSE.md is the full text with terms (628 lines).
- docs/ui-design.md is presented as a roadmap item in both READMEs ("Roadmap: graph explorer" / "Plan:
  istraživač grafa") and the document itself opens with "Status: design, not yet built". No README text claims
  the explorer exists.
- Entity and case headers are consistent across all 22 data files, bilingual, and point to the contributing
  section. Case headers say what the case covers and that entities live in `data/entities/`.
- The "VIP additions" block comments explain each addition (ownership, kumstvo, governance roles, document types,
  archive properties, statuses) and the design choice of keying archive records by the document URL
  (schema.ttl:1096-1097); README.md:58-62 lists the same groups.
- KDoc in VeracityValidation.kt, SourceArchiver.kt, Wayback.kt, DataLayoutCheck.kt, TtlToCytoscape.kt and Vocab.kt
  is current and matches behaviour; only DataSet.kt:9 is stale (M9).
- `:app:test` genuinely needs no network: UrlCheckerTest and WaybackClientTest use a local `HttpServer`.
- PROCITAJME.md is a faithful, complete mirror of README.md; no section is missing or reordered.
