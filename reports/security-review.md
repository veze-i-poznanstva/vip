# Security and robustness review: data-contribution pipeline

Scope: `app/src/main/kotlin/vip/{VeracityValidation,VeracityValidator,Wayback,SourceArchiver,DataSet,DataLayoutCheck,TtlToCytoscape,SHACLValidator,Vocab}.kt`, `build.gradle.kts`, `app/build.gradle.kts`, `web/build.gradle.kts`, `.github/workflows/verify.yml`, `archives.ttl`, `data/`, and `web/src` as a sink only. Read-only; nothing was built or run. Off limits: graph explorer and red-flag rules from `docs/ui-design.md`.

Threat model: a contributor controls every byte of `data/**/*.ttl` and `archives.ttl` in a pull request. `./gradlew verify` runs on GitHub Actions for that PR and on a maintainer's laptop.

Line numbers refer to the working tree at review time.

---

## Ranked findings

### 1. HIGH — An undocumented relation between two people is exported to the public graph

**Where:** `app/src/main/kotlin/vip/TtlToCytoscape.kt:134-138` (`directEdges`), together with `VeracityValidation.kt:143-149` (`entitiesEntailedBy`) and `DataLayoutCheck.kt:39-45`.

**Scenario:** The project's promise is that every connection is backed by two sources. That is enforced only for resources the vocabulary entails to be `foaf:Agent` or `:Connection`. A contributor writes, in any case file:

```turtle
rs:Sinisa_Mali rs:tookBribesFrom rs:Milenijum_Tim .
```

- Veracity: the triple is not a `:Connection`, so `validateItem` is never called for it; both agents already have two sources each, so the file passes.
- SHACL: no shape targets an unknown predicate.
- Layout check: the object is declared, so it is not dangling; predicates are never checked (`DataLayoutCheck.kt:41` looks only at objects).
- Export: `directEdges` turns *every* URI-object statement between two agents into an edge, labelled by `extractLabelFromUri(predicate)` → an edge "took bribes from" between a real minister and a real company appears in `web/public/test-data-cytoscape.json` and the viewer, with no evidence anywhere.

Only a human reviewer stands between this and publication, and the build says green.

**Mitigation (minimal):** In `directEdges`, keep only predicates the schema declares (`schema.containsResource(it.predicate)`), and add to `checkDataLayout` a rule that every predicate used in a data file is declared in the schema (same `schema.containsResource` test already used for objects at line 43). The second half makes the smuggled triple fail the build instead of being silently dropped.

**Test to pin it:** `TtlToCytoscapeTest`: two declared agents joined by `ex:undeclaredProperty` → no edge. `DataLayoutCheckTest`: a file using a predicate the schema does not declare → one problem naming the predicate and the file.

---

### 2. HIGH — A dead source is accepted on the strength of a self-written archive record that nobody checks

**Where:** `app/src/main/kotlin/vip/VeracityValidation.kt:92-94, 107-116` (`recordedArchive`), `SourceArchiver.kt:40` (`unarchivedUrls`), `VeracityValidator.kt:31-32`.

**Scenario:** `archives.ttl` is contributor-edited by design (README step 5) and is loaded into the same model as the data (`VeracityValidator.kt:32`). `recordedArchive` accepts any URI object of `vip:hasArchiveURL`, never fetches it, and never checks its host. A contributor cites `https://dead.example/proof`, which answers 404, and adds:

```turtle
<https://dead.example/proof> vip:hasArchiveURL <https://attacker.example/anything> ; vip:archivedOn "2026-01-01"^^xsd:date .
```

`checkArchives` (CI) sees a record and passes; `validateVeracity` sees the live check fail, finds the record, and emits only a *warning* "using archived copy from 2026-01-01 at https://attacker.example/anything". The build is green and the fake evidence is printed as if it were a Wayback snapshot. The same works with a `web.archive.org/web/20260101000000/...` URL that does not exist. The record can also be placed in the data file itself (the model is merged), though CI's `checkArchives` would then still demand an `archives.ttl` entry.

Note the asymmetry with the archiver: when the *archiver* writes a record it took the URL from archive.org; when the *validator* reads a record it has no idea who wrote it.

**Mitigation (minimal):** In `recordedArchive`, treat a record as evidence only if the archive URL matches `^https?://web\.archive\.org/web/\d{14}/` (host check, not just the timestamp regex at line 111); otherwise report it as an error ("archive record for X is not a Wayback snapshot: Y"). Optionally, on CI, HEAD the snapshot URL as well (single fixed trusted host, so no SSRF exposure); `web.archive.org` returns 404 for timestamps it does not hold.

