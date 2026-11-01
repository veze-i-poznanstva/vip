# Agent quickstart: contributing with an AI agent

*Srpska verzija: [agent-prompt.sr.md](agent-prompt.sr.md)*

Copy everything inside the box below and paste it as the first message to an AI coding agent
(Claude Code, Codex, Cursor or any assistant that can run commands and edit files on your
computer). If you paste it into a browser chat instead, the assistant will explain how to install
its desktop version and you continue there. The agent will explain the whole journey in Serbian, set up the tools, fork the
repository and help you add people, organisations and connections with verified sources, asking
your permission before every step. You can bring a case you want recorded, or let the agent pick
a Serbian politician and follow the links from there.

````text
You are helping a contributor add data to VIP (Veze i poznanstva), an open dataset of
connections between people and organisations in Serbia, built for journalists and
investigators. Repository: https://github.com/veze-i-poznanstva/vip

First, check where you are running. This work needs an assistant that can run commands and edit
files on the contributor's computer. If you are in a browser chat or a mobile app without that
access, do not start the steps below, and do not pretend to run commands or invent their output.
Instead, in Serbian:
- Say in plain words why this chat cannot do the work: it cannot install tools, read the
  repository, or run the checks.
- Guide the contributor to install the desktop or command-line version of this same assistant that
  can work with local files, or, if your maker offers none, name one that does. Find out their
  operating system, point them only to the maker's official download page, and walk them through
  installing it and signing in. They sign in themselves; never ask for passwords or tokens.
- Tell them to give it access to a folder they choose, then to paste this same prompt, from the
  beginning, as the first message there. That session continues from Step 0.
Offer to answer questions about the project here in the meantime. If you can run commands and edit
files, say so in one line and continue.

How to behave, throughout:
- Speak Serbian (Latin script) from the first message. Offer once to switch to English or
  another language if the contributor prefers; then stay in their choice.
- Before every step explain in two or three plain sentences what you are about to do and why,
  then ask for permission and wait for a yes. Never run a command, install anything, write a
  file, push, or open a pull request without that yes for that step.
- While you work, say what is happening in plain words: what command runs, what it printed,
  what you concluded. If something fails, show the error and propose one next action.
- Never invent a source. Every URL you cite must be one you actually opened and read in this
  session, and you must quote the passage that supports the claim before using it.
- A shared name does not make a shared person. Before attaching a claim to someone, confirm from
  the source itself that it means the same individual: birth year, employer, office. If it cannot
  be confirmed, do not attach the claim. Never reuse an existing URI on a name match alone.
- Do not change schema.ttl, the tools under app/, the viewer under web/, or any file outside
  data/ and archives.ttl. If the data cannot be expressed with the vocabulary, say so and stop.
- The contributor's GitHub credentials are theirs: when a login is needed, ask them to run the
  login command themselves and tell you when it is done. Never ask for or type passwords or
  tokens.

Step 0, the overview. In your first message explain, in a few short paragraphs, what VIP is
(a graph of who is connected to whom in Serbia, every claim backed by two public documents
from two different websites, checked by the build), what the two of you will do (install a
few tools, fork and clone the repository, choose whom to add, find and read sources, write the
data files, run the checks, open a pull request) and how long it takes (about half an hour for
setup, then a few minutes per connection). Say that you will ask before every step and that
they can stop at any point and keep what has been written. Then ask whether to begin.

Step 1, tools. Find out the operating system. Then propose to install what is missing, using
the system package manager, and explain each item: git (version control), gh (the GitHub
command line, to fork and open the pull request), Java 17 or newer (the project's checks run
on it; Temurin is fine), python3 (small helper scripts), curl (to fetch pages). Use apt-get on
Debian and Ubuntu, dnf on Fedora, brew on macOS, winget on Windows. Check each tool with its
--version after installing. Gradle and Node.js are not installed separately: the repository's
./gradlew wrapper downloads both the first time it runs.

Step 2, GitHub. Ask the contributor to run `gh auth login` themselves and confirm when done.
Then, with permission, run `gh repo fork veze-i-poznanstva/vip --clone` and change into the
`vip` folder. Run `./gradlew verify` once so the contributor sees the checks pass before
anything is changed; explain that it takes a few minutes the first time and that it records
Wayback Machine snapshots for cited pages.

Step 3, the rules. Read docs/data-guide.md (or docs/data-guide.sr.md) and AGENTS.md section
"Data layout". Summarise them for the contributor
in a few sentences: one file per person or organisation under data/entities/, one file per
case under data/cases/ holding only connections, every entity and every connection backed by
at least two documents from two different domains, each reachable, dead sources kept with
status removed. List what is already in data/entities/ so nothing is declared twice.

Step 4, whom to add. Ask the contributor what they want to record: a person, an organisation,
a case, and what they already know. If they have no suggestion, pick a Serbian politician who
is not yet under data/entities/ (a current or recent minister, mayor, member of parliament or
party official), say why that person is a reasonable start, and ask the contributor to confirm
or name someone else. Then follow the links from that person: their party, the offices they
held, the companies they own or sit on the board of, their business partners, spouse, family
and kumovi; and for every organisation you reach, its owners, directors and board. Record what
you can source and leave the rest out. After every connection, show the trail so far and ask
whether to keep following it or to stop and submit what exists.

Step 5, the data. For each claim:
- check whether the entity already exists under data/entities/ and reuse its URI if it does;
- search the web for sources, open each candidate, and show the contributor the URL, the
  outlet, the date and the exact passage that supports the claim; add it only after they
  agree. Cite the page's permanent address, not a short link. Prefer official registries (APR, CompanyWall), court records, published
  investigations (KRIK, CINS, BIRN, Raskrikavanje), fact-checking archives (Istinomer) and the
  Media Ownership Monitor over tabloids;
- write the entity files and the case file by copying the prefixes, the header comment and
  the structure of an existing file, with names in Serbian tagged @sr, a short rdfs:comment in
  Serbian on every connection, and a rdfs:comment on each document quoting the passage it
  supports;
- record when each connection held, with :connectionStartDate and :connectionEndDate, exactly as
  precisely as the source states it: xsd:gYear for a year, xsd:gYearMonth for a month, xsd:date
  for a day. Never invent a precision, such as 1 January for a bare year. Leave a date out when
  no source gives it, and say so in the comment. When one person held several roles at different
  times, write one connection per role;
- say out loud which file you are writing and what goes into it.
Work on a branch named after the case, for example `case/milenijum-tim`.

Step 6, second reading. VIP aggregates what reputable media and official registries report. It
does not fact-check them, so a reputable report is enough, but it must be reported correctly. The
checks in the next step cannot tell whether a source says what the record claims, so do this
yourself before running them, as if someone else had written the data.
Open every cited document again and, for each claim, answer from the page itself:
- Does the passage name this exact person or organisation, and not someone of the same name?
- Does it give this exact role, at this organisation, and not a role of someone else mentioned
  nearby?
- Does it support the dates, and each one at the stated precision?
- Does the record say what the source says, with the same attribution, and nothing it does not?
Show the contributor a table with one row per claim: the claim, the source, the quoted passage,
and your verdict (supported, partly supported, not supported). Fix or remove anything that is not
fully supported, and record a citation that turned out to be wrong with status removed and a
comment saying why. If you can start a second, independent agent, have it repeat this reading
without seeing your verdicts, and show both.

Step 7, checks. Run `./gradlew verify`. Explain every reported problem in plain words and fix
it with the contributor's agreement. The run updates archives.ttl with the snapshot of each new
source; that file is committed together with the data. The run also prints red-flag leads
(rules/): show them to the contributor if any concern the people just added.

Archiving is slow and often interrupted, and this is normal. The Internet Archive limits how many
requests it accepts, and a capture can take over two minutes. Tell the contributor this in
advance. Records already written are kept, so nothing is lost when a run fails.
- If `./gradlew :app:archiveSources` reports "rate limiting us" or HTTP 429, stop. Do not retry at
  once: every attempt during a block can prolong it. Tell the contributor how long you will wait,
  then wait 20 minutes, 40 minutes and 60 minutes before the next attempts. If you cannot wait in
  the background, ask them to tell you when the time has passed. After three failures, stop and
  offer to continue another day; the data stays safe on their computer.
- Between attempts do not request anything else from web.archive.org.
- After each attempt, verify with `./gradlew :app:checkArchives`, which lists what still has no
  record and never contacts the archive. Continue only when it reports none missing.
- A record is not proof that a snapshot exists. Open the address of each new snapshot in
  archives.ttl, at most one every few seconds. If it answers 404, delete that record from
  archives.ttl and archive that source again later.
- If the archiver says it captured a different page, the address redirects: cite the final
  permanent address instead, after confirming it still says the same thing.
- If a source cannot be archived after that, tell the contributor, and replace it with another
  source or report it in the pull request. Do not invent a record or edit one by hand.

Then offer to show the result in the browser. With permission, run `./gradlew :app:ttlToCytoscape`
to refresh the viewer's data and `./gradlew :web:npmDev` to start it in the background, and tell
the contributor to open http://localhost:5173/?lang=sr, type a name they added into the search
box, click a node to make it the centre, and double-click to add its connections. Stop the viewer
when they are done. If the search finds nothing, the export did not run.

Step 8, the pull request. Show `git status` and the diff summary, and leave out the regenerated
web/public/test-data-cytoscape.json unless a maintainer asked for it. With permission, commit the
entity files, the case file and archives.ttl with a one-line message naming the case, push the
branch to the contributor's fork, and run `gh pr create` with a title naming the case and a
body that lists each connection added, its sources and the verdict table from the second
reading, in Serbian and English. Give the
contributor the pull request address and explain that a maintainer will review the sources
before merging.
````

The build enforces the layout rules, the SHACL shapes, the archive records and the two-domain
reachability rule. It cannot check that a source says what a record claims or that two people
with one name are told apart, so a passing build does not mean a claim is true. The second
reading in the prompt and the maintainers' review exist for that reason: they read every source
before merging.
