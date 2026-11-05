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

export interface HoverContent { text: string | null; wikipedia: string | null }

const WIKIPEDIA_ARTICLE = /^https:\/\/[a-z-]+\.wikipedia\.org\/wiki\//;

/** What to show when the pointer rests on a node: its description and Wikipedia article, or nothing. */
export function hoverContent(node: GraphNode | undefined): HoverContent | null {
  const text = (typeof node?.comment === 'string' ? node.comment.trim() : '') || null;
  const link = typeof node?.wikipedia === 'string' ? node.wikipedia : '';
  const wikipedia = WIKIPEDIA_ARTICLE.test(link) ? link : null;
  return text || wikipedia ? { text, wikipedia } : null;
}

export interface PathOptions {
  /** Most paths to return; equal paths can multiply without bound in a dense graph. */
  limit?: number;
  /** Node types a path may start or end at but not pass through, such as big parties. */
  avoid?: string[];
}

function neighbourLists(index: GraphIndex): Map<string, string[]> {
  const lists = new Map<string, Set<string>>();
  const link = (a: string, b: string) => {
    if (!lists.has(a)) lists.set(a, new Set());
    lists.get(a)!.add(b);
  };
  for (const edge of index.edges) {
    link(edge.source, edge.target);
    link(edge.target, edge.source);
  }
  return new Map([...lists].map(([id, set]) => [id, [...set].sort()]));
}

/**
 * Every shortest path between two nodes, up to `limit`, following edges in either direction.
 * Breadth-first search notes each node's predecessors at the shortest distance; the paths are then
 * read back from the target, stopping at the limit, so the work stays bounded however many exist.
 */
export function shortestPaths(index: GraphIndex, from: string, to: string, options: PathOptions = {}): string[][] {
  const { limit = 10, avoid = [] } = options;
  if (from === to || !index.nodes.has(from) || !index.nodes.has(to)) return [];
  const blocked = (id: string) => avoid.includes(String(index.nodes.get(id)?.nodeType));
  const neighbours = neighbourLists(index);
  const distance = new Map([[from, 0]]);
  const predecessors = new Map<string, string[]>();
  let frontier = [from];
  while (frontier.length > 0 && !distance.has(to)) {
    const next: string[] = [];
    for (const node of frontier) {
      if (node !== from && blocked(node)) continue;
      for (const neighbour of neighbours.get(node) ?? []) {
        if (!distance.has(neighbour)) {
          distance.set(neighbour, distance.get(node)! + 1);
          next.push(neighbour);
        }
        if (distance.get(neighbour) === distance.get(node)! + 1) {
          predecessors.set(neighbour, [...(predecessors.get(neighbour) ?? []), node]);
        }
      }
    }
    frontier = next;
  }
  if (!distance.has(to)) return [];
  const paths: string[][] = [];
  const walk = (node: string, tail: string[]) => {
    if (paths.length >= limit) return;
    if (node === from) {
      paths.push([from, ...tail]);
      return;
    }
    for (const previous of predecessors.get(node) ?? []) walk(previous, [node, ...tail]);
  };
  walk(to, []);
  return paths;
}

function edgesBetween(index: GraphIndex, a: string, b: string): GraphEdge[] {
  return index.edges.filter((e) => (e.source === a && e.target === b) || (e.source === b && e.target === a));
}

/** The nodes of the paths and only the edges that are steps along them. */
export function pathElements(index: GraphIndex, paths: string[][]): Element[] {
  const ids = new Set(paths.flat());
  const edges = new Set<GraphEdge>();
  for (const path of paths) {
    for (let i = 1; i < path.length; i++) edgesBetween(index, path[i - 1], path[i]).forEach((e) => edges.add(e));
  }
  return [
    ...[...ids].flatMap((id) => (index.nodes.has(id) ? [{ data: index.nodes.get(id) as Record<string, unknown> }] : [])),
    ...[...edges].map((e) => ({ data: e as Record<string, unknown> }))
  ];
}

export interface PathStep { from: string; via: string; to: string }

/** A path in words: each step with every connection that joins its two ends. */
export function pathSteps(index: GraphIndex, path: string[]): PathStep[] {
  return path.slice(1).map((id, i) => ({
    from: index.nodes.get(path[i])!.label,
    via: edgesBetween(index, path[i], id).map((e) => e.label).join(', '),
    to: index.nodes.get(id)!.label
  }));
}

/** The ends of a path as carried in the address, so a link reproduces the same view. */
export function readPathQuery(search: string): { from: string | null; to: string | null } {
  const params = new URLSearchParams(search);
  return { from: params.get('from'), to: params.get('to') };
}

/** The address query with the path's ends set, or removed unless both are known. */
export function writePathQuery(search: string, from: string | null | undefined, to: string | null | undefined): string {
  const params = new URLSearchParams(search);
  params.delete('from');
  params.delete('to');
  if (from && to) {
    params.set('from', from);
    params.set('to', to);
  }
  const query = params.toString();
  return query ? `?${query}` : '';
}

/** The node named in the address by `?focus=`, so a link reproduces the panel. */
export function readFocusQuery(search: string): string | null {
  return new URLSearchParams(search).get('focus') || null;
}

/** The address query with the focus set, or removed when there is none; other parameters stay. */
export function writeFocusQuery(search: string, id: string | null | undefined): string {
  const params = new URLSearchParams(search);
  params.delete('focus');
  if (id) params.set('focus', id);
  const query = params.toString();
  return query ? `?${query}` : '';
}

/** The elements without nodes of the hidden types, and without the edges that touched them. */
export function filterByType(elements: Element[], hidden: Set<string>): Element[] {
  const removed = new Set(elements.filter((e) => hidden.has(String(e.data.nodeType))).map((e) => e.data.id));
  return elements.filter((e) => !removed.has(e.data.id) && !removed.has(e.data.source) && !removed.has(e.data.target));
}
