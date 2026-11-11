import { DataFactory, Parser, Store } from 'n3';
import { QueryEngine } from '@comunica/query-sparql-rdfjs-lite';
import type { Bindings, Term } from '@rdfjs/types';

/** A dataset loaded in memory; opaque to callers, passed back to `select`. */
export interface Dataset { store: Store }

/** Parses Turtle into an in-memory store. Throws the parser's message on malformed input. */
export function loadTurtle(text: string): Dataset {
  return { store: new Store(new Parser().parse(text)) };
}

const RDFS = 'http://www.w3.org/2000/01/rdf-schema#';

/**
 * Every subject with a literal of the predicate in the language, else in English, else untagged; a
 * subject with the literal only in other languages is absent.
 */
export function textsOf(dataset: Dataset, predicate: string, language: string): Map<string, string> {
  const rank = (tag: string) => (tag === language ? 3 : tag === 'en' ? 2 : tag === '' ? 1 : 0);
  const best = new Map<string, { rank: number; text: string }>();
  for (const quad of dataset.store.getQuads(null, DataFactory.namedNode(predicate), null, null)) {
    if (quad.object.termType !== 'Literal') continue;
    const r = rank(quad.object.language);
    const current = best.get(quad.subject.value);
    if (r > 0 && (!current || r > current.rank)) best.set(quad.subject.value, { rank: r, text: quad.object.value });
  }
  return new Map([...best].map(([iri, { text }]) => [iri, text]));
}

/** Every labelled IRI with its `rdfs:label` by the same preference. Used to name roles and other vocabulary terms in results. */
export const labelsOf = (dataset: Dataset, language: string): Map<string, string> => textsOf(dataset, RDFS + 'label', language);

/** One bound value of a row: an IRI, a literal with its language or datatype, or a blank node. */
export interface Value { kind: 'iri' | 'literal' | 'blank'; value: string; language?: string; datatype?: string }
/** One result row: the bound variables only, by name without the question mark. */
export type Row = Record<string, Value>;
export interface Result { variables: string[]; rows: Row[]; truncated: boolean }

/**
 * Why a query did not run: `syntax` from the parser, `unsupported` for anything but SELECT,
 * `timeout` when the time limit passed, `run` from the engine.
 */
export class QueryError extends Error {
  constructor(public readonly kind: 'syntax' | 'unsupported' | 'timeout' | 'run', message: string) {
    super(message);
    this.name = 'QueryError';
  }
}

const isParseError = (e: unknown): boolean => e instanceof Error && /parse error|unknown prefix/i.test(e.message);
const messageOf = (e: unknown): string => (e instanceof Error ? e.message : String(e));

const engine = new QueryEngine();

function valueOf(term: Term): Value {
  if (term.termType === 'NamedNode') return { kind: 'iri', value: term.value };
  if (term.termType === 'BlankNode') return { kind: 'blank', value: term.value };
  const literal = term as Term & { language: string; datatype: Term };
  const value: Value = { kind: 'literal', value: term.value };
  if (literal.language) value.language = literal.language;
  else if (literal.datatype && literal.datatype.value !== 'http://www.w3.org/2001/XMLSchema#string') value.datatype = literal.datatype.value;
  return value;
}

function rowOf(bindings: Bindings): Row {
  const row: Row = {};
  for (const [variable, term] of bindings) row[variable.value] = valueOf(term);
  return row;
}

/** How much of a result is read: rows beyond `rows` are not read, and reading stops after `timeMs`. */
export interface Limits { rows?: number; timeMs?: number }
export const DEFAULT_LIMITS: Required<Limits> = { rows: 500, timeMs: 5000 };

/**
 * Runs a SPARQL `SELECT` over the dataset. Reads at most `limits.rows` rows and reports `truncated`
 * when more were available; a query's own `LIMIT` is respected within that cap. Reading stops with a
 * `timeout` error once `limits.timeMs` have passed on `clock`, checked at every row and by a timer
 * for a query that yields nothing for that long.
 */
export async function select(dataset: Dataset, sparql: string, limits: Limits = {}, clock: () => number = Date.now): Promise<Result> {
  const { rows: cap, timeMs } = { ...DEFAULT_LIMITS, ...limits };
  const deadline = clock() + timeMs;
  const timeout = () => new QueryError('timeout', `Stopped after ${timeMs / 1000} s; narrow the query`);
  let result;
  try {
    result = await engine.query(sparql, { sources: [dataset.store] });
  } catch (e) {
    throw new QueryError(isParseError(e) ? 'syntax' : 'run', messageOf(e));
  }
  if (result.resultType !== 'bindings') throw new QueryError('unsupported', 'Only SELECT is supported');
  const metadata = await result.metadata();
  // Comunica types the entries as { variable }, but at run time they are the variables themselves.
  const entries = metadata.variables as unknown as Array<{ value: string } | { variable: { value: string } }>;
  const variables = entries.map((v) => ('variable' in v ? v.variable.value : v.value));
  const rows: Row[] = [];
  let truncated = false;
  const stream = await result.execute();
  let timer: ReturnType<typeof setTimeout> | undefined;
  const expired = new Promise<never>((_, reject) => {
    timer = setTimeout(() => { stream.destroy(); reject(timeout()); }, Math.max(0, deadline - clock()));
  });
  const read = (async () => {
    for await (const bindings of stream) {
      if (clock() >= deadline) {
        stream.destroy();
        throw timeout();
      }
      if (rows.length === cap) {
        truncated = true;
        stream.destroy();
        break;
      }
      rows.push(rowOf(bindings));
    }
    return { variables, rows, truncated };
  })();
  try {
    return await Promise.race([read, expired]);
  } finally {
    clearTimeout(timer);
  }
}
