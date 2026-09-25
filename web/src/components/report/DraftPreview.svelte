<!-- inline preview for a QA conclusions draft: one or more labelled lists side by side, the
     fallback notice when the AI wasn't used, and Use draft / Discard. Writes nothing itself --
     the parent applies the draft on "use". -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { FALLBACK_NOTICE } from '../../lib/draftrun.js'

  export let columns = [] // [{label, lines:[]}]
  export let usedFallback = false
  export let replaces = false // true when applying overwrites text already in the box(es)

  const dispatch = createEventDispatcher()
</script>

<div class="draft-panel">
  {#if usedFallback}<p class="draft-notice">{FALLBACK_NOTICE}</p>{/if}
  <div class="draft-cols">
    {#each columns as column (column.label)}
      <div class="draft-col">
        <span class="draft-col-label">{column.label}</span>
        {#if column.lines.length}
          <ul>{#each column.lines as line}<li>{line}</li>{/each}</ul>
        {:else}
          <p class="draft-empty">Nothing found</p>
        {/if}
      </div>
    {/each}
  </div>
  <div class="draft-actions">
    {#if replaces}<span class="draft-warn">Replaces the current text</span>{/if}
    <button type="button" class="btn" on:click={() => dispatch('discard')}>Discard</button>
    <button type="button" class="btn btn-primary" on:click={() => dispatch('use')}>Use draft</button>
  </div>
</div>

<style>
  .draft-panel { margin: 6px 0 12px; padding: 10px; border: 1px solid var(--outline); border-radius: 10px; background: var(--canvas); }
  .draft-notice { margin: 0 0 8px; font-size: 12px; color: var(--muted); }
  .draft-cols { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 12px; }
  .draft-col-label { display: block; font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; color: var(--muted); margin-bottom: 2px; }
  ul { margin: 0; padding-left: 18px; font-size: 13px; }
  .draft-empty { margin: 0; font-size: 13px; color: var(--muted); }
  .draft-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; margin-top: 10px; }
  .draft-warn { margin-right: auto; font-size: 12px; color: var(--muted); }
</style>