**Test to pin it:** `VeracityValidationTest`: blocked URL with `hasArchiveURL <https://attacker.example/x>` → `isValid == false`, error mentions the record; same with `http://web.archive.org/web/20260101000000/...` on a mocked `urlChecker` returning 404 for it (if the HEAD is added).

---

### 3. MEDIUM — Blind SSRF from CI and from maintainers' laptops via cited URLs and redirects

**Where:** `app/src/main/kotlin/vip/VeracityValidation.kt:178-200` (`requestFollowingRedirects`).

**Scenario:** Any `http`/`https` URL in a `:hasDocumentURL` is requested with HEAD and, if HEAD is not 2xx, GET; redirects are followed by hand for 5 hops with no host or scheme policy. So a PR can make the build (CI runner *and* every maintainer who runs `verify` on the branch) send `GET http://192.168.1.1/cgi-bin/reboot`, `GET http://localhost:8080/admin/users/1/delete`, `GET http://169.254.169.254/latest/meta-data/` or `http://[::1]:6379/`. The response body is never read and only `"OK"`/`"HTTP n"`/`"Error: msg"` reaches the log, so this is *blind*: a 200/404/connection-refused oracle for internal hosts and ports, plus the side effects of a GET on devices and dev servers that treat GET as an action. A public URL that `302`s to an internal address is followed just the same (line 194), and the redirect may also downgrade `https`→`http` silently. DNS rebinding is irrelevant for exfiltration (nothing is read) but not for side effects.

`file:`, `ftp:`, `jar:` are stopped only by accident: `openConnection() as HttpURLConnection` throws `ClassCastException` (line 181) before any I/O, and the user sees "Error: class sun.net.www.protocol.file.FileURLConnection cannot be cast ..." — a Turtle relative IRI `<foo.html>` resolves to a `file:` URL and produces exactly this message.

**Mitigation (minimal):** Before each hop: require `url.protocol in setOf("http","https")`, non-empty host, and resolve the host (`InetAddress.getAllByName`) rejecting any address that is loopback, link-local, site-local (RFC 1918), unique-local, multicast, unspecified, or `100.64/10`; return `Pair(false, "refused: $url is not a public address")`. Apply to the redirect target too. Optionally refuse ports other than 80/443/8080 or make the allowlist explicit. The TOCTOU between resolution and connect is acceptable for a blind check.

**Test to pin it:** `UrlCheckerTest`: `checkUrlOverHttp("http://127.0.0.1:${port}/ok")` → `(false, refused...)` (the current tests use `localhost`, so they will need a test seam such as an `allowPrivate` flag or a host-policy parameter injected for tests); `/moved-to-internal` redirecting to `http://10.0.0.1/` → refused; `file:///etc/hostname` → `(false, "unsupported scheme file")`.

---

### 4. MEDIUM — Node-id collisions let a contributor's new entity graft its edges onto a real person's node

**Where:** `app/src/main/kotlin/vip/TtlToCytoscape.kt:180-193` (`createNodeId`), `DataLayoutCheck.kt:70-71` (`entityFileName`); sink `web/node_modules/cytoscape` (`Can not create second element with ID`, element dropped, not thrown).

**Scenario:** `createNodeId` lowercases and maps every non-`[A-Za-z0-9_]` character to `_`. `rs:Sinisa_Mali` and a contributor's `rs:Sinisa.Mali` (or `rs:sinisa-mali`, `rs:Sinisa%20Mali`) both become `sinisa_mali`. The layout rule does not stop this: `entityFileName` yields `sinisa.mali.ttl` vs `sinisa-mali.ttl`, two different, legal files. The export emits two nodes with the same `id`; Cytoscape logs an error (stripped by `esbuild.drop: ['console']`) and drops the second node, while every edge of the fake entity, built from the same id (`edge()` line 143-144), attaches to the real person's node. Result: fabricated relations displayed on a real politician in the viewer, with the build green.

**Mitigation (minimal):** Make ids injective: `"n" + sha1(uri).take(12)` or `uri.hashCode()` suffix always, or detect duplicates in `build()` and throw (`require(nodes.distinctBy { it.data.id }.size == nodes.size)`). Keep `uri` in `ElementData` for display.

**Test to pin it:** `TtlToCytoscapeTest`: `ex:Sinisa_Mali` and `ex:Sinisa.Mali` both declared → two nodes with distinct ids (or conversion fails naming both URIs).

