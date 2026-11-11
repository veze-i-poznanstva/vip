import { describe, expect, it } from 'vitest';
import { MAX_LINK_PROMPT, assistantLinks, briefAddress, buildBrief, buildPrompt, extractQuery, summarizeVocabulary, usedTerms } from './ask';
import { loadTurtle } from './query';
import { dictionaries, translate } from './i18n';
import { readFileSync } from 'node:fs';

const vocabulary = loadTurtle(`
  @prefix : <http://poderopedia.com/vocab/> .
  @prefix vip: <https://w3id.org/vip/vocab#> .
  @prefix owl: <http://www.w3.org/2002/07/owl#> .
  @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
  @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
  @prefix foaf: <http://xmlns.com/foaf/0.1/> .
  :Connection a owl:Class ; rdfs:label "Connection"@en, "Veza"@sr .
  :WorkConnection a owl:Class ; rdfs:subClassOf :Connection ;
      rdfs:label "Work Connection"@en, "Radna veza"@sr ;
      rdfs:comment "A person and an organization in a work context"@en, "Osoba i organizacija u radnom kontekstu"@sr .
  :hasWorkRole a rdf:Property, owl:ObjectProperty ; rdfs:label "Work role"@en, "Radna uloga"@sr ;
      rdfs:comment "Position of the person"@en, "Položaj osobe"@sr ; rdfs:domain :WorkConnection ; rdfs:range :WorkRole .
  :WorkRole a owl:Class ; rdfs:label "Work Role"@en, "Radna uloga"@sr .
  vip:mayor a :WorkRole ; rdfs:label "Mayor"@en, "Gradonačelnik"@sr .
  vip:newsArticle a :DocumentType ; rdfs:label "News article"@en, "Novinski članak"@sr .
  :DocumentType a owl:Class ; rdfs:label "Document type"@en, "Vrsta dokumenta"@sr .
`);

describe('summarizeVocabulary', () => {
  it('lists classes with parent and comment, properties with domain and range, and the role and document type individuals, in the language', () => {
    const summary = summarizeVocabulary(vocabulary, 'sr');
    expect(summary.classes).toEqual([
      { term: ':Connection', label: 'Veza' },
      { term: ':DocumentType', label: 'Vrsta dokumenta' },
      { term: ':WorkConnection', label: 'Radna veza', parent: ':Connection', comment: 'Osoba i organizacija u radnom kontekstu' },
      { term: ':WorkRole', label: 'Radna uloga' }
    ]);
    expect(summary.properties).toEqual([
      { term: ':hasWorkRole', label: 'Radna uloga', comment: 'Položaj osobe', domain: ':WorkConnection', range: ':WorkRole' }
    ]);
    expect(summary.individuals).toEqual([
      { term: 'vip:mayor', label: 'Gradonačelnik', type: ':WorkRole' },
      { term: 'vip:newsArticle', label: 'Novinski članak', type: ':DocumentType' }
    ]);
  });
});

const dictionary: Record<string, string> = {
  'ask.prompt.task': 'TASK',
  'ask.prompt.prefixes': 'PREFIXES',
  'ask.prompt.classes': 'CLASSES',
  'ask.prompt.properties': 'PROPERTIES',
  'ask.prompt.individuals': 'INDIVIDUALS',
  'ask.prompt.conventions': 'CONVENTIONS',
  'ask.prompt.examples': 'EXAMPLES',
  'ask.prompt.question': 'QUESTION',
  'ask.prompt.subclassOf': 'subclass of {parent}'
};
const t = (key: string, params: Record<string, string | number> = {}) =>
  (dictionary[key] ?? key).replace(/\{(\w+)\}/g, (m, n) => (n in params ? String(params[n]) : m));
const rule = (name: string, query: string) => ({ name, title: { en: name, sr: name }, explanation: { en: '', sr: '' }, query });

