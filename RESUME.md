# RESUME.md — where work stands and how to continue

Written 30 October 2026 at the end of the second long session. Read this first in a new session, then
CLAUDE.md (rules), then README.md (what the project is). Delete this file once it is stale.

## Environment, do not get this wrong

- Repository: `~/code/ghdl/vip`, branch `main`, **nothing pushed yet**, no remote configured.
- Identity: everything under `~/code/ghdl/` commits as
  `danluddlum <350449005+danluddlum@users.noreply.github.com>` via `~/code/ghdl/.gitconfig`,
  included from `~/.gitconfig`. SSH key `~/.ssh/id_ed25519_ghdl`, added to the danluddlum account.
  HTTPS GitHub URLs are rewritten to ssh in that folder on purpose.
- **Never use `gh` on VIP repositories** (it is signed in as other accounts). Only git over ssh.
- The whole history was rewritten so that danluddlum is the only author/committer. No other
  identity, name, employer or home path may appear in any file or commit.
- GitHub: organisation `veze-i-poznanstva`. Repository `veze-i-poznanstva/vip` exists (transferred
  from a personal account) but is private or otherwise invisible to outsiders; make it public
  before publishing. Fork `veze-i-poznanstva/w3id.org` is cloned at `~/code/ghdl/w3id.org`;
  branch `add-vip-prefix` holds one clean commit adding `ids/vip/` (README.md, .htaccess), checked
  with the maintainers' tool (42 rules, no problems). **The pull request to perma-id/w3id.org is
  not opened yet**; open it in the browser as danluddlum:
  https://github.com/perma-id/w3id.org/compare/master...veze-i-poznanstva:w3id.org:add-vip-prefix
- Claude Code memory is keyed by directory; the old path `~/code/tmp/vip` no longer exists, so a
  new session starts without the previous notes. This file replaces them.

## State of the code (all green)

`CI=true ./gradlew verify` passes: 221 unit tests (Kotlin) and 16 Vitest tests (web), data
layout, SHACL, archive records, ontology consistent, veracity live with 12 archive-copy warnings
(Istinomer and SNS block automated clients), red-flag rules (1 lead: Mali and Brnabić, Peta
gimnazija, SNS), converter, web demo build. Dependency verification is on
(`gradle/verification-metadata.xml`; the Node.js download is the one trusted artifact) and the
Gradle wrapper checksum is pinned. Locally `verify` runs the archiver (Save Page Now, at most 25
new captures per run); the sandbox refuses that, so the user runs `./gradlew :app:archiveSources`.

Done on 30 October, second session (all uncommitted, nothing pushed):
- Internationalisation, limited by decision to the viewer and the docs: `web/src/lib/i18n.ts`
  (`$t`, `language`, `resolveLanguage`), dictionaries `web/src/locales/{en,sr}.json` with a key
  parity test, language switch, `?lang=`; docs paired `README.md`/`PROCITAJME.md` and
  `docs/<name>.md`/`docs/<name>.sr.md`, enforced by `DocumentationTranslationsTest` (existence and
  heading outline). Tool output stays English by decision. Converter edge labels still come from
  the vocabulary's `@en` label (increment "4" of the i18n plan, not done, user unsure).
- Graph explorer increment: `web/src/lib/graph.ts` (fold with Cyrillic transliteration,
  buildIndex, search, egoGraph, expand), search box with suggestions, one-hop view (concentric
  layout around the focus), double-click expands, "Show all" returns to the demo view.
  `docs/ui-design.md` status updated. Not visually checked in a browser (the sandbox's localhost is
  unreachable from Chrome and the extension disconnected); the user should open
  `./gradlew :web:npmDev` with `?lang=sr`.
- Contributor prompt for AI agents: `docs/agent-prompt.md` and `.sr.md`, linked from both READMEs
  under "Contributing data". Generic (any coding agent), starts in Serbian, asks permission per
  step, searches sources but adds them only after the contributor confirms the quoted passage,
  uses gh for fork and pull request.
