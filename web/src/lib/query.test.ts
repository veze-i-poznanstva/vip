import { describe, expect, it } from 'vitest';
import { QueryError, labelsOf, loadTurtle, select } from './query';

const turtle = `
@prefix : <http://poderopedia.com/vocab/> .
@prefix vip: <https://w3id.org/vip/vocab#> .
@prefix rs: <https://w3id.org/vip/rs/> .
@prefix bio: <http://purl.org/vocab/bio/0.1/> .
@prefix foaf: <http://xmlns.com/foaf/0.1/> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

rs:Ana a foaf:Person ; rdfs:label "Ana"@sr .
rs:Firma a :Company ; rdfs:label "Firma" .
rs:Grad a :GovernmentOrganization .
rs:Ana_Firma a vip:OwnershipConnection ; vip:owner rs:Ana ; vip:ownedCompany rs:Firma ;
    :connectionStartDate "2014"^^xsd:gYear ; vip:ownershipShare 30.0 ;
    :hasDocumentation [ a :Documentation ] .
rs:Ana_Grad a :WorkConnection ; bio:participant rs:Ana ; :hasEconomicOrganizationParticipant rs:Grad ;
    :connectionStartDate "2014-04-24"^^xsd:date ; :connectionEndDate "2018-05"^^xsd:gYearMonth .
`;
const prefixes = `
PREFIX : <http://poderopedia.com/vocab/>
PREFIX vip: <https://w3id.org/vip/vocab#>
PREFIX rs: <https://w3id.org/vip/rs/>
PREFIX bio: <http://purl.org/vocab/bio/0.1/>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX foaf: <http://xmlns.com/foaf/0.1/>
`;
const data = loadTurtle(turtle);

describe('select', () => {
  it('joins two patterns and returns the bound terms in projection order', async () => {
    const result = await select(data, `${prefixes}
      SELECT ?person ?company WHERE { ?ownership vip:owner ?person ; vip:ownedCompany ?company }`);
    expect(result.variables).toEqual(['person', 'company']);
    expect(result.rows).toEqual([{
      person: { kind: 'iri', value: 'https://w3id.org/vip/rs/Ana' },
      company: { kind: 'iri', value: 'https://w3id.org/vip/rs/Firma' }
    }]);
    expect(result.truncated).toBe(false);
  });
});

describe('select with a property path', () => {
  it('follows an alternative path, so a rule can read owner or participant in one pattern', async () => {
    const result = await select(data, `${prefixes}
      SELECT ?connection WHERE { ?connection (vip:owner|bio:participant) rs:Ana } ORDER BY ?connection`);
    expect(result.rows.map((row) => row.connection.value)).toEqual([
      'https://w3id.org/vip/rs/Ana_Firma',
      'https://w3id.org/vip/rs/Ana_Grad'
    ]);
  });
});

describe('select with the year filter the rules use', () => {
  it('compares dates of different precision on their first four characters', async () => {
    const result = await select(data, `${prefixes}
      SELECT ?ownedStart ?postEnd WHERE {
        rs:Ana_Firma :connectionStartDate ?ownedStart .
        rs:Ana_Grad :connectionEndDate ?postEnd .
        FILTER (SUBSTR(STR(?postEnd), 1, 4) >= SUBSTR(STR(?ownedStart), 1, 4))
      }`);
    expect(result.rows).toEqual([{
      ownedStart: { kind: 'literal', value: '2014', datatype: 'http://www.w3.org/2001/XMLSchema#gYear' },
      postEnd: { kind: 'literal', value: '2018-05', datatype: 'http://www.w3.org/2001/XMLSchema#gYearMonth' }
    }]);
  });
});

describe('select of malformed SPARQL', () => {
  it('fails with a syntax error that carries the engine message', async () => {
    const attempt = select(data, 'SELECT ?s WHERE { ?s ?p ');
    await expect(attempt).rejects.toBeInstanceOf(QueryError);
    await expect(attempt).rejects.toMatchObject({ kind: 'syntax' });
    await expect(attempt).rejects.toThrow(/Parse error/);
  });
});

describe('select with an undeclared prefix', () => {
  it('is a syntax error too, naming the prefix', async () => {
    const attempt = select(data, 'SELECT ?s WHERE { ?s vip:owner ?o }');
    await expect(attempt).rejects.toMatchObject({ kind: 'syntax', message: 'Unknown prefix: vip' });
  });
});

