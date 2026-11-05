<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { t } from './i18n';
  import { githubLinks } from './details';

  export let file: string | undefined;
  export let line: number | undefined;
  /** A link to this view, for the copy button. */
  export let permalink: string;

  const dispatch = createEventDispatcher<{ report: void }>();
  $: links = githubLinks(file, line);
  let copied = false;

  async function copy() {
    try {
      await navigator.clipboard.writeText(permalink);
      copied = true;
    } catch {
      copied = false;
    }
  }
</script>

<section>
  <h3>{$t('details.data')}</h3>
  <div class="row">
    {#if links}
      <a href={links.record} target="_blank" rel="noopener noreferrer">{$t('data.github')}</a>
      <a href={links.raw} target="_blank" rel="noopener noreferrer">{$t('data.raw')}</a>
      <a href={links.history} target="_blank" rel="noopener noreferrer">{$t('data.history')}</a>
    {/if}
    <button type="button" on:click={() => dispatch('report')}>{$t('report')}</button>
    <button type="button" on:click={copy}>{copied ? $t('data.copied') : $t('data.copy')}</button>
  </div>
</section>

<style>
  h3 { margin: 1rem 0 0.3rem; font-size: 0.8rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.03em; color: #495057; }
  .row { display: flex; flex-wrap: wrap; gap: 0.3rem 0.9rem; align-items: center; }
  a, button { color: #1c6ac2; font-size: 0.9rem; }
  button { padding: 0; border: none; background: none; cursor: pointer; font-weight: 400; }
  button:hover { background: none; text-decoration: underline; }
  a:hover { text-decoration: underline; }
</style>
