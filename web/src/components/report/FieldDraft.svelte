<!-- draft button above a field with `draftFrom`: findings <- s13 weaknesses (AI, preview first),
     recommendations <- s16 plan actions (no AI; pre-filled on load when blank). -->
<script>
  import { onMount } from 'svelte'
  import DraftPreview from './DraftPreview.svelte'
  import { isBlank } from '../../lib/reporttemplate.js'
  import { allWeaknesses, recommendationsFromPlan } from '../../lib/drafts.js'
  import { draftFindings } from '../../lib/draftrun.js'

  export let field // {key, draftFrom: 'weaknesses' | 'plan'}
  export let template
  export let data // mutated in place, then onChange()
  export let disabled = false
  export let onChange = () => {}

  let busy = false
  let preview = null // {lines, usedFallback}

  $: fromPlan = field.draftFrom === 'plan'
  $: sourceText = fromPlan ? recommendationsFromPlan(data.cards?.plan ?? []) : allWeaknesses(template, data).join('\n')
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
      if (isBlank(current) || confirm('Replace the current recommendations with the improvement plan actions?')) write(sourceText)
      return
    }
    busy = true
    try {
      preview = await draftFindings(template, data)
    } finally {
      busy = false
    }
  }
  function usePreview() {
    write(preview.lines.join('\n'))
    preview = null
  }
</script>

<div class="field-draft">
  <button type="button" class="btn-link" on:click={draft} disabled={disabled || busy || !sourceText}>
    {#if busy}Drafting…{:else if fromPlan}Fill from improvement plan{:else}Draft from weaknesses{/if}
  </button>
  {#if !sourceText}
    <span class="hint">{fromPlan ? 'Add improvement actions in section 16 first' : 'Add weaknesses in section 13 first'}</span>
  {/if}
</div>
{#if preview}
  <DraftPreview columns={[{ label: 'Major findings', lines: preview.lines }]} usedFallback={preview.usedFallback}
    replaces={!isBlank(current)} on:use={usePreview} on:discard={() => (preview = null)} />
{/if}

<style>
  .field-draft { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; margin-bottom: 6px; }
  .btn-link { font-size: 12px; }
  .hint { font-size: 12px; color: var(--muted); }
</style>
