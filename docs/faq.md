# Frequently asked questions

*Srpska verzija: [faq.sr.md](faq.sr.md)*

Short answers to what people ask first. Back to the [README](../README.md).

## I want to report corruption. Can I do it here?

No. VIP is a secondary source: it records what reputable media and official registries have
already published, and it does not investigate, take tips or publish anything first.

Get it published somewhere else first. Take what you know to a newsroom that does investigative
reporting, or to the authority responsible for it. Do not put it in a report on this site: reports
here are public issues on GitHub under your account, so they protect neither you nor the people
you name.

Once at least two reputable sources on two different sites have published it, it can be recorded
here, with those sources quoted. Then use **Information wrong?** on the page, or add the records
yourself with the [agent quickstart prompt](agent-prompt.md). The [data guide](data-guide.md)
says what counts as a source.

## Is this legal?

We are not lawyers, no lawyer has reviewed VIP, and nothing here is legal advice. We asked an AI
assistant to check the law. This is what it pointed to, with links so that you can read the texts
yourself; it may be wrong or out of date.

What speaks for it:

- **Nothing is published here first.** Every record repeats what reputable media or official
  registries have already published, quotes the passage and links to it. VIP adds no claim of its
  own.
- **Data protection law makes room for freedom of expression.** Serbia's
  [Law on Personal Data Protection](https://www.paragraf.rs/propisi/zakon_o_zastiti_podataka_o_licnosti.html) sets most of its rules aside for processing done for
  journalistic research and the publication of information in the media, where that is necessary
  to protect freedom of expression and information (Article 88). The EU's [GDPR](https://eur-lex.europa.eu/eli/reg/2016/679/oj) asks its
  member states for the same kind of exemption for journalistic purposes (Article 85).
- **Office-holders have to put up with scrutiny.** The
  [Law on Public Information and Media](https://www.paragraf.rs/propisi/zakon_o_javnom_informisanju_i_medijima.html) obliges a holder of public or political office to
  tolerate critical opinions about their work (Article 8), and lets information from private life
  be published without consent where the public's interest in it prevails, in particular when it
  concerns such an office-holder (Article 93).
- **The law itself looks at an official's circle.** Anti-corruption and anti-money-laundering laws
  treat an official's relatives and close associates as relevant; the
  [editorial standard](data-guide.md#editorial-standard) cites them.

What that does not settle:

- **VIP is not a registered media outlet.** The provisions above are written for journalism and
  the media, and the media law does not count a website as media unless it is entered in the Media
  Register (Article 37). Whether they cover a volunteer dataset like this one is a question for a
  lawyer or a court.
- **Already published does not mean free to collect.** Under the data protection law, gathering,
  grouping and making data available are each processing of personal data (Article 4), whether or
  not the data were public before.
- **Quoting a source is not immunity.** A person who believes a record harms their rights can go
  to court. Two sources, quoted word for word with their attribution, are how we try to be
  accurate; we do not know that they are a defence.
- **Which country's law applies** depends on where the publisher and the people described are. We
  have not had that assessed.

If you are in the data and believe a record is wrong or should not be here, use **Information
wrong?** on the page; [Privacy](privacy.md) says what happens next. If you are a lawyer and see a
mistake in this answer, tell us the same way.

## Who should not be in the database?

Most people. VIP is about public office and public money, so a person is here only through a
public role, or through something sources have documented that ties them to one. These stay out:

- **People with no public role** and no documented dealing with public office or public money.
- **A relative, a friend or a schoolmate for that tie alone.** Kinship makes a person relevant
  only together with something they did or received where it could matter: a job, a company, a
  contract, an asset. The [editorial standard](data-guide.md#editorial-standard) gives the
  reasoning.
- **Ordinary employees and junior officials**, unless sources report them for such a dealing.
  Scrutiny belongs to those who decide: elected and appointed office-holders, directors, board
  members, owners.
- **Children.** No minor is recorded, whoever the parent is.
- **People a source names only in passing:** a witness, a victim, a bystander.
- **Anyone known from a single source,** a rumour, a social network or an anonymous tip.
- **A person whose connection the sources themselves dispute.**

For those who are in the data, private life stays out all the same: no personal number, no home
address, no private contact details, nothing on health or on family beyond the documented kinship,
and party membership only when the person made it public.

If you are in the data and these rules say you should not be, that is our error: use
**Information wrong?** and the record is removed.

## I do not have an AI account. What can I still do?

Almost everything. The graph, the search, the records with their sources
and the Leads (Tragovi) run in your browser and need no account of any kind.

An assistant comes in at three places, and each has a way without paying:

- **Ask (Pitaj)** opens Claude or ChatGPT with your question. Both have a free plan with limited
  use. **Copy the prompt** works with any other assistant that can read a web page.
- **Contributing data** with the [agent quickstart prompt](agent-prompt.md) needs an assistant
  that can run commands and edit files on your computer. Google Antigravity has a free plan for
  individuals with weekly limits. A free allowance can run out in the middle of the work; what is
  done stays on your computer and you go on when the allowance returns.
- **Without any assistant** you can send a report with **Information wrong?** (it needs a GitHub
  account), or write the records by hand from the [data guide](data-guide.md) and check them with
  `./gradlew verify`.

Free plans and their limits are the makers' decision and change often. What is said here was
checked on their pricing pages in November 2026; look there before you rely on it. VIP is not
connected to any of them and sends them nothing: your question reaches an assistant only when you
click or paste, as [Privacy](privacy.md) describes.

*Last changed: 15 November 2026.*
