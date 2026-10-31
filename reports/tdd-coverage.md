# TDD coverage review — veracity / archiving / layout / graph export

Read-only review of `app/src/main/kotlin/vip/` (VeracityValidation, VeracityValidator, Wayback, SourceArchiver, DataSet, DataLayoutCheck, TtlToCytoscape, SHACLValidator, Vocab) against `app/src/test/kotlin/vip/`. Nothing was built or run. Off-limits (graph explorer, red-flag rules, web/) not reviewed.

Legend: **P0** = a gap that can let bad data pass validation; **P1** = can wrongly fail or crash on good data; **P2** = untested branch / surviving mutant, correctness-relevant; **P3** = cosmetic or output-shape only. "Mutant" = a plausible code change the current suite would not detect.

Overall: the suite is strong on the happy paths and the main rule boundaries (1 vs 2 URLs, 1 vs 2 domains, live vs archived vs dead). Rule-combination paths, data-shape fault paths (literals where URIs are expected, bad dates), the error-page regex, Save Page Now failure modes, `Outcome.alreadyArchived`, the schema-less layout path, and every `main()` are untested.

---

## P0 — gaps that can produce a false PASS

### P0.1 `checkUrlOverHttp`: a dead article redirected to the homepage counts as reachable
`VeracityValidation.kt:191-197`. Only paths matching `ERROR_PAGE_PATH` are refused; a 302 to `/` (the most common soft-404 on Serbian news sites) is followed and the 200 homepage makes the URL "accessible". This is a known heuristic limit, but it is undocumented and unpinned.
- Test (characterisation, so the limit is at least visible): `` `a redirect to the site root is currently accepted as reachable` `` — context `/vanished` → `Location: /`; assert `ok == true`. If the team decides the opposite, flip it and implement "redirect to `/` from a non-root URL is not reachable".

### P0.2 `WaybackClient.lookup` ignores the snapshot's `status`
`Wayback.kt:47-48`. `closest.status` (e.g. `"404"`, `"500"`) is never read. If the availability API returns a capture of an error page, the validator issues a warning and passes the URL. (Availability API usually filters to 2xx/3xx, so confirm with spec before changing; at minimum pin it.)
- Test: `` `lookup treats a snapshot whose status is not 2xx as no snapshot` `` — availability body with `"status":"404"`; assert `lookup(...) == null` (or pin current behaviour: `assertNotNull`, and record the decision).

### P0.3 `DataLayoutCheck`: a misspelt class is never reported as dangling
`DataLayoutCheck.kt:41` excludes `rdf:type` from the dangling check. `ex:Ana a foaf:Persno .` in `entities/` yields no problem at all (not a Connection, so it is an "entity"; name matches). SHACL cannot catch it either (no target class). Nothing in the pipeline will ever flag the typo.
- Test: `` `an entity typed with a class the vocabulary does not define is reported` `` — `write("entities/ana.ttl", "ex:Ana a foaf:Persno .")`; assert one problem containing `foaf/0.1/Persno`. Expected to FAIL today; decide whether `rdf:type` objects should be checked against `schema.containsResource` (they should, unless the spec says otherwise).

### P0.4 `recordedArchive` is preferred over a live lookup — no test proves it
`VeracityValidation.kt:92`. Mutant: swap to `lookupArchiveSafely(url) ?: recordedArchive(...)` — every current test passes (no test has both a record and a non-null lookup). The consequence is CI silently talking to archive.org and accepting whatever it returns instead of the committed record.
- Test: `` `a recorded archive snapshot is used without consulting the live archive` `` — record `hasArchiveURL` for the blocked URL; `counting` lookup; assert `lookups == 0` and the warning carries the recorded timestamp.

### P0.5 `VeracityValidator.main`: a missing archives file is silently ignored
`VeracityValidator.kt:32` `takeIf { it.exists() }`. A typo in `-Parchives=` drops every record, and P0.4 then decides correctness via the network. Untestable as written (see P3 refactor); after refactor assert that a non-existent archives path is reported (or at least printed as "no archive records").

