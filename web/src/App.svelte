<script lang="ts">
  import { onMount } from 'svelte';
  import CytoscapeGraph from './lib/CytoscapeGraph.svelte';
  import type { ElementDefinition } from 'cytoscape';
  import { language, resolveLanguage, supportedLanguages, t } from './lib/i18n';
  import type { Language } from './lib/i18n';
  import { buildIndex, egoGraph, expand, hoverContent, pushTrail, search, subgraph } from './lib/graph';
  import type { Element, GraphIndex, GraphNode, HoverContent } from './lib/graph';

  let graph: CytoscapeGraph;
  let selectedLayout = 'cose';
  let elements: ElementDefinition[] = [];
  let loading = true;
  let error = '';

  // The explorer: a search picks a focus node; the canvas then shows its neighbourhood and
  // grows by double-clicking nodes. Without a focus the whole dataset is shown, as the demo did.
  let index: GraphIndex = buildIndex([]);
  let query = '';
  let focus: GraphNode | null = null;
  let shown = new Set<string>();
  /** Every node that has been the centre this session, oldest first. */
  let trail: GraphNode[] = [];
  $: suggestions = focus && query === focus.label ? [] : search(index, query).slice(0, 8);
  $: shownElements = focus ? subgraph(index, shown) : (elements as unknown as Element[]);
  $: explorerLayout = focus ? 'concentric' : selectedLayout;

  /** Makes the node the centre: its neighbourhood replaces the canvas and it joins the trail. */
  function choose(node: GraphNode) {
    focus = node;
    query = node.label;
    shown = new Set(egoGraph(index, node.id).filter((e) => !('source' in e.data)).map((e) => String(e.data.id)));
    trail = pushTrail(trail.map((n) => n.id), node.id).map((id) => index.nodes.get(id)!);
  }

  function showAll() {
    focus = null;
    query = '';
    shown = new Set();
  }

  /** The popup for the node under the pointer. It stays briefly after the pointer leaves, so its link can be reached. */
  let hover: (HoverContent & { x: number; y: number; flipX: boolean; flipY: boolean }) | null = null;
  let hideTimer: ReturnType<typeof setTimeout> | undefined;

  function keepHover() {
    clearTimeout(hideTimer);
  }

  function hideHoverSoon() {
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => (hover = null), 400);
  }

  function onNodeHover(event: CustomEvent<{ id: string; x: number; y: number; flipX: boolean; flipY: boolean } | null>) {
    const content = event.detail ? hoverContent(index.nodes.get(event.detail.id)) : null;
    if (!event.detail || !content) {
      hideHoverSoon();
      return;
    }
    keepHover();
    hover = { ...event.detail, ...content };
  }

  function onNodeClick(event: CustomEvent<string>) {
    const node = index.nodes.get(event.detail);
    if (node && node.id !== focus?.id) choose(node);
  }

  function onNodeDoubleClick(event: CustomEvent<string>) {
    if (focus) shown = expand(index, shown, event.detail);
  }
  
  const layouts = ['cose', 'circle', 'concentric', 'breadthfirst', 'grid', 'random'];

  // The language comes from ?lang=, then the last choice on this browser, then the browser itself.
  const stored = (key: string): string | null => { try { return localStorage.getItem(key); } catch { return null; } };
  language.set(resolveLanguage(supportedLanguages as unknown as string[], [
    new URLSearchParams(location.search).get('lang'), stored('lang'), ...navigator.languages
  ]) as Language);
  $: {
    document.documentElement.lang = $language;
    document.title = $t('app.title');
    try { localStorage.setItem('lang', $language); } catch { /* private mode: the choice lasts the session */ }
  }

  onMount(async () => {
    try {
      console.log('Fetching graph data...');
      const response = await fetch('/test-data-cytoscape.json');
      if (!response.ok) {
        throw new Error($t('error.load', { status: response.statusText }));
      }
      const data = await response.json();
      console.log('Loaded data:', data.length, 'elements');
      elements = data;
      index = buildIndex(data);
      loading = false;
    } catch (e) {
      console.error('Error loading graph data:', e);
      error = e.message;
      loading = false;
      // Fallback to sample data
      elements = [
    // Classes (nodes)
    { data: { id: 'Person', label: 'Person', type: 'class' } },
    { data: { id: 'Organization', label: 'Organization', type: 'class' } },
    { data: { id: 'Document', label: 'Document', type: 'class' } },
    { data: { id: 'Role', label: 'Role', type: 'class' } },
    
    // Instances (nodes)
    { data: { id: 'john', label: 'John Doe', type: 'instance', class: 'Person' } },
    { data: { id: 'jane', label: 'Jane Smith', type: 'instance', class: 'Person' } },
    { data: { id: 'acme', label: 'ACME Corp', type: 'instance', class: 'Organization' } },
    { data: { id: 'doc1', label: 'Contract #123', type: 'instance', class: 'Document' } },
    { data: { id: 'ceo', label: 'CEO', type: 'instance', class: 'Role' } },
    
    // Properties (edges)
    { data: { source: 'john', target: 'acme', label: 'worksFor' } },
    { data: { source: 'jane', target: 'acme', label: 'worksFor' } },
    { data: { source: 'john', target: 'ceo', label: 'hasRole' } },
    { data: { source: 'acme', target: 'doc1', label: 'created' } },
    { data: { source: 'doc1', target: 'jane', label: 'signedBy' } },
    
    // Class hierarchy (edges)
    { data: { source: 'Person', target: 'Organization', label: 'rdfs:subClassOf' } },
      ];
    }
  });

  const graphStyle = [
    // Default node style (must come first)
    {
      selector: 'node',
      style: {
        'background-color': '#95a5a6',
        'shape': 'ellipse',
        'width': 60,
        'height': 60,
        'label': 'data(label)',
        'text-valign': 'center',
        'text-halign': 'center',
        'color': '#fff',
        'text-outline-width': 2,
        'text-outline-color': '#95a5a6',
        'font-size': '10px'
      }
    },
    // Specific node type styles (must come after default)
    {
      selector: 'node[nodeType = "person"]',
      style: {
        'background-color': '#4a90e2',
        'shape': 'ellipse',
        'width': 80,
        'height': 80,
        'text-valign': 'center',
        'text-halign': 'center',
        'label': 'data(label)',
        'color': '#fff',
        'text-outline-width': 2,
        'text-outline-color': '#4a90e2',
        'font-weight': 'bold',
        'font-size': '12px'
      }
    },
    {
      selector: 'node[nodeType = "organization"]',
      style: {
        'background-color': '#7ed321',
        'shape': 'rectangle',
        'width': 120,
        'height': 60,
        'label': 'data(label)',
        'text-valign': 'center',
        'text-halign': 'center',
        'color': '#fff',
        'text-outline-width': 2,
        'text-outline-color': '#7ed321',
        'font-size': '11px',
        'text-wrap': 'wrap',
        'text-max-width': '100px'
      }
    },
    {
      selector: 'node[nodeType = "academic"]',
      style: {
        'background-color': '#f39c12',
        'shape': 'round-rectangle',
        'width': 100,
        'height': 50,
        'label': 'data(label)',
        'text-valign': 'center',
        'text-halign': 'center',
        'color': '#fff',
        'text-outline-width': 2,
        'text-outline-color': '#f39c12',
        'font-size': '11px',
        'text-wrap': 'wrap',
        'text-max-width': '90px'
      }
    },
    {
      selector: 'node[nodeType = "party"]',
      style: {
        'background-color': '#e74c3c',
        'shape': 'diamond',
        'width': 80,
        'height': 80,
        'label': 'data(label)',
        'text-valign': 'center',
        'text-halign': 'center',
        'color': '#fff',
        'text-outline-width': 2,
        'text-outline-color': '#e74c3c',
        'font-size': '11px'
      }
    },
    {
      selector: 'edge',
      style: {
        'width': 2,
        'line-color': '#666',
        'target-arrow-color': '#666',
        'target-arrow-shape': 'triangle',
        'curve-style': 'bezier',
        'label': 'data(label)',
        'font-size': '10px',
        'text-rotation': 'autorotate',
        'text-margin-y': -10,
        'text-background-color': '#fff',
        'text-background-opacity': 0.8,
        'text-background-padding': '2px'
      }
    },
    {
      selector: 'edge[label="rdfs:subClassOf"]',
      style: {
        'line-style': 'dashed',
        'line-color': '#4a90e2',
        'target-arrow-color': '#4a90e2'
      }
    },
    {
      selector: 'node.focus',
      style: {
        'border-width': 5,
        'border-color': '#333',
        'width': 100,
        'height': 100,
        'font-size': '14px'
      }
    },
    {
      selector: ':selected',
      style: {
        'background-color': '#ff6b6b',
        'line-color': '#ff6b6b',
        'target-arrow-color': '#ff6b6b',
        'z-index': 999
      }
    }
  ];

  function handleLayoutChange() {
    if (!focus) graph?.runLayout(selectedLayout);
  }

