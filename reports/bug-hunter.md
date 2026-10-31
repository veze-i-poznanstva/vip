# Bug hunter report

Scope: VeracityValidation/Validator, Wayback, SourceArchiver, DataSet/DataLayoutCheck,
TtlToCytoscape, SHACLValidator, Vocab, the Gradle build and the CI workflow, plus the existing
tests. Read-only review; nothing was built or run. Library behaviour that a finding depends on
was confirmed by reading the Jena 4.10.0 bytecode in the Gradle cache where noted.

Severity reflects how wrong the result is and how likely the input is in this dataset.
Each finding ends with the characterization test to write first (Baby Steps: it should fail
against current code, then fix minimally).

---

## 1. `WaybackClient.lookup` has no same-page guard, so the guard in `save` is bypassed on the next run — medium/high

`app/src/main/kotlin/vip/Wayback.kt:40-53` (lookup) vs `:64-69` (save).

`save` refuses a snapshot whose captured page differs from the requested one (a site that
redirects a dead article to its 404 page). But the archive still *performs* the capture before
we refuse it. On the next run of `archiveSources`, `lookup` asks the availability API, gets back
exactly that capture, and `record()` writes it without any check. The validator then treats the
error-page snapshot as evidence (warning, not error).

Evidence in the repository that `lookup` records what `save` would refuse:
`archives.ttl` keys `https://pretraga.pkspartner.rs/sr/kompanija/0e55cb2461a3e2c0/roberts-millennium-doo`
to `https://web.archive.org/web/20261030133123/https://pretraga.cubepartner.rs/...` — a different
host. `normalise()` would not equate these, so `save` cannot have produced this record; `lookup` did.

Scenario: article `https://gone.example/news/a/` → 302 → `/404.php` (200).
Run 1: `save` → ArchiveException "captured a different page". Run 2: `lookup` → snapshot of
`/404.php` → recorded → `validateVeracity` passes with a warning "using archived copy".

Tests:
- `WaybackClientTest."lookup refuses a snapshot of a different page"`: availability stub answers
  `closest.url = ".../web/20261030133033/https://gone.example/404.php"` for
  `https://gone.example/news/article/`; expect `ArchiveException` (or null). Currently returns the snapshot.
- `SourceArchiverTest."a looked-up snapshot of a different page is not recorded"`: FakeWayback
  whose `lookup` returns a `/404.php` snapshot; expect `failed` to contain the URL and no record.

