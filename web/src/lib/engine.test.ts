import { describe, expect, it } from 'vitest';
import { cachedEngine, loadEngine } from './engine';

const files: Record<string, string> = {
  'data.ttl': '@prefix rs: <https://w3id.org/vip/rs/> . @prefix foaf: <http://xmlns.com/foaf/0.1/> . rs:Ana a foaf:Person .',
  'schema.ttl': '@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> . <https://w3id.org/vip/vocab#mayor> rdfs:label "Mayor"@en, "Gradonačelnik"@sr .',
  'rules.json': JSON.stringify([{ name: 'r', title: { en: 'R', sr: 'R' }, explanation: { en: 'e', sr: 'e' }, query: 'SELECT * WHERE { ?s ?p ?o }' }])
};
const fetchText = async (name: string) => {
  if (!(name in files)) throw new Error(`${name}: 404`);
  return files[name];
};

describe('loadEngine', () => {
  it('loads the data and the vocabulary into one dataset and parses the rules', async () => {
    const engine = await loadEngine(fetchText);
    expect(engine.rules.map((r) => r.name)).toEqual(['r']);
    expect(engine.labels('sr').get('https://w3id.org/vip/vocab#mayor')).toBe('Gradonačelnik');
    const result = await engine.select('SELECT ?s WHERE { ?s a <http://xmlns.com/foaf/0.1/Person> }');
    expect(result.rows.map((r) => r.s.value)).toEqual(['https://w3id.org/vip/rs/Ana']);
  });
});

describe('cachedEngine', () => {
  it('loads once and hands the same engine to every caller, so reopening a panel fetches nothing', async () => {
    let fetches = 0;
    const load = cachedEngine(async (name) => { fetches++; return fetchText(name); });
    const [a, b] = await Promise.all([load(), load()]);
    expect(a).toBe(b);
    expect(await load()).toBe(a);
    expect(fetches).toBe(3);
  });

  it('forgets a failed load, so the next call tries again', async () => {
    let fail = true;
    const load = cachedEngine(async (name) => { if (fail) throw new Error('offline'); return fetchText(name); });
    await expect(load()).rejects.toThrow('offline');
    fail = false;
    expect((await load()).rules.map((r) => r.name)).toEqual(['r']);
  });
});