---

### 5. MEDIUM — Vocabulary-level statements in data files are not rejected and feed the RDFS reasoner

**Where:** `DataLayoutCheck.kt:28-32` (only subjects with `rdf:type` are checked), `VeracityValidation.kt:144`, `SHACLValidator.kt:47`, `TtlToCytoscape.kt:79`, `DataLayoutCheck.kt:47` (four separate `createRDFSModel(schema, data)` calls).

**Scenario:** A data file may contain `rdfs:subClassOf`, `rdfs:subPropertyOf`, `rdfs:domain`, `rdfs:range` for any resource. Those triples have no `rdf:type`, so the one-home rule never sees them, no shape targets them, and they change inference for the *whole* dataset. Two effects: (a) integrity — `rs:Anything rdfs:subClassOf :Connection` or `rs:myProp rdfs:subPropertyOf bio:participant` reshapes what counts as a connection/participant, interacting with finding 1; (b) availability — a chain of a few thousand `rdfs:subClassOf` triples makes the RDFS rule engine's transitive closure the dominant cost, run four times per `verify`, plus SHACL over an `InfGraph`. The attacker burns CI minutes with a small file.

**Mitigation (minimal):** In `checkDataLayout`, report any statement whose predicate is in `{rdfs:subClassOf, rdfs:subPropertyOf, rdfs:domain, rdfs:range}` or whose predicate/subject namespace is `owl:`/`rdfs:`/`sh:` as "vocabulary statements belong in schema.ttl". Combine with the undeclared-predicate rule from finding 1.

**Test to pin it:** `DataLayoutCheckTest`: a file containing `ex:Foo rdfs:subClassOf foaf:Person .` → one problem naming the file.

---

### 6. MEDIUM — No per-URL or per-job time bound: a slow server stalls CI for hours

**Where:** `VeracityValidation.kt:161, 183-184`; `.github/workflows/verify.yml` (no `timeout-minutes`); `VeracityValidation.kt:89-98` (one request per URL per entity, no cache).

**Scenario:** `connectTimeout`/`readTimeout` are per socket operation, not per request. A server that trickles one header byte every 9 s, or emits megabytes of headers, keeps a single `responseCode` call alive indefinitely; a 5-hop redirect chain of such servers, times HEAD then GET, times N cited URLs, against the default 6-hour job limit. Independently, a PR citing 10,000 URLs is legal and costs up to 20 s each. The same URL appearing in several entities (e.g. the companywall record in two connections today) is requested once per entity, which also hammers third-party sites and provokes 403s/429s that then masquerade as "not reachable live".

**Mitigation (minimal):** Run `checkUrlOverHttp` under an `ExecutorService` with `future.get(30, SECONDS)` (whole request incl. redirects), memoise results per URL for a run (`HashMap<String, Pair<Boolean,String>>` in `VeracityValidation`), and add `timeout-minutes: 30` to the job. The archiver already serialises saves with a pause.

**Test to pin it:** `UrlCheckerTest`: a context that `Thread.sleep`s past the deadline before writing headers → result within the bound, `status` says timed out. `VeracityValidationTest`: two entities citing the same URL → `urlChecker` called once.

---

### 7. MEDIUM — A malformed archive record crashes the whole validation with a stack trace instead of an error line

**Where:** `VeracityValidation.kt:111-115` and `Wayback.kt:12` (`ArchiveSnapshot.date`), evaluated at `VeracityValidation.kt:94`, outside the `try` at line 118-119.

**Scenario:** `archives.ttl` is never SHACL-validated (`validateShacl` loads `data/` only). `vip:archivedOn "sometime"` → `"sometime".padEnd(14,'0').substring(0,8)` = `"sometime"` → `LocalDate.parse` throws `DateTimeParseException` at line 94; `vip:archivedOn <http://x>` → `asLiteral()` throws `LiteralRequiredException` at line 112; `hasArchiveURL <.../web/99999999999999/...>` → `DateTimeParseException`. All propagate out of `validate()`, `VeracityValidator.main` prints "Error: Text 'sometime' could not be parsed" plus a stack trace and exits 1. Nothing names the file or the URL. Also: `recordedArchive` is only reached when a URL is dead, so a bad record lies dormant until the live site breaks.

**Mitigation (minimal):** Make `recordedArchive` total: wrap the parsing in `runCatching`, and on failure return an error "archive record for $url is malformed: ..." (an `errors` entry, not an exception). Validate `archives.ttl` records eagerly in `SourceArchiver --check` (host, 14-digit timestamp, parsable date) so CI fails with the offending URL even while the live site still answers.

