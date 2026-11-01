<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { search } from './graph';
  import type { GraphIndex, GraphNode } from './graph';
  import { t } from './i18n';

  export let index: GraphIndex;
  export let query = '';
  export let placeholder: string;
  /** The entity this box currently holds; its own name opens no suggestions. */
  export let selected: GraphNode | null = null;

  const dispatch = createEventDispatcher<{ pick: GraphNode; clear: void }>();
  let input: HTMLInputElement;

  export function focus() {
    input?.focus();
  }

  $: open = query.trim().length >= 2 && query !== selected?.label;
  $: suggestions = open ? search(index, query).slice(0, 8) : [];

  function onInput() {
    if (selected && query !== selected.label) dispatch('clear');
  }

  function onKeydown(event: KeyboardEvent) {
    if (event.key === 'Enter' && suggestions.length > 0) {
      event.preventDefault();
      dispatch('pick', suggestions[0]);
    }
  }
</script>

<div class="picker">
  <input
    type="search"
    bind:this={input}
    bind:value={query}
    {placeholder}
    aria-label={placeholder}
    autocomplete="off"
    on:input={onInput}
    on:keydown={onKeydown}
  />
  {#if open}
    <ul class="suggestions">
      {#each suggestions as node (node.id)}
        <li><button type="button" on:click={() => dispatch('pick', node)}>{node.label}</button></li>
      {:else}
        <li class="none">{$t('search.none')}</li>
      {/each}
    </ul>
  {/if}
</div>

<style>
  .picker {
    position: relative;
    flex: 1;
  }

  input {
    box-sizing: border-box;
    width: 100%;
    padding: 0.4rem 0.6rem;
    font-size: 1rem;
    border: 1px solid #ced4da;
    border-radius: 4px;
  }

  .suggestions {
    position: absolute;
    top: 100%;
    left: 0;
    right: 0;
    z-index: 30;
    margin: 0.2rem 0 0;
    padding: 0;
    list-style: none;
    background: #fff;
    border: 1px solid #ced4da;
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  }

  .suggestions button {
    width: 100%;
    text-align: left;
    padding: 0.4rem 0.6rem;
    border: none;
    background: none;
    color: #212529;
    font-size: 1rem;
    cursor: pointer;
  }

  .suggestions button:hover {
    background: #f1f3f5;
  }

  .none {
    padding: 0.4rem 0.6rem;
    color: #868e96;
  }
</style>
