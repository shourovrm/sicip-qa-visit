<!-- repeatable entries (persons met, courses, attendance, identity spot-checks, graduate calls,
     follow-up issues, free-text flags) -- add/remove rows, each rendering block.fields via
     FieldInput. Emits the whole new array on every change (functional update) rather than
     mutating in place, so the parent's `data = data` reassignment after handling it is what
     triggers Svelte + the autosave timer; mutating cards in place here would leave that
     reassignment with nothing new to see.

     A block with `linkFrom` (only C attendance, linked to A courses) is read-only scaffolding:
     no Add/Remove -- syncLinks (called by the parent on every change) owns which cards exist --
     and the linked fields (course/batch) render as a plain-text header instead of inputs. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import FieldInput from './FieldInput.svelte'

  export let block // {type:'cards', key, itemLabel, start, titleField, compare?, fields, linkFrom?}
  export let cards = [] // data.cards[block.key]
  export let disabled = false
  export let courseOptions = [] // for a courseRef field among block.fields, if any
  export let template = null // only needed for a linked block's "jump to the source section" link

  const dispatch = createEventDispatcher()

  $: linked = Boolean(block.linkFrom)
  $: linkFields = block.linkFrom?.fields ?? []
  $: editableFields = linked ? block.fields.filter((f) => !linkFields.includes(f.key)) : block.fields

  function emit(next) {
    dispatch('change', next)
  }

  function addCard() {
    emit([...cards, { _id: crypto.randomUUID() }])
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

  // e.g. "Welding (SMAW) · Batch 07" -- linkFrom.fields is always [course, batch] on this
  // template's one linked block, so this isn't worth generalising for a single caller.
  function linkedHeader(card, index) {
    const [course, batch] = linkFields.map((key) => card[key])
    if (!course) return cardTitle(card, index)
    return batch ? `${course} · Batch ${batch}` : course
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

  // find which section owns the linked block's source cards (e.g. "courses" lives in section A)
  // so the empty-state link can open + scroll to it, same mechanism as SectionChips' chip jump.
  function jumpToSource() {
    if (!template || !block.linkFrom) return
    let targetKey = null
    for (const section of template.sections) {
      if (section.blocks.some((b) => b.type === 'cards' && b.key === block.linkFrom.cards)) targetKey = section.key
    }
    const el = targetKey && document.getElementById(`section-${targetKey}`)
    if (!el) return
    el.open = true
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
</script>

<div class="cards">
  {#if linked && cards.length === 0}
    <p class="empty-link">No courses yet. <button type="button" class="btn-link" on:click={jumpToSource}>Add courses in section A</button></p>
  {/if}
  {#each cards as card, index (card._id ?? index)}
    <div class="card entry">
      <div class="entry-header">
        {#if linked}
          <span class="entry-title">{linkedHeader(card, index)}</span>
        {:else}
          <span class="entry-title">{cardTitle(card, index)}</span>
          {#if !disabled}<button type="button" class="btn-link remove" on:click={() => removeCard(index)}>Remove</button>{/if}
        {/if}
      </div>
      {#each editableFields as field (field.key)}
        <FieldInput {field} value={card[field.key] ?? ''} {disabled}
          courseOptions={field.kind === 'courseRef' ? courseOptions : undefined}
          on:change={(e) => setField(index, field.key, e.detail)} />
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
  {#if !disabled && !linked}
    <button type="button" class="btn add-card" on:click={addCard}>+ Add {block.itemLabel.toLowerCase()}</button>
  {/if}
</div>

<style>
  .cards { margin-top: 8px; }
  .entry { margin-bottom: 12px; }
  .entry-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
  .entry-title { font-weight: 700; font-size: 13px; }
  .remove { color: var(--danger); }
  .empty-link { font-size: 13px; color: var(--muted); margin: 4px 0 12px; }
  .compare { margin: 4px 0 0; padding: 8px 10px; border-radius: 8px; font-size: 13px; background: var(--canvas); color: var(--muted); }
  .compare.match { background: var(--tone-yes-bg); color: var(--tone-yes-fg); font-weight: 700; }
  .compare.mismatch { background: var(--tone-no-bg); color: var(--tone-no-fg); font-weight: 700; }
  .add-card { width: 100%; background: var(--primary-container); color: var(--on-primary-container); margin-top: 4px; }
</style>
