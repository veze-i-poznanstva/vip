/**
 * The query engine as the panels use it: the published data and vocabulary in one store, the
 * rules, and the operations over them. Loaded once per page through a dynamic import, so the
 * SPARQL engine is a separate chunk that only a query panel fetches.
 */
import { labelsOf, loadTurtle, select } from './query';
import type { Dataset, Limits, Result } from './query';
import { runRules } from './leads';
import type { RuleInfo, RuleRun } from './leads';

export interface Engine {
  dataset: Dataset;
  rules: RuleInfo[];
  select(sparql: string, limits?: Limits): Promise<Result>;
  /** Vocabulary labels by IRI in the language; computed once per language. */
  labels(language: string): Map<string, string>;
  runRules(limits?: Limits): Promise<RuleRun[]>;
}

/** Reads a published file by name; the panel passes a fetch of the site's public folder, tests pass a map. */
export type FetchText = (name: string) => Promise<string>;

/**
 * Loads `data.ttl` and `schema.ttl` into one store, as the build's reasoner sees them (the rules read
 * the data, the vocabulary names the roles), and `rules.json`. A missing file rejects with its message.
 */
export async function loadEngine(fetchText: FetchText): Promise<Engine> {
  const [data, schema, rulesText] = await Promise.all([fetchText('data.ttl'), fetchText('schema.ttl'), fetchText('rules.json')]);
  const dataset = loadTurtle(data + '\n' + schema);
  const rules: RuleInfo[] = JSON.parse(rulesText);
  const labelCache = new Map<string, Map<string, string>>();
  return {
    dataset,
    rules,
    select: (sparql, limits) => select(dataset, sparql, limits),
    labels: (language) => {
      let labels = labelCache.get(language);
      if (!labels) labelCache.set(language, (labels = labelsOf(dataset, language)));
      return labels;
    },
    runRules: (limits) => runRules(dataset, rules, limits)
  };
}

/** A loader that fetches and parses once and then hands every caller the same engine; a failed load is forgotten, so the next call retries. */
export function cachedEngine(fetchText: FetchText): () => Promise<Engine> {
  let loading: Promise<Engine> | undefined;
  return () => {
    loading ??= loadEngine(fetchText).catch((e) => { loading = undefined; throw e; });
    return loading;
  };
}
