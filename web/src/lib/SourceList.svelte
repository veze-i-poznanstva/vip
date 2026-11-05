<script lang="ts">
  import { t } from './i18n';
  import { groupSources } from './details';
  import type { Source } from './details';

  export let sources: Source[] | undefined;

  $: grouped = groupSources(sources);
  const docTypes = ['newsArticle', 'encyclopediaArticle', 'registryRecord', 'officialDocument', 'factCheckProfile', 'webPage', 'videoRecording'];
  const shown = (url: string) => url.replace(/^[a-z][a-z0-9+.-]*:\/\//i, '');
  const safe = (url: string | undefined) => (url && /^https?:\/\//i.test(url) ? url : undefined);
</script>

<section>
  <h3>{$t('details.sources', { count: grouped.sourceCount, domains: grouped.domainCount })}</h3>
  {#if grouped.sourceCount === 0}
    <p class="quiet">{$t('details.noSources')}</p>
  {:else}
    {#if grouped.publishedDomains < 2}
      <p class="warning">{$t('details.fewDomains')}</p>
    {/if}
    {#each grouped.groups as group (group.domain)}
      <div class="group">
        <strong>{group.domain}</strong>
        <ul>
          {#each group.sources as source}
            <li class:removed={source.status === 'removed'}>
              <div class="line">
                {#if source.type}<span class="type">{docTypes.includes(source.type) ? $t('doc.' + source.type) : source.type}</span>{/if}
                {#if source.status === 'removed'}<span class="badge">{$t('details.removed')}</span>{/if}
              </div>
              {#if safe(source.url)}
                <a class="url" href={safe(source.url)} target="_blank" rel="noopener noreferrer" title={source.url}>{shown(source.url)}</a>
              {:else}
                <span class="url">{source.url}</span>
              {/if}
              {#if safe(source.archive)}
                <a class="archive" href={safe(source.archive)} target="_blank" rel="noopener noreferrer">{source.archivedOn ? $t('details.archived', { date: source.archivedOn }) : $t('details.archive')}</a>
              {/if}
              {#if source.quote}<blockquote>{source.quote}</blockquote>{/if}
            </li>
          {/each}
        </ul>
      </div>
    {/each}
  {/if}
</section>

<style>
  h3 { margin: 1rem 0 0.3rem; font-size: 0.8rem; font-weight: 600; text-transform: uppercase; letter-spacing: 0.03em; color: #495057; }
  p { margin: 0.25rem 0; }
  .quiet { color: #868e96; }
  .warning { color: #a65d00; font-size: 0.85rem; }
  .group { margin-top: 0.5rem; }
  ul { margin: 0.2rem 0 0; padding: 0; list-style: none; }
  li { margin-bottom: 0.5rem; padding-left: 0.5rem; border-left: 2px solid #ced4da; }
  li.removed { opacity: 0.6; }
  .line { display: flex; gap: 0.4rem; align-items: center; font-size: 0.8rem; color: #495057; }
  .badge { padding: 0 0.35rem; border: 1px solid #adb5bd; border-radius: 999px; font-size: 0.75rem; }
  a { color: #1c6ac2; }
  .url { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .archive { font-size: 0.85rem; }
  blockquote { margin: 0.25rem 0 0; padding: 0; font-size: 0.85rem; color: #212529; font-style: italic; }
</style>
