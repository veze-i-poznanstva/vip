import { describe, expect, it } from 'vitest';
import { buildIndex } from './graph';
import { connectionsOf, connectionTitle, edgeLabel, githubLinks, groupSources, panelContent, period, roleNames, statesOf, isEnded, wikidataPage } from './details';
import type { RoleLabels, Source } from './details';

const dictionary: Record<string, string> = {
  'edge.OwnershipConnection': 'owns',
  'edge.WorkConnection': 'works at',
  'period.since': 'since {start}',
  'period.until': 'until {end}',
  'period.range': '{start} – {end}',
  'period.unknown': 'dates not recorded'
};
const t = (key: string, params: Record<string, string | number> = {}) =>
  (dictionary[key] ?? key).replace(/\{(\w+)\}/g, (m, n) => (n in params ? String(params[n]) : m));

const index = buildIndex([
  { data: { id: 'a', label: 'Ana', nodeType: 'person' } },
  { data: { id: 'b', label: 'Firma', nodeType: 'organization' } },
  { data: { id: 'c', label: 'Cile', nodeType: 'person' } },
  { data: { id: 'e1', source: 'a', target: 'b', label: 'owns', kind: 'OwnershipConnection', share: 50 } },
  { data: { id: 'e2', source: 'c', target: 'a', label: 'friend' } },
  { data: { id: 'loop', source: 'c', target: 'c', label: 'self' } }
]);

describe('connectionsOf', () => {
  it('lists each edge touching the node with the other party and the direction', () => {
    const rows = connectionsOf(index, 'a');
    expect(rows.map((r) => [r.edge.id, r.other?.id, r.direction])).toEqual([['e1', 'b', 'out'], ['e2', 'c', 'in']]);
  });

  it('is empty for an unknown node', () => {
    expect(connectionsOf(index, 'zzz')).toEqual([]);
  });

  it('does not crash when a node is both ends of an edge', () => {
    const rows = connectionsOf(index, 'c');
    expect(rows.map((r) => [r.edge.id, r.other?.id, r.direction])).toEqual([['e2', 'a', 'out'], ['loop', 'c', 'self']]);
  });

  it('leaves the other party undefined when the edge points at a missing node', () => {
    const odd = buildIndex([{ data: { id: 'a', label: 'Ana' } }, { data: { id: 'x', source: 'a', target: 'gone', label: 'l' } }]);
    expect(connectionsOf(odd, 'a')[0].other).toBeUndefined();
  });
});

describe('period', () => {
  it('says since for a start alone', () => expect(period('2003', undefined, t)).toBe('since 2003'));
  it('shows both ends at the precision given', () => expect(period('2013', '2016-04', t)).toBe('2013 – 2016-04'));
  it('says until for an end alone', () => expect(period(undefined, '1994', t)).toBe('until 1994'));
  it('says dates are not recorded, never ongoing', () => {
    expect(period(undefined, undefined, t)).toBe('dates not recorded');
    expect(period('', '', t)).toBe('dates not recorded');
  });
});

describe('connectionTitle', () => {
  it('uses the translated kind and the share', () => {
    expect(connectionTitle({ kind: 'OwnershipConnection', share: 50, label: 'x' }, t, 'en')).toBe('owns 50%');
  });
  it('falls back to the type label, then the converter label', () => {
    expect(connectionTitle({ kind: 'Odd', typeLabel: 'Odd connection', label: 'x' }, t, 'en')).toBe('Odd connection');
    expect(connectionTitle({ kind: 'Odd', label: 'x' }, t, 'en')).toBe('x');
    expect(connectionTitle({ label: 'friend' }, t, 'en')).toBe('friend');
  });
  it('adds the roles in the given language when present', () => {
    const roles: RoleLabels[] = [{ en: 'chair', sr: 'predsednik' }, { en: 'member' }];
    expect(connectionTitle({ kind: 'WorkConnection', roles, label: 'x' }, t, 'sr')).toBe('works at (predsednik, member)');
    expect(connectionTitle({ kind: 'WorkConnection', roles, label: 'x' }, t, 'en')).toBe('works at (chair, member)');
  });
});

