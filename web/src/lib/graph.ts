const CYRILLIC_TO_LATIN: Record<string, string> = {
  а: 'a', б: 'b', в: 'v', г: 'g', д: 'd', ђ: 'đ', е: 'e', ж: 'ž', з: 'z', и: 'i', ј: 'j', к: 'k',
  л: 'l', љ: 'lj', м: 'm', н: 'n', њ: 'nj', о: 'o', п: 'p', р: 'r', с: 's', т: 't', ћ: 'ć', у: 'u',
  ф: 'f', х: 'h', ц: 'c', ч: 'č', џ: 'dž', ш: 'š'
};

/**
 * Search form of a label: Serbian Cyrillic transliterated to Latin, lowercase, without
 * diacritics (đ included, which Unicode decomposition leaves alone).
 */
export function fold(text: string): string {
  return text
    .toLowerCase()
    .replace(/[а-џ]/g, (letter) => CYRILLIC_TO_LATIN[letter] ?? letter)
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .replace(/đ/g, 'd');
}

/** A node of the viewer's data: whatever the converter put under `data`, with id and label. */
export interface GraphNode { id: string; label: string; [key: string]: unknown }
export interface GraphEdge { id: string; source: string; target: string; label: string; [key: string]: unknown }

/** Any element in Cytoscape's `{ data: {...} }` form; edges have source and target. */
export interface Element { data: Record<string, unknown> }

export interface GraphIndex {
  nodes: Map<string, GraphNode>;
  edges: GraphEdge[];
  /** Folded label per node id, computed once at load. */
  folded: Map<string, string>;
}

export function buildIndex(elements: Element[]): GraphIndex {
  const nodes = new Map<string, GraphNode>();
  const edges: GraphEdge[] = [];
  for (const { data } of elements) {
    if (typeof data.source === 'string' && typeof data.target === 'string') edges.push(data as unknown as GraphEdge);
    else nodes.set(String(data.id), data as unknown as GraphNode);
  }
  const folded = new Map([...nodes.values()].map((n) => [n.id, fold(n.label ?? '')]));
  return { nodes, edges, folded };
}

/** Minimum query length; the design shows suggestions after two characters. */
export const MIN_QUERY = 2;

/** Nodes whose folded label contains the folded query, word-start matches first, then by label. */
export function search(index: GraphIndex, query: string): GraphNode[] {
  const needle = fold(query.trim());
  if (needle.length < MIN_QUERY) return [];
  const rank = (id: string): number => {
    const label = index.folded.get(id)!;
    const at = label.indexOf(needle);
    return at < 0 ? -1 : at === 0 || label[at - 1] === ' ' ? 0 : 1;
  };
  return [...index.nodes.values()]
    .map((node) => ({ node, rank: rank(node.id) }))
    .filter(({ rank }) => rank >= 0)
    .sort((a, b) => a.rank - b.rank || a.node.label.localeCompare(b.node.label))
    .map(({ node }) => node);
}

/** Ids of the nodes one hop from the given ones. */
export function neighbours(index: GraphIndex, ids: Iterable<string>): Set<string> {
  const around = new Set(ids);
  const found = new Set<string>();
  for (const edge of index.edges) {
    if (around.has(edge.source)) found.add(edge.target);
    if (around.has(edge.target)) found.add(edge.source);
  }
  return found;
}

/** The elements for a set of node ids: those nodes and every edge whose two ends are among them. */
export function subgraph(index: GraphIndex, ids: Set<string>): Element[] {
  const nodes = [...ids].flatMap((id) => (index.nodes.has(id) ? [{ data: index.nodes.get(id) as Record<string, unknown> }] : []));
  const edges = index.edges.filter((e) => ids.has(e.source) && ids.has(e.target)).map((e) => ({ data: e as Record<string, unknown> }));
  return [...nodes, ...edges];
}

/** The focus node with its immediate neighbourhood. */
export function egoGraph(index: GraphIndex, focus: string): Element[] {
  return subgraph(index, new Set([focus, ...neighbours(index, [focus])]));
}

/** The shown set plus the neighbours of one shown node; the input set is left as it was. */
export function expand(index: GraphIndex, shown: Set<string>, id: string): Set<string> {
  return new Set([...shown, ...neighbours(index, [id])]);
}

/** The trail of focus nodes with the new one last; a node revisited moves to the end rather than repeating. */
export function pushTrail(trail: string[], id: string): string[] {
  return [...trail.filter((t) => t !== id), id];
}
