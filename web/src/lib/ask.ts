/**
 * Ask: the page does not interpret a question; it builds a prompt for the visitor's own assistant
 * from the vocabulary, and runs the query the assistant answers with. Pure functions; the panel is
 * the shell.
 */
import { DataFactory } from 'n3';
import { textsOf } from './query';
import type { Dataset } from './query';

const RDF = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#';
const RDFS = 'http://www.w3.org/2000/01/rdf-schema#';
const OWL = 'http://www.w3.org/2002/07/owl#';

/** The prefixes a query over the data uses, as the rules declare them. */
export const PREFIXES: Record<string, string> = {
  '': 'http://poderopedia.com/vocab/',
  vip: 'https://w3id.org/vip/vocab#',
  rs: 'https://w3id.org/vip/rs/',
  bio: 'http://purl.org/vocab/bio/0.1/',
  foaf: 'http://xmlns.com/foaf/0.1/',
  rdfs: RDFS,
  xsd: 'http://www.w3.org/2001/XMLSchema#'
};

/** `:WorkConnection`, `vip:mayor`, `foaf:Person`; an IRI outside the prefixes stays in angle brackets. */
export function compact(iri: string): string {
  for (const [prefix, namespace] of Object.entries(PREFIXES)) {
    if (iri.startsWith(namespace)) return `${prefix}:${iri.slice(namespace.length)}`;
  }
  return `<${iri}>`;
}

export interface ClassSummary { term: string; label: string; parent?: string; comment?: string }
export interface PropertySummary { term: string; label: string; comment?: string; domain?: string; range?: string }
export interface IndividualSummary { term: string; label: string; type: string }
export interface VocabularySummary { classes: ClassSummary[]; properties: PropertySummary[]; individuals: IndividualSummary[] }

const node = DataFactory.namedNode;
const byTerm = <T extends { term: string }>(items: T[]) => items.sort((a, b) => a.term.localeCompare(b.term));

/** The terms the records use: the types of subjects, the predicates, and the IRIs that are objects. */
export interface UsedTerms { classes: Set<string>; properties: Set<string>; individuals: Set<string> }

/**
 * What the data actually uses, so the prompt can leave the rest of the vocabulary out: every
 * `rdf:type` object (with the entailed superclasses the export materialised), every predicate, and
 * every IRI in object position.
 */
export function usedTerms(dataset: Dataset): UsedTerms {
  const used: UsedTerms = { classes: new Set(), properties: new Set(), individuals: new Set() };
  const vocabulary = (iri: string) => iri.startsWith(PREFIXES['']) || iri.startsWith(PREFIXES.vip);
  for (const quad of dataset.store.getQuads(null, null, null, null)) {
    // The vocabulary's own statements (a role's type, a class's label) are not use by a record.
    if (quad.subject.termType === 'NamedNode' && vocabulary(quad.subject.value)) continue;
    used.properties.add(quad.predicate.value);
    if (quad.object.termType !== 'NamedNode') continue;
    if (quad.predicate.value === RDF + 'type') used.classes.add(quad.object.value);
    else used.individuals.add(quad.object.value);
  }
  return used;
}

/**
 * The vocabulary as a prompt can carry it, in the language: every class with its parent and comment,
 * every property with its domain and range, and the individuals the vocabulary itself declares (work
 * roles, document types, statuses). Instance data in the same store is left out. Given `used`, only
 * the classes, properties and individuals the records use are kept.
 */
export function summarizeVocabulary(dataset: Dataset, language: string, used?: UsedTerms): VocabularySummary {
  const { store } = dataset;
  const keep = (set: Set<string> | undefined) => (iri: string) => !set || set.has(iri);
  const labels = textsOf(dataset, RDFS + 'label', language);
  const comments = textsOf(dataset, RDFS + 'comment', language);
  const objects = (subject: string, predicate: string) =>
    store.getObjects(node(subject), node(predicate), null).filter((o) => o.termType === 'NamedNode').map((o) => compact(o.value)).sort();
  const subjectsOfType = (type: string) => [...new Set(store.getSubjects(node(RDF + 'type'), node(type), null).map((s) => s.value))];
  const vocabulary = (iri: string) => iri.startsWith(PREFIXES['']) || iri.startsWith(PREFIXES.vip);
  const optional = <T>(key: string, value: T | undefined) => (value === undefined ? {} : { [key]: value });
  const first = (values: string[]) => values[0];

  const classIris = subjectsOfType(OWL + 'Class');
  const classes = byTerm(classIris.filter(keep(used?.classes)).map((iri) => ({
    term: compact(iri),
    label: labels.get(iri) ?? compact(iri),
    ...optional('parent', objects(iri, RDFS + 'subClassOf').join(', ') || undefined),
    ...optional('comment', comments.get(iri))
  })));
  const properties = byTerm(subjectsOfType(RDF + 'Property').filter(keep(used?.properties)).map((iri) => ({
    term: compact(iri),
    label: labels.get(iri) ?? compact(iri),
    ...optional('comment', comments.get(iri)),
    ...optional('domain', first(objects(iri, RDFS + 'domain'))),
    ...optional('range', first(objects(iri, RDFS + 'range')))
  })));
  const declared = new Set([...classIris, ...subjectsOfType(RDF + 'Property')]);
  const individuals = byTerm(classIris.flatMap((type) =>
    subjectsOfType(type).filter((iri) => vocabulary(iri) && !declared.has(iri) && keep(used?.individuals)(iri)).map((iri) => ({ term: compact(iri), label: labels.get(iri) ?? compact(iri), type: compact(type) }))
  ));
  return { classes, properties, individuals };
}