### P0.6 `SHACLValidator` has no tests at all
`SHACLValidator.kt:23-48`. The RDFS-inference branch (`createRDFSModel(shapesModel, dataModel)`) is the reason `sh:class foaf:Agent` passes for a `foaf:Person`; mutant "validate `dataModel.graph` directly" turns every connection into a violation (false fail), and mutant "skip `report.conforms()` exit" is a false pass. Extract `fun validateShacl(shapes: Model, data: Model): ValidationReport` and test:
- `` `a person participant satisfies sh:class foaf:Agent through the vocabulary` `` — `:Connection` with two `foaf:Person` participants and documentation; assert `conforms()`.
- `` `a connection with one participant violates ConnectionShape` `` — assert `!conforms()` and an entry whose `resultPath()` is `bio:participant`.
- `` `a documentation node without a URL violates DocumentationShape` ``.

---

## P1 — gaps that can produce a false FAIL or a crash

### P1.1 Bad `vip:archivedOn` crashes the whole validation
`VeracityValidation.kt:111-115` + `Wayback.kt:11`. With an archive URL lacking `/web/<14 digits>/` and `archivedOn "2026-10"` → `"20261000000000"` → `LocalDate.parse("20261000")` throws `DateTimeParseException` when the warning string is built (line 94) → `validate` aborts → `main` exits 1 with "Error:" for the entire dataset. Also `archivedOn` as a URI (`asLiteral()` throws) and `archivedOn` absent → date `0000-01-01` is printed as if real.
- Tests: `` `an archive record with an unparseable date is reported for that URL, not a crash` `` (assert `validateItem` returns, errors/warnings name the URL); `` `an archive record with a non-Wayback URL takes its date from archivedOn` `` (archive URL `https://archive.ph/abc`, `archivedOn "2026-10-20"`; assert warning contains `2026-10-20`) — this also kills "swap `?:` fallback order"; `` `an archive record with neither timestamp nor date gets no invented date` `` — pins or fixes the `00000101000000` sentinel.

### P1.2 `hasDocumentURL` / `hasArchiveURL` given as a string literal
`VeracityValidation.kt:63`, `SourceArchiver.kt:36`, `VeracityValidation.kt:110`. A literal URL is silently dropped → "has no documentation" / never archived. Mutant "remove `filter { isURIResource }`" → `asResource()` throws instead; neither behaviour is pinned.
- Tests: `` `validateItem ignores a document URL written as a string literal` `` (assert error contains `only 1 URL`); `` `unarchivedUrls ignores a document URL written as a string literal` ``; `` `a recorded archive URL that is a literal counts as no record` `` (assert the lookup is consulted).

### P1.3 Redirect without `Location`
`VeracityValidation.kt:193`. Mutant "drop `location != null`" → `URL(url, null)` throws → status becomes `Error: ...` instead of `HTTP 302`. Untested.
- Test: `` `a redirect without a Location header is not accessible and reports the code` `` — context answering 302 with no header; assert `status == "HTTP 302"`.

### P1.4 Malformed / non-HTTP URL
`VeracityValidation.kt:170-175`, `extractDomain` line 40-41. `"not a url"`, `"mailto:x@y"` (ClassCastException on `as HttpURLConnection`), `"https://"` (empty host) are untested. Mutant: catch branch returns `""` instead of `urlString`.
- Tests: `` `extractDomain returns the input unchanged when it is not a URL` ``; `` `a malformed URL is not accessible and reports the error` `` (`checkUrlOverHttp("not a url")` → `(false, "Error: ...")`).

