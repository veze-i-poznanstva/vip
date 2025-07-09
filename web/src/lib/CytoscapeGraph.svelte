<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import cytoscape, { type Core, type ElementDefinition } from 'cytoscape';

  export let elements: ElementDefinition[] = [];
  export let layout: string = 'cose';
  export let style: cytoscape.Stylesheet[] = [
    {
      selector: 'node',
      style: {
        'background-color': '#666',
        'label': 'data(label)',
        'text-valign': 'center',
        'text-halign': 'center',
        'font-size': '12px',
        'color': '#fff',
        'text-outline-width': 2,
        'text-outline-color': '#666',
        'width': 30,
        'height': 30
      }
    },
    {
      selector: 'edge',
      style: {
        'width': 3,
        'line-color': '#ccc',
        'target-arrow-color': '#ccc',
        'target-arrow-shape': 'triangle',
        'curve-style': 'bezier',
        'label': 'data(label)',
        'font-size': '10px',
        'text-rotation': 'autorotate',
        'text-margin-y': -10
      }
    },
    {
      selector: ':selected',
      style: {
        'background-color': '#ff6b6b',
        'line-color': '#ff6b6b',
        'target-arrow-color': '#ff6b6b',
        'source-arrow-color': '#ff6b6b'
      }
    }
  ];

  let container: HTMLDivElement;
  let cy: Core;

  onMount(() => {
    cy = cytoscape({
      container,
      elements,
      style,
      layout: {
        name: layout,
        animate: true,
        animationDuration: 500,
        fit: true,
        padding: 50
      },
      minZoom: 0.1,
      maxZoom: 10,
      wheelSensitivity: 0.2
    });

    cy.on('tap', 'node', (evt) => {
      const node = evt.target;
      console.log('Node clicked:', node.data());
    });

    cy.on('tap', 'edge', (evt) => {
      const edge = evt.target;
      console.log('Edge clicked:', edge.data());
    });
  });

  onDestroy(() => {
    if (cy) {
      cy.destroy();
    }
  });

  export function updateElements(newElements: ElementDefinition[]) {
    if (cy) {
      cy.elements().remove();
      cy.add(newElements);
      cy.layout({ name: layout, animate: true }).run();
    }
  }

  export function runLayout(layoutName: string) {
    if (cy) {
      cy.layout({ name: layoutName, animate: true }).run();
    }
  }

  export function fit() {
    if (cy) {
      cy.fit();
    }
  }

  export function resetZoom() {
    if (cy) {
      cy.zoom(1);
      cy.center();
    }
  }
</script>

<div bind:this={container} class="cytoscape-container"></div>

<style>
  .cytoscape-container {
    width: 100%;
    height: 100%;
    background-color: #f5f5f5;
    border: 1px solid #ddd;
    border-radius: 4px;
  }

  :global(.dark) .cytoscape-container {
    background-color: #1a1a1a;
    border-color: #333;
  }
</style>