describe('buildPrompt', () => {
  it('carries the task, the prefixes, every class, property and individual, the conventions, the example rules and the question, in that order', () => {
    const summary = summarizeVocabulary(vocabulary, 'en');
    const prompt = buildPrompt(summary, [rule('a', 'SELECT ?a WHERE { ?a a :WorkConnection }'), rule('b', 'SELECT ?b WHERE { ?b a :WorkRole }')], 'Who is a mayor?', t);
    const order = ['TASK', 'PREFIXES', 'PREFIX vip: <https://w3id.org/vip/vocab#>', 'CLASSES', ':WorkConnection', 'PROPERTIES', ':hasWorkRole', 'INDIVIDUALS', 'vip:mayor', 'CONVENTIONS', 'EXAMPLES', 'SELECT ?a WHERE', 'SELECT ?b WHERE', 'QUESTION', 'Who is a mayor?'];
    const positions = order.map((text) => prompt.indexOf(text));
    expect(positions.every((p) => p >= 0), prompt).toBe(true);
    expect(positions).toEqual([...positions].sort((x, y) => x - y));
    expect(prompt).toContain('- :WorkConnection "Work Connection", subclass of :Connection: A person and an organization in a work context');
    expect(prompt).toContain('- :hasWorkRole "Work role", :WorkConnection → :WorkRole: Position of the person');
    expect(prompt).toContain('- vip:mayor "Mayor" (:WorkRole)');
    expect(prompt).toContain('- :Connection "Connection"\n');
    expect(prompt.trimEnd().endsWith('Who is a mayor?')).toBe(true);
  });
});

describe('extractQuery', () => {
  it('takes the first fenced block, with or without a language tag and with text around it, else the whole text', () => {
    expect(extractQuery('Here you go:\n```sparql\nSELECT ?s WHERE { ?s ?p ?o }\n```\nLet me know.')).toBe('SELECT ?s WHERE { ?s ?p ?o }');
    expect(extractQuery('```\nSELECT ?a WHERE { ?a a :WorkRole }\n```\n```sparql\nSELECT ?b WHERE { }\n```')).toBe('SELECT ?a WHERE { ?a a :WorkRole }');
    expect(extractQuery('  SELECT ?s WHERE { ?s ?p ?o }  \n')).toBe('SELECT ?s WHERE { ?s ?p ?o }');
    expect(extractQuery('')).toBe('');
    expect(extractQuery('```sparql\n```')).toBe('');
  });
});

describe('assistantLinks', () => {
  it('prefills a short prompt in the Claude and ChatGPT addresses, and gives only the bare addresses for a long one', () => {
    const short = assistantLinks('Who owns Firma? ćirilica');
    expect(short.prefilled).toBe(true);
    expect(short.claude).toBe('https://claude.ai/new?q=Who%20owns%20Firma%3F%20%C4%87irilica');
    expect(short.chatgpt).toBe('https://chatgpt.com/?q=Who%20owns%20Firma%3F%20%C4%87irilica');
    const long = assistantLinks('x'.repeat(MAX_LINK_PROMPT + 1));
    expect(long.prefilled).toBe(false);
    expect(long.claude).toBe('https://claude.ai/new');
    expect(long.chatgpt).toBe('https://chatgpt.com/');
  });
});

describe('the prompt over the published vocabulary', () => {
  const published = readFileSync(new URL('../../public/schema.ttl', import.meta.url), 'utf8');
  const schema = loadTurtle(published);
  const rules = JSON.parse(readFileSync(new URL('../../public/rules.json', import.meta.url), 'utf8'));

  for (const language of ['en', 'sr'] as const) {
    it(`in ${language} names every class, property, role and document type, and stays within a size a chat box accepts`, () => {
      const summary = summarizeVocabulary(schema, language);
      const prompt = buildPrompt(summary, rules, 'Ko je gradonačelnik?', (key, params) => translate(dictionaries[language], key, params));
      expect(summary.classes.length).toBeGreaterThan(50);
      expect(summary.properties.length).toBeGreaterThan(50);
      expect(summary.individuals.map((i) => i.term)).toContain('vip:mayor');
      expect(summary.individuals.map((i) => i.term)).toContain('vip:newsArticle');
      for (const item of [...summary.classes, ...summary.properties, ...summary.individuals]) expect(prompt).toContain(`- ${item.term} "`);
      expect(prompt).not.toContain('ask.prompt.');
      expect(prompt.length).toBeLessThan(40000);
    });
  }
});

