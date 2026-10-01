<!-- repeatable entries (persons met, courses, attendance, identity spot-checks, graduate calls,
     follow-up issues, free-text flags) -- add/remove rows, each rendering block.fields via
     FieldInput. Emits the whole new array on every change (functional update) rather than
     mutating in place, so the parent's `data = data` reassignment after handling it is what
     triggers Svelte + the autosave timer; mutating cards in place here would leave that
     reassignment with nothing new to see.

     A block with `linkFrom` (C attendance, I interviews -- both linked to A courses) is
     read-only scaffolding: no Add/Remove -- normalize() (called by the parent on every change)
     owns which cards exist -- and the linked fields (course/batch) render as a plain-text header
     instead of inputs.

     A block with `display: "tabs"` (I interviews) renders as a course tab strip instead of
     stacked cards: one tab per card, label + "x/y answered", green once done; only the selected
     course's fields show at a time. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import FieldInput from './FieldInput.svelte'
  import { compareMismatch, isBlank, isShown } from '../../lib/reporttemplate.js'

  export let block // {type:'cards', key, itemLabel, start, titleField, compare?, fields, linkFrom?, display?}
  export let cards = [] // data.cards[block.key]
  export let disabled = false
  export let optionsFor = () => [] // (field, card) -> suggestions for a courseRef field
  export let template = null // only needed for a linked block's "jump to the source section" link
  export let suggest = () => [] // (field, card) -> typing hints for a field with `suggest`
  export let seed = () => ({}) // starting values of a new card (newFrom)
  export let fill = () => null // (fields, card, key, value) -> extra values to set, e.g. a picked trainee's phone

  const dispatch = createEventDispatcher()

  $: linked = Boolean(block.linkFrom)
  $: tabs = block.display === 'tabs'
  $: linkFields = block.linkFrom?.fields ?? []
  $: editableFields = linked ? block.fields.filter((f) => !linkFields.includes(f.key)) : block.fields

  let activeTab = 0
  // a course card can disappear (removed in section A, then normalize drops it here) --
  // never point past the end of the list.
  $: if (activeTab >= cards.length) activeTab = Math.max(0, cards.length - 1)

  function emit(next) {
    dispatch('change', next)
  }

  function addCard() {
    emit([...cards, { _id: crypto.randomUUID(), ...seed() }])
  }
  function removeCard(index) {
    emit(cards.filter((_, i) => i !== index))
  }
  function setField(index, key, value) {
    emit(cards.map((c, i) => (i === index ? { ...c, [key]: value, ...(fill(block.fields, c, key, value) ?? {}) } : c)))
  }

  function cardTitle(card, index) {
    // anonymous feedback cards: always "Trainee 2", the trade only as context
    if (block.anonymous) {
      const trade = String(card[block.titleField] ?? '').trim()
      return trade ? `${block.itemLabel} ${index + 1} · ${trade}` : `${block.itemLabel} ${index + 1}`
    }
    const named = block.titleField ? String(card[block.titleField] ?? '').trim() : ''
    return named || `${block.itemLabel} ${index + 1}`
  }

  // e.g. "Welding (SMAW) · Batch 07" -- linkFrom.fields is always [course, batch] on this
  // template's linked blocks, so this isn't worth generalising further.
  function linkedHeader(card, index) {
    const [course, batch] = linkFields.map((key) => card[key])
    if (!course) return cardTitle(card, index)
    return batch ? `${course} · Batch ${batch}` : course
  }

  // mirrors reporttemplate.js's counts(): a field counts toward a card's progress if required or
  // a segmented choice. Used only for the tab strip's "x/y answered" + green-when-done -- the
  // real section/report progress is computeProgress()'s job, not this component's.
  function countsField(field) {
    return Boolean(field.required) || field.kind === 'choice'
  }
  function cardProgress(card) {
    const counted = block.fields.filter((f) => countsField(f) && isShown(f, card))
    const answered = counted.filter((f) => !isBlank(card[f.key])).length
    return { answered, total: counted.length, done: counted.length > 0 && answered === counted.length }
  }

  // >=2 non-blank compare fields present, not all equal -> mismatch. under 2 present, no
  // verdict yet -- ask for more figures instead of guessing.
  function compareState(card) {
    if (!block.compare) return null
    const present = block.compare.fields.map((k) => card[k]).filter((v) => v != null && String(v).trim() !== '')
    if (present.length < 2) return 'need'
    // gap mode (surprise v2 headcount vs 7-day averages): only a large shortfall is a mismatch
    if (block.compare.gapPct != null) return compareMismatch(block.compare, card) ? 'mismatch' : 'match'
    const nums = present.map(Number)
    return nums.every((n) => n === nums[0]) ? 'match' : 'mismatch'
  }

  // find which section owns the linked block's source cards (e.g. "courses" lives in section A)
  // so the empty-state link can switch the editor to it, same event as SectionChips' chip jump.
  function jumpToSource() {
    if (!template || !block.linkFrom) return
    let targetKey = null
    for (const section of template.sections) {
      if (section.blocks.some((b) => b.type === 'cards' && b.key === block.linkFrom.cards)) targetKey = section.key
    }
    if (targetKey) window.dispatchEvent(new CustomEvent('report-section', { detail: targetKey }))
  }
