<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { language, t } from './i18n';
  import type { GraphIndex } from './graph';
  import type { Engine } from './engine';
  import { assistantLinks, briefAddress, buildPrompt, extractQuery, summarizeVocabulary, usedTerms } from './ask';
  import { leadFocus, leadSentence, nodesByUri } from './leads';
  import type { LeadFocus, Naming } from './leads';
  import { QueryError } from './query';
  import type { Result } from './query';

  export let engine: Engine;
  export let index: GraphIndex;

  const dispatch = createEventDispatcher<{ show: LeadFocus; advanced: string }>();

  let question = '';
  let answer = '';
  let copied = false;
  let result: Result | null = null;
  let ran = '';
  let error = '';
  let showQuery = false;
  let running = false;

  // Only the terms the records use: a shorter prompt that still names everything a query can match.
  const used = usedTerms(engine.dataset);
  $: summary = summarizeVocabulary(engine.dataset, $language, used);
  $: prompt = buildPrompt(summary, engine.rules, question, $t);
  // The link carries a short instruction to read the brief on the published site, not the brief itself.
  $: brief = briefAddress($language);
  // Without a question there is nothing to hand over, and a link without an address cannot be followed by pointer or keyboard.
  $: links = question.trim() ? assistantLinks($t('ask.link', { url: brief, question: question.trim() })) : null;
  $: naming = { nodes: nodesByUri(index.nodes.values()), labels: engine.labels($language), t: $t } satisfies Naming;

  async function copyPrompt() {
    try {
      await navigator.clipboard.writeText(prompt);
      copied = true;
      setTimeout(() => (copied = false), 2000);
    } catch { /* the text area below the button stays selectable */ }
  }

  // One query at a time: a second press while the first still runs would let the slower answer overwrite the newer one.
  async function run() {
    if (running) return;
    error = '';
    result = null;
    ran = extractQuery(answer);
    if (!ran) {
      error = $t('ask.noQuery');
      return;
    }
    running = true;
    try {
      result = await engine.select(ran);
    } catch (e) {
      const kind = e instanceof QueryError ? e.kind : 'run';
      error = $t(`ask.error.${kind}`, { message: e instanceof Error ? e.message : String(e) });
    } finally {
      running = false;
    }
  }

  function show(row: Result['rows'][number]) {
    const focus = leadFocus(row, naming.nodes);
    if (focus) dispatch('show', focus);
  }
</script>

<p class="hint">{$t('ask.hint')}</p>

<label class="field">
  <span>{$t('ask.question')}</span>
  <textarea rows="3" bind:value={question} placeholder={$t('ask.question.placeholder')}></textarea>
</label>
<div class="actions">
  <button type="button" on:click={copyPrompt} disabled={!question.trim()}>{copied ? $t('ask.copied') : $t('ask.copy')}</button>
  <a href={links?.claude} target="_blank" rel="noopener noreferrer" class:disabled={!links} aria-disabled={!links}>{$t('ask.open.claude')}</a>
  <a href={links?.chatgpt} target="_blank" rel="noopener noreferrer" class:disabled={!links} aria-disabled={!links}>{$t('ask.open.chatgpt')}</a>
</div>
<p class="quiet">{$t('ask.open.note', { url: brief })}</p>

<label class="field">
  <span>{$t('ask.answer')}</span>
  <textarea rows="5" bind:value={answer} placeholder={$t('ask.answer.placeholder')}></textarea>
</label>
<div class="actions">
  <button type="button" class="primary" on:click={run} disabled={!answer.trim() || running}>{$t('ask.run')}</button>
  {#if ran}
    <button type="button" class="link" on:click={() => (showQuery = !showQuery)}>{$t('ask.showQuery')}</button>
    <button type="button" class="link" on:click={() => dispatch('advanced', ran)}>{$t('ask.advanced')}</button>
  {/if}
</div>
{#if showQuery && ran}<pre>{ran}</pre>{/if}

{#if error}
  <p class="error">{error}</p>
{:else if result}
  <p class="count">{$t('ask.results', { count: result.rows.length })}</p>
  {#if result.rows.length > 0}
    <ol>
      {#each result.rows as row}
        <li>
          <span>{leadSentence('ask', row, naming)}</span>
          <button type="button" class="link" on:click={() => show(row)}>{$t('leads.show')}</button>
        </li>
      {/each}
    </ol>
    {#if result.truncated}<p class="quiet">{$t('leads.truncated', { count: result.rows.length })}</p>{/if}
  {/if}
{/if}
<p class="caveat">{$t('ask.note')}</p>

<style>
  .hint { margin: 0.2rem 0 0.6rem; color: #495057; }
  .field { display: block; margin: 0.5rem 0; }
  .field span { display: block; font-weight: 600; margin-bottom: 0.2rem; }
  textarea { width: 100%; box-sizing: border-box; font: inherit; padding: 0.4rem; border: 1px solid #ced4da; border-radius: 4px; }
  .actions { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; margin: 0.3rem 0 0.6rem; }
  .actions a { color: #1c7ed6; }
  .actions a.disabled { pointer-events: none; color: #adb5bd; }
  .actions button { padding: 0.35rem 0.7rem; }
  .actions button.link { border: none; background: none; padding: 0; color: #1c7ed6; cursor: pointer; font-size: inherit; }
  pre { white-space: pre-wrap; word-break: break-word; background: #f8f9fa; padding: 0.5rem; border-radius: 4px; font-size: 0.8rem; }
  .count { margin: 0.2rem 0; font-weight: 600; }
  ol { margin: 0.2rem 0 0.4rem; padding-left: 1.3rem; }
  li { margin: 0.3rem 0; }
  li .link { border: none; background: none; padding: 0 0 0 0.4rem; color: #1c7ed6; cursor: pointer; font-size: inherit; }
  .quiet { color: #868e96; margin: 0.2rem 0; }
  .error { color: #c92a2a; margin: 0.2rem 0; }
  .caveat { margin-top: 1rem; color: #868e96; font-style: italic; }
</style>