describe('summarizeVocabulary of the terms the data uses', () => {
  it('keeps only the classes that type a record, the properties a record uses and the individuals a record points to', () => {
    const withData = loadTurtle(`
      @prefix : <http://poderopedia.com/vocab/> .
      @prefix vip: <https://w3id.org/vip/vocab#> .
      @prefix rs: <https://w3id.org/vip/rs/> .
      @prefix owl: <http://www.w3.org/2002/07/owl#> .
      @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      :Connection a owl:Class ; rdfs:label "Connection"@en .
      :WorkConnection a owl:Class ; rdfs:subClassOf :Connection ; rdfs:label "Work Connection"@en .
      :CompanyConnection a owl:Class ; rdfs:subClassOf :Connection ; rdfs:label "Company Connection"@en .
      :hasWorkRole a rdf:Property ; rdfs:label "Work role"@en ; rdfs:domain :WorkConnection ; rdfs:range :WorkRole .
      :hasAuntOrUncle a rdf:Property ; rdfs:label "Has Aunt or Uncle"@en .
      :WorkRole a owl:Class ; rdfs:label "Work Role"@en .
      vip:mayor a :WorkRole ; rdfs:label "Mayor"@en .
      vip:minister a :WorkRole ; rdfs:label "Minister"@en .
      rs:AnaGrad a :WorkConnection, :Connection ; :hasWorkRole vip:mayor .
    `);
    const summary = summarizeVocabulary(withData, 'en', usedTerms(withData));
    expect(summary.classes.map((c) => c.term)).toEqual([':Connection', ':WorkConnection']);
    expect(summary.properties.map((p) => p.term)).toEqual([':hasWorkRole']);
    expect(summary.individuals.map((i) => i.term)).toEqual(['vip:mayor']);
  });
});

describe('the prompt over the published data', () => {
  it('names the terms the records use, leaves the unused vocabulary out and stays well under the size of a chat message', () => {
    const published = (name: string) => readFileSync(new URL(`../../public/${name}`, import.meta.url), 'utf8');
    const all = loadTurtle(published('data.ttl') + '\n' + published('schema.ttl'));
    const summary = summarizeVocabulary(all, 'sr', usedTerms(all));
    const prompt = buildPrompt(summary, JSON.parse(published('rules.json')), 'Ko je gradonačelnik?', (key, params) => translate(dictionaries.sr, key, params));
    for (const term of [':WorkConnection', 'vip:OwnershipConnection', ':hasWorkRole', 'vip:ownedCompany', 'vip:mayor', 'vip:newsArticle']) expect(prompt).toContain(`- ${term} "`);
    expect(prompt).not.toContain(':hasAuntOrUncle');
    expect(prompt.length).toBeLessThan(20000);
  });
});

describe('buildBrief', () => {
  it('is the prompt without the question, so the build can publish it and a link can point an assistant at it', () => {
    const summary = summarizeVocabulary(vocabulary, 'en');
    const rules = [rule('a', 'SELECT ?a WHERE { ?a a :WorkConnection }')];
    const brief = buildBrief(summary, rules, t);
    expect(buildPrompt(summary, rules, 'Who is a mayor?', t)).toBe(brief + 'QUESTION\nWho is a mayor?\n');
    expect(brief).not.toContain('QUESTION');
    expect(brief.endsWith('\n\n')).toBe(true);
  });
});

describe('briefAddress', () => {
  it('always names the brief on the published site, whatever page the visitor is on, so an assistant can fetch it', () => {
    expect(briefAddress('sr')).toBe('https://veze-i-poznanstva.github.io/vip/prompt.sr.txt');
    expect(briefAddress('en')).toBe('https://veze-i-poznanstva.github.io/vip/prompt.en.txt');
  });
});