- Deferred review findings closed: deterministic label choice (sr, en, alphabetical);
  `Outcome.alreadyArchived` fixed and printed; pause injected; per-run capture cap (25) with a
  `deferred` list; IDN hosts and non-ASCII paths (`asciiForm`); `vip:hasFinancialInformation`
  added to the vocabulary; every CLI is `main = exitProcess(runXxx(args, out, err, deps))` with a
  `<Tool>CliTest`; contributed strings printed through `String.printable()`; a missing archives
  file is now an error for the veracity validator; `npm ci`; wrapper checksum; dependency
  verification; unused version catalog removed.
- Red-flag rules: `rules/*.rq` (nepotism, owner-in-office, schoolmates-in-power), `RedFlags.kt`
  runner, `./gradlew :app:checkRedFlags` in `verify`, tests per rule, documented in both READMEs.
- Global CLAUDE.md got a "Subagents" section (delegate mechanical work to Haiku/Sonnet, verify it).

Decisions already taken (do not reopen without the user):
- Licence DACL 0.1, kept despite its police-use restriction versus the "criminal investigation"
  purpose; audience is journalists and civil society.
- Namespaces: `:` PoderVocab, `vip:` https://w3id.org/vip/vocab#, `rs:` https://w3id.org/vip/rs/.
- Layout: `data/entities/<slug>.ttl` one per person/organisation, `data/cases/<case>.ttl`
  connections only; six layout rules enforced by `checkDataLayout`.
- Identity sources on a person count as that person's two sources; subdomains are distinct domains.
- "Inspired by Poderopedia" in the README is the only credit wording wanted.
- Two languages, English and Serbian Latin, everywhere a reader looks; tool output English.
- Red-flag matches are leads, printed, never a failing check.

The review sweep of 30 October is reconciled in `reports/sweep-summary.md`; the four reviewer
reports sit beside it. Decide whether `reports/` is committed or gitignored (not decided; a "cp"
today would commit it).

## Correction of 1 November 2026

`reports/first-principles-review.md` (external review, not committed by us) found a misattributed
NALED role. Outcome: `rs:Ivan_Bosnjak` merged two men and is gone. `rs:Ivan_Bosnjak_Milenijum_Tim`
(born 1980, Belgrade, sr.wikipedia "Ivan Bošnjak (privrednik)") and `rs:Ivan_Bosnjak_Zrenjanin`
(born 4.11.1974, Melenci, state secretary) are separate. NALED Executive Board claim now rests on
KRIK 2019 (Nova.rs repeats it, one origin), period unknown; "membership development manager" was
Višnja Račić, removed. The "business and political partners" Brnabić-Bošnjak connection had no
supporting source and was deleted. Brnabić's NALED record split into three dated roles; the old end
date April 2017 was wrong (August 2016). 13 of 14 connections now have dates. Date properties accept
year, month or day precision; shapes refuse end before start; rules drop known non-overlap and report
`overlapping` or `unknown`. Prompt gained Step 6, second reading.
The eight direct `:subsidiaryCompanyOf` claims are gone: nine `vip:OwnershipConnection` records in
`data/cases/milenijum-tim.ttl` replace them, each with its own evidence. Two were not subsidiaries
(Millennium Properties 26%, Pizzarotti Millennium 4%; the latter was misnamed Milenijum Picaroti).
Unresolved: no registry record for MT Gastel; CompanyWall page for MB 20183209 sits under an
"mv-investment" address but shows DANUBE RIVERSIDE, so it is not cited.
Still open from that review: exporter pairwise edges, archive snapshot identity check, publication contract (reviewer,
status), `vip:ownershipShare` bounds. Full source re-read of the 21 entities is still not done.

## What is left, in the order agreed with the user

1. **Human review of the data**: each old source should be read to confirm it supports the exact
   claim it is attached to; some early sources were weak or fabricated and were marked
   `vip:removed`, the rest were never re-read by a person.
2. **Publishing checklist**: make the GitHub repository public; "cp" (commit everything, add
   remote `git@github.com:veze-i-poznanstva/vip.git`, push `main`); open the w3id pull request;
   once merged, `https://w3id.org/vip/vocab#` resolves to schema.ttl and `rs/` to the entities
   folder; later point `rs/<Name>` at the explorer.