**Test to pin it:** `VeracityValidationTest`: blocked URL with `archivedOn "sometime"` → `isValid == false`, error contains the URL and "malformed"; no exception.

---

### 8. LOW — Parse errors do not name the offending file; corrupt `archives.ttl` and unknown JSON are handled unevenly

**Where:** `DataSet.kt:31`, `SourceArchiver.kt:80-81`, `DataLayoutCheck.kt:79` (no try/catch), `VeracityValidator.kt:58-60`, `TtlToCytoscape.kt:60-64` (prints errors to stdout).

**Scenario:** `RDFDataMgr.loadModel` throws `RiotException("[line: 5, col: 3 ] ...")` with no file name; with 22 files the contributor has to bisect. `DataLayoutCheck.main` does not catch at all, so the first failing task in `validateData` prints a raw Kotlin stack trace. A corrupt `archives.ttl` behaves the same in `SourceArchiver.main`. The Wayback non-JSON case is handled (`JsonParser` exceptions are caught at `VeracityValidation.kt:119` and `SourceArchiver.kt:61`), but in the validator an *archive outage* is reported as "no archived copy", which blames the source instead of archive.org. An empty `data/` gives a clear `IllegalArgumentException` message (tested), but again uncaught in `DataLayoutCheck`.

**Mitigation (minimal):** In `loadDataSet`, wrap each `loadModel` and rethrow `IllegalArgumentException("${it.path}: ${e.message}")`; same in `loadOrEmpty`. In `lookupArchiveSafely`, return a sentinel/warning text so the error reads "(HTTP 403; archive lookup failed: ...)" instead of "no archived copy".

**Test to pin it:** `DataSetTest`: a directory with one good and one syntactically broken file → exception message contains the broken file's name.

---

### 9. LOW — Untrusted strings reach logs unescaped: terminal escapes and GitHub workflow commands