</script>

<div class="cards">
  {#if linked && cards.length === 0}
    <p class="empty-link">No courses yet. <button type="button" class="btn-link" on:click={jumpToSource}>Add courses in section A</button></p>
  {:else if tabs}
    <div class="tab-strip" role="tablist">
      {#each cards as card, index (card._id ?? index)}
        {@const p = cardProgress(card)}
        <button type="button" class="tab" role="tab" aria-selected={index === activeTab}
          class:active={index === activeTab} class:done={p.done} on:click={() => (activeTab = index)}>
          <span class="tab-label">{linked ? linkedHeader(card, index) : cardTitle(card, index)}</span>
          {#if p.total > 0}<span class="tab-count">{p.answered}/{p.total} answered</span>{/if}
        </button>
      {/each}
    </div>
    {#if cards[activeTab]}
      {@const card = cards[activeTab]}
      <div class="card entry">
        {#each editableFields.filter((f) => isShown(f, card)) as field (field.key)}
          <FieldInput {field} value={card[field.key] ?? ''} {disabled} compact={Boolean(block.anonymous)}
            courseOptions={field.kind === 'courseRef' ? optionsFor(field, card) : undefined}
            suggestions={field.suggest ? suggest(field, card) : []}
            on:change={(e) => setField(activeTab, field.key, e.detail)} />
        {/each}
      </div>
    {/if}
  {:else}
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
        <div class="entry-fields">
          {#each editableFields.filter((f) => isShown(f, card)) as field (field.key)}
            <div class:wide={field.kind === 'longtext' || field.kind === 'choice'}>
              <FieldInput {field} value={card[field.key] ?? ''} {disabled} compact={Boolean(block.anonymous)}
                courseOptions={field.kind === 'courseRef' ? optionsFor(field, card) : undefined}
            suggestions={field.suggest ? suggest(field, card) : []}
                on:change={(e) => setField(index, field.key, e.detail)} />
            </div>
          {/each}
        </div>
        {#if block.compare}
          {@const state = compareState(card)}
          <p class="compare" class:match={state === 'match'} class:mismatch={state === 'mismatch'}>
            {#if state === 'need'}Enter {block.compare.fields.length} figures to check for a mismatch.
            {:else if state === 'match'}{block.compare.gapPct != null ? 'Headcount is in line with the 7-day average.' : 'Figures match.'}
            {:else}{block.compare.message}{/if}
          </p>
        {/if}
      </div>
    {/each}
  {/if}
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
  .add-card { background: var(--surface); border: 1px solid var(--outline); border-radius: 8px; color: var(--ink); font-weight: 600; margin-top: 4px; }
  /* laptop: a card's short fields sit side by side, long text takes the full row */
  .entry-fields { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 0 14px; }
  .entry-fields .wide { grid-column: 1 / -1; }

  .tab-strip { display: flex; gap: 6px; overflow-x: auto; margin-bottom: 12px; padding-bottom: 2px; }
  .tab {
    flex: none;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
    min-width: 120px;
    padding: 8px 12px;
    border: 1px solid var(--outline);
    border-radius: 10px;
    background: var(--surface);
    color: var(--muted);
    cursor: pointer;
  }
  .tab.active { border-color: var(--primary); box-shadow: inset 0 0 0 1px var(--primary); color: var(--ink); }
  .tab.done { border-color: transparent; background: var(--tone-yes-bg); color: var(--tone-yes-fg); }
  .tab.done.active { box-shadow: inset 0 0 0 1px var(--tone-yes-fg); }
  .tab-label { font-size: 13px; font-weight: 700; white-space: nowrap; }
  .tab-count { font-size: 11px; font-variant-numeric: tabular-nums; }
</style>
