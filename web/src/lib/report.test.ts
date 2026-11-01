import { describe, expect, it } from 'vitest';
import { issueUrl, reportPrompt } from './report';

describe('reportPrompt', () => {
  const prompt = reportPrompt({ subject: 'Ana Brnabić', uri: 'https://w3id.org/vip/rs/Ana_Brnabic' }, 'Pogrešan datum.');

  it('names what is reported, its address and what the reader says is wrong', () => {
    expect(prompt).toContain('Ana Brnabić');
    expect(prompt).toContain('https://w3id.org/vip/rs/Ana_Brnabic');
    expect(prompt).toContain('Pogrešan datum.');
  });

  it('sends the agent to the contributor rules and asks for a pull request, in Serbian, with permission', () => {
    expect(prompt).toContain('https://github.com/veze-i-poznanstva/vip/blob/main/docs/agent-prompt.md');
    expect(prompt).toMatch(/pull request/i);
    expect(prompt).toMatch(/Serbian/);
    expect(prompt).toMatch(/permission/i);
  });

  it('tells the agent the rule: check what the sources say, and do not assume the report is right', () => {
    expect(prompt).toMatch(/what the cited sources say/i);
    expect(prompt).toMatch(/do not assume/i);
  });

  it('works without a description or an address', () => {
    const bare = reportPrompt({ subject: 'NALED' }, '  ');
    expect(bare).toContain('NALED');
    expect(bare).toMatch(/ask the contributor what is wrong/i);
    expect(bare).not.toContain('undefined');
  });
});

describe('issueUrl', () => {
  it('opens a new issue with the subject and description encoded', () => {
    const url = new URL(issueUrl({ subject: 'Ana & NALED' }, 'Pogrešno?'));
    expect(url.origin + url.pathname).toBe('https://github.com/veze-i-poznanstva/vip/issues/new');
    expect(url.searchParams.get('title')).toBe('Prijava greške: Ana & NALED');
    expect(url.searchParams.get('body')).toContain('Pogrešno?');
  });

  it('includes the address of the record when known', () => {
    const url = new URL(issueUrl({ subject: 'A', uri: 'https://w3id.org/vip/rs/A' }, ''));
    expect(url.searchParams.get('body')).toContain('https://w3id.org/vip/rs/A');
  });
});
