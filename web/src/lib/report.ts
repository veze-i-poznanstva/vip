const REPOSITORY = 'https://github.com/veze-i-poznanstva/vip';

/** What a reader is reporting: an entity, or a path between two entities. */
export interface ReportSubject {
  subject: string;
  uri?: string;
}

/** A prompt the reader pastes to an AI agent, which proposes a correction as a pull request. */
export function reportPrompt(report: ReportSubject, description: string): string {
  const what = description.trim();
  return [
    'A reader reports a possible error in VIP (Veze i poznanstva), an open dataset of connections between',
    `people and organisations in Serbia: ${REPOSITORY}`,
    '',
    `Reported: ${report.subject}`,
    ...(report.uri ? [`Record: ${report.uri}`] : []),
    what ? `What the reader says is wrong: ${what}` : 'The reader has not said what is wrong yet: ask the contributor what is wrong.',
    '',
    'Help them correct it. Speak Serbian (Latin script) unless they prefer another language. Explain each step',
    'and ask for permission before it.',
    `Follow the rules in ${REPOSITORY}/blob/main/docs/agent-prompt.md, from "First, check where you are`,
    'running", doing the steps on tools, GitHub, the rules, second reading, checks and the pull request.',
    'Replace its steps on choosing whom to add and adding data with this task:',
    '- Find the record in data/ and read what the cited sources say. Do not assume the report is right, or wrong.',
    '- VIP is a secondary source: it aggregates what reputable media and official registries report. If the record',
    '  misstates its source or confuses two people, correct the record.',
    '- If a cited source no longer says it, mark that citation removed; a claim left without two sources from two',
    '  domains must be removed. If a reputable source publishes a correction, add it and correct the record.',
    '- If the record does say what its sources say, change nothing. Explain that the source must be corrected first,',
    '  by the outlet or registry that published it, and that VIP then follows.',
    '- Keep a citation found wrong with status removed and a comment saying why.',
    'Open a pull request that says what was reported, what the sources say, and what changed.'
  ].join('\n');
}

/** A prefilled report for GitHub, for readers without an AI agent. */
export function issueUrl(report: ReportSubject, description: string): string {
  const body = [`Prijavljeno: ${report.subject}`, ...(report.uri ? [`Zapis: ${report.uri}`] : []), '', description.trim()].join('\n');
  const params = new URLSearchParams({ title: `Prijava greške: ${report.subject}`, body });
  return `${REPOSITORY}/issues/new?${params}`;
}

/** The address `?ispravka=1` opens the corrections page, so the README can link straight to it. */
export function wantsCorrection(search: string): boolean {
  return new URLSearchParams(search).has('ispravka');
}

export function withoutCorrection(search: string): string {
  const params = new URLSearchParams(search);
  params.delete('ispravka');
  const query = params.toString();
  return query ? `?${query}` : '';
}
