/**
 * Leads: the red-flag rules run in the browser, their rows turned into sentences a reporter can
 * read, and the map view a row opens. Pure functions; `LeadsPanel.svelte` is the shell.
 */

/**
 * Fills `{name}` placeholders from the values. A segment in square brackets is kept only when every
 * placeholder in it is bound, so an optional variable (a role, say) takes its words with it.
 */
export function fillTemplate(template: string, values: Record<string, string>): string {
  const fill = (text: string) => text.replace(/\{(\w+)\}/g, (match, name) => (name in values ? values[name] : match));
  const bound = (text: string) => [...text.matchAll(/\{(\w+)\}/g)].every((m) => m[1] in values);
  return fill(template.replace(/\[([^\[\]]*)\]/g, (_, segment) => (bound(segment) ? segment : '')));
}

import { select } from './query';
import type { Dataset, Limits, Row, Value } from './query';
import type { GraphNode } from './graph';
import type { Translate } from './details';

/** What a value needs to be named: the agents by URI, the vocabulary labels by IRI, the translator. */
export interface Naming { nodes: Map<string, GraphNode>; labels: Map<string, string>; t: Translate }

const localName = (iri: string) => iri.split(/[/#]/).filter(Boolean).pop() ?? iri;

/**
 * A value as a reader sees it: an agent by its node label, a vocabulary term by its label in the
 * viewer's language, any other IRI by its local name; a literal through `leads.value.<text>` when the
 * dictionary has it (the rules' status words), else as written; a blank node as an ellipsis.
 */
export function describeValue(value: Value, naming: Naming): string {
  if (value.kind === 'blank') return '…';
  if (value.kind === 'iri') return naming.nodes.get(value.value)?.label ?? naming.labels.get(value.value) ?? localName(value.value);
  const key = `leads.value.${value.value}`;
  const translated = naming.t(key);
  return translated === key ? value.value : translated;
}

/**
 * One row as a sentence: the dictionary's `leads.<rule>` template filled with the named values,
 * or, for a rule the dictionary does not know, every bound variable with its value.
 */
export function leadSentence(rule: string, row: Row, naming: Naming): string {
  const named = Object.fromEntries(Object.entries(row).map(([variable, value]) => [variable, describeValue(value, naming)]));
  const key = `leads.${rule}`;
  const template = naming.t(key);
  if (template === key) return Object.entries(named).map(([variable, value]) => `${variable}: ${value}`).join('; ');
  return fillTemplate(template, named);
}

/** The map view a row opens: the path between two people, else the neighbourhood of the first agent. */
export type LeadFocus = { kind: 'path'; from: GraphNode; to: GraphNode } | { kind: 'ego'; node: GraphNode };

export function leadFocus(row: Row, nodes: Map<string, GraphNode>): LeadFocus | null {
  const agents = Object.values(row).filter((v) => v.kind === 'iri').map((v) => nodes.get(v.value)).filter((n): n is GraphNode => !!n);
  const people = agents.filter((n) => n.nodeType === 'person');
  if (people.length === 2) return { kind: 'path', from: people[0], to: people[1] };
  const first = people[0] ?? agents[0];
  return first ? { kind: 'ego', node: first } : null;
}

/** The agents of the graph by their URI, for naming query results and opening them on the map. */
export function nodesByUri(nodes: Iterable<GraphNode>): Map<string, GraphNode> {
  const byUri = new Map<string, GraphNode>();
  for (const node of nodes) if (typeof node.uri === 'string') byUri.set(node.uri, node);
  return byUri;
}

/** A rule as `rules.json` publishes it: name, title and explanation by language, the SPARQL text. */
export interface RuleInfo { name: string; title: Record<string, string>; explanation: Record<string, string>; query: string }

/** One rule's run: its rows, whether they were cut at the cap, and the message when it could not run. */
export interface RuleRun { rule: RuleInfo; rows: Row[]; truncated: boolean; error?: string }

/** Runs every rule in order; a rule that fails is reported with its message and does not stop the others. */
export async function runRules(dataset: Dataset, rules: RuleInfo[], limits: Limits = {}): Promise<RuleRun[]> {
  const runs: RuleRun[] = [];
  for (const rule of rules) {
    try {
      const { rows, truncated } = await select(dataset, rule.query, limits);
      runs.push({ rule, rows, truncated });
    } catch (e) {
      runs.push({ rule, rows: [], truncated: false, error: e instanceof Error ? e.message : String(e) });
    }
  }
  return runs;
}