### P1.5 `samePage` normalisation untested for the two cases this dataset actually has
`Wayback.kt:76-80`. Serbian Wikipedia URLs are Cyrillic (`/wiki/Синиша_Мали`), SPN returns them percent-encoded; YouTube URLs carry a query string; many sites add a trailing slash. Mutants "drop `URLDecoder.decode`" / "drop `trimEnd('/')`" survive and would make the archiver throw "captured a different page" for every Cyrillic source.
- Tests: `` `save accepts a snapshot whose address is percent-encoded` `` (`save("https://sr.example/wiki/Синиша_Мали")`, server answers `Content-Location: /web/.../https://sr.example/wiki/%D0%A1%D0%B8...`); `` `save accepts a snapshot that differs only by a trailing slash` ``; `` `save keeps the query string of the page` `` (`?v=x&t=5s`; assert `snapshot.url` contains `t=5s`). Also `` `a URL with a stray percent sign fails as an ArchiveException, not IllegalArgumentException` `` (decode throws) — decide, then pin.

### P1.6 `DataLayoutCheck` with `schema == null`
`DataLayoutCheck.kt:35`. No test calls `checkDataLayout(ds, null)`. Mutant "return emptyList()" survives.
- Test: `` `without a schema only duplicate declarations are checked` `` — duplicate + misplaced entity + dangling ref; assert exactly the one "home" problem.

### P1.7 Single-file dataset in the layout check
`DataLayoutCheck.kt:55` `if (dataSet.root == null) emptyList()`. Untested; mutant "always report outside layout" survives, breaking `checkDataLayout -Pdata=some.ttl`.
- Test: `` `a single turtle file is not outside the layout` `` — `checkDataLayout(loadDataSet(file), schema)`; assert no problem mentions "outside".

### P1.8 `hasArchiveURL` in data is not a dangling reference
`DataLayoutCheck.kt:37`. Mutant "drop `HAS_ARCHIVE_URL` from `referenceProperties`" survives (no test puts an archive URL into a data file). Relevant as soon as someone runs the check over `archives.ttl` or inlines a record.
- Test: `` `archive URLs are references, not missing entities` `` — documentation node with `vip:hasArchiveURL <http://web.archive.org/...>`; assert `problems()` empty.

### P1.9 Malformed Turtle / missing path in `loadDataSet`
`DataSet.kt:26-31`. A syntax error propagates as `RiotException` whose message does not name the file; a non-existent path falls into `listOf(path)` and fails with `RiotNotFoundException`. Both untested.
- Tests: `` `a turtle syntax error names the offending file` `` (write `garbage`, `assertFailsWith<Exception>`, assert message contains file name — will likely FAIL today; wrap and rethrow with the path); `` `a path that does not exist is an error` ``.

---

## P2 — untested branches and surviving mutants (correctness)

