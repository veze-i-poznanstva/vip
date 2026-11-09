import type { GraphEdge, GraphIndex, GraphNode } from './graph';

/** Translator as the `$t` store provides it. */
export type Translate = (key: string, params?: Record<string, string | number>) => string;

export interface Source {
  url: string;
  domain: string;
  type?: string;
  status?: string;
  quote?: string;
  archive?: string;
  archivedOn?: string;
}

export interface Fact { property: string; label: string; value: string }

/** The optional fields the converter adds to an edge's data. */
export interface ConnectionFields {
  label?: string;
  kind?: string;
  typeLabel?: string;
  roles?: string[];
  share?: number;
}

export interface ConnectionRow {
  edge: GraphEdge;
  /** The node at the other end; undefined if the edge points at a node that is not in the data. */
  other: GraphNode | undefined;
  direction: 'out' | 'in' | 'self';
}

/** One row per edge touching the node, in data order. */
export function connectionsOf(index: GraphIndex, nodeId: string): ConnectionRow[] {
  return index.edges.flatMap((edge): ConnectionRow[] => {
    if (edge.source === nodeId && edge.target === nodeId) return [{ edge, other: index.nodes.get(nodeId), direction: 'self' }];
    if (edge.source === nodeId) return [{ edge, other: index.nodes.get(edge.target), direction: 'out' }];
    if (edge.target === nodeId) return [{ edge, other: index.nodes.get(edge.source), direction: 'in' }];
    return [];
  });
}

const present = (value: string | undefined): value is string => typeof value === 'string' && value !== '';

/** The period as written in the data; a missing end means unknown, never "ongoing". */
export function period(start: string | undefined, end: string | undefined, t: Translate): string {
  if (present(start) && present(end)) return t('period.range', { start, end });
  if (present(start)) return t('period.since', { start });
  if (present(end)) return t('period.until', { end });
  return t('period.unknown');
}

/** The translated kind of a connection, or undefined when the kind is unknown or untranslated. */
function kindText(edge: ConnectionFields, t: Translate): string | undefined {
  if (!edge.kind) return undefined;
  const key = `edge.${edge.kind}`;
  const text = t(key);
  return text === key ? undefined : text;
}

/** What the tie is, in words: translated kind, else the type label, else the converter's label. */
export function connectionKind(edge: ConnectionFields, t: Translate): string {
  return kindText(edge, t) ?? edge.typeLabel ?? edge.label ?? '';
}

const withShare = (text: string, share: number | undefined) => (typeof share === 'number' ? `${text} ${share}%` : text);

/** The label drawn on the canvas: the translated kind and share when known, else the converter's label. */
export function edgeLabel(edge: ConnectionFields, t: Translate): string {
  const kind = kindText(edge, t);
  return kind === undefined ? edge.label ?? '' : withShare(kind, edge.share);
}

/** The tie as the panel words it: kind, share and roles. */
export function connectionTitle(edge: ConnectionFields, t: Translate): string {
  const text = withShare(connectionKind(edge, t), edge.share);
  return edge.roles && edge.roles.length > 0 ? `${text} (${edge.roles.join(', ')})` : text;
}

export interface SourceGroup { domain: string; sources: Source[] }
export interface GroupedSources {
  groups: SourceGroup[];
  sourceCount: number;
  domainCount: number;
  /** Distinct domains with at least one source that is not removed. */
  publishedDomains: number;
}

const bareDomain = (domain: string) => domain.replace(/^www\./i, '').toLowerCase();

/** Sources grouped by domain (www ignored), first-seen order; removed ones stay but do not count as independent. */
export function groupSources(sources: Source[] | undefined): GroupedSources {
  const groups = new Map<string, SourceGroup>();
  for (const source of sources ?? []) {
    const key = bareDomain(source.domain ?? '');
    if (!groups.has(key)) groups.set(key, { domain: key, sources: [] });
    groups.get(key)!.sources.push(source);
  }
  const list = [...groups.values()];
  return {
    groups: list,
    sourceCount: (sources ?? []).length,
    domainCount: list.length,
    publishedDomains: list.filter((g) => g.sources.some((s) => s.status !== 'removed')).length
  };
}

const REPOSITORY = 'veze-i-poznanstva/vip';

export interface GithubLinks { record: string; raw: string; history: string }

/** Links to the record in the repository; null when the export gave no file. */
export function githubLinks(file: string | undefined, line?: number): GithubLinks | null {
  if (!file) return null;
  return {
    record: `https://github.com/${REPOSITORY}/blob/main/${file}${typeof line === 'number' ? `#L${line}` : ''}`,
    raw: `https://raw.githubusercontent.com/${REPOSITORY}/main/${file}`,
    history: `https://github.com/${REPOSITORY}/commits/main/${file}`
  };
}

const WIKIDATA_ENTITY = /^http:\/\/www\.wikidata\.org\/entity\/(Q[1-9][0-9]*)$/;

export interface WikidataPage { id: string; url: string }

/** The Q number and the page to open for a Wikidata entity IRI; null for anything else. */
export function wikidataPage(iri: unknown): WikidataPage | null {
  const match = typeof iri === 'string' ? WIKIDATA_ENTITY.exec(iri) : null;
  return match ? { id: match[1], url: `https://www.wikidata.org/wiki/${match[1]}` } : null;
}

export type PanelContent =
  | { kind: 'node'; node: GraphNode }
  | { kind: 'edge'; edge: GraphEdge; from: GraphNode | undefined; to: GraphNode | undefined };

/** What the panel shows: a clicked edge wins over the focus node; nothing when neither is known. */
export function panelContent(index: GraphIndex, focusId: string | null, edgeId: string | null): PanelContent | null {
  const edge = edgeId ? index.edges.find((e) => e.id === edgeId) : undefined;
  if (edge) return { kind: 'edge', edge, from: index.nodes.get(edge.source), to: index.nodes.get(edge.target) };
  const node = focusId ? index.nodes.get(focusId) : undefined;
  return node ? { kind: 'node', node } : null;
}
