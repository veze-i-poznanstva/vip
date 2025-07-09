<script lang="ts">
  import { onMount } from 'svelte';
  import CytoscapeGraph from './lib/CytoscapeGraph.svelte';
  import type { ElementDefinition } from 'cytoscape';

  let graph: CytoscapeGraph;
  let selectedLayout = 'cose';
  let elements: ElementDefinition[] = [];
  let loading = true;
  let error = '';
  
  const layouts = [
    { value: 'cose', label: 'Force-directed (COSE)' },
    { value: 'circle', label: 'Circle' },
    { value: 'concentric', label: 'Concentric' },
    { value: 'breadthfirst', label: 'Hierarchical' },
    { value: 'grid', label: 'Grid' },
    { value: 'random', label: 'Random' }
  ];

  onMount(async () => {
    try {
      console.log('Fetching graph data...');
      const response = await fetch('/test-data-cytoscape.json');
      if (!response.ok) {
        throw new Error(`Failed to load data: ${response.statusText}`);
      }
      const data = await response.json();
      console.log('Loaded data:', data.length, 'elements');
      elements = data;
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
    {
      selector: 'node[nodeType="person"]',
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
      selector: 'node[nodeType="organization"]',
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
      selector: 'node[nodeType="academic"]',
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
      selector: 'node[nodeType="party"]',
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
    graph?.runLayout(selectedLayout);
  }

</script>

<main>
  <div class="header">
    <h1>VIP Graph Visualizer</h1>
    <div class="controls">
      <div class="control-group">
        <label for="layout-select">Layout:</label>
        <select id="layout-select" bind:value={selectedLayout} on:change={handleLayoutChange}>
          {#each layouts as layout}
            <option value={layout.value}>{layout.label}</option>
          {/each}
        </select>
      </div>
      <div class="control-group">
        <button on:click={() => graph?.fit()} title="Fit graph to viewport">
          <span>🔍</span> Fit to View
        </button>
        <button on:click={() => graph?.resetZoom()} title="Reset zoom to 100%">
          <span>🔄</span> Reset Zoom
        </button>
      </div>
    </div>
  </div>
  
  <div class="graph-container">
    {#if loading}
      <div class="loading">Loading graph data...</div>
    {:else if error}
      <div class="error">Error: {error}</div>
    {:else}
      <CytoscapeGraph 
        bind:this={graph}
        elements={elements} 
        layout={selectedLayout}
        style={graphStyle}
      />
    {/if}
  </div>

  <div class="info">
    <div class="legend">
      <h3>Legend</h3>
      <div class="legend-items">
        <div class="legend-item">
          <div class="legend-box person-node"></div>
          <span>People</span>
        </div>
        <div class="legend-item">
          <div class="legend-box org-node"></div>
          <span>Organizations</span>
        </div>
        <div class="legend-item">
          <div class="legend-box academic-node"></div>
          <span>Academic</span>
        </div>
        <div class="legend-item">
          <div class="legend-box party-node"></div>
          <span>Political Party</span>
        </div>
        <div class="legend-item">
          <div class="legend-line"></div>
          <span>Relationships</span>
        </div>
      </div>
    </div>
    <div class="instructions">
      <p><strong>Instructions:</strong> Click and drag to pan • Scroll to zoom • Click nodes/edges for details • Try different layouts</p>
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