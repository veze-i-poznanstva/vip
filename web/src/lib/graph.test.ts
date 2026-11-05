import { describe, expect, it } from 'vitest';
import { buildIndex, egoGraph, expand, filterByType, fold, hoverContent, pathElements, pathSteps, pushTrail, readFocusQuery, readPathQuery, search, shortestPaths, subgraph, writeFocusQuery, writePathQuery } from './graph';
import type { Element } from './graph';

describe('fold', () => {
  it('lowercases and strips diacritics so "brnabic" matches "Ana Brnabić"', () => {
    expect(fold('Ana Brnabić')).toBe('ana brnabic');
  });
});

describe('fold of đ', () => {
  it('maps đ and Đ to d, which Unicode decomposition does not do', () => {
    expect(fold('Đorđe Đukić')).toBe('dorde dukic');
  });
});

describe('fold of Cyrillic', () => {
  it('transliterates Serbian Cyrillic so "Брнабић" finds "Brnabić"', () => {
    expect(fold('Ана Брнабић')).toBe(fold('Ana Brnabić'));
    expect(fold('Љубљана Џин Њего Ђорђе Жарко Ћира Чеда Шиме')).toBe('ljubljana dzin njego dorde zarko cira ceda sime');
  });
});

const elements = [
  { data: { id: 'ana', label: 'Ana Brnabić', nodeType: 'person' } },
  { data: { id: 'ivan', label: 'Ivan Bošnjak', nodeType: 'person' } },
  { data: { id: 'sinisa', label: 'Siniša Mali', nodeType: 'person' } },
  { data: { id: 'naled', label: 'NALED', nodeType: 'organization' } },
  { data: { id: 'e1', source: 'ana', target: 'ivan', label: 'poslovni partneri' } },
  { data: { id: 'e2', source: 'ana', target: 'naled', label: 'founder' } },
  { data: { id: 'e3', source: 'sinisa', target: 'ivan', label: 'classmate' } }
];

describe('search', () => {
  it('finds nodes whose folded label contains the folded query', () => {
    expect(search(buildIndex(elements), 'brnab').map((n) => n.id)).toEqual(['ana']);
  });
});

describe('search ranking', () => {
  it('puts matches at the start of a word first, then alphabetical; a query under two characters finds nothing', () => {
    expect(search(buildIndex(elements), 'an').map((n) => n.id)).toEqual(['ana', 'ivan']);
    expect(search(buildIndex(elements), 'a')).toEqual([]);
    expect(search(buildIndex(elements), ' ')).toEqual([]);
  });
});

describe('egoGraph', () => {
  it('holds the focus, its neighbours and every edge among them, as Cytoscape elements', () => {
    const view = egoGraph(buildIndex(elements), 'ana');
    expect(view.map((e) => e.data.id).sort()).toEqual(['ana', 'e1', 'e2', 'ivan', 'naled']);
  });
});

describe('expand', () => {
  it('adds the neighbours of a shown node and the edges they bring, keeping everything already shown', () => {
    const index = buildIndex(elements);
    const shown = new Set(['ana', 'ivan', 'naled']);
    expect([...expand(index, shown, 'ivan')].sort()).toEqual(['ana', 'ivan', 'naled', 'sinisa']);
    expect(shown.size).toBe(3);
    expect(subgraph(index, expand(index, shown, 'ivan')).map((e) => e.data.id).sort()).toEqual(['ana', 'e1', 'e2', 'e3', 'ivan', 'naled', 'sinisa']);
  });
});

describe('pushTrail', () => {
  it('appends the new focus, drops a later copy of the same node, and leaves the input untouched', () => {
    const trail = ['ana', 'ivan'];
    expect(pushTrail(trail, 'sinisa')).toEqual(['ana', 'ivan', 'sinisa']);
    expect(pushTrail(trail, 'ana')).toEqual(['ivan', 'ana']);
    expect(pushTrail(trail, 'ivan')).toEqual(['ana', 'ivan']);
    expect(trail).toEqual(['ana', 'ivan']);
  });
});