Fix direction: extract `samePage` into a shared check and apply it to `lookup` results too
(the availability API's `closest.url` embeds the captured page).

## 2. Layout rules 3/4 treat every typed non-connection as a person/organisation — medium

`app/src/main/kotlin/vip/DataLayoutCheck.kt:81-86`.

`declared.partition { isConnection }` puts every typed URI subject that is not a `:Connection`
into `entities`. A URI-named `:FinancialInformation`, `:NumericDatum`, `:NewsEvent` or a
URI-named `:Documentation` node is then:
- in `entities/acme.ttl`: counted as a second entity → "declares 2 entities; one entity per file"
  (rule 4 false positive), or mis-named if it is the only typed subject;
- in `cases/x.ttl`: reported as "people and organisations belong in entities/".

The spec says entities are people and organisations (foaf:Agent). The partition should be
agent / connection / other, with "other" exempt from rules 3 and 4.

Test: `DataLayoutCheckTest."a URI-named financial record in an entity file is not a second entity"`:
`entities/acme.ttl` = `ex:Acme a :Company . ex:Acme_2024 a :FinancialInformation ; :hasFinancialInformationYear "2024"^^xsd:gYear ; :hasValue 1.0 .`
→ expect `emptyList()`. Currently one problem.

## 3. A Turtle syntax error in one of 22 files does not say which file — medium

`app/src/main/kotlin/vip/DataSet.kt:31`; mains in `DataLayoutCheck.kt:111` and `SourceArchiver.kt:190` have no catch.

`RDFDataMgr.loadModel` throws `RiotException` whose message is formatted by
`SysRIOT.fmtMessage` as `"[line: %d, col: %d] %s"` (confirmed in jena-arq bytecode) — no file
name. `loadDataSet` does not wrap it, so a contributor sees "[line: 23, col: 7] Broken IRI"
with no hint which of the data files is broken. In `DataLayoutCheck`/`SourceArchiver` it surfaces
as an uncaught stack trace.

Test: `DataSetTest."a syntax error names the file it is in"`: write `good.ttl` and
`bad.ttl` (e.g. `ex:A a foaf:Person` with no terminating dot plus `ex:B a`); 
`assertFailsWith<Exception> { loadDataSet(dir) }` and assert `message` contains `"bad.ttl"`.
Currently fails.

## 4. `extractDomain` is case-sensitive — medium

`app/src/main/kotlin/vip/VeracityValidation.kt:38-39`.

`java.net.URL.host` preserves case and the `www.` test is case-sensitive. Host names are
case-insensitive, so `https://Istinomer.rs/a` + `https://istinomer.rs/b`, or
`https://WWW.istinomer.rs/a` + `https://istinomer.rs/b`, count as two domains and satisfy rule 1
with a single site. Browsers lower-case hosts, so this is more an integrity hole than an
everyday mistake, but it is the sourcing rule.

Test: `VeracityValidationTest.extractDomain`: add
`assertEquals("example.com", validator.extractDomain("https://WWW.Example.COM/p"))`. Currently `"WWW.Example.COM"`.

## 5. `DataSet.relativePath`/`folderOf` break on Windows path separators — medium/low

`app/src/main/kotlin/vip/DataSet.kt:16,19,28`.

`file.relativeTo(root).path` uses the platform separator; `substringBefore('/', "")` then
returns `""` for `entities\ana.ttl`, so on Windows every file is reported "outside the data
layout" and rule 3/4 never run. Sorting in `loadDataSet` also uses the platform path (harmless).
Not verified on Windows; by inspection.

Test (portable pin): `DataSetTest."folderOf is the first path segment regardless of separator"`
using `invariantSeparatorsPath` in the implementation; on Linux the current test suite cannot
expose it, so this is a fix-by-inspection with a regression test.

## 6. Redirect limit is off by one against the documented behaviour — low/medium

`VeracityValidation.kt:160,180-200`; README says "follows up to five redirects".

`repeat(MAX_REDIRECTS)` issues five *requests*; the fifth response being a redirect ends the
loop with "too many redirects", so at most four redirects are followed.

Test: `UrlCheckerTest."a chain of five redirects is followed"`: contexts `/r1`→`/r2`→…→`/r5`→`/ok`;
expect `ok == true`. Currently "too many redirects". (Decide whether to fix the code or the README.)

## 7. `recordedArchive` can crash the whole run on an odd record — low/medium

`VeracityValidation.kt:109-115` and the `archive.date` call at `:94`.

- `archivedOn` whose object is a resource → `asLiteral()` throws `LiteralRequiredException`.
- `archivedOn` lexical form `"2026-10"` (gYearMonth) or `"2026"` → `replace("-","").padEnd(14,'0')`
  gives `"20261000000000"` → `ArchiveSnapshot.date` → `DateTimeParseException` thrown while
  building the warning string, aborting validation of *every* entity (the catch in main prints a
  stack trace and exits 1 without a report).
- No timestamp anywhere → `"00000101000000"` → the warning reports a snapshot "from 0000-01-01".

`archives.ttl` is machine-written today, but `recordedArchive` reads the merged model, so a
hand-written `vip:archivedOn` in a data file hits this too.

Test: `VeracityValidationTest."a malformed archive record is reported for that entity only"`:
record `vip:hasArchiveURL <https://archive.ph/abc>` (no timestamp) and `vip:archivedOn "2026-10"`
on a blocked URL; expect a result (error or warning) for the entity, not an exception. Currently throws.

## 8. A URL with a fragment can never be saved — low/medium

`Wayback.kt:56,63-69,79-80`.

`HttpURLConnection` never sends the fragment, so the archive captures `https://x/page` and
answers with that address; `normalise()` keeps `#frag` on our side, so `samePage` fails and
`save` throws "captured a different page" on every run. (`lookup` then usually finds it on
the next run — see finding 1 — so the symptom is one spurious failure per fragment URL.)

Test: `WaybackClientTest."save accepts a snapshot of a URL that carries a fragment"`:
`client().save("https://site.example/page#top")` → expect timestamp `20261030090000`. Currently throws.

## 9. `normalise` uses `URLDecoder`, which is for form data, not URLs — low

`Wayback.kt:79`.

`URLDecoder.decode` turns `+` into a space (harmless for equality since both sides are
decoded) and throws `IllegalArgumentException` on any `%` not followed by two hex digits
(`.../100%-uspeh`, `.../a%zz`). `archive()` catches `Exception`, so the URL is marked failed
with a JDK message instead of an `ArchiveException`; `save` called directly propagates the
wrong exception type.

Test: `WaybackClientTest."save tolerates a literal percent sign in the page address"`:
`client().save("https://site.example/100%")` → expect a snapshot or `ArchiveException`; currently `IllegalArgumentException`.

## 10. Non-ASCII hosts and paths go on the wire unencoded — low (unverified live)

`VeracityValidation.kt:179-181` (and `Wayback.kt:56`).

`java.net.URL` does not IDNA-encode the host or percent-encode the path. For an IDN host
(`https://www.влада.срб/...`, plausible for Serbian sources) `InetAddress.getByName` gets the
Unicode name and fails → every such URL is "Error: ..." live and falls back to the archive.
For a Unicode path (`/wiki/Синиша_Мали`, present in the data) the request line is written
with the JVM's `file.encoding`; UTF-8 JVMs send raw UTF-8 bytes (which Wikipedia tolerates),
a POSIX-locale JVM sends `?`. Safer: build the request from `URI(url).toASCIIString()` /
`IDN.toASCII(host)`.

Test: once an `asciiForm(url)` helper exists, pin
`asciiForm("https://sr.wikipedia.org/wiki/Синиша_Мали") == "https://sr.wikipedia.org/wiki/%D0%A1%D0%B8%D0%BD%D0%B8%D1%88%D0%B0_%D0%9C%D0%B0%D0%BB%D0%B8"`
and `asciiForm("https://пример.срб/") startsWith "https://xn--"`.

## 11. Layout rules 1 and 2 report `file.name`, which is ambiguous in this dataset — low

`DataLayoutCheck.kt:63,76`.

`entities/milenijum-tim.ttl` and `cases/milenijum-tim.ttl` both exist. A duplicate home across
them reads "declared in 2 files (milenijum-tim.ttl, milenijum-tim.ttl)". Rules 3-5 already use
`relativePath`.

Test: `DataLayoutCheckTest."duplicate home reports the folder of each file"`: declare `ex:X`
in `entities/x.ttl` and `cases/x.ttl`; assert the message contains `"entities/x.ttl"` and `"cases/x.ttl"`.

## 12. `createNodeId` can collide — low

`TtlToCytoscape.kt:180-193`.

`Ana_B`, `Ana-B`, `ana_b` and `Ana.B` all become `ana_b`; `agentIds` is a set of ids, so two
agents share one node id (Cytoscape rejects the second) and participants are matched by id.
Pure-Cyrillic local names all become underscores and fall to the hash, which is fine; mixed
names (`Siniša` → `sini_a`) can collide too.

Test: `TtlToCytoscapeTest."distinct agents get distinct node ids"`: `ex:Ana_B a foaf:Person . ex:Ana-B a foaf:Person .`
→ `nodes().map { id }.distinct().size == 2`. Currently 1 distinct id.

## 13. `dedupe` merges distinct connections, not just symmetric duplicates — low

`TtlToCytoscape.kt:150-151`.

Two `:WorkConnection`s between the same pair with the same role label (different dates) become
one edge. Intended for `hasBusinessPartner` both ways; a side effect for repeated roles.
Decide and pin either way.

Test: two `_:w1`, `_:w2 a :WorkConnection ; bio:participant ex:A ; :hasEconomicOrganizationParticipant ex:Org ; :hasWorkRole vip:founder`
with different `:connectionStartDate` → assert expected edge count (currently 1).

## 14. `labelOf` is nondeterministic with several names and throws on a resource object — low

`TtlToCytoscape.kt:170-172`.

`getProperty` returns an arbitrary statement, so `foaf:name "Milenijum tim"@sr, "Millennium Team"@en`
yields either label; the JSON is committed (`web/public/test-data-cytoscape.json`) and
regenerated by `verify`, so this shows up as churn. `asLiteral()` throws if `foaf:name` is a URI.

Test: node with two language-tagged names → assert the `@sr` (or a documented preference) is chosen.

## 15. CI detection is `CI == "true"` only — low

`app/build.gradle.kts:177`.

GitHub Actions, GitLab, Circle, Travis set `CI=true`; Jenkins sets nothing, some set `CI=1`.
On those, `validateData` runs `archiveSources` (network, writes `archives.ttl` that is never
committed) instead of `checkArchives`. Consider `CI` set and not in `{"", "false", "0"}`.
No unit test; document the semantics or pin in a Gradle TestKit test if one is ever added.

## 16. `SourceArchiver` housekeeping — low

`SourceArchiver.kt:162,165-168,192-193,210`.

- `archivesFile.outputStream()` is never closed (Jena flushes, so the data lands; still a leak).
- Prefix `""→PODER` is registered but unused; the `vip:` prefix only exists because the loaded
  file had it, so a fresh `archives.ttl` is written with full IRIs.
- `Outcome.alreadyArchived` counts every record including stale ones for URLs no longer cited,
  and is never printed.
- After the first 429, the loop keeps calling the archive for every remaining URL (each fails
  with 429, no backoff). Test: FakeWayback throws rate-limit on the first save → assert the
  remaining URLs are not attempted (if that is the desired behaviour).

## 17. Literal `:hasDocumentURL` values vanish silently — low

`VeracityValidation.kt:63`, `SourceArchiver.kt:133`.

`:hasDocumentURL "https://..."` (a string, easy mistake) is filtered out; the entity is reported
as "has no documentation" although it has two Documentation nodes, and the archiver never
archives it. SHACL (`sh:class foaf:Document`) also flags it, but the veracity message misleads.

Test: entity with two literal URLs → expect an error naming the literal ("is not an IRI"), not "has no documentation".

## 18. `VeracityValidator` main ignores a missing archives file silently — low

`VeracityValidator.kt:233,236`.

`takeIf { it.exists() }` means `-Parchives=typo.ttl` runs with no records, so every blocked URL
goes to the live Wayback API (slow, rate-limited) and nothing says why. Also the schema
`FileInputStream` is never closed and an unknown extension gives a null `Lang` → RiotException.
Suggest printing a warning when the archives path does not exist.

---

## Checked and found correct

- Duplicate URLs across Documentation nodes are counted once (`distinct()`); a Documentation
  node with several URLs contributes all of them; removed documents are excluded before counting
  and before archiving, by the same predicate in both places; a URL removed in one node and
  published in another is still evidence/archived (published wins) — consistent.
- http vs https of one host, ports and userinfo collapse to one domain; `www.` stripping works;
  subdomains are *separate* domains by design (`sr.wikipedia.org` + `en.wikipedia.org` satisfy
  rule 1 — confirm this is intended, it is not a bug against the stated rule).
- Query strings with `&` survive the archive-URL regex, the lookup query encoding (tested) and
  `/save/`; fragments are the only problem (finding 8).
- HEAD→GET fallback, relative/absolute/protocol-relative redirects, cross-scheme redirects,
  redirect-to-error-page detection and the loop guard behave as the tests say; a redirect to a
  non-HTTP scheme ends as "Error: ClassCastException…" (ugly message, correct verdict).
- Both `http://` and `https://web.archive.org` archive URLs are accepted; the timestamp regex
  and `archivedOn` fallback agree for every record in `archives.ttl`.
- Wayback JSON without `closest` → null (tested); 429 and non-200 handled; save timeout separate.
- `entitiesEntailedBy` relies on `rdfs:range foaf:Agent`, so a mistyped participant URI is
  reported as "has no documentation" (useful), Documentation nodes and vocabulary terms are not
  entities (tested), blank-node connections are described via label. Jena `containsResource`
  checks subject, predicate and object positions (confirmed in bytecode).
- Layout rule 2 excludes `rdf:type`, `hasDocumentURL`, `hasArchiveURL` objects and schema terms;
  typos in role/status/type individuals are reported as dangling; blank nodes are ignored.
- `entityFileName` lower-cases with `Locale.ROOT` semantics and handles `_`; URIs with digits
  and mixed case are fine (`ekonomski-fakultet-university-of-belgrade.ttl`).
- Single-file datasets skip folder rules deliberately (`root == null`).
- Gradle: `validateVeracity.mustRunAfter(archiveSources, checkArchives)`; `checkArchives` never
  touches the network; `CI=true` is what GitHub Actions sets; `gradlew` is committed `100755`;
  `:web:npmBuild` depends on `:app:ttlToCytoscape`.
- Dataset today: 58 live cited URLs, 58 records, no stale records, 10 removed documents, every
  entity and connection has ≥2 domains (www-insensitive). CRLF is legal Turtle; no file has a
  BOM (Jena BOM handling not verified — a one-line test writing `﻿@prefix…` would pin it).
- Connections with one participant produce no edge and no error in the export; SHACL
  `ConnectionShape` (`sh:minCount 2`) is the gate, and RDFS inference is needed for
  `personParticipant` etc. to count as `bio:participant` — but note that inference also makes
  `sh:class foaf:Agent` on participants unfalsifiable (every participant is inferred an Agent),
  which is why layout rule 2 exists. Known and documented in code.