### VeracityValidation
| Line | Mutant / scenario left alive | Test to add |
|---|---|---|
| 80-84, 99-101 | Rule 1 and Rule 2 both failing — mutant "return after Rule 1 if errors non-empty" | `` `a single dead URL is reported for both rules` `` — 1 URL, blocked, no archive; assert `errors.size == 2`, one contains `only 1 URL`, one `inaccessible URLs` |
| 89-98 | Mixed outcome: one archived (warning), one dead (error) in the same item | `` `one archived and one dead URL give one warning and one error` `` — assert `!isValid`, `warnings.size == 1`, `errors.size == 1` |
| 96, 100 | Several inaccessible URLs — mutant "report only first" / `joinToString` separator | `` `every inaccessible URL is listed in the error` `` — 2 blocked URLs, assert error contains both and `", "` |
| 65 | `.distinct()` — same URL in two documentation nodes | `` `the same URL cited twice counts once` `` — assert error contains `only 1 URL` (mutant gives "from only 1 domain") |
| 59-61 | Documentation node with no `hasDocumentURL`; explicit status `vip:published` / `vip:archived` kept — mutant `filterNot { hasProperty(hasStatus) }` survives inside this class (only killed incidentally by `SourceArchiverTest`) | `` `a documentation node without a URL is no evidence` ``; `` `documents with status published or archived remain evidence` `` |
| 129, 132 | Empty model → `isValid == true`, no errors; mutant `isValid = entities.isNotEmpty() && ...` | `` `an empty dataset is valid and reports nothing` `` (pin, with and without schema) |
| 153-154 | `describe` blank node with `rdfs:label` → `Blank node "x"`; mutant "drop label branch" survives (only `startsWith("Blank node")` asserted) | `` `a labelled blank-node connection is named by its label in errors` `` — assert error contains `Blank node "poslovni partneri"` |
| 145-148 | Participant URI that is never typed is inferred `foaf:Agent` via `bio:participant rdfs:range` and validated → "has no documentation" (desired? it is caught by the layout check too) | `` `an untyped participant referenced by a connection is an agent that needs documentation` `` (pin) |
| 163 | `ERROR_PAGE_PATH` only exercised with `/404.php`; mutants dropping `(?i)`, `not[-_]?found`, `error`, or the `$` anchor all survive | `` `redirects onto not-found and error pages are refused` `` — Locations `/not-found`, `/NotFound/`, `/Error.aspx`, `/en/not_found.html`; and `` `a redirect onto an article whose path merely contains 404 or error is followed` `` — `/error-report-2024.html`? (matches today! `error` + `-report`... no: regex requires end after optional ext, so `/error-report-2024.html` does NOT match; good) use `/404-ways-to-save/` and `/news/errors-of-2024/` as the must-pass cases |
| 192 | `200..299` boundary — mutant `200..200` (204 from HEAD on some CDNs) | `` `a 204 answer is accessible` `` |
| 180, 200 | `MAX_REDIRECTS` boundary 5 — mutants 4/6 survive | `` `four hops are followed, five are too many` `` — chains `/r4→/r3→/r2→/r1→/ok` (ok) and `/r5→…→/ok` (too many redirects) |
| 171-172 | GET only when HEAD fails; mutant "always GET" or "report HEAD's status" survive | `` `a page accepting HEAD is not fetched twice` `` (record methods on server; assert `["HEAD"]`); `` `when HEAD and GET both fail the GET status is reported` `` (HEAD→405, GET→410; assert `HTTP 410`) |

### Wayback
| Line | Mutant / scenario | Test |
|---|---|---|
| 59 | `code >= 400` on save (only 429 exercised, caught earlier); mutant `>= 500` or `> 400` | `` `save fails loudly when Save Page Now answers 4xx or 5xx` `` — 500 → `ArchiveException` containing `500` |
| 63-64 | Location without `/web/<14 digits>/` (SPN job page) | `` `save rejects a location that is not a snapshot address` `` — `Location: /save/status/abc`; assert message contains `Unexpected snapshot address` |
| 47 | `archived_snapshots` key entirely missing → null (mutant: remove `?.` → NPE) | `` `lookup returns null when the API omits archived_snapshots` `` |
| 47-48 | Non-JSON 200 body (archive.org maintenance page) → `JsonSyntaxException`, not `ArchiveException` | `` `lookup on a non-JSON answer fails as an ArchiveException` `` (decide, then pin) |
| 48 | `closest` without `url`/`timestamp` → NPE | `` `lookup on a snapshot without a timestamp fails loudly` `` |
| 86-89 | `readTimeout = readTimeoutMs` for lookups — mutant "use `saveTimeoutMs` for both" survives | `` `lookup gives up on a slow API after timeoutMs` `` — availability handler sleeps 400 ms, `timeoutMs = 100`; `assertFailsWith<java.net.SocketTimeoutException>` |
| 11 | `ArchiveSnapshot.date` with timestamp shorter than 8 chars → `StringIndexOutOfBoundsException` | `` `a snapshot timestamp shorter than a date is rejected` `` (pin or guard) |