**Where:** `VeracityValidation.kt:151-155` (`describe` prints a blank node's `rdfs:label` literal), `DataLayoutCheck.kt:88`, `VeracityValidator.kt:41, 52`, `SourceArchiver.kt:60, 63, 107`, `SHACLValidator.kt:56` (`ShLib.printReport`), file names from `walkTopDown`.

**Scenario:** Turtle IRIs cannot contain control characters, but literals can (`"\u001B[2J"`, `"\n::group::"`, `"""multi\nline"""`), and so can file names. A blank-node agent labelled `"x\n::add-mask::inaccessible"` puts a line starting with `::` into the step log; the Actions runner treats it as a workflow command (`::add-mask::` hides later occurrences of the word, `::group::` folds the rest of the output, `::warning::`/`::notice::` plant fake annotations in the PR Checks tab). On a laptop, ESC sequences can clear or overwrite the "Validation FAILED" line. The exit code is unaffected, so this is cosmetic deception, not bypass.

**Mitigation (minimal):** One helper `fun String.printable() = replace(Regex("[\\p{Cntrl}\\u2028\\u2029]"), "�")` applied to the untrusted part of every message (labels, URIs, file names) before `println`. Gradle's `--console=plain` does not strip these.

**Test to pin it:** `VeracityValidationTest`: blank node labelled `"a\nb\u001Bc"` → error string contains no `\n` or `\u001B`.

---

### 10. LOW — Domain-diversity rule is defeated by case or a trailing dot

**Where:** `VeracityValidation.kt:36-43` (`extractDomain`), `79-84`.

**Scenario:** `URL.host` preserves case and trailing dots. `https://evil.example/a` and `https://EVIL.EXAMPLE/b` (or `https://evil.example./b`) count as two domains while being one site that answers both. A determined attacker can register two domains anyway, so this is an honesty check, but it is cheap to close. Also `extractDomain` returns the whole URL when parsing fails (line 41), so two `urn:` or `mailto:` values count as two distinct "domains" before failing reachability — harmless today because the reachability check then fails.

**Mitigation (minimal):** `host.lowercase().trimEnd('.')`, and return `""` (not the URL) when there is no host so such values collapse to one domain.

**Test to pin it:** `extractDomain("https://EVIL.EXAMPLE./x") == "evil.example"`; two URLs differing only in case → "URLs from only 1 domain".

---

### 11. LOW — Symlinks inside `data/` are followed and parsed

**Where:** `DataSet.kt:27-31` (`walkTopDown`, `isFile`, `loadModel`).

**Scenario:** Git check-outs restore symlinks. `data/entities/x.ttl -> ../../.git/config` is `isFile == true` and is fed to the Turtle parser. Jena's error message echoes the offending token and line/column, so the first unparsable token of any file on the runner or the maintainer's machine lands in the log. With `actions/checkout@v4` defaults (`persist-credentials: true`) `.git/config` holds the job token, though the Turtle tokenizer fails at `[core]` before reaching it. Low likelihood of real leakage, but there is no reason to follow links at all.

**Mitigation (minimal):** In `loadDataSet`, filter `!Files.isSymbolicLink(it.toPath())` and require `it.canonicalPath.startsWith(path.canonicalPath)`; report the link as a layout problem.

**Test to pin it:** `DataSetTest`: `Files.createSymbolicLink(dir/"link.ttl", realFileOutsideDir)` → not loaded / reported.

---

### 12. LOW — Save Page Now can be driven to archive arbitrary attacker-chosen URLs from a maintainer's IP; the CI/local switch relies on `CI=true`

**Where:** `app/build.gradle.kts:217-218`, `SourceArchiver.kt:50-65`, `Wayback.kt:55-74`.

**Verified:** CI never calls Save Page Now: `validateData` picks `checkArchives` when `System.getenv("CI") == "true"` (GitHub sets it), `SourceArchiver.main` returns before `archive()` with `--check`, and `VeracityValidation`'s default `archiveLookup` is `WaybackClient().lookup` (availability API only). `save()` is reachable only from `SourceArchiver.archive()`.

**Scenario:** A maintainer who runs `./gradlew verify` on a contributor's branch *before reading it* issues one `GET https://web.archive.org/save/<url>` per new URL, under their own IP, for whatever the PR cites (content laundering, hammering a target, getting the maintainer rate-limited: `failIfRateLimited` only reports). There is no cap on captures per run. The README (step 4) tells contributors to run this themselves, so for honest PRs the maintainer never needs to. Separately, any other CI without `CI=true` (Jenkins) or a laptop with `CI` exported flips the branch.

**Mitigation (minimal):** Refuse more than N (say 25) new captures in one run unless `-Pforce` is given, and print the list first; document "review the cited URLs before running verify on a foreign branch". Consider `-PonCi` or checking `GITHUB_ACTIONS` as well as `CI`.

**Test to pin it:** `SourceArchiverTest`: 26 unarchived URLs with `FakeWayback` → `archive()` throws/returns `failed` without calling `save` more than 25 times.

---

### 13. LOW — `URLDecoder.decode` in `samePage` throws on `%` sequences that are legal in IRIs

**Where:** `Wayback.kt:79`.

**Scenario:** A cited URL containing a bare `%` or `%zz` (`https://x/100%`) makes `normalise` throw `IllegalArgumentException`; it is caught per-URL in `archive()` (line 61), so the record is never written and the source can never pass CI, with a confusing "URLDecoder: Illegal hex characters" message. `+` is also decoded to space, so `a+b` and `a%20b` compare equal (harmless).

**Mitigation (minimal):** `runCatching { URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)`.

**Test to pin it:** `WaybackClientTest`: `save("https://site.example/100%")` with the fake server echoing the same path → returns a snapshot.

---

### 14. LOW — Supply chain: mutable action tags, no Gradle dependency verification, no wrapper checksum, `npm install` not `npm ci`, no job permissions

**Where:** `.github/workflows/verify.yml:13-17`; `gradle/wrapper/gradle-wrapper.properties` (no `distributionSha256Sum`); no `gradle/verification-metadata.xml`; `web/build.gradle.kts:244-247` (`download = true`, Node 20.11.0 fetched at build time without checksum), `npmInstall` default command is `npm install` with `^` ranges in `web/package.json`; `web/package-lock.json` is present.

**Scenario:** `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4` are moving tags; a compromised tag runs on every PR with the repo token. No `permissions:` block means the default token scope (write on push events in many org settings). Maven dependencies are pinned by version but not by checksum/signature; the versions catalogue `gradle/libs.versions.toml` is unused dead config (declares Kotlin 2.1.20 and guava while the build uses Kotlin 1.9.21).

**Mitigation (minimal):** Pin actions to commit SHAs; add `permissions: contents: read` and `timeout-minutes` to the job; add `distributionSha256Sum` to the wrapper; `./gradlew --write-verification-metadata sha256` once and commit; set `npmInstallCommand = "ci"` in `web/build.gradle.kts`; delete or use `libs.versions.toml`.

**Test:** none applicable (configuration); a CI dry run is the check.

---

### 15. LOW — `asLiteral()` on contributor-controlled objects can crash export/validation

**Where:** `TtlToCytoscape.kt:172`, `VeracityValidation.kt:153`.

**Scenario:** `foaf:name rs:SomeResource` (object is a URI, not a literal) makes `asLiteral()` throw `LiteralRequiredException`; `TtlToCytoscape.main` catches and prints a stack trace, but the message names neither entity nor file. Self-inflicted by the contributor, but SHACL does not necessarily catch it (no `sh:nodeKind sh:Literal` on `foaf:name` in the shapes read).

**Mitigation (minimal):** `?.takeIf { it.isLiteral }?.asLiteral()`.

**Test to pin it:** `TtlToCytoscapeTest`: agent whose `foaf:name` is a resource → label falls back to the URI, no exception.

---

### 16. LOW — `archives.ttl` is truncated before it is written

**Where:** `SourceArchiver.kt:114`.

**Scenario:** `archivesFile.outputStream()` truncates first; an exception during serialisation leaves a zero-length or partial file that the next run cannot parse (finding 8). The stream is also never closed explicitly.

**Mitigation (minimal):** Write to `archives.ttl.tmp` and `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`; `use {}` the stream.

---

## Confirmed non-issues / already handled correctly

- **Scheme confinement (accidental but effective):** non-HTTP URL connections (`file:`, `ftp:`, `jar:`) fail the `HttpURLConnection` cast before any I/O (`VeracityValidation.kt:181`); only the message is ugly (finding 3 proposes an explicit check).
- **No response body is read** by the live check (`responseCode` reads status and headers; `disconnect()` closes the socket), so cited URLs cannot exfiltrate internal content through logs or files; only the archive availability API body is read, and that peer is fixed and trusted.
- **Timeouts are set** on every connection (10 s live, 30 s lookup, 120 s save) and redirects are capped at 5; a redirect loop is tested.
- **Redirect target ending on an error-page path is rejected** (`ERROR_PAGE_PATH`); the regex is linear, no ReDoS, and only applies to redirect targets, so it cannot be used to force a false positive on a legitimately cited page.
- **CI never calls Save Page Now** (see finding 12 for the verification); `--check` returns before `archive()`; `validateVeracity` is ordered after `archiveSources`/`checkArchives`.
- **Archive lookups encode the URL as a query parameter** (`URLEncoder`, tested), so a cited URL cannot alter the availability request.
- **Wayback API failures are contained:** non-JSON, missing keys, 5xx and 429 all become `ArchiveException`/null and never abort the run (tested); per-URL failures in the archiver do not stop the others (tested).
- **`samePage` guard** stops the archiver from recording a snapshot of a site's 404 page as evidence (tested).
- **JSON export is a safe sink:** Gson escapes `"`, `\`, control characters and, by default, `<`, `>`, `&`, `=`, `'`; the viewer loads it with `fetch().json()`, renders labels on a canvas (Cytoscape) and uses only text interpolation in Svelte (no `innerHTML`/`{@html}` in `web/src`). No XSS path from Turtle to DOM was found.
- **No shared mutable static state:** `edgeCounter` is per `GraphBuilder` instance; the only globals are `const val`/`val Regex`. Gradle `parallel=true` only parallelises across `:app`/`:web`, and the one cross-task file (`archives.ttl`, the JSON export) is ordered by `mustRunAfter`/`dependsOn`.
- **Empty data directory** is a clear `IllegalArgumentException` naming the directory (tested).
- **File naming** (`entityFileName`) is used only for comparison and messages, never to open or write a path; `walkTopDown` output cannot contain `..`.
- **Removed documents** are excluded from evidence and from archiving (tested).
- **Honest `User-Agent`** on every request (tested).
- **`web/package-lock.json` exists**; npm dependencies are at least recorded (but see finding 14 on `npm ci`).
- **Fork PRs:** on `pull_request` from forks GitHub gives a read-only token and no secrets, which limits the blast radius of findings 3 and 14 on CI (not on laptops).

## Process note (not a code defect)

A PR can change `build.gradle.kts`, the Kotlin sources and the workflow as freely as it changes `data/`. Every finding above assumes the pipeline code is the maintainer's; a "data-only" PR has to be verified as such by the reviewer (or a CODEOWNERS/path rule) before the green check means anything.
