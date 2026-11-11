<script lang="ts">
  import { createEventDispatcher, onMount } from 'svelte';
  import { language, t } from './i18n';
  import type { GraphIndex } from './graph';
  import type { Engine } from './engine';
  import { leadFocus, leadSentence, nodesByUri } from './leads';
  import type { LeadFocus, Naming, RuleRun } from './leads';
  import type { Row } from './query';

  /** The loaded engine: data, vocabulary and rules. */
  export let engine: Engine;
  /** The graph, for naming agents and opening them on the map. */
  export let index: GraphIndex;

  const dispatch = createEventDispatcher<{ show: LeadFocus }>();

  let runs: RuleRun[] = [];
  let running = true;

  onMount(async () => {
    runs = await engine.runRules();
    running = false;
  });

  $: naming = { nodes: nodesByUri(index.nodes.values()), labels: engine.labels($language), t: $t } satisfies Naming;
  const text = (byLanguage: Record<string, string>, lang: string) => byLanguage[lang] || byLanguage.en || '';

  function show(row: Row) {
    const focus = leadFocus(row, naming.nodes);
    if (focus) dispatch('show', focus);
  }
</script>

<p class="hint">{$t('leads.hint')}</p>

{#if running}
  <p class="quiet">{$t('leads.loading')}</p>
{:else}
  {#each runs as run (run.rule.name)}
    <section>
      <h3>{text(run.rule.title, $language)}</h3>
      <p class="explanation">{text(run.rule.explanation, $language)}</p>
      {#if run.error}
        <p class="error">{$t('leads.ruleError', { message: run.error })}</p>
      {:else if run.rows.length === 0}
        <p class="quiet">{$t('leads.none')}</p>
      {:else}
        <p class="count">{$t('leads.count', { count: run.rows.length })}</p>
        <ol>
          {#each run.rows as row}
            <li>
              <span>{leadSentence(run.rule.name, row, naming)}</span>
              <button type="button" class="link" on:click={() => show(row)}>{$t('leads.show')}</button>
            </li>
          {/each}
        </ol>
        {#if run.truncated}<p class="quiet">{$t('leads.truncated', { count: run.rows.length })}</p>{/if}
      {/if}
    </section>
  {/each}
  <p class="caveat">{$t('leads.caveat')}</p>
{/if}

<style>
  h3 { margin: 0.9rem 0 0.2rem; font-size: 1rem; }
  .hint, .explanation { margin: 0.2rem 0 0.4rem; color: #495057; }
  .count { margin: 0.2rem 0; font-weight: 600; }
  ol { margin: 0.2rem 0 0.4rem; padding-left: 1.3rem; }
  li { margin: 0.3rem 0; }
  .link { border: none; background: none; padding: 0 0 0 0.4rem; color: #1c7ed6; cursor: pointer; font-size: inherit; }
  .quiet { color: #868e96; margin: 0.2rem 0; }
  .error { color: #c92a2a; margin: 0.2rem 0; }
  .caveat { margin-top: 1rem; color: #868e96; font-style: italic; }
</style>