### SourceArchiver
| Line | Mutant / scenario | Test |
|---|---|---|
| 65 | `Outcome.alreadyArchived` is **never asserted**; any formula survives. Also `countArchived(data, archives)` ignores `data` and counts records for URLs no longer cited, so the number is wrong when `archives.ttl` has stale records | `` `alreadyArchived counts the records that existed before this run` `` — 1 pre-existing record + 1 new; assert `alreadyArchived == 1`; `` `records for documents no longer cited are not counted as already archived` `` (will FAIL today → fix or drop the field; `main` never prints it) |
| 51-56 | `lookup` throwing (429) for one URL: is it in `failed`, do the others proceed, is `save` skipped for it? | `` `a failed lookup is reported and the others still proceed` `` — FakeWayback with `lookup` throwing for `busy`; assert `failed.keys == {busy}`, `saved == [ok]` |
| 55 | `Thread.sleep` between saves: with `pauseBetweenSavesMs = 0` in every test, mutants "never sleep", "sleep before first save", "sleep after lookups too" all survive | Inject `pause: (Long) -> Unit` (default `Thread::sleep`) and assert the call list: 3 new captures → `[6000, 6000]`; 2 existing snapshots → `[]` |
| 59-62 | Log lines never asserted; `e.message == null` → `toString()` branch | `` `the log names each archived and failed URL` `` with a capturing `log`; `` `an exception without a message is still reported` `` |
| 73-76 | `record` datatype: test checks `lexicalForm` only — mutant `XSDdate → XSDstring` survives (then `archives.ttl` stops being `^^xsd:date`) | extend the existing test: `assertEquals(XSDDatatype.XSDdate.uri, literal.datatypeURI)` |
| 73-76 | `record` twice for one URL → two `hasArchiveURL` triples (`addProperty`, never replace) | `` `recording a URL twice keeps one archive URL` `` (pin or fix) |
| 79-80 | `loadOrEmpty` untested | `` `loadOrEmpty gives an empty model for a missing file and the parsed model otherwise` `` |
| 27-40 | Same URL `removed` in one node, `published` in another → archived (per-document filter) | `` `a URL still published elsewhere is archived even if one citation is removed` `` (pin) |

### DataSet / DataLayoutCheck
| Line | Mutant / scenario | Test |
|---|---|---|
| DataSet 28 | `endsWith(".ttl")` → `contains(".ttl")` survives (`notes.ttl.bak`); `.TTL` ignored | `` `only files ending in .ttl are loaded` `` |
| DataSet 16-19 | `relativePath`/`folderOf` for nested `cases/2025/x.ttl` → `"cases"` (accepted as case file); `Entities/` (capital) → outside | `` `a case file in a sub-folder of cases is still a case file` ``; pin the case-sensitivity |
| Layout 33-34 | Duplicate message uses `file.name`; `entities/x.ttl` + `cases/x.ttl` prints `x.ttl, x.ttl` | `` `duplicate declarations are reported with their relative paths` `` (will fail → use `relativePath`) |
| Layout 44 | Dangling reference from two files — mutant "report first file only" | `` `a missing entity referenced from two files names both` `` |
| Layout 26-60 | Rule interaction: entity redeclared in a case file → both "home" and "entities/" problems | `` `an entity redeclared in a case file breaks two rules` `` — assert `size == 2` |
| Layout 50-52 | A URI-named `:Documentation` node in a case file is treated as a misplaced "entity" (entity := any typed non-Connection URI) | `` `a URI-named documentation node in a case file is reported as misplaced` `` (pin, or exclude non-Agent types — data today uses blank nodes only) |
| Layout 70-71 | `entityFileName` untested directly: `#` fragment, trailing `/` → `".ttl"`, `d.o.o.` dots | `` `entityFileName lowercases, hyphenates and handles hash URIs` ``; `` `an entity URI ending in a slash cannot be named` `` (pin or guard) |
| Layout 46-49 | `vip:OwnershipConnection` in `cases/` recognised as a connection through the vocabulary (only `:Connection`/`:SocialConnection` tried) | `` `an ownership connection in a case file is not a misplaced entity` `` |