describe('roleNames', () => {
  it('picks the name in the given language, else the English one, else any', () => {
    expect(roleNames([{ en: 'Mayor', sr: 'Gradonačelnik' }], 'sr')).toEqual(['Gradonačelnik']);
    expect(roleNames([{ en: 'Mayor' }], 'sr')).toEqual(['Mayor']);
    expect(roleNames([{ es: 'Alcalde' }], 'sr')).toEqual(['Alcalde']);
  });
  it('keeps a plain string role, drops anything else and tolerates no roles', () => {
    expect(roleNames(['chair', 7, null, {}], 'en')).toEqual(['chair']);
    expect(roleNames(undefined, 'en')).toEqual([]);
  });
});

describe('edgeLabel', () => {
  it('draws the translated kind with the share', () => {
    expect(edgeLabel({ kind: 'OwnershipConnection', share: 50, label: 'x' }, t)).toBe('owns 50%');
  });
  it('keeps the converter label when no kind is known', () => {
    expect(edgeLabel({ label: 'poslovni partneri' }, t)).toBe('poslovni partneri');
    expect(edgeLabel({ kind: 'Unmapped', label: 'raw' }, t)).toBe('raw');
  });
  it('draws a record\'s own label in the given language when the kind has no translation', () => {
    const labels = { en: 'Car provided', sr: 'Ustupljen automobil' };
    expect(edgeLabel({ kind: 'Connection', label: 'Ustupljen automobil', labels }, t, 'en')).toBe('Car provided');
    expect(edgeLabel({ kind: 'Connection', label: 'Ustupljen automobil', labels }, t, 'sr')).toBe('Ustupljen automobil');
    expect(edgeLabel({ kind: 'Connection', label: 'Ustupljen automobil', labels }, t, 'de')).toBe('Ustupljen automobil');
    expect(connectionTitle({ kind: 'Connection', label: 'Ustupljen automobil', labels }, t, 'en')).toBe('Car provided');
  });
  it('does not add roles to the drawn label', () => {
    expect(edgeLabel({ kind: 'WorkConnection', roles: [{ en: 'chair' }], label: 'x' }, t)).toBe('works at');
  });
});

describe('groupSources', () => {
  const s = (url: string, domain: string, status?: string): Source => ({ url, domain, status });

  it('is empty without sources, and tolerates undefined', () => {
    expect(groupSources([])).toEqual({ groups: [], sourceCount: 0, domainCount: 0, publishedDomains: 0 });
    expect(groupSources(undefined).groups).toEqual([]);
  });

  it('groups by domain in first-seen order', () => {
    const g = groupSources([s('u1', 'a.rs'), s('u2', 'b.rs'), s('u3', 'a.rs')]);
    expect(g.groups.map((x) => [x.domain, x.sources.length])).toEqual([['a.rs', 2], ['b.rs', 1]]);
    expect(g.sourceCount).toBe(3);
    expect(g.domainCount).toBe(2);
    expect(g.publishedDomains).toBe(2);
  });

  it('does not count removed sources towards independent domains', () => {
    const g = groupSources([s('u1', 'a.rs'), s('u2', 'b.rs', 'removed')]);
    expect(g.publishedDomains).toBe(1);
    expect(g.domainCount).toBe(2);
  });

  it('counts a www domain the same as the bare one', () => {
    expect(groupSources([s('u1', 'www.a.rs'), s('u2', 'a.rs')]).groups).toHaveLength(1);
  });

  it('handles only removed sources', () => {
    expect(groupSources([s('u1', 'a.rs', 'removed')]).publishedDomains).toBe(0);
  });
});

