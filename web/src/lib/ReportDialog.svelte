<script lang="ts">
  import { createEventDispatcher, onMount } from 'svelte';
  import { t } from './i18n';
  import { issueUrl, reportPrompt } from './report';
  import type { ReportSubject } from './report';

  export let report: ReportSubject;

  const dispatch = createEventDispatcher<{ close: void }>();
  let dialog: HTMLDialogElement;
  let description = '';
  let copied = false;

  onMount(() => dialog.showModal());

  $: prompt = reportPrompt(report, description);

  async function copy() {
    try {
      await navigator.clipboard.writeText(prompt);
      copied = true;
    } catch {
      copied = false;
    }
  }
</script>

<dialog bind:this={dialog} on:close={() => dispatch('close')}>
  <h2>{$t('report.title')}</h2>
  <p class="subject">{report.subject}</p>
  <label>
    {$t('report.what')}
    <textarea rows="3" bind:value={description}></textarea>
  </label>

  <h3>{$t('report.send')}</h3>
  <p>{$t('report.send.text')}</p>
  <a class="action" href={issueUrl(report, description)} target="_blank" rel="noopener noreferrer">{$t('report.send.link')}</a>

  <h3>{$t('report.ai')}</h3>
  <p>{$t('report.ai.text')}</p>
  <textarea class="prompt" rows="6" readonly aria-label={$t('report.ai')} value={prompt}></textarea>
  <button type="button" on:click={copy}>{copied ? $t('report.copied') : $t('report.copy')}</button>

  <form method="dialog">
    <button type="submit">{$t('path.close')}</button>
  </form>
</dialog>

<style>
  dialog {
    box-sizing: border-box;
    width: min(38rem, calc(100vw - 2rem));
    max-height: calc(100vh - 2rem);
    padding: 1.25rem 1.5rem;
    color: #212529;
    background: #fff;
    border: 1px solid #ced4da;
    border-radius: 8px;
    font-size: 0.95rem;
    line-height: 1.45;
  }

  dialog::backdrop {
    background: rgba(0, 0, 0, 0.45);
  }

  h2 {
    margin: 0;
    font-size: 1.25rem;
  }

  h3 {
    margin: 1.1rem 0 0.25rem;
    font-size: 1rem;
  }

  p {
    margin: 0.25rem 0 0.5rem;
  }

  .subject {
    font-weight: 600;
  }

  label {
    display: block;
    margin-top: 0.5rem;
  }

  textarea {
    display: block;
    box-sizing: border-box;
    width: 100%;
    margin-top: 0.25rem;
    padding: 0.5rem;
    font: inherit;
    border: 1px solid #ced4da;
    border-radius: 4px;
  }

  .prompt {
    margin-bottom: 0.5rem;
    font-family: ui-monospace, monospace;
    font-size: 0.8rem;
  }

  .action {
    font-weight: 600;
    color: #1c6ac2;
  }

  form {
    margin-top: 1rem;
    text-align: right;
  }
</style>
