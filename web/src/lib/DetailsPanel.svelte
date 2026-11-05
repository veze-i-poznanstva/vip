<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { t } from './i18n';
  import { connectionKind, connectionsOf, connectionTitle, period } from './details';
  import type { ConnectionFields, Fact, PanelContent, Source } from './details';
  import type { GraphEdge, GraphIndex } from './graph';
  import SourceList from './SourceList.svelte';
  import DataLinks from './DataLinks.svelte';

  export let content: PanelContent;
  export let index: GraphIndex;
  /** True when a connection is shown and the focus node can be returned to. */
  export let canBack = false;
  /** Builds the address that reproduces a view of the given node. */
  export let permalink: (id: string) => string;
  /** The focus node, whose address an opened connection's link keeps. */
  export let focusId: string | null = null;

  const dispatch = createEventDispatcher<{ close: void; centre: string; selectEdge: string; back: void; report: { subject: string; uri?: string } }>();

  const typeKeys: Record<string, string> = { person: 'legend.people', organization: 'legend.organizations', academic: 'legend.academic', party: 'legend.party' };
  const knownFacts = ['birthDate', 'birthPlace', 'foundationDate', 'industry', 'location'];
  const factLabel = (fact: Fact) => (knownFacts.includes(fact.property) ? $t('fact.' + fact.property) : fact.label);
  const str = (value: unknown): string | undefined => (typeof value === 'string' && value !== '' ? value : undefined);
  const list = <T,>(value: unknown): T[] => (Array.isArray(value) ? (value as T[]) : []);
  const safeLink = (url: string | undefined) => (url && /^https?:\/\//i.test(url) ? url : undefined);

  const title = (e: GraphEdge, tr: typeof $t) => connectionTitle(e as unknown as ConnectionFields, tr);
  const kindOf = (e: GraphEdge, tr: typeof $t) => connectionKind(e as unknown as ConnectionFields, tr);

  $: node = content.kind === 'node' ? content.node : null;
  $: edge = content.kind === 'edge' ? content.edge : null;
  $: rows = node ? connectionsOf(index, node.id) : [];
  $: aliases = node ? list<string>(node.aliases) : [];
  $: facts = node ? list<Fact>(node.facts) : [];
  $: nodeSources = node ? list<Source>(node.sources) : [];
  $: edgeSources = edge ? list<Source>(edge.sources) : [];
  $: fromName = content.kind === 'edge' ? content.from?.label ?? '?' : '';
  $: toName = content.kind === 'edge' ? content.to?.label ?? '?' : '';
  $: edgeFacts = edge
    ? [
        { key: 'details.type', value: kindOf(edge, $t) },
        ...(list<string>(edge.roles).length > 0 ? [{ key: 'details.roles', value: list<string>(edge.roles).join(', ') }] : []),
        ...(typeof edge.share === 'number' ? [{ key: 'details.share', value: `${edge.share}%` }] : []),
        { key: 'details.period', value: period(str(edge.start), str(edge.end), $t) },
        ...(str(edge.comment) ? [{ key: 'details.comment', value: str(edge.comment)! }] : [])
      ]
    : [];
</script>

<div class="panel-body">
  {#if node}
    <header>
      <h2>{node.label}</h2>
      {#if typeKeys[String(node.nodeType)]}<span class="chip">{$t(typeKeys[String(node.nodeType)])}</span>{/if}
      <button type="button" class="close" aria-label={$t('details.close')} title={$t('details.close')} on:click={() => dispatch('close')}>×</button>
    </header>

    {#if str(node.comment) || safeLink(str(node.wikipedia)) || aliases.length > 0}
      <section class="description">
        {#if str(node.comment)}<p>{node.comment}</p>{/if}
        {#if safeLink(str(node.wikipedia))}<p><a href={safeLink(str(node.wikipedia))} target="_blank" rel="noopener noreferrer">{$t('wikipedia')}</a></p>{/if}
        {#if aliases.length > 0}<p class="quiet">{$t('details.aka', { names: aliases.join(', ') })}</p>{/if}
      </section>
    {/if}

    {#if facts.length > 0}
      <section>
        <h3>{$t('details.facts')}</h3>
        <dl>
          {#each facts as fact}
            <dt>{factLabel(fact)}</dt>
            <dd>{fact.value}</dd>
          {/each}
        </dl>
      </section>
    {/if}

    <section>
      <h3>{$t('details.connections', { count: rows.length })}</h3>
      <ul class="connections">
        {#each rows as row (row.edge.id)}
          <li>
            <div class="row-main">
              {#if row.other}
                <button type="button" class="link name" title={$t('details.otherParty', { name: row.other.label })} on:click={() => dispatch('centre', row.other?.id ?? '')}>{row.other.label}</button>
              {:else}
                <span class="name">?</span>
              {/if}
              <button type="button" class="link tie" on:click={() => dispatch('selectEdge', row.edge.id)}>
                {title(row.edge, $t)}
                <span class="when">{period(str(row.edge.start), str(row.edge.end), $t)}</span>
                {#if list(row.edge.sources).length > 0}<span class="count">{$t('details.sourceCount', { count: list(row.edge.sources).length })}</span>{/if}
              </button>
            </div>
          </li>
        {/each}
      </ul>
    </section>

    <SourceList sources={nodeSources} />
    <DataLinks file={str(node.file)} line={typeof node.line === 'number' ? node.line : undefined} permalink={permalink(node.id)}
      on:report={() => node && dispatch('report', { subject: node.label, uri: str(node.uri) })} />
  {:else if edge}
    <header>
      <button type="button" class="link" on:click={() => dispatch('back')} hidden={!canBack}>← {$t('details.back')}</button>
      <button type="button" class="close" aria-label={$t('details.close')} title={$t('details.close')} on:click={() => dispatch('close')}>×</button>
    </header>
    <h2>{fromName} → {toName}</h2>
    <p class="quiet">{title(edge, $t)}</p>
    <section>
      <dl>
        {#each edgeFacts as fact}
          <dt>{$t(fact.key)}</dt>
          <dd>{fact.value}</dd>
        {/each}
      </dl>
    </section>
    <SourceList sources={edgeSources} />
    <DataLinks file={str(edge.file)} line={typeof edge.line === 'number' ? edge.line : undefined} permalink={permalink(focusId ?? edge.source)}
      on:report={() => edge && dispatch('report', { subject: `${fromName} – ${toName}`, uri: str(edge.uri) })} />
  {/if}
</div>

<style>
  .panel-body { padding: 0.9rem 1rem 1.2rem; color: #212529; font-size: 0.95rem; line-height: 1.4; }
  header { display: flex; align-items: center; gap: 0.5rem; }
  header h2 { flex: 1; }
  h2 { margin: 0 0 0.25rem; font-size: 1.15rem; overflow-wrap: anywhere; }
  header .close { margin-left: auto; }
  h3 { margin: 1rem 0 0.3rem; font-size: 0.8rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.03em; color: #495057; }
  p { margin: 0.25rem 0; }
  .quiet { color: #868e96; }
  .chip { flex: none; padding: 0.05rem 0.5rem; border: 1px solid #ced4da; border-radius: 999px; font-size: 0.8rem; color: #495057; }
  a, button.link { color: #1c6ac2; }
  .close { flex: none; width: 2rem; height: 2rem; padding: 0; border: 1px solid #ced4da; border-radius: 4px; background: #fff; font-size: 1.2rem; line-height: 1; cursor: pointer; color: #212529; }
  button.link { padding: 0; border: none; background: none; cursor: pointer; font-size: inherit; font-weight: 400; text-align: left; }
  button.link:hover { background: none; text-decoration: underline; }
  dl { display: grid; grid-template-columns: max-content 1fr; gap: 0.2rem 0.8rem; margin: 0; }
  dt { color: #495057; }
  dd { margin: 0; overflow-wrap: anywhere; }
  .connections { margin: 0; padding: 0; list-style: none; }
  .connections li { padding: 0.35rem 0; border-bottom: 1px solid #e9ecef; }
  .row-main { display: flex; flex-direction: column; align-items: flex-start; gap: 0.1rem; }
  .name { font-weight: 600; }
  .tie { display: flex; flex-wrap: wrap; gap: 0.1rem 0.5rem; }
  .when, .count { color: #868e96; font-size: 0.85rem; }
</style>