describe('hoverContent', () => {
  it('gives the trimmed comment and the Wikipedia link of a node', () => {
    expect(hoverContent({ id: 'a', label: 'A', comment: ' Izdavač. ', wikipedia: 'https://sr.wikipedia.org/wiki/A' }))
      .toEqual({ text: 'Izdavač.', wikipedia: 'https://sr.wikipedia.org/wiki/A' });
  });

  it('works with only one of the two, and is null with neither', () => {
    expect(hoverContent({ id: 'a', label: 'A', comment: 'Opis' })).toEqual({ text: 'Opis', wikipedia: null });
    expect(hoverContent({ id: 'a', label: 'A', wikipedia: 'https://en.wikipedia.org/wiki/A' }))
      .toEqual({ text: null, wikipedia: 'https://en.wikipedia.org/wiki/A' });
    expect(hoverContent({ id: 'a', label: 'A', comment: '  ' })).toBeNull();
    expect(hoverContent(undefined)).toBeNull();
  });

  it('refuses a link that is not https Wikipedia, so data cannot plant another address', () => {
    expect(hoverContent({ id: 'a', label: 'A', wikipedia: 'javascript:alert(1)' })).toBeNull();
    expect(hoverContent({ id: 'a', label: 'A', wikipedia: 'https://wikipedia.org.example/x' })).toBeNull();
  });
});

const network = buildIndex([
  { data: { id: 'mali', label: 'Mali', nodeType: 'person' } },
  { data: { id: 'ana', label: 'Ana', nodeType: 'person' } },
  { data: { id: 'ivan', label: 'Ivan', nodeType: 'person' } },
  { data: { id: 'firm', label: 'Firma', nodeType: 'organization' } },
  { data: { id: 'sns', label: 'SNS', nodeType: 'party' } },
  { data: { id: 'school', label: 'Škola', nodeType: 'academic' } },
  { data: { id: 'naled', label: 'NALED', nodeType: 'organization' } },
  { data: { id: 'alone', label: 'Sam', nodeType: 'person' } },
  { data: { id: 'e1', source: 'mali', target: 'sns', label: 'member' } },
  { data: { id: 'e2', source: 'mali', target: 'school', label: 'alumni' } },
  { data: { id: 'e3', source: 'ana', target: 'sns', label: 'member' } },
  { data: { id: 'e4', source: 'ana', target: 'school', label: 'alumni' } },
  { data: { id: 'e5', source: 'ana', target: 'naled', label: 'chair' } },
  { data: { id: 'e6', source: 'ana', target: 'naled', label: 'founder' } },
  { data: { id: 'e7', source: 'ivan', target: 'naled', label: 'board' } },
  { data: { id: 'e8', source: 'ivan', target: 'firm', label: 'owner' } },
  { data: { id: 'e9', source: 'mali', target: 'firm', label: 'x' }, }
].slice(0, 16));

describe('shortestPaths', () => {
  it('follows edges in either direction and finds every path of the shortest length', () => {
    expect(shortestPaths(network, 'mali', 'firm')).toEqual([
      ['mali', 'school', 'ana', 'naled', 'ivan', 'firm'],
      ['mali', 'sns', 'ana', 'naled', 'ivan', 'firm']
    ]);
  });

  it('gives a one-step path for a direct connection, ignoring longer ones', () => {
    expect(shortestPaths(network, 'ivan', 'firm')).toEqual([['ivan', 'firm']]);
  });

  it('gives no path when nothing connects them, or when a node is unknown or the same', () => {
    expect(shortestPaths(network, 'mali', 'alone')).toEqual([]);
    expect(shortestPaths(network, 'mali', 'nobody')).toEqual([]);
    expect(shortestPaths(network, 'mali', 'mali')).toEqual([]);
  });

  it('caps how many equal paths it returns', () => {
    expect(shortestPaths(network, 'mali', 'firm', { limit: 1 })).toHaveLength(1);
  });

  it('can refuse to pass through parties and schools, which may still be an end of the path', () => {
    expect(shortestPaths(network, 'mali', 'firm', { avoid: ['party', 'academic'] })).toEqual([]);
    expect(shortestPaths(network, 'ana', 'firm', { avoid: ['party', 'academic'] })).toEqual([['ana', 'naled', 'ivan', 'firm']]);
    expect(shortestPaths(network, 'mali', 'sns', { avoid: ['party'] })).toEqual([['mali', 'sns']]);
  });

  it('stays fast and bounded on a graph where equal paths explode', () => {
    const layers = 12;
    const elements: Element[] = [{ data: { id: 'start', label: 's' } }, { data: { id: 'end', label: 'e' } }];
    const edge = (source: string, target: string) => elements.push({ data: { id: `${source}-${target}`, source, target, label: 'l' } });
    for (let l = 0; l < layers; l++) for (const n of [0, 1, 2]) {
      elements.push({ data: { id: `n${l}_${n}`, label: 'n' } });
      if (l === 0) edge('start', `n0_${n}`);
      else for (const m of [0, 1, 2]) edge(`n${l - 1}_${m}`, `n${l}_${n}`);
      if (l === layers - 1) edge(`n${l}_${n}`, 'end');
    }
    const paths = shortestPaths(buildIndex(elements), 'start', 'end');
    expect(paths).toHaveLength(10);
    expect(paths.every((p) => p.length === layers + 2)).toBe(true);
  });
});

describe('path display', () => {
  const paths = [['ana', 'naled', 'ivan']];

  it('pathElements holds the nodes and only the edges that are steps of the path', () => {
    const ids = pathElements(network, [['mali', 'sns', 'ana']]).map((e) => e.data.id).sort();
    expect(ids).toEqual(['ana', 'e1', 'e3', 'mali', 'sns']);
  });

  it('pathSteps names each step, joining every connection between the same two nodes', () => {
    expect(pathSteps(network, paths[0])).toEqual([
      { from: 'Ana', via: 'chair, founder', to: 'NALED' },
      { from: 'NALED', via: 'board', to: 'Ivan' }
    ]);
  });
});

describe('path address', () => {
  it('reads the two ends from the address, and nothing when either is missing', () => {
    expect(readPathQuery('?lang=sr&from=mali&to=firm')).toEqual({ from: 'mali', to: 'firm' });
    expect(readPathQuery('?from=mali')).toEqual({ from: 'mali', to: null });
    expect(readPathQuery('')).toEqual({ from: null, to: null });
  });

  it('writes both ends and keeps other parameters such as the language', () => {
    expect(writePathQuery('?lang=sr', 'mali', 'firm')).toBe('?lang=sr&from=mali&to=firm');
  });

  it('removes the ends when a path is cleared or incomplete, leaving no stray question mark', () => {
    expect(writePathQuery('?lang=sr&from=a&to=b', null, null)).toBe('?lang=sr');
    expect(writePathQuery('?from=a&to=b', 'a', null)).toBe('');
  });
});

describe('filterByType', () => {
  const all = [
    { data: { id: 'a', label: 'A', nodeType: 'person' } },
    { data: { id: 'b', label: 'B', nodeType: 'party' } },
    { data: { id: 'c', label: 'C', nodeType: 'organization' } },
    { data: { id: 'x', label: 'X' } },
    { data: { id: 'e1', source: 'a', target: 'b', label: 'member' } },
    { data: { id: 'e2', source: 'a', target: 'c', label: 'works' } }
  ];
  const ids = (hidden: string[]) => filterByType(all, new Set(hidden)).map((e) => e.data.id);

  it('keeps everything when nothing is hidden', () => {
    expect(ids([])).toEqual(['a', 'b', 'c', 'x', 'e1', 'e2']);
  });

  it('drops the nodes of a hidden type and the edges that touch them', () => {
    expect(ids(['party'])).toEqual(['a', 'c', 'x', 'e2']);
  });

  it('keeps nodes without a type, and gives only those when every type is hidden', () => {
    expect(ids(['person', 'party', 'organization'])).toEqual(['x']);
  });
});

describe('focus in the address', () => {
  it('reads the focus id, or null when absent or empty', () => {
    expect(readFocusQuery('?lang=sr&focus=rs:Ana')).toBe('rs:Ana');
    expect(readFocusQuery('?lang=sr')).toBeNull();
    expect(readFocusQuery('?focus=')).toBeNull();
  });

  it('sets the focus and keeps other parameters, replacing an earlier focus', () => {
    expect(writeFocusQuery('?lang=sr&focus=old', 'new')).toBe('?lang=sr&focus=new');
  });

  it('removes the focus when there is none, and gives an empty query when nothing is left', () => {
    expect(writeFocusQuery('?lang=sr&focus=old', null)).toBe('?lang=sr');
    expect(writeFocusQuery('?focus=old', undefined)).toBe('');
  });

  it('encodes ids with reserved characters so they read back', () => {
    expect(readFocusQuery(writeFocusQuery('', 'a&b=c'))).toBe('a&b=c');
  });
});
