<!-- one criterion's final Remarks: the printed bullets plus Edit. A hand edit (one point per
     line, with Improve wording) is stored as entry.ai = {source, text}; source = the built
     bullets it replaced, so changing an answer later makes it stale and the bullets come back
     (lib/remarks.js printedRemarks). Dispatches the whole new entry, like CriteriaItem. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import ImproveWording from './ImproveWording.svelte'
  import { buildRemarks, printedRemarks } from '../../lib/remarks.js'

  export let item
  export let entry = undefined
  export let disabled = false

  const dispatch = createEventDispatcher()
  let editing = false
  let draft = ''

  $: bullets = printedRemarks(item, entry ?? {})
  $: built = buildRemarks(item, entry ?? {}).join('\n')
  $: stale = Boolean(entry?.ai?.text) && entry.ai.source !== built

  function startEdit() {
    draft = bullets.join('\n')
    editing = true
  }
  function save() {
    dispatch('change', { ...entry, ai: { source: built, text: draft.trim() } })
    editing = false
  }
  function reset() {
    const { ai, ...rest } = entry ?? {}
    dispatch('change', rest)
    editing = false
  }
</script>

{#if editing}
  <textarea class="draft" rows={Math.max(3, bullets.length + 1)} bind:value={draft} placeholder="One point per line"></textarea>
  <ImproveWording text={draft} label={item.text} {disabled} on:change={(e) => (draft = e.detail)} />
  <div class="actions">
    {#if entry?.ai?.text}<button type="button" class="btn-link" on:click={reset}>Reset</button>{/if}
    <button type="button" class="btn-link" on:click={() => (editing = false)}>Cancel</button>
    <button type="button" class="btn btn-primary" on:click={save}>Save</button>
  </div>
{:else}
  {#if bullets.length === 0}
    <p class="empty">Nothing yet. Mark an option to add a sentence.</p>
  {:else}
    <ul>{#each bullets as b}<li>{b}</li>{/each}</ul>
    {#if !disabled}<button type="button" class="btn-link edit" on:click={startEdit}>Edit</button>{/if}
  {/if}
  {#if stale}<p class="stale">Answers changed after your edit, so the remarks were rebuilt. Edit to write them again.</p>{/if}
{/if}

<style>
  ul { margin: 0; padding-left: 16px; font-size: 13px; }
  li { margin: 2px 0; }
  .empty { margin: 0; font-size: 12px; color: var(--muted); font-style: italic; }
  .edit { font-size: 12px; margin-top: 4px; }
  .draft { width: 100%; font-size: 13px; }
  .actions { display: flex; justify-content: flex-end; gap: 10px; align-items: center; margin-top: 6px; }
  .stale { margin: 4px 0 0; font-size: 12px; color: var(--danger); }
</style>