describe('githubLinks', () => {
  it('builds record, raw and history links', () => {
    expect(githubLinks('data/entities/x.ttl', 12)).toEqual({
      record: 'https://github.com/veze-i-poznanstva/vip/blob/main/data/entities/x.ttl#L12',
      raw: 'https://raw.githubusercontent.com/veze-i-poznanstva/vip/main/data/entities/x.ttl',
      history: 'https://github.com/veze-i-poznanstva/vip/commits/main/data/entities/x.ttl'
    });
  });
  it('omits the line anchor when no line is known', () => {
    expect(githubLinks('data/a.ttl')?.record).toBe('https://github.com/veze-i-poznanstva/vip/blob/main/data/a.ttl');
  });
  it('is null without a file', () => {
    expect(githubLinks(undefined)).toBeNull();
    expect(githubLinks('')).toBeNull();
  });
});

describe('wikidataPage', () => {
  it('turns the entity IRI into the page a reader can open', () => {
    expect(wikidataPage('http://www.wikidata.org/entity/Q114870170')).toEqual({ id: 'Q114870170', url: 'https://www.wikidata.org/wiki/Q114870170' });
  });
  it('is null for anything that is not a Wikidata entity IRI', () => {
    expect(wikidataPage(undefined)).toBeNull();
    expect(wikidataPage('')).toBeNull();
    expect(wikidataPage('https://www.wikidata.org/wiki/Q42')).toBeNull();
    expect(wikidataPage('http://www.wikidata.org/entity/Q0')).toBeNull();
    expect(wikidataPage('http://www.wikidata.org/entity/Q42/extra')).toBeNull();
    expect(wikidataPage(42)).toBeNull();
  });
});

describe('panelContent', () => {
  it('is nothing without a focus or edge', () => {
    expect(panelContent(index, null, null)).toBeNull();
  });
  it('shows the focus node', () => {
    expect(panelContent(index, 'a', null)).toMatchObject({ kind: 'node', node: { id: 'a' } });
  });
  it('shows a selected edge with both ends, over the focus', () => {
    expect(panelContent(index, 'a', 'e1')).toMatchObject({ kind: 'edge', edge: { id: 'e1' }, from: { id: 'a' }, to: { id: 'b' } });
  });
  it('shows an edge even without a focus', () => {
    expect(panelContent(index, null, 'e1')?.kind).toBe('edge');
  });
  it('ignores an unknown edge and falls back to the node', () => {
    expect(panelContent(index, 'a', 'nope')?.kind).toBe('node');
    expect(panelContent(index, null, 'nope')).toBeNull();
  });
  it('ignores an unknown focus', () => {
    expect(panelContent(index, 'nope', null)).toBeNull();
  });
});

describe('statesOf', () => {
  it('returns the history as is when present', () => {
    const history = [{ start: '2019', end: '2022-07', share: 40 }, { start: '2022-08', share: 100 }];
    expect(statesOf({ history, share: 100 })).toBe(history);
  });
  it('builds one state from the edge when there is no history', () => {
    const sources = [{ url: 'https://a.rs/x', domain: 'a.rs' }];
    expect(statesOf({ share: 50, start: '2019', end: '2020', sources })).toEqual([
      { uri: undefined, share: 50, roles: undefined, start: '2019', end: '2020', comment: undefined, file: undefined, line: undefined, sources }
    ]);
  });
  it('treats an empty history as absent', () => {
    expect(statesOf({ history: [], share: 10 })).toHaveLength(1);
  });
});

describe('isEnded', () => {
  it('is true only for a non-empty string end', () => {
    expect(isEnded({ end: '2022-07' })).toBe(true);
    expect(isEnded({ end: '' })).toBe(false);
    expect(isEnded({})).toBe(false);
    expect(isEnded({ end: 2022 })).toBe(false);
    expect(isEnded({ end: null })).toBe(false);
  });
});

describe('edgeLabel for an ended relationship', () => {
  it('appends the end in parentheses', () => {
    expect(edgeLabel({ kind: 'OwnershipConnection', share: 100, end: '2022-07', label: 'x' }, t)).toBe('owns 100% (until 2022-07)');
  });
  it('leaves an open or unknown end alone', () => {
    expect(edgeLabel({ kind: 'OwnershipConnection', share: 100, label: 'x' }, t)).toBe('owns 100%');
  });
  it('also marks the fallback label', () => {
    expect(edgeLabel({ label: 'friend', end: '2001' }, t)).toBe('friend (until 2001)');
  });
});
