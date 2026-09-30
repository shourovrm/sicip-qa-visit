<!-- qa-v2 evidence for one criterion: numbered chips ("Trainers list (Attachment 3)", × removes it from
     this criterion only) and a box that suggests this report's evidence, the app's own tables and
     the Word tables while typing. Enter or Add dispatches `add` with the name; the parent applies
     lib/evidence.js so a reused name keeps its first number. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { evidenceLabel } from '../../lib/evidence.js'

  export let entries = [] // itemEvidence(data, item.id)
  export let suggestions = [] // evidenceSuggestions(template, data, item.id)
  export let disabled = false

  const dispatch = createEventDispatcher()
  const listId = `evidence-${Math.random().toString(36).slice(2, 9)}`
  let typed = ''

  function add() {
    const name = typed.trim()
    if (!name) return
    dispatch('add', name)
    typed = ''
  }
  function onKey(event) {
    if (event.key === 'Enter') {
      event.preventDefault()
      add()
    }
  }
</script>

<div class="evidence">
  <div class="label">Evidence seen</div>
  {#if entries.length}
    <ul class="chips">
      {#each entries as entry (entry._id)}
        <li class="chip">
          <span>{evidenceLabel(entry)}</span>
          {#if !disabled}<button type="button" class="x" aria-label="Remove {entry.name}" on:click={() => dispatch('remove', entry._id)}>×</button>{/if}
        </li>
      {/each}
    </ul>
  {/if}
  {#if !disabled}
    <div class="add">
      <input type="text" list={listId} placeholder="Add evidence (document, register, photo)" bind:value={typed} on:keydown={onKey} />
      <datalist id={listId}>{#each suggestions as name}<option value={name} />{/each}</datalist>
      <button type="button" class="btn" on:click={add} disabled={!typed.trim()}>Add</button>
    </div>
  {/if}
</div>

<style>
  .evidence { margin-top: 10px; }
  .label { font-size: 12px; font-weight: 700; color: var(--muted); margin-bottom: 4px; }
  .chips { list-style: none; margin: 0 0 6px; padding: 0; display: flex; flex-wrap: wrap; gap: 6px; }
  .chip { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; padding: 3px 4px 3px 10px; border: 1px solid var(--outline); border-radius: var(--radius-pill); background: var(--canvas); }
  .x { border: none; background: none; cursor: pointer; font-size: 15px; line-height: 1; color: var(--muted); padding: 0 4px; }
  .add { display: flex; gap: 6px; }
  .add input { flex: 1; min-width: 0; font-size: 13px; }
</style>
