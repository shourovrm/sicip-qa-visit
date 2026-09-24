<!-- repeatable entries (attendance courses, identity spot-checks, graduate calls, follow-up
     issues) -- add/remove rows, each rendering block.fields via FieldInput. Emits the whole new
     array on every change (functional update) rather than mutating in place, so the parent's
     `data = data` reassignment after handling it is what triggers Svelte + the autosave timer;
     mutating cards in place here would leave that reassignment with nothing new to see. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import FieldInput from './FieldInput.svelte'

  export let block // {type:'cards', key, itemLabel, start, titleField, compare?, fields}
  export let cards = [] // data.cards[block.key]
  export let disabled = false

  const dispatch = createEventDispatcher()

  function emit(next) {
    dispatch('change', next)
  }

  function addCard() {
    emit([...cards, {}])
  }
  function removeCard(index) {
    emit(cards.filter((_, i) => i !== index))
  }
  function setField(index, key, value) {
    emit(cards.map((c, i) => (i === index ? { ...c, [key]: value } : c)))
  }

  function cardTitle(card, index) {
    const named = block.titleField ? String(card[block.titleField] ?? '').trim() : ''
    return named || `${block.itemLabel} ${index + 1}`
  }

  // spec: >=2 non-blank compare fields present, not all equal -> mismatch. under 2 present, no
  // verdict yet -- ask for more figures instead of guessing.
  function compareState(card) {
    if (!block.compare) return null
    const present = block.compare.fields.map((k) => card[k]).filter((v) => v != null && String(v).trim() !== '')
    if (present.length < 2) return 'need'
    const nums = present.map(Number)
    return nums.every((n) => n === nums[0]) ? 'match' : 'mismatch'
  }
</script>

<div class="cards">
  {#each cards as card, index (index)}
    <div class="card entry">
      <div class="entry-header">
        <span class="entry-title">{cardTitle(card, index)}</span>
        {#if !disabled}<button type="button" class="btn-link remove" on:click={() => removeCard(index)}>Remove</button>{/if}
      </div>
      {#each block.fields as field (field.key)}
        <FieldInput {field} value={card[field.key] ?? ''} {disabled} on:change={(e) => setField(index, field.key, e.detail)} />
      {/each}
      {#if block.compare}
        {@const state = compareState(card)}
        <p class="compare" class:match={state === 'match'} class:mismatch={state === 'mismatch'}>
          {#if state === 'need'}Enter {block.compare.fields.length} figures to check for a mismatch.
          {:else if state === 'match'}Figures match.
          {:else}{block.compare.message}{/if}
        </p>
      {/if}
    </div>
  {/each}
  {#if !disabled}
    <button type="button" class="btn add-card" on:click={addCard}>+ Add {block.itemLabel.toLowerCase()}</button>
  {/if}
</div>

<style>
  .cards { margin-top: 8px; }
  .entry { margin-bottom: 12px; }
  .entry-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
  .entry-title { font-weight: 700; font-size: 13px; }
  .remove { color: var(--danger); }
  .compare { margin: 4px 0 0; padding: 8px 10px; border-radius: 8px; font-size: 13px; background: var(--canvas); color: var(--muted); }
  .compare.match { background: var(--tone-yes-bg); color: var(--tone-yes-fg); font-weight: 700; }
  .compare.mismatch { background: var(--tone-no-bg); color: var(--tone-no-fg); font-weight: 700; }
  .add-card { width: 100%; background: var(--primary-container); color: var(--on-primary-container); margin-top: 4px; }
</style>