### TtlToCytoscape
| Line | Mutant / scenario | Test |
|---|---|---|
| 142 | `edgeCounter++` → `edgeCounter` gives duplicate ids (viewer breaks) — never asserted | `` `every edge has a distinct id` `` |
| 101-107 | `type == "instance"` and `uri` never asserted | `` `nodes carry their URI and the instance type` `` |
| 111-116 | `else -> "default"` unreached (`ex:X a foaf:Agent`) | `` `an agent that is neither person nor organisation is typed default` `` |
| 170-171 | `labelOf` precedence `foaf:name` > `rdfs:label` > `dcterms:title` untested; two `foaf:name` in different languages → nondeterministic label (Jena statement order) → generated JSON can flip between runs | `` `a node label prefers foaf:name over rdfs:label` ``; `` `a node with Serbian and English names always gets the same label` `` (run twice; will be flaky today → pick a language order) |
| 127 | Secondary sort by URI: two persons → source/target nondeterministic without it | `` `a connection between two people points from the lower URI to the higher` `` |
| 125 | `.distinct()` on participants: `bio:participant ex:A ; :personParticipant ex:A` → without it a self-edge | `` `a participant stated through two properties is one participant` `` — assert `edges().size == 1` |
| 150-151 | `dedupe` key includes label; mutant "ignore label" collapses different relations | `` `two different relations between the same pair are two edges` `` |
| 150-151 | Two connections with the same label between the same pair collapse to one edge (by design?) | `` `two work connections with the same role between the same pair draw one edge` `` (pin) |
| 82 | Blank-node agents excluded | `` `a blank-node person is not a node and not a participant` `` |
| 156-164 | Role without schema label dropped; untyped resource inferred `:WorkConnection` via `hasWorkRole` domain → label from roles; untyped with no roles → `"connected to"` | `` `a connection without a declared type and without roles is labelled connected to` `` — `_:c :hasEconomicOrganizationParticipant ex:NALED ; bio:participant ex:Ana` (no `a`) |
| 180-192 | `createNodeId`: `/___` → hash fallback (mutant drop `all { '_' }`); collision `ex:Ana_Brnabic` vs `ex:ana-brnabic` → same id, silently merged | `` `a local name of only punctuation gets a hash id` ``; `` `two URIs that normalise to the same id are a collision` `` (pin or make ids collision-free) |
| 195-206 | `extractLabelFromUri` never tested directly: CamelCase split, `ifEmpty { uri }`, acronym `NALED` → `N A L E D` | `` `extractLabelFromUri splits camel case and underscores` ``; `` `an empty local name falls back to the URI` ``; `` `an acronym is not split letter by letter` `` (will fail → decide) |
| 154-155 | Own label is not lowercased, type/role labels are | `` `a connection's own label keeps its case` `` (pin the asymmetry or remove it) |

### Vocab
`Vocab.kt` constants have no drift guard: renaming `vip:hasArchiveURL` in `schema.ttl` breaks nothing in tests.
- Test: `` `every vocabulary term the tools depend on is defined in schema.ttl` `` — for each `Vocab` constant other than the namespace prefixes and `RS`, assert `schema.containsResource(createResource(uri))`.

---

## P3 — CLI `main()` functions: zero tests

`VeracityValidator.kt:10-63`, `SourceArchiver.kt:82-119`, `DataLayoutCheck.kt:73-90`, `TtlToCytoscape.kt:26-65`, `SHACLValidator.kt:11-71`. All call `exitProcess` and construct network clients inline, so none can be tested. Same refactor for each, no behaviour change:

```kotlin
fun main(args: Array<String>) = exitProcess(run(args, System.out, System.err /*, deps with defaults */))
internal fun run(args: Array<String>, out: PrintStream, err: PrintStream, validation: VeracityValidation = VeracityValidation()): Int
```
Then, offline, with `@TempDir` data and stub `UrlChecker`/`FakeWayback`:
- VeracityValidator: `` `exit code 1 and numbered errors when a rule fails` ``, `` `exit 0 with a Warnings section when only archived copies were needed` ``, `` `a missing archives file is reported` `` (P0.5), `` `usage and exit 1 without arguments` ``, schema with unknown extension (`RDFLanguages.filenameToLang` → null) fails clearly.
- SourceArchiver: `` `--check exits 1 and lists unarchived documents without touching the archive` `` (FakeWayback records zero calls), `` `--check exits 0 when every document has a record` ``, `` `archive mode writes archives.ttl with the default prefix and exits 1 if any capture failed` `` (also pins that the written file re-parses and that the output stream is closed — `RDFDataMgr.write(archivesFile.outputStream(), …)` at line 113 never closes it).
- DataLayoutCheck: exit codes and "Files (n)" line.
- TtlToCytoscape: arg count 1 and 4 → usage; missing input → exit 1; node/edge counts in output; `parentFile?.mkdirs()` creating a nested output dir.
- SHACLValidator: see P0.6; plus "schema against its own shapes" branch (`dataFile == shapesFile`).

