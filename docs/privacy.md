# Privacy

*Srpska verzija: [privacy.sr.md](privacy.sr.md)*

What the viewer does with your data, which is almost nothing, and what the dataset does with the
data of the people in it. Back to the [README](../README.md).

## In your browser

The viewer at veze-i-poznanstva.github.io/vip runs entirely in your browser. It downloads the
graph, the records and the rules from the same site and does everything else, including every
query, on your device. There is no server of ours, no account, no analytics, no cookies, and no
fonts or scripts loaded from anyone else.

One thing is stored in your browser: the language you choose, under the key `lang`, written only
when you pick a language and read on your next visit. Clearing the site's data in your browser
removes it.

When you share a query from Advanced (Napredno), the query text travels in the link you copy, and nowhere
else.

## Hosting

GitHub hosts the site through GitHub Pages. GitHub logs the IP address of every visit for security
purposes, whether or not the visitor is signed in to GitHub, and holds those logs under
[GitHub's privacy statement](https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement).
We never see them. See
[GitHub Pages: data collection](https://docs.github.com/en/pages/getting-started-with-github-pages/what-is-github-pages#data-collection).

## When you click out

Nothing leaves your browser until you click a link or paste a text somewhere. Then the site you
reach sees the visit, under its own terms:

- **Open in Claude** and **Open in ChatGPT** (the Ask (Pitaj) panel) open the assistant with your
  question and an instruction to read the published brief. Your question goes to Anthropic or
  OpenAI only then, under your account with them and their terms. **Copy the prompt** keeps
  everything on your device until you paste it.
- **Send a report** (Information wrong?) opens a prefilled issue on GitHub. Sending it publishes
  the report under your GitHub account. **Fix it yourself with AI** only copies a prompt.
- Links to sources, archived copies, Wikipedia and the repository take you to those sites.

## The people in the data

VIP publishes personal data: the names of people in public roles and their documented connections
to organisations and to one another, with dates and sources. That is the dataset's purpose:
information in the public interest about people who hold public office, lead parties, own
companies that deal with the state, or stand in their immediate circle, aggregated from what
reputable media and official registries have already published. [Why map
connections](why-connections.md) says why, and the [data guide](data-guide.md) says how.

Rules we keep, whoever the person is:

- Every entity and connection cites at least two public sources from two different domains, each
  with an archived copy, and quotes the passage it relies on.
- We record what a source says, with its attribution, and nothing more. A connection is a lead,
  not an accusation; the records state connections, not guilt.
- We record no personal identification number (JMBG), no personal tax number, no home address and
  no private contact details. A company's registration number is public registry data.
- Party affiliation is recorded only when the person made it public, by standing for the party or
  holding office in it.
- A connection ended in the sources is marked ended; a missing end date means unknown, never
  "current".

## Corrections and history

If you are in the data and a record is wrong, use **Information wrong?** on the page. A record
that misstates its source or confuses two people of the same name is corrected. A record that says
what its sources say is not changed by us: the source has to be corrected first, by the outlet or
registry that published it, and VIP follows. Where reputable sources contradict each other, VIP
does not choose between them; the disputed claim is left out. A citation found wrong is deleted from the file;
the repository's history keeps earlier versions of every file, and the commit that deletes it
says why. Both report routes are public.

*Last changed: 11 November 2026.*