</script>

<main>
  <div class="header">
    <h1>{$t('app.title')}</h1>
    <div class="search">
      <input type="search" bind:value={query} placeholder={$t('search.placeholder')} aria-label={$t('search.placeholder')} autocomplete="off" />
      {#if query.trim().length >= 2 && !(focus && query === focus.label)}
        <ul class="suggestions">
          {#each suggestions as node (node.id)}
            <li><button type="button" on:click={() => choose(node)}>{node.label}</button></li>
          {:else}
            <li class="none">{$t('search.none')}</li>
          {/each}
        </ul>
      {/if}
      {#if focus}
        <button type="button" on:click={showAll}>{$t('showAll')}</button>
      {/if}
    </div>
    <div class="controls">
      <div class="control-group">
        <label for="layout-select">{$t('layout')}:</label>
        <select id="layout-select" bind:value={selectedLayout} on:change={handleLayoutChange}>
          {#each layouts as layout}
            <option value={layout}>{$t('layout.' + layout)}</option>
          {/each}
        </select>
      </div>
      <div class="control-group">
        <button on:click={() => graph?.fit()} title={$t('fit.hint')}>
          <span>🔍</span> {$t('fit')}
        </button>
        <button on:click={() => graph?.resetZoom()} title={$t('resetZoom.hint')}>
          <span>🔄</span> {$t('resetZoom')}
        </button>
      </div>
      <div class="control-group">
        <label for="language-select">{$t('language')}:</label>
        <select id="language-select" bind:value={$language}>
          {#each supportedLanguages as code}
            <option value={code}>{$t('language.' + code)}</option>
          {/each}
        </select>
      </div>
    </div>
  </div>
  
  <div class="graph-container">
    {#if loading}
      <div class="loading">{$t('loading')}</div>
    {:else if error}
      <div class="error">{$t('error', { message: error })}</div>
    {:else}
      <CytoscapeGraph
        bind:this={graph}
        elements={shownElements}
        layout={explorerLayout}
        focusId={focus?.id ?? null}
        style={graphStyle}
        on:nodeClick={onNodeClick}
        on:nodeDoubleClick={onNodeDoubleClick}
        on:nodeHover={onNodeHover}
      />
      {#if hover}
        <!-- svelte-ignore a11y-no-static-element-interactions -->
        <div
          class="node-comment"
          class:flip-x={hover.flipX}
          class:flip-y={hover.flipY}
          style="left: calc({hover.x}px + 1rem); top: calc({hover.y}px + 1rem)"
          on:mouseenter={keepHover}
          on:mouseleave={hideHoverSoon}
        >
          <p>
            {#if hover.text}{hover.text}{/if}
            {#if hover.wikipedia}<a href={hover.wikipedia} target="_blank" rel="noopener noreferrer">{$t('wikipedia')}</a>{/if}
          </p>
        </div>
      {/if}
    {/if}
  </div>

  {#if trail.length > 0}
    <nav class="trail" aria-label={$t('trail')}>
      <span class="trail-label">{$t('trail')}:</span>
      {#each trail as node, i (node.id)}
        {#if i > 0}<span class="trail-sep">›</span>{/if}
        <button type="button" class:current={node.id === focus?.id} on:click={() => choose(node)}>{node.label}</button>
      {/each}
    </nav>
  {/if}

  <div class="info">
    <div class="legend">
      <h3>{$t('legend')}</h3>
      <div class="legend-items">
        <div class="legend-item">
          <div class="legend-box person-node"></div>
          <span>{$t('legend.people')}</span>
        </div>
        <div class="legend-item">
          <div class="legend-box org-node"></div>
          <span>{$t('legend.organizations')}</span>
        </div>
        <div class="legend-item">
          <div class="legend-box academic-node"></div>
          <span>{$t('legend.academic')}</span>
        </div>
        <div class="legend-item">
          <div class="legend-box party-node"></div>
          <span>{$t('legend.party')}</span>
        </div>
        <div class="legend-item">
          <div class="legend-line"></div>
          <span>{$t('legend.relationships')}</span>
        </div>
      </div>
    </div>
    <div class="instructions">
      <p><strong>{$t('instructions')}:</strong> {focus ? $t('search.hint', { label: focus.label }) : $t('instructions.text')}</p>
    </div>
  </div>
</main>

<style>
  main {
    display: flex;
    flex-direction: column;
    height: 100vh;
    width: 100vw;
    margin: 0;
    padding: 0;
    position: absolute;
    top: 0;
    left: 0;
  }

  .header {
    padding: 1rem;
    background-color: #f8f9fa;
    border-bottom: 1px solid #dee2e6;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  h1 {
    margin: 0;
    font-size: 1.5rem;
    color: #333;
  }

  .search {
    position: relative;
    display: flex;
    gap: 0.5rem;
    align-items: center;
    flex: 1;
    max-width: 32rem;
    margin: 0 1rem;
  }

  .search input {
    flex: 1;
    padding: 0.4rem 0.6rem;
    font-size: 1rem;
    border: 1px solid #ced4da;
    border-radius: 4px;
  }

  .suggestions {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    z-index: 10;
    margin: 0.2rem 0 0;
    padding: 0;
    list-style: none;
    background: #fff;
    border: 1px solid #ced4da;
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  }

  .suggestions button {
    width: 100%;
    text-align: left;
    padding: 0.4rem 0.6rem;
    border: none;
    background: none;
    font-size: 1rem;
    cursor: pointer;
  }

  .suggestions button:hover {
    background: #f1f3f5;
  }

  .suggestions .none {
    padding: 0.4rem 0.6rem;
    color: #868e96;
  }

  .node-comment {
    position: absolute;
    z-index: 20;
    max-width: 22rem;
    padding: 0.5rem 0.7rem;
    margin: 12px;
    font-size: 0.9rem;
    line-height: 1.35;
    color: #212529;
    background: #fff;
    border: 1px solid #ced4da;
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  }

  .node-comment p {
    margin: 0;
  }

  .node-comment a {
    color: #1c7ed6;
  }

  .node-comment.flip-x {
    transform: translateX(-100%);
    margin-left: -12px;
  }

  .node-comment.flip-y {
    transform: translateY(-100%);
    margin-top: -12px;
  }

  .node-comment.flip-x.flip-y {
    transform: translate(-100%, -100%);
  }

  .trail {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 0.3rem;
    padding: 0.4rem 1rem;
    background: #f8f9fa;
    border-top: 1px solid #dee2e6;
    font-size: 0.9rem;
  }

  .trail-label {
    font-weight: 600;
    color: #495057;
  }

  .trail-sep {
    color: #adb5bd;
  }

  .trail button {
    border: none;
    background: none;
    padding: 0.2rem 0.4rem;
    color: #1c7ed6;
    cursor: pointer;
    font-size: inherit;
  }

  .trail button.current {
    color: #212529;
    font-weight: 600;
    cursor: default;
  }

  .controls {
    display: flex;
    gap: 1.5rem;
    align-items: center;
  }

  .control-group {
    display: flex;
    gap: 0.5rem;
    align-items: center;
  }

  label {
    font-size: 14px;
    font-weight: 500;
    color: #495057;
  }

  select, button {
    padding: 0.5rem 1rem;
    border: 1px solid #ced4da;
    border-radius: 4px;
    background-color: #fff;
    cursor: pointer;
    font-size: 14px;
    transition: all 0.2s ease;
    color: #212529;
    font-weight: 500;
  }

  select {
    min-width: 180px;
    appearance: none;
    background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
    background-repeat: no-repeat;
    background-position: right 0.7rem center;
    background-size: 1em;
    padding-right: 2.5rem;
  }

  select:focus {
    outline: 2px solid #4a90e2;
    outline-offset: 2px;
    border-color: #4a90e2;
  }

  select option {
    color: #212529;
    background-color: #fff;
    font-weight: normal;
    padding: 0.5rem;
  }

  button {
    display: flex;
    align-items: center;
    gap: 0.3rem;
  }

  button:hover {
    background-color: #e9ecef;
    border-color: #adb5bd;
  }

  button span {
    font-size: 16px;
  }

  .graph-container {
    position: relative;
    flex: 1;
    padding: 1rem;
    overflow: hidden;
  }

  .info {
    padding: 1rem;
    background-color: #f8f9fa;
    border-top: 1px solid #dee2e6;
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 2rem;
  }

  .legend {
    display: flex;
    align-items: center;
    gap: 1rem;
  }

  .legend h3 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: #495057;
  }

  .legend-items {
    display: flex;
    gap: 1.5rem;
  }

  .legend-item {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 13px;
    color: #666;
  }

  .legend-box {
    width: 20px;
    height: 20px;
    border: 1px solid #ccc;
  }

  .legend-box.person-node {
    background-color: #4a90e2;
    border-radius: 50%;
  }

  .legend-box.org-node {
    background-color: #7ed321;
    border-radius: 2px;
  }

  .legend-box.academic-node {
    background-color: #f39c12;
    border-radius: 4px;
  }

  .legend-box.party-node {
    background-color: #e74c3c;
    transform: rotate(45deg);
    width: 15px;
    height: 15px;
  }

  .legend-line {
    width: 30px;
    height: 2px;
    background-color: #666;
    position: relative;
  }

  .legend-line::after {
    content: '';
    position: absolute;
    right: 0;
    top: -4px;
    width: 0;
    height: 0;
    border-left: 8px solid #666;
    border-top: 5px solid transparent;
    border-bottom: 5px solid transparent;
  }

  .instructions {
    flex: 1;
    text-align: right;
  }

  .instructions p {
    margin: 0;
    color: #666;
    font-size: 13px;
  }

  .loading, .error {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    font-size: 18px;
    color: #666;
  }

  .error {
    color: #e74c3c;
  }

  :global(.dark) .header,
  :global(.dark) .info {
    background-color: #2a2a2a;
    border-color: #444;
  }

  :global(.dark) h1 {
    color: #f0f0f0;
  }

  :global(.dark) select,
  :global(.dark) button {
    background-color: #3a3a3a;
    border-color: #555;
    color: #f0f0f0;
    font-weight: 500;
  }
  
  :global(.dark) select {
    background-image: url("data:image/svg+xml;charset=UTF-8,%3csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23f0f0f0' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3e%3cpolyline points='6 9 12 15 18 9'%3e%3c/polyline%3e%3c/svg%3e");
  }
  
  :global(.dark) select:focus {
    outline-color: #6bb3ff;
    border-color: #6bb3ff;
  }

  :global(.dark) button:hover {
    background-color: #4a4a4a;
  }

  :global(.dark) label,
  :global(.dark) .legend h3 {
    color: #e0e0e0;
  }

  :global(.dark) .legend-item,
  :global(.dark) .instructions p {
    color: #b0b0b0;
  }
</style>