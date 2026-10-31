import { describe, expect, it } from 'vitest';
import { buildIndex, egoGraph, expand, fold, pushTrail, search, subgraph } from './graph';

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
