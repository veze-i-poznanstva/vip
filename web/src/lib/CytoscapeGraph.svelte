<script lang="ts">
  import { onMount, onDestroy, createEventDispatcher } from 'svelte';
  import cytoscape, { type Core, type ElementDefinition } from 'cytoscape';

  export let elements: ElementDefinition[] = [];
  export let layout: string = 'cose';
  /** The node drawn in the centre of a concentric layout and marked with the `focus` class. */
  export let focusId: string | null = null;

  const dispatch = createEventDispatcher<{
    nodeClick: string;
    nodeDoubleClick: string;
    nodeHover: { id: string; x: number; y: number; flipX: boolean; flipY: boolean } | null;
  }>();

  function layoutOptions(name: string): cytoscape.LayoutOptions {
    const base = { name, animate: true, animationDuration: 500, fit: true, padding: 50 };
    if (name === 'breadthfirst' && focusId) {
      // A path reads left to right, which also suits a wide canvas.
      const leftToRight = (_: unknown, position: cytoscape.Position) => ({ x: position.y * 1.6, y: position.x });
      return { ...base, animate: false, roots: [focusId], spacingFactor: 1.1, transform: leftToRight } as cytoscape.LayoutOptions;
    }
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
  let resizeObserver: ResizeObserver | undefined;

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

    cy.on('tap', 'node', (evt) => dispatch('nodeClick', evt.target.id()));

    cy.on('tap', 'edge', (evt) => {
      const edge = evt.target;
      console.log('Edge clicked:', edge.data());
    });

    // The canvas changes size when panels open below it; keep the graph inside it.
    resizeObserver = new ResizeObserver(() => {
      cy.resize();
      cy.fit(undefined, 80);
    });
    resizeObserver.observe(container);

    cy.on('mouseover', 'node', (evt) => {
      const at = evt.renderedPosition;
      dispatch('nodeHover', {
        id: evt.target.id(),
        x: at.x,
        y: at.y,
        flipX: at.x > cy.width() - 380,
        flipY: at.y > cy.height() - 180
      });
    });
    cy.on('mouseout', 'node', () => dispatch('nodeHover', null));
    cy.on('tap pan zoom', () => dispatch('nodeHover', null));

    cy.on('dbltap', 'node', (evt) => dispatch('nodeDoubleClick', evt.target.id()));
    markFocus();
  });

  // React to changes in elements prop
  $: if (cy) {
    console.log('Updating graph with', elements.length, 'elements');
    cy.elements().remove();
    cy.add(elements);
    markFocus();
    const running = cy.layout(layoutOptions(layout));
    running.one('layoutstop', () => cy.fit(undefined, 80));
    running.run();
  }

  onDestroy(() => {
    resizeObserver?.disconnect();
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

  export function zoomBy(factor: number) {
    if (cy) {
      cy.zoom({ level: cy.zoom() * factor, renderedPosition: { x: cy.width() / 2, y: cy.height() / 2 } });
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