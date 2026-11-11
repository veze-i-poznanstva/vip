<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { language, t } from './i18n';
  import type { GraphIndex } from './graph';
  import type { Engine } from './engine';
  import { describeValue, leadFocus, nodesByUri } from './leads';
  import type { LeadFocus, Naming } from './leads';
  import { QueryError } from './query';
  import type { Result, Row } from './query';
  import { writeQueryAddress } from './advanced';

  export let engine: Engine;
  export let index: GraphIndex;
  /** The query to start from: pasted from Ask, read from the address, or empty. */
  export let query = '';

  const dispatch = createEventDispatcher<{ show: LeadFocus }>();

  let result: Result | null = null;
  let error = '';
  let shared = false;
  let template = '';

  $: naming = { nodes: nodesByUri(index.nodes.values()), labels: engine.labels($language), t: $t } satisfies Naming;

  async function run() {
    error = '';
    result = null;
    try {
      result = await engine.select(query);
    } catch (e) {
      const kind = e instanceof QueryError ? e.kind : 'run';
      error = $t(`ask.error.${kind}`, { message: e instanceof Error ? e.message : String(e) });
    }
  }

  function loadTemplate() {
    const rule = engine.rules.find((r) => r.name === template);
    if (rule) query = rule.query;
    template = '';
  }

  async function share() {
    const address = location.origin + location.pathname + writeQueryAddress(location.search, query);
    history.replaceState(null, '', address);
    try {
      await navigator.clipboard.writeText(address);
      shared = true;
      setTimeout(() => (shared = false), 2000);
    } catch { /* the address bar carries the link anyway */ }
  }

  function show(row: Row) {
    const focus = leadFocus(row, naming.nodes);
    if (focus) dispatch('show', focus);
  }
</script>

<p class="hint">{$t('advanced.hint')}</p>

<label class="field">
  <span>{$t('advanced.query')}</span>
  <textarea rows="10" bind:value={query} spellcheck="false" placeholder={'SELECT ?person WHERE { ... }'}></textarea>
</label>
<div class="actions">
  <button type="button" class="primary" on:click={run} disabled={!query.trim()}>{$t('ask.run')}</button>
  <select bind:value={template} on:change={loadTemplate} aria-label={$t('advanced.templates')}>
    <option value="">{$t('advanced.templates')}</option>
    {#each engine.rules as rule}
      <option value={rule.name}>{rule.title[$language] || rule.title.en}</option>
    {/each}
  </select>
  <button type="button" on:click={share} disabled={!query.trim()}>{shared ? $t('advanced.shared') : $t('advanced.share')}</button>
</div>

{#if error}
  <p class="error">{error}</p>
{:else if result}
  <p class="count">{$t('ask.results', { count: result.rows.length })}</p>
  {#if result.rows.length > 0}
    <div class="table-wrap">
      <table>
        <thead>
          <tr>{#each result.variables as variable}<th>{variable}</th>{/each}<th></th></tr>
        </thead>
        <tbody>
          {#each result.rows as row}
            <tr>
              {#each result.variables as variable}
                <td title={row[variable]?.value ?? ''}>{row[variable] ? describeValue(row[variable], naming) : ''}</td>
              {/each}
              <td><button type="button" class="link" on:click={() => show(row)}>{$t('leads.show')}</button></td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
    {#if result.truncated}<p class="quiet">{$t('leads.truncated', { count: result.rows.length })}</p>{/if}
  {/if}
{/if}
<p class="caveat">{$t('leads.caveat')}</p>

<style>
  .hint { margin: 0.2rem 0 0.6rem; color: #495057; }
  .field { display: block; margin: 0.5rem 0; }
  .field span { display: block; font-weight: 600; margin-bottom: 0.2rem; }
  textarea { width: 100%; box-sizing: border-box; font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.8rem; padding: 0.4rem; border: 1px solid #ced4da; border-radius: 4px; }
  .actions { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; margin: 0.3rem 0 0.6rem; }
  .actions button, .actions select { padding: 0.35rem 0.7rem; }
  .count { margin: 0.2rem 0; font-weight: 600; }
  .table-wrap { overflow-x: auto; }
  table { border-collapse: collapse; font-size: 0.85rem; }
  th, td { text-align: left; padding: 0.25rem 0.5rem; border-bottom: 1px solid #e9ecef; vertical-align: top; }
  th { color: #495057; font-weight: 600; }
  .link { border: none; background: none; padding: 0; color: #1c7ed6; cursor: pointer; font-size: inherit; }
  .quiet { color: #868e96; margin: 0.2rem 0; }
  .error { color: #c92a2a; margin: 0.2rem 0; }
  .caveat { margin-top: 1rem; color: #868e96; font-style: italic; }
</style>
