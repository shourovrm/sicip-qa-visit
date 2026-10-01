<!-- draft button above a field with `draftFrom`: findings <- s13 weaknesses (AI, preview first),
     QA v1 recommendations <- s16 plan actions (no AI; pre-filled on load when blank), QA v2
     recommendations <- one per s13 weakness (AI, preview first), surprise v2 recommendations <-
     the Major findings box lines (AI, preview first). -->
<script>
  import { onMount } from 'svelte'
  import DraftPreview from './DraftPreview.svelte'
  import { isBlank } from '../../lib/reporttemplate.js'
  import { allWeaknesses, recommendationsFromPlan } from '../../lib/drafts.js'
  import { findingLines } from '../../lib/findingsbox.js'
  import { draftFindings, draftRecommendations } from '../../lib/draftrun.js'

  export let field // {key, draftFrom: 'weaknesses' | 'plan' | 'findings' | 'weaknessRecommendations'}
  export let template
  export let data // mutated in place, then onChange()
  export let disabled = false
  export let onChange = () => {}

  let busy = false
  let preview = null // {lines, usedFallback}

  $: fromPlan = field.draftFrom === 'plan'
  $: fromFindings = field.draftFrom === 'findings'
  $: perWeakness = field.draftFrom === 'weaknessRecommendations'
  // what each recommendation is written for: the Major findings box (surprise) or weaknesses (QA v2)
  $: findingsBlock = template.sections.flatMap((s) => s.blocks).find((b) => b.type === 'findings')
  $: pickedFindings = findingLines(findingsBlock, data)
  $: weaknesses = allWeaknesses(template, data)
  $: sourceText = fromPlan
    ? recommendationsFromPlan(data.cards?.plan ?? [])
    : fromFindings ? pickedFindings.join('\n') : weaknesses.join('\n')
  $: writesRecommendations = fromFindings || perWeakness
  $: current = data.fields[field.key]

  function write(text) {
    data.fields[field.key] = text
    onChange()
  }

  // "pre-filled": a blank recommendations box picks up the plan as soon as the report opens
  onMount(() => {
    if (fromPlan && !disabled && isBlank(current) && sourceText) write(sourceText)
  })

  async function draft() {
    if (fromPlan) {
      if (isBlank(current)) write(sourceText)
      else if (confirm('Replace the current recommendations with the improvement plan actions?')) {
        undo = { before: current, after: sourceText }
        write(sourceText)
      }
      return
    }
    busy = true
    try {
      if (fromFindings) preview = await draftRecommendations(pickedFindings)
      else if (perWeakness) preview = await draftRecommendations(weaknesses)
      else preview = await draftFindings(template, data)
    } finally {
      busy = false
    }
  }
  // "Use my words": the text a draft replaced, offered back while the draft is untouched
  let undo = null
  $: canUndo = undo !== null && current === undo.after
  function useMine() {
    write(undo.before)
    undo = null
  }
  function usePreview() {
    const drafted = preview.lines.join('\n')
    if (!isBlank(current)) undo = { before: current, after: drafted }
    write(drafted)
    preview = null
  }
</script>

<div class="field-draft">
  <button type="button" class="btn-link" on:click={draft} disabled={disabled || busy || !sourceText}>
    {#if busy}Drafting…{:else if fromPlan}Fill from improvement plan{:else if fromFindings}Draft from major findings{:else if perWeakness}Draft one per weakness{:else}Draft from weaknesses{/if}
  </button>
  {#if canUndo && !disabled}<button type="button" class="btn-link" on:click={useMine}>Use my words</button>{/if}
  {#if !sourceText}
    <span class="hint">{fromPlan ? 'Add improvement actions in section 16 first' : fromFindings ? 'Add lines to Major findings above first' : 'Add weaknesses in section 13 first'}</span>
  {/if}
</div>
{#if preview}
  <DraftPreview columns={[{ label: writesRecommendations ? 'Recommendations' : 'Major findings', lines: preview.lines }]} usedFallback={preview.usedFallback}
    replaces={!isBlank(current)} on:use={usePreview} on:discard={() => (preview = null)} />
{/if}

<style>
  .field-draft { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
  .btn-link { font-size: 12px; }
  .hint { font-size: 12px; color: var(--muted); }
</style>