describe('select that matches nothing', () => {
  it('returns the variables and no rows', async () => {
    const result = await select(data, `${prefixes} SELECT ?who WHERE { ?who a :PoliticalParty }`);
    expect(result).toEqual({ variables: ['who'], rows: [], truncated: false });
  });
});

describe('select given a CONSTRUCT', () => {
  it('refuses it as unsupported before running anything', async () => {
    const attempt = select(data, `${prefixes} CONSTRUCT { ?s a :Agent } WHERE { ?s a foaf:Person }`);
    await expect(attempt).rejects.toMatchObject({ kind: 'unsupported' });
  });
});

describe('select beyond the row cap', () => {
  it('keeps the first rows up to the cap and says the result was cut', async () => {
    const result = await select(data, 'SELECT ?s WHERE { ?s a ?type }', { rows: 4 });
    expect(result.rows).toHaveLength(4);
    expect(result.truncated).toBe(true);
  });

  it('is not cut when the rows exactly fill the cap', async () => {
    const result = await select(data, 'SELECT ?s WHERE { ?s a ?type }', { rows: 6 });
    expect(result.rows).toHaveLength(6);
    expect(result.truncated).toBe(false);
  });
});

describe('select past the time limit', () => {
  it('stops reading and reports the limit in the message', async () => {
    let now = 0;
    const clock = () => (now += 3000);
    const attempt = select(data, 'SELECT ?s WHERE { ?s ?p ?o }', { timeMs: 5000 }, clock);
    await expect(attempt).rejects.toMatchObject({ kind: 'timeout' });
    await expect(attempt).rejects.toThrow(/5 s/);
  });
});

describe('select of literals', () => {
  it('keeps a language tag, drops the string datatype and keeps any other datatype', async () => {
    const result = await select(data, `${prefixes}
      SELECT ?label ?share ?name WHERE { rs:Ana rdfs:label ?label . rs:Ana_Firma vip:ownershipShare ?share . rs:Firma rdfs:label ?name }`);
    expect(result.rows).toEqual([{
      label: { kind: 'literal', value: 'Ana', language: 'sr' },
      share: { kind: 'literal', value: '30.0', datatype: 'http://www.w3.org/2001/XMLSchema#decimal' },
      name: { kind: 'literal', value: 'Firma' }
    }]);
  });
});

describe('select of a blank node', () => {
  it('returns it as a blank value', async () => {
    const result = await select(data, `${prefixes} SELECT ?doc WHERE { rs:Ana_Firma :hasDocumentation ?doc }`);
    expect(result.rows).toHaveLength(1);
    expect(result.rows[0].doc.kind).toBe('blank');
  });
});

describe('select with an unbound optional', () => {
  it('lists the variable but leaves it out of the row', async () => {
    const result = await select(data, `${prefixes}
      SELECT ?post ?role WHERE { ?post a :WorkConnection OPTIONAL { ?post :hasWorkRole ?role } }`);
    expect(result.variables).toEqual(['post', 'role']);
    expect(result.rows).toEqual([{ post: { kind: 'iri', value: 'https://w3id.org/vip/rs/Ana_Grad' } }]);
  });
});

describe('loadTurtle of malformed text', () => {
  it('throws the parser message with the line', () => {
    expect(() => loadTurtle('@prefix rs: <https://w3id.org/vip/rs/> .\nrs:Ana a .')).toThrow(/line 2/);
  });
});

describe('labelsOf', () => {
  it('maps each labelled IRI to its label in the language, then English, then untagged', () => {
    const labelled = loadTurtle(`
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      <http://x/mayor> rdfs:label "Mayor"@en, "Alcalde"@es, "Gradonačelnik"@sr .
      <http://x/chair> rdfs:label "Chair"@en, "Presidente"@es .
      <http://x/plain> rdfs:label "Plain" .
      <http://x/spanish> rdfs:label "Solo"@es .
    `);
    const sr = labelsOf(labelled, 'sr');
    expect(sr.get('http://x/mayor')).toBe('Gradonačelnik');
    expect(sr.get('http://x/chair')).toBe('Chair');
    expect(sr.get('http://x/plain')).toBe('Plain');
    expect(sr.has('http://x/spanish')).toBe(false);
    expect(labelsOf(labelled, 'en').get('http://x/mayor')).toBe('Mayor');
  });
});
