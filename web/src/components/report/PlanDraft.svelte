<!-- s16 improvement plan: "Draft from weaknesses" rebuilds the plan cards, one per s13 weakness,
     AI-suggested actions; responsible/timeline survive for weaknesses already in the plan. -->
<script>
  import { FALLBACK_NOTICE, draftPlan } from '../../lib/draftrun.js'
  import { allWeaknesses } from '../../lib/drafts.js'

  export let block // cards block with draftFrom
  export let template
  export let data // mutated in place, then onChange()
  export let disabled = false
  export let onChange = () => {}

  let busy = false
  let notice = ''

  $: weaknessCount = allWeaknesses(template, data).length
  $: cards = data.cards?.[block.key] ?? []

  async function draft() {
    const confirmText = `Rebuild the plan from ${weaknessCount} weaknesses? Responsible and timeline are kept for matching weaknesses.`
    if (cards.length > 0 && !confirm(confirmText)) return
    busy = true
    notice = ''
    try {
      const result = await draftPlan(template, data, cards)
      data.cards[block.key] = result.cards
      notice = result.usedFallback ? FALLBACK_NOTICE : ''
      onChange()
    } finally {
      busy = false
    }
  }
</script>

<div class="plan-draft">
  <button type="button" class="btn" on:click={draft} disabled={disabled || busy || weaknessCount === 0}>
    {busy ? 'Drafting…' : 'Draft from weaknesses'}
  </button>
  {#if weaknessCount === 0}<span class="hint">Add weaknesses in section 13 first</span>{/if}
  {#if notice}<span class="hint">{notice}</span>{/if}
</div>

<style>
  .plan-draft { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 8px; }
  .hint { font-size: 12px; color: var(--muted); }
</style>
