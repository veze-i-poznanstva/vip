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
    '- VIP aggregates what reputable media and official registries report. If the record misstates its source,',
    '  correct the record. If a reputable source supports the correction, add it and correct the record.',
    '- If the record does say what its sources say and nothing better is available, change nothing and explain why.',
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