---

## Tests that are weak, tautological, or test the stub

- `VeracityValidationTest.validate with schema should not treat Documentation nodes or the vocabulary itself as entities` asserts only `isValid`; it passes for the mutant `entitiesEntailedBy = emptyList()`. Add `assertEquals(0, result.errors.size)` **and** a positive companion is already present (`report an agent that has no documentation`), so this one is fine only in combination; consider asserting the entity count via a seam.
- `VeracityValidationTest.extractDomain …` builds `VeracityValidation()` with the real network checker and archive client (unused). Harmless but misleading; use `validator()`.
- `SourceArchiverTest.the validator accepts a snapshot recorded by the archiver for a blocked page` is an integration test living in the archiver suite and is the only thing killing the `filterNot(status == removed)` → `filterNot(hasStatus)` mutant in `VeracityValidation.documentationUrls`. Move/duplicate that assertion into `VeracityValidationTest` (see P2 "status published").
- `WaybackClientTest.save waits longer for the archive than a lookup does` is good (kills the timeout mutant), but its lookup counterpart is missing (P2 Wayback 86-89).
- `UrlCheckerTest.requests identify the validator in the User-Agent header` only sees the HEAD request to `/ok`; fine, same code path for GET.
- No test anywhere reads `Outcome.alreadyArchived` (see P2 SourceArchiver 65).
- `DataLayoutCheckTest` and `TtlToCytoscapeTest` load the real `schema.ttl` — appropriate (the vocabulary is the spec), but every test is then also a schema-regression test; a schema label change silently changes expected edge labels. Acceptable; note it.

---

## Coverage map (what is already well covered)

- Rule 1 boundaries (0/1/2 URLs; 1/2 domains; www-stripping) — `VeracityValidationTest` ×8.
- Rule 2: live, blocked+recorded, blocked+looked-up, dead+none, archive down, no lookups for live URLs, warnings aggregated — ×6.
- Documentation through specialised properties, bare URLs ignored, removed status, blank-node connection reporting, schema-entailed agents/connections without documentation — ×6.
- HTTP checker: 200, 404, HEAD-refused, absolute/relative redirect, error-page redirect (`/404.php` only), redirect loop, unreachable host, User-Agent — ×9.
- Wayback: lookup found/none/500/429/encoding; save Content-Location/Location/429/timeouts/different page/scheme-www/no location — ×12.
- Archiver: unarchived listing (distinct, removed, recorded), existing vs capture, one failure among many, round-trip with validator — ×7.
- DataSet: directory, single file, no-ttl error, ordering, nesting — ×5.
- Layout: duplicates, cross-file references, blank nodes, dangling, vocab terms, misplaced both ways, naming, one-per-file, stray files — ×10.
- Cytoscape: ids, node types, labels, no doc/connection nodes, participant ordering for 3 connection kinds, roles, own label, type label, n-ary, ghost participants, direct edges, symmetric dedupe, no literal edges — ×14.

## Suggested order of work (Baby Steps, one test at a time)
1. P0.4, P0.3, P0.1 (pin/decide), P0.2 (pin/decide).
2. P1.1 (crash on bad date), P1.2, P1.3, P1.5 (Cyrillic/query), P1.6, P1.7.
3. SHACL extraction + 3 tests (P0.6), then the `run()` refactors (P3) starting with VeracityValidator (P0.5).
4. P2 tables, mutation-style, highest first: `alreadyArchived`, edge ids, `distinct()`s, `ERROR_PAGE_PATH`, timeouts.
