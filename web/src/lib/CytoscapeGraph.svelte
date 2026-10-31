<script lang="ts">
  import { onMount, onDestroy, createEventDispatcher } from 'svelte';
  import cytoscape, { type Core, type ElementDefinition } from 'cytoscape';

  export let elements: ElementDefinition[] = [];
  export let layout: string = 'cose';
  /** The node drawn in the centre of a concentric layout and marked with the `focus` class. */
  export let focusId: string | null = null;

  const dispatch = createEventDispatcher<{ nodeDoubleClick: string }>();

  function layoutOptions(name: string): cytoscape.LayoutOptions {
    const base = { name, animate: true, animationDuration: 500, fit: true, padding: 50 };
    if (name !== 'concentric') return base as cytoscape.LayoutOptions;
    return { ...base, concentric: (node: cytoscape.NodeSingular) => (node.id() === focusId ? 2 : 1), levelWidth: () => 1, minNodeSpacing: 40 } as cytoscape.LayoutOptions;
  }

  function markFocus() {
    cy.nodes().removeClass('focus');
    if (focusId) cy.getElementById(focusId).addClass('focus');
  }
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
      layout: layoutOptions(layout),
      minZoom: 0.1,
      maxZoom: 10,
      wheelSensitivity: 0.2
    });

    cy.on('tap', 'node', (evt) => {
      const node = evt.target;
      console.log('Node clicked:', node.data());
      console.log('Node type:', node.data('nodeType'));
      console.log('Node classes:', node.classes());
    });

    cy.on('tap', 'edge', (evt) => {
      const edge = evt.target;
      console.log('Edge clicked:', edge.data());
    });

    cy.on('dbltap', 'node', (evt) => dispatch('nodeDoubleClick', evt.target.id()));
    markFocus();
  });

  // React to changes in elements prop
  $: if (cy && elements.length > 0) {
    console.log('Updating graph with', elements.length, 'elements');
    cy.elements().remove();
    cy.add(elements);
    markFocus();
    cy.layout(layoutOptions(layout)).run();
  }

  onDestroy(() => {
    if (cy) {
      cy.destroy();
    }
  });

  export function updateElements(newElements: ElementDefinition[]) {
    if (cy) {
      cy.elements().remove();
      cy.add(newElements);
      cy.layout(layoutOptions(layout)).run();
    }
  }

  export function runLayout(layoutName: string) {
    if (cy) {
      cy.layout(layoutOptions(layoutName)).run();
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