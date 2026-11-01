<script lang="ts">
  import { onMount, tick } from 'svelte';
  import CytoscapeGraph from './lib/CytoscapeGraph.svelte';
  import EntityPicker from './lib/EntityPicker.svelte';
  import ReportDialog from './lib/ReportDialog.svelte';
  import AboutDialog from './lib/AboutDialog.svelte';
  import type { ReportSubject } from './lib/report';
  import type { ElementDefinition } from 'cytoscape';
  import { language, resolveLanguage, supportedLanguages, t } from './lib/i18n';
  import type { Language } from './lib/i18n';
  import { buildIndex, egoGraph, expand, filterByType, hoverContent, pathElements, pathSteps, pushTrail, readPathQuery, shortestPaths, subgraph, writePathQuery } from './lib/graph';
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

  // Connecting two entities, as directions work in a map: a From box and a To box. The search box
  // becomes From, and a node's popup is a shortcut that fills it.
  let connecting = false;
  let pathFrom: GraphNode | null = null;
  let pathTo: GraphNode | null = null;
  let toQuery = '';
  let toPicker: EntityPicker | undefined;
  let avoidHubs = false;
  $: paths = pathFrom && pathTo ? shortestPaths(index, pathFrom.id, pathTo.id, { avoid: avoidHubs ? ['party', 'academic'] : [] }) : [];
  $: pathShown = pathFrom && pathTo
    ? (paths.length > 0 ? pathElements(index, paths) : [pathFrom, pathTo].map((n) => ({ data: n as Record<string, unknown> })))
    : null;

  $: if (!loading) {
    history.replaceState(null, '', location.pathname + writePathQuery(location.search, pathFrom?.id, pathTo?.id));
  }

  // Types switched off in the filter; a path always shows every step, so it ignores them.
  let hiddenTypes = new Set<string>();
  const filters = [
    { type: 'person', box: 'person-node', key: 'legend.people' },
    { type: 'organization', box: 'org-node', key: 'legend.organizations' },
    { type: 'academic', box: 'academic-node', key: 'legend.academic' },
    { type: 'party', box: 'party-node', key: 'legend.party' }
  ];

  function toggleType(type: string) {
    const next = new Set(hiddenTypes);
    if (!next.delete(type)) next.add(type);
    hiddenTypes = next;
  }

  $: shownElements = pathShown ?? filterByType(focus ? subgraph(index, shown) : (elements as unknown as Element[]), hiddenTypes);
  $: explorerLayout = pathShown ? 'breadthfirst' : focus ? 'concentric' : selectedLayout;

  /** Makes the node the centre: its neighbourhood replaces the canvas and it joins the trail. */
  function choose(node: GraphNode) {
    closePath();
    focus = node;
    query = node.label;
    shown = new Set(egoGraph(index, node.id).filter((e) => !('source' in e.data)).map((e) => String(e.data.id)));
    trail = pushTrail(trail.map((n) => n.id), node.id).map((id) => index.nodes.get(id)!);
  }

  function openPath() {
    connecting = true;
    pathFrom = focus;
    if (!focus) query = '';
    tick().then(() => toPicker?.focus());
  }

  function startPath(id: string) {
    const node = index.nodes.get(id);
    if (!node) return;
    connecting = true;
    pathFrom = node;
    pathTo = null;
    query = node.label;
    toQuery = '';
    hover = null;
    tick().then(() => toPicker?.focus());
  }

  function pickFrom(event: CustomEvent<GraphNode>) {
    if (!connecting) {
      choose(event.detail);
      return;
    }
    pathFrom = event.detail;
    query = event.detail.label;
    if (!pathTo) toPicker?.focus();
  }

  function pickTo(event: CustomEvent<GraphNode>) {
    pathTo = event.detail;
    toQuery = event.detail.label;
  }

  function swapPath() {
    [pathFrom, pathTo] = [pathTo, pathFrom];
    [query, toQuery] = [toQuery, query];
  }

  function closePath() {
    connecting = false;
    pathFrom = null;
    pathTo = null;
    toQuery = '';
  }

  let report: ReportSubject | null = null;
  let about = false;

  function reportNode(id: string) {
    const node = index.nodes.get(id);
    if (!node) return;
    hover = null;
    report = { subject: node.label, uri: typeof node.uri === 'string' ? node.uri : undefined };
  }

  function showAll() {
    closePath();
    focus = null;
    query = '';
    shown = new Set();
  }

  /** The popup for the node under the pointer. It stays briefly after the pointer leaves, so its link can be reached. */
  let hover: (HoverContent & { id: string; x: number; y: number; flipX: boolean; flipY: boolean }) | null = null;
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
      const response = await fetch(`${import.meta.env.BASE_URL}test-data-cytoscape.json`);
      if (!response.ok) {
        throw new Error($t('error.load', { status: response.statusText }));
      }
      const data = await response.json();
      console.log('Loaded data:', data.length, 'elements');
      elements = data;
      index = buildIndex(data);
      loading = false;
      const linked = readPathQuery(location.search);
      if (linked.from && linked.to && index.nodes.has(linked.from) && index.nodes.has(linked.to)) {
        connecting = true;
        pathFrom = index.nodes.get(linked.from)!;
        pathTo = index.nodes.get(linked.to)!;
        query = pathFrom.label;
        toQuery = pathTo.label;
      }
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
        'font-size': '12px',
        'text-wrap': 'wrap',
        'text-max-width': '72px'
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
      <EntityPicker
        {index}
        bind:query
        selected={connecting ? pathFrom : focus}
        placeholder={connecting ? $t('path.from') : $t('search.placeholder')}
        on:pick={pickFrom}
        on:clear={() => connecting && (pathFrom = null)}
      />
      {#if connecting}
        <button type="button" class="square" title={$t('path.swap')} aria-label={$t('path.swap')} on:click={swapPath}>⇄</button>
        <EntityPicker
          bind:this={toPicker}
          {index}
          bind:query={toQuery}
          selected={pathTo}
          placeholder={$t('path.to')}
          on:pick={pickTo}
          on:clear={() => (pathTo = null)}
        />
        <button type="button" on:click={closePath}>{$t('path.close')}</button>
      {:else}
        <button type="button" class="primary" on:click={openPath}>{$t('path.open')}</button>
      {/if}
      {#if focus || connecting}
        <button type="button" on:click={showAll}>{$t('showAll')}</button>
      {/if}
    </div>
    <button type="button" class="link" on:click={() => (about = true)}>{$t('about')}</button>
    <button type="button" class="link" on:click={() => (report = { subject: $t('report.general') })}>{$t('report')}</button>
    <select id="language-select" aria-label={$t('language')} bind:value={$language}>
      {#each supportedLanguages as code}
        <option value={code} aria-label={$t('language.' + code)}>{code.toUpperCase()}</option>
      {/each}
    </select>
  </div>

  <div class="graph-container">
    <div class="canvas-tools">
      <button type="button" class="square" aria-label={$t('zoomIn')} title={$t('zoomIn')} on:click={() => graph?.zoomBy(1.25)}>+</button>
      <button type="button" class="square" aria-label={$t('zoomOut')} title={$t('zoomOut')} on:click={() => graph?.zoomBy(0.8)}>−</button>
      <button type="button" title={$t('fit.hint')} on:click={() => graph?.fit()}>{$t('fit')}</button>
      <button type="button" title={$t('resetZoom.hint')} on:click={() => graph?.resetZoom()}>100%</button>
      <label class="layout-tool">
        <span>{$t('layout')}</span>
        <select bind:value={selectedLayout} on:change={handleLayoutChange} disabled={!!focus || connecting}>
          {#each layouts as layout}
            <option value={layout}>{$t('layout.' + layout)}</option>
          {/each}
        </select>
      </label>
    </div>
    {#if loading}
      <div class="loading">{$t('loading')}</div>
    {:else if error}
      <div class="error">{$t('error', { message: error })}</div>
    {:else}
      <CytoscapeGraph
        bind:this={graph}
        elements={shownElements}
        layout={explorerLayout}
        focusId={pathFrom && pathTo ? pathFrom.id : focus?.id ?? null}
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
          <button type="button" class="connect" on:click={() => hover && startPath(hover.id)}>{$t('path.connect')}</button>
          <button type="button" class="connect" on:click={() => hover && reportNode(hover.id)}>{$t('report')}</button>
        </div>
      {/if}
    {/if}
  </div>

  {#if pathFrom && pathTo}
    <section class="path" aria-label={$t('path.connect')}>
      <div class="path-head">
        <strong>{pathFrom.label} – {pathTo.label}</strong>
        <label><input type="checkbox" bind:checked={avoidHubs} /> {$t('path.avoid')}</label>
        <button type="button" class="link" on:click={() => (report = { subject: `${pathFrom?.label} – ${pathTo?.label}` })}>{$t('report')}</button>
      </div>
      {#if paths.length === 0}
        <p>{$t('path.none')}</p>
      {:else}
        <ol>
          {#each paths as path}
            <li>
              {#each pathSteps(index, path) as step, i}
                {#if i === 0}<b>{step.from}</b>{/if}
                <span class="via">{step.via}</span>
                <b>{step.to}</b>
              {/each}
            </li>
          {/each}
        </ol>
      {/if}
      <p class="path-note">{$t('path.note')}</p>
    </section>
  {/if}

  {#if trail.length > 0}
    <nav class="trail" aria-label={$t('trail')}>
      <span class="trail-label">{$t('trail')}:</span>
      {#each trail as node, i (node.id)}
        {#if i > 0}<span class="trail-sep">›</span>{/if}
        <button type="button" class:current={node.id === focus?.id} on:click={() => choose(node)}>{node.label}</button>
      {/each}
    </nav>
  {/if}

  {#if !(pathFrom && pathTo)}
  <div class="info">
    <div class="legend">
      <h3>{$t('legend')}</h3>
      <div class="legend-items">
        {#each filters as filter}
          <button
            type="button"
            class="legend-item filter"
            class:off={hiddenTypes.has(filter.type)}
            aria-pressed={!hiddenTypes.has(filter.type)}
            on:click={() => toggleType(filter.type)}
          >
            <div class="legend-box {filter.box}"></div>
            <span>{$t(filter.key)}</span>
          </button>
        {/each}
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
  {/if}
  {#if report}
    <ReportDialog {report} on:close={() => (report = null)} />
  {/if}
  {#if about}
    <AboutDialog on:close={() => (about = false)} />
  {/if}
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
    gap: 1rem;
    align-items: center;
  }

  h1 {
    flex: none;
    margin: 0;
    font-size: 1.25rem;
    color: #333;
  }

  .search {
    display: flex;
    flex: 1;
    gap: 0.5rem;
    align-items: center;
    min-width: 0;
  }

  .search button {
    flex: none;
    height: 2.75rem;
  }

  #language-select {
    flex: none;
    width: 4.25rem;
    min-width: 0;
    height: 2.75rem;
    padding-left: 0.6rem;
    padding-right: 1.4rem;
    font-size: 0.9rem;
  }

  .search .square {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 2.75rem;
    padding: 0;
    font-size: 1.2rem;
  }

  button.link {
    flex: none;
    padding: 0.3rem 0.4rem;
    border: none;
    background: none;
    color: #1c6ac2;
    font-size: 0.9rem;
    white-space: nowrap;
    cursor: pointer;
  }

  button.link:hover {
    text-decoration: underline;
  }

  .search .primary {
    color: #fff;
    background: #1f5fbf;
    border-color: #1f5fbf;
  }

  .canvas-tools {
    position: absolute;
    left: 1.75rem;
    top: 1.75rem;
    z-index: 10;
    display: flex;
    flex-wrap: wrap;
    gap: 0.35rem;
    align-items: center;
  }

  .canvas-tools button,
  .canvas-tools select {
    height: 2.5rem;
    background: #fff;
  }

  .canvas-tools .square {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 2.5rem;
    padding: 0;
    font-size: 1.2rem;
  }

  .layout-tool {
    display: flex;
    gap: 0.35rem;
    align-items: center;
    font-size: 0.85rem;
    color: #495057;
  }

  @media (max-width: 800px) {
    .header {
      flex-wrap: wrap;
    }

    .search {
      flex: 1 0 100%;
      flex-wrap: wrap;
      order: 3;
    }

    .search :global(.picker) {
      flex: 1 0 100%;
    }
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

  .node-comment .connect {
    margin-top: 0.4rem;
    padding: 0.2rem 0.5rem;
    font-size: 0.85rem;
    cursor: pointer;
  }

  .path {
    max-height: 30vh;
    overflow-y: auto;
    padding: 0.5rem 1rem;
    font-size: 0.9rem;
    background: #fff;
    border-top: 1px solid #dee2e6;
    color: #212529;
  }

  .path-head {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 1rem;
  }

  .path ol {
    margin: 0.4rem 0;
    padding-left: 1.4rem;
  }

  .path .via {
    margin: 0 0.3rem;
    color: #495057;
  }

  .path .via::before {
    content: '– ';
  }

  .path .via::after {
    content: ' –';
  }

  .path p {
    margin: 0.3rem 0;
  }

  .path-note {
    color: #868e96;
    font-style: italic;
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
    flex-wrap: wrap;
    gap: 0.5rem 0.75rem;
    align-items: center;
  }

  .legend-item {
    display: flex;
    align-items: center;
    gap: 0.5rem;
    font-size: 13px;
    color: #666;
  }

  button.filter {
    padding: 0.3rem 0.6rem;
    min-height: 2.25rem;
    background: #fff;
    border: 1px solid #ced4da;
    border-radius: 999px;
    cursor: pointer;
  }

  button.filter:hover {
    border-color: #868e96;
  }

  button.filter.off {
    background: transparent;
    border-style: dashed;
    color: #868e96;
    text-decoration: line-through;
  }

  button.filter.off .legend-box {
    opacity: 0.25;
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