# Data guide

*Srpska verzija: [data-guide.sr.md](data-guide.sr.md)*

How facts are recorded in VIP and how to add your own. Back to the [README](../README.md).

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
  `:WorkConnection` with one or more work roles and optional start and end dates. A public
  office is a `:WorkConnection` too: a mayor's with the city (`rs:Grad_Beograd`, a
  `:GovernmentOrganization`), a minister's with the Government (`rs:Vlada_Srbije`), with the
  role `vip:mayor` or `vip:minister` and the portfolio in the comment; the owner-in-office rule
  reads exactly these. Schooling is an `:AlumniEducationalConnection`. Company ownership is a `vip:OwnershipConnection` with an
  optional share (a decimal percentage, 0 to 100). Each connection carries its own documentation,
  so the evidence for "X sits on the board of Y" is separate from the evidence that X and Y exist.
  Kinship is no exception: siblings are a `vip:SiblingConnection` and a parent and child a
  `vip:ParentChildConnection` (`vip:parent`, `vip:child`), recorded in
  `data/cases/porodicne-veze.ttl` with their own sources. The shapes refuse PoderVocab's bare
  family properties (`:hasSibling`, `:hasParent`) on a person, because they leave no place for
  the evidence.
- **Time is part of the claim.** A connection carries `:connectionStartDate` and
  `:connectionEndDate` as precisely as its source states them: `xsd:gYear`, `xsd:gYearMonth` or
  `xsd:date`, never an invented 1 January. A missing date means unknown, not ongoing. Roles held
  at different times are separate connections. The shapes refuse an end before a start.
  A relationship has one identity but is written as one dated record per state: Alo Media System
  owned 100% of Best Media Team from 24 December 2025 and 90% from February 2026, so there are two
  `vip:OwnershipConnection` records between the same parties, the first ending in `2026-02` where
  the second starts, each with its own sources. Nothing new is declared; the build derives the
  relationship from the records (same kind, same parties, same roles). For each company, the
  shares of records that may apply at the same time must not total more than 100%; records known
  to follow one another are not summed, and two undated or open claims that together exceed 100%
  fail the build until one is dated or removed. The viewer merges the records of one relationship
  into one edge labelled with the latest documented state and keeps the history in the details
  panel; a relationship whose latest state has an end date is drawn dashed. A missing end is
  unknown, never "current".
- **Every term has labels in English, Spanish and Serbian.** Every class, property and
  individual in the vocabulary and every SHACL message carries `@en`, `@es` and `@sr` text, so
  user interfaces and validation reports can be rendered in any of the three.
- **Same name, separate records.** Each person has one URI and a name that says who it is, such
  as `Ivan Bošnjak (Zrenjanin)`. Tell people apart by public facts: birth year, office, employer.
  `vip:wikipediaArticle` names the article about the entity itself, not one that only mentions
  it. `vip:wikidataItem` is the entity's Wikidata item as its entity IRI
  (`http://www.wikidata.org/entity/Q114870170`): a permanent number that survives renaming and
  spans scripts and languages, so it pins which namesake is meant. It is an identifier, never a
  source. The build writes it from the Wikipedia article (`./gradlew :app:linkWikidata`), so
  contributors never look it up; an entity without an article is identified by its VIP URI.
  Never record a JMBG or a personal tax number: the shapes refuse a tax ID on a person, while a
  company's registration and tax numbers are public registry data.
- **Three namespaces.** PoderVocab terms use the empty prefix `:` for
  `http://poderopedia.com/vocab/`; the project's own vocabulary additions use `vip:` for
  `https://w3id.org/vip/vocab#`; Serbian instance data uses `rs:` for `https://w3id.org/vip/rs/`,
  so `rs:Sinisa_Mali` is the person and other countries get their own segment.

The vocabulary is extended where PoderVocab had gaps. The additions are marked in `schema.ttl`
under the heading "VIP additions": the ownership connection and its properties, the
godparenthood connection (kumstvo, a kinship-grade bond in Serbian society that PoderVocab
lacks), the family connections (siblings, parent and child), the schoolmate connection (same school, when the class is not known), the link from an organisation to its financial information, governance work roles such
as founder and chair of the board, the document types and the document statuses, and the two
public identifiers of an entity, its Wikipedia article and its Wikidata item.

## The vocabulary

PoderVocab models agents and the connections between them. People and organizations are
`foaf:Person` and `foaf:Organization`. Organizations are specialised into company, political,
governmental, non-governmental, non-profit, international, religious and academic kinds.
Connections are first-class resources with their own taxonomy: friendship, same class,
educational, work role, company acquisition and merger, ownership, membership, financier,
political, sentimental and news-event connections, among others. Provenance is carried by
`SourcedEntity`, `SourceReliability` and `Documentation`. Labels and comments are in English,
Spanish and Serbian.

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
  `alo-media-system.ttl` holds the ownership of the tabloid Alo's publisher. Two cases cut across
  investigations: `javne-funkcije/` holds public offices, one file per public body
  (`vlada-srbije.ttl`, `grad-beograd.ttl`), and `porodicne-veze.ttl` holds kinship. A case may be a
  folder like that when one file would otherwise keep growing; the layout check reads only the
  top-level folder.
  A reviewer reads one case file and sees a whole story with its sources.

Working with an AI coding agent? Paste the quickstart prompt in
[docs/agent-prompt.md](agent-prompt.md) as your first message: it explains the journey,
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
  `:hasDocumentURL`, `:hasDocumentType` and `:hasDocumentStatus vip:published`; a document cited by a connection also needs an
  `rdfs:comment` quoting the passage that supports it (or, when removed, saying why).
- **Blocked sources.** Some sites (Istinomer, the SNS site) refuse automated clients. Cite them
  anyway: the archiver records a Wayback snapshot and the validator reports a warning, not an
  error, as long as the snapshot exists.
- **Archive rate limiting.** The Internet Archive throttles after many requests. The archiver
  keeps what it managed, exits 1 and asks you to rerun later; records already written are kept.
  A block can last an hour or more and retrying during it can prolong it, so wait 20 minutes, then
  longer, and check progress with `./gradlew :app:checkArchives`, which never contacts the archive. It also requests at most 25 new captures per run and lists the rest for the next
  run, so one branch cannot flood the archive. Read the cited addresses before running `verify`
  on a stranger's branch: the archiver will ask the Wayback Machine to fetch each of them.
- **Dead sources.** Never delete a citation. Set `:hasDocumentStatus vip:removed` and add an
  `rdfs:comment` saying what happened; the validator then ignores it as evidence and the archiver
  skips it. Replace it with a live source so the entity still has two domains.
- **Fast checks.** `./gradlew :app:test :app:checkDataLayout :app:validateShacl` needs no network.
  `./gradlew :app:validateData` runs the archiver locally; with the environment variable `CI`
  set to `true` it only checks, as on the build server.