export type Translate = (key: string, params?: Record<string, string | number>) => string;
interface PromptRule { name: string; query: string }

/**
 * The brief for the visitor's assistant, everything but the question: the task, the prefixes, the
 * vocabulary summary, the conventions of the data and the project's rules as worked examples. The
 * fixed sentences come from the dictionary, so it is in the viewer's language. The build publishes
 * it as `prompt.<language>.txt`, so a short link can send an assistant to read it.
 */
export function buildBrief(summary: VocabularySummary, rules: PromptRule[], t: Translate): string {
  const prefixes = Object.entries(PREFIXES).map(([prefix, namespace]) => `PREFIX ${prefix}: <${namespace}>`);
  const classes = summary.classes.map((c) =>
    `- ${c.term} "${c.label}"` + (c.parent ? `, ${t('ask.prompt.subclassOf', { parent: c.parent })}` : '') + (c.comment ? `: ${c.comment}` : ''));
  const properties = summary.properties.map((p) =>
    `- ${p.term} "${p.label}"` + (p.domain || p.range ? `, ${p.domain ?? '?'} → ${p.range ?? '?'}` : '') + (p.comment ? `: ${p.comment}` : ''));
  const individuals = summary.individuals.map((i) => `- ${i.term} "${i.label}" (${i.type})`);
  const examples = rules.slice(0, 2).map((rule) => '```sparql\n' + rule.query.trim() + '\n```');
  return [
    t('ask.prompt.task'), '',
    t('ask.prompt.prefixes'), ...prefixes, '',
    t('ask.prompt.classes'), ...classes, '',
    t('ask.prompt.properties'), ...properties, '',
    t('ask.prompt.individuals'), ...individuals, '',
    t('ask.prompt.conventions'), '',
    t('ask.prompt.examples'), ...examples, ''
  ].join('\n') + '\n';
}

/** The whole prompt: the brief with the question last, for copying and pasting. */
export function buildPrompt(summary: VocabularySummary, rules: PromptRule[], question: string, t: Translate): string {
  return buildBrief(summary, rules, t) + t('ask.prompt.question') + '\n' + question.trim() + '\n';
}

/** Where the viewer is published. The assistant must be able to fetch the brief, so links name this site, never the page the visitor happens to be on (a local preview, a fork). */
export const PUBLISHED_SITE = 'https://veze-i-poznanstva.github.io/vip/';

/** The address of the published brief in the language. */
export function briefAddress(language: string): string {
  return new URL(`prompt.${language}.txt`, PUBLISHED_SITE).href;
}

/** The query in an assistant's answer: the first fenced code block, else the whole text, trimmed. */
export function extractQuery(answer: string): string {
  const fenced = answer.match(/```[^\n]*\n([\s\S]*?)```/);
  return (fenced ? fenced[1] : answer).trim();
}

/** The longest encoded prompt a link carries; beyond it the assistant opens empty and the visitor pastes. */
export const MAX_LINK_PROMPT = 8000;

export interface AssistantLinks { claude: string; chatgpt: string; prefilled: boolean }

/** Addresses that open the visitor's assistant with the prompt filled in when it is short enough to travel in a link. */
export function assistantLinks(prompt: string): AssistantLinks {
  const encoded = encodeURIComponent(prompt);
  const prefilled = encoded.length <= MAX_LINK_PROMPT;
  return {
    claude: prefilled ? `https://claude.ai/new?q=${encoded}` : 'https://claude.ai/new',
    chatgpt: prefilled ? `https://chatgpt.com/?q=${encoded}` : 'https://chatgpt.com/',
    prefilled
  };
}
