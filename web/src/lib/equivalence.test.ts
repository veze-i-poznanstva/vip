import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { loadTurtle, select } from './query';
import type { Row } from './query';

/**
 * The browser engine must find exactly the leads the build finds: `red-flags.json` is written by
 * `exportQueryData` from the same rules over the same data, with Jena and its reasoner.
 */
const published = (name: string) => readFileSync(new URL(`../../public/${name}`, import.meta.url), 'utf8');
const rules: { name: string; query: string }[] = JSON.parse(published('rules.json'));
const expected: { name: string; variables: string[]; matches: Record<string, string>[] }[] = JSON.parse(published('red-flags.json'));
const data = loadTurtle(published('data.ttl'));

const plain = (row: Row): Record<string, string> => Object.fromEntries(Object.entries(row).map(([k, v]) => [k, v.value]));
const sorted = (matches: Record<string, string>[]) => matches.map((m) => JSON.stringify(m, Object.keys(m).sort())).sort();

describe('every published rule in the browser engine', () => {
  it('has a build result to compare with', () => {
    expect(expected.map((e) => e.name)).toEqual(rules.map((r) => r.name));
    expect(rules.length).toBeGreaterThan(0);
  });

  for (const rule of rules) {
    it(`${rule.name} gives the same leads as the build`, async () => {
      const result = await select(data, rule.query);
      const build = expected.find((e) => e.name === rule.name)!;
      expect(result.variables).toEqual(build.variables);
      expect(sorted(result.rows.map(plain))).toEqual(sorted(build.matches));
      expect(result.truncated).toBe(false);
    });
  }
});