3. **Explorer, next increments** (`docs/ui-design.md`): converter export of sources, aliases,
   connection URI and comment; details panel with sources; recentre and trail; depth two; deep
   links; veracity status in the export.
4. **Open i18n items, only if wanted**: converter labels per language (viewer edge labels are
   English today); best-practices checker should expect `@sr` labels (it expects en and es);
   command-line tool messages.
5. Six pre-existing svelte-check type errors in `CytoscapeGraph.svelte` and the App error handler
   are not caught by `verify` (it runs only the Vite build); fix before adding type-checking to the gate.

## Pending steps on GitHub, all need the danluddlum browser login

1. Open the w3id pull request (link above). Title: `Add vip prefix (Veze i poznanstva)`. Body:
   ```
   New identifier directory ids/vip for VIP (Veze i poznanstva), a civic dataset of
   connections between people and organisations in Serbia and its vocabulary additions
   to PoderVocab.

   - https://w3id.org/vip/vocab#  -> the ontology file in the project repository
   - https://w3id.org/vip/rs/     -> instance data for Serbia (one Turtle file per entity)

   Checked locally with tools/check: 42 rules, no problems. Contact: @danluddlum.
   ```
   Reviewers may ask why the redirect target (`veze-i-poznanstva/vip`, raw `schema.ttl` on
   `main`) does not resolve yet: it is private and unpushed until "cp".
2. github.com/settings/emails for danluddlum: tick "Keep my email addresses private" and "Block
   command line pushes that expose my email". Web-UI commits otherwise expose the Proton address
   (already purged from the fork's branches, but GitHub may keep the old commits by hash).
3. Make `veze-i-poznanstva/vip` public before the pull request is reviewed.
4. The `ids/vip/.htaccess` in the fork redirects `vocab` to raw `schema.ttl` and `rs/...` to the
   `data/entities` folder (Apache cannot lowercase the name). When the explorer exists, change the
   second rule to point `rs/<Name>` at the explorer page; identifiers never change.

## Things learned about the tooling this session

- The Claude Code sandbox refuses: Save Page Now requests (archiving), git force-pushes, and
  browser-impersonating requests. The user runs those from their terminal (`! command`).
- The Internet Archive rate-limits after ~60 requests; lookups then answer 429 for some minutes.
  Save Page Now often takes over 30 s; the client gives captures two minutes.
- Istinomer and sns.org.rs return 403 to any Java client (Cloudflare); they are served from
  recorded Wayback snapshots and reported as warnings. Do not impersonate browsers.
- `pretraga.pkspartner.rs` permanently redirects to `pretraga.cubepartner.rs`; the archive record
  points at the new host and is legitimate (same path).
- The NALED news item about Ana Brnabić becoming board chair is dead (site redirects to 404.php);
  it is kept with `vip:removed` and replaced by NALED's 2019 article and the Kurir biography.
- Every test is offline: local `HttpServer`, stub `UrlChecker`, `ArchiveLookup`, `FakeWayback`.
  Tests opt into `HostPolicy.AnyHost`; production refuses private hosts.
- A dev server started inside the sandbox is not reachable from the user's Chrome; even with the
  sandbox disabled the Claude in Chrome extension dropped. Visual checks of the viewer are the
  user's to do.
- Gradle dependency verification: `./gradlew --write-verification-metadata sha256 help` generates
  the file; the Node.js tarball the web build downloads is platform-specific, so it is listed under
  `<trusted-artifacts>` rather than pinned.
- Sonnet agents did the Serbian translations (checked against PROCITAJME as glossary), the CLI
  `run()` refactor and the mutant-killing tests; each was verified by running the suite.

## How to resume

```
cd ~/code/ghdl/vip
git status --short | wc -l        # expect the uncommitted work, or 0 after "cp"
CI=true ./gradlew verify          # must be green before anything else
```
"cp" means commit and push to main, no attribution lines, trunk-based, no feature branches.
Baby Steps TDD for every change; run tests in CI mode after each step.
