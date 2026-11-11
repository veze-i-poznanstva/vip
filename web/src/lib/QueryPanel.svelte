<script lang="ts">
  import { createEventDispatcher, onMount } from 'svelte';
  import { t } from './i18n';
  import type { GraphIndex } from './graph';
  import { loadEngine } from './engine';
  import type { Engine } from './engine';
  import type { LeadFocus } from './leads';
  import LeadsPanel from './LeadsPanel.svelte';
  import AskPanel from './AskPanel.svelte';
  import AdvancedPanel from './AdvancedPanel.svelte';

  /** The graph, for naming agents and opening them on the map. */
  export let index: GraphIndex;
  /** Which view is open: the leads, the question box, or the pasted query. */
  export let mode: 'leads' | 'ask' | 'advanced' = 'leads';
  /** The query the advanced view starts with, from the address or from an answer. */
  export let query = '';

  const dispatch = createEventDispatcher<{ close: void; show: LeadFocus }>();

  // The engine, its data and its rules load when the panel first opens. The whole panel is imported
  // on demand by the app, so the SPARQL engine stays out of the page's first load.
  let engine: Engine | null = null;
  let error = '';

  const fetchText = async (name: string) => {
    const response = await fetch(`${import.meta.env.BASE_URL}${name}`);
    if (!response.ok) throw new Error(`${name}: ${response.status}`);
    return response.text();
  };

  onMount(async () => {
    try {
      engine = await loadEngine(fetchText);
    } catch (e) {
      error = e instanceof Error ? e.message : String(e);
    }
  });

  // Advanced appears as a tab only once something opened it: an answer's "edit the query" or the address.
  let advancedSeen = mode === 'advanced' || query !== '';
  $: if (mode === 'advanced') advancedSeen = true;
  $: tabs = [{ mode: 'leads', key: 'leads' }, { mode: 'ask', key: 'ask' }, ...(advancedSeen ? [{ mode: 'advanced', key: 'advanced' }] : [])] as { mode: typeof mode; key: string }[];

  function editQuery(event: CustomEvent<string>) {
    query = event.detail;
    mode = 'advanced';
  }
</script>

<div class="panel-body">
  <header>
    <h2>{$t('query.title')}</h2>
    <button type="button" class="close" aria-label={$t('query.close')} title={$t('query.close')} on:click={() => dispatch('close')}>×</button>
  </header>
  <nav class="tabs" aria-label={$t('query.title')}>
    {#each tabs as tab}
      <button type="button" class:current={mode === tab.mode} aria-pressed={mode === tab.mode} on:click={() => (mode = tab.mode)}>{$t(tab.key)}</button>
    {/each}
  </nav>

  {#if error}
    <p class="error">{$t('leads.error', { message: error })}</p>
  {:else if !engine}
    <p class="quiet">{$t('leads.loading')}</p>
  {:else if mode === 'leads'}
    <LeadsPanel {engine} {index} on:show />
  {:else if mode === 'ask'}
    <AskPanel {engine} {index} on:show on:advanced={editQuery} />
  {:else if mode === 'advanced'}
    <AdvancedPanel {engine} {index} bind:query on:show />
  {/if}
</div>

<style>
  .panel-body { padding: 0.75rem 1rem 1rem; font-size: 0.9rem; color: #212529; }
  header { display: flex; align-items: center; gap: 0.5rem; }
  h2 { flex: 1; margin: 0; font-size: 1.1rem; }
  .close { border: none; background: none; font-size: 1.4rem; line-height: 1; padding: 0 0.3rem; cursor: pointer; color: #495057; }
  .tabs { display: flex; gap: 0.3rem; margin: 0.5rem 0; border-bottom: 1px solid #dee2e6; }
  .tabs button { border: none; background: none; padding: 0.3rem 0.6rem; cursor: pointer; color: #1c7ed6; font-size: inherit; border-bottom: 2px solid transparent; }
  .tabs button.current { color: #212529; font-weight: 600; border-bottom-color: #212529; cursor: default; }
  .quiet { color: #868e96; margin: 0.2rem 0; }
  .error { color: #c92a2a; margin: 0.2rem 0; }
</style>
