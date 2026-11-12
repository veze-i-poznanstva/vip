import { describe, expect, it } from 'vitest';
import { describeValue, fillTemplate, leadFocus, leadSentence, nodesByUri, runRules } from './leads';
import { loadTurtle } from './query';

describe('fillTemplate', () => {
  it('fills every bound placeholder', () => {
    expect(fillTemplate('{person} owns {company}', { person: 'Ana', company: 'Firma' })).toBe('Ana owns Firma');
  });
});

describe('fillTemplate with optional segments', () => {
  it('keeps a bracketed segment when its placeholders are bound and drops it when one is not', () => {
    const template = '{person} holds office at {office}[ as {role}]; {overlap}';
    expect(fillTemplate(template, { person: 'Ana', office: 'Grad', role: 'mayor', overlap: 'unknown' })).toBe('Ana holds office at Grad as mayor; unknown');
    expect(fillTemplate(template, { person: 'Ana', office: 'Grad', overlap: 'unknown' })).toBe('Ana holds office at Grad; unknown');
  });
});

const dictionary: Record<string, string> = {
  'leads.value.unknown': 'overlap unknown',
  'leads.owner-in-office': '{person} owns {company} and holds office at {office}[ as {role}]; {overlap}'
};
const t = (key: string, params: Record<string, string | number> = {}) =>
  (dictionary[key] ?? key).replace(/\{(\w+)\}/g, (m, n) => (n in params ? String(params[n]) : m));
const ana = { id: 'ana', label: 'Ana', nodeType: 'person', uri: 'https://w3id.org/vip/rs/Ana' };
const firma = { id: 'firma', label: 'Firma d.o.o.', nodeType: 'organization', uri: 'https://w3id.org/vip/rs/Firma' };
const context = {
  nodes: new Map([[ana.uri, ana], [firma.uri, firma]]),
  labels: new Map([['https://w3id.org/vip/vocab#mayor', 'Gradonačelnik']]),
  t
};

describe('describeValue', () => {
  it('names an agent by its node label, a vocabulary term by its label, any other IRI by its local name, and translates a known literal', () => {
    expect(describeValue({ kind: 'iri', value: ana.uri }, context)).toBe('Ana');
    expect(describeValue({ kind: 'iri', value: 'https://w3id.org/vip/vocab#mayor' }, context)).toBe('Gradonačelnik');
    expect(describeValue({ kind: 'iri', value: 'https://w3id.org/vip/rs/Nobody_Yet' }, context)).toBe('Nobody_Yet');
    expect(describeValue({ kind: 'literal', value: 'unknown' }, context)).toBe('overlap unknown');
    expect(describeValue({ kind: 'literal', value: '2014', datatype: 'http://www.w3.org/2001/XMLSchema#gYear' }, context)).toBe('2014');
    expect(describeValue({ kind: 'blank', value: 'b0' }, context)).toBe('…');
  });
});

describe('leadSentence', () => {
  it('fills the rule template with the named values, and lists variable and value for a rule without one', () => {
    const row = {
      person: { kind: 'iri' as const, value: ana.uri },
      company: { kind: 'iri' as const, value: firma.uri },
      office: { kind: 'iri' as const, value: 'https://w3id.org/vip/rs/Grad' },
      role: { kind: 'iri' as const, value: 'https://w3id.org/vip/vocab#mayor' },
      overlap: { kind: 'literal' as const, value: 'unknown' }
    };
    expect(leadSentence('owner-in-office', row, context)).toBe('Ana owns Firma d.o.o. and holds office at Grad as Gradonačelnik; overlap unknown');
    expect(leadSentence('odd-rule', { person: row.person, company: row.company }, context)).toBe('person: Ana; company: Firma d.o.o.');
  });
});

describe('leadFocus', () => {
  const cile = { id: 'cile', label: 'Cile', nodeType: 'person', uri: 'https://w3id.org/vip/rs/Cile' };
  const nodes = new Map([[ana.uri, ana], [firma.uri, firma], [cile.uri, cile]]);
  const iri = (value: string) => ({ kind: 'iri' as const, value });

  it('connects two people, centres on a lone person, and has nothing to show without an agent', () => {
    expect(leadFocus({ personA: iri(ana.uri), personB: iri(cile.uri), school: iri(firma.uri) }, nodes)).toEqual({ kind: 'path', from: ana, to: cile });
    expect(leadFocus({ person: iri(ana.uri), company: iri(firma.uri), overlap: { kind: 'literal', value: 'unknown' } }, nodes)).toEqual({ kind: 'ego', node: ana });
    expect(leadFocus({ company: iri(firma.uri) }, nodes)).toEqual({ kind: 'ego', node: firma });
    expect(leadFocus({ overlap: { kind: 'literal', value: 'unknown' } }, nodes)).toBeNull();
  });

  it('counts distinct people, so a row naming the same person twice opens their map rather than a path to themselves', () => {
    expect(leadFocus({ a: iri(ana.uri), b: iri(ana.uri) }, nodes)).toEqual({ kind: 'ego', node: ana });
    expect(leadFocus({ a: iri(ana.uri), b: iri(cile.uri), c: iri(ana.uri) }, nodes)).toEqual({ kind: 'path', from: ana, to: cile });
  });
});

describe('nodesByUri', () => {
  it('indexes the nodes that carry a URI and skips the rest', () => {
    const byUri = nodesByUri([ana, { id: 'x', label: 'No URI' }]);
    expect([...byUri.keys()]).toEqual([ana.uri]);
    expect(byUri.get(ana.uri)).toBe(ana);
  });
});

describe('runRules', () => {
  it('runs every rule and keeps a broken one as an error beside the others', async () => {
    const dataset = loadTurtle(`
      @prefix vip: <https://w3id.org/vip/vocab#> .
      @prefix rs: <https://w3id.org/vip/rs/> .
      rs:AF a vip:OwnershipConnection ; vip:owner rs:Ana ; vip:ownedCompany rs:Firma .
    `);
    const rule = (name: string, query: string) => ({ name, title: { en: name, sr: name }, explanation: { en: '', sr: '' }, query });
    const runs = await runRules(dataset, [
      rule('owners', 'PREFIX vip: <https://w3id.org/vip/vocab#> SELECT ?person WHERE { ?o vip:owner ?person }'),
      rule('broken', 'SELECT ?person WHERE { ?o vip:owner ')
    ]);
    expect(runs.map((r) => r.rule.name)).toEqual(['owners', 'broken']);
    expect(runs[0].rows).toEqual([{ person: { kind: 'iri', value: 'https://w3id.org/vip/rs/Ana' } }]);
    expect(runs[0].error).toBeUndefined();
    expect(runs[1].rows).toEqual([]);
    expect(runs[1].error).toMatch(/Unknown prefix: vip/);
  });
});
