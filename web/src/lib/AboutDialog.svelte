<script lang="ts">
  import { createEventDispatcher, onMount } from 'svelte';
  import { t } from './i18n';

  const dispatch = createEventDispatcher<{ close: void; report: void }>();
  const repository = 'https://github.com/veze-i-poznanstva/vip';
  const updated: string = __BUILD_DATE__;
  let dialog: HTMLDialogElement;

  onMount(() => dialog.showModal());
</script>

<dialog bind:this={dialog} on:close={() => dispatch('close')}>
  <h2>{$t('app.title')}</h2>
  <p class="tagline">{$t('about.tagline')}</p>
  <p>{$t('about.what')}</p>
  <p><strong>{$t('about.limit')}</strong></p>
  <p>{$t('about.sources')}</p>
  <p>{$t('about.updated', { date: updated })}</p>
  <p>
    <a href={repository} target="_blank" rel="noopener noreferrer">{$t('about.code')}</a>
    ·
    <a href={`${repository}/blob/main/docs/agent-prompt.sr.md`} target="_blank" rel="noopener noreferrer">{$t('about.contribute')}</a>
  </p>
  <form method="dialog">
    <button type="submit">{$t('path.close')}</button>
  </form>
</dialog>

<style>
  dialog {
    box-sizing: border-box;
    width: min(34rem, calc(100vw - 2rem));
    padding: 1.25rem 1.5rem;
    color: #212529;
    background: #fff;
    border: 1px solid #ced4da;
    border-radius: 8px;
    font-size: 0.95rem;
    line-height: 1.5;
  }

  dialog::backdrop {
    background: rgba(0, 0, 0, 0.45);
  }

  h2 {
    margin: 0;
    font-size: 1.35rem;
  }

  .tagline {
    margin-top: 0.15rem;
    font-weight: 600;
  }

  a {
    color: #1c6ac2;
  }

  form {
    margin-top: 1rem;
    text-align: right;
  }
</style>
