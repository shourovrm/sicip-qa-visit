<!-- s13 component-wise strengths & weaknesses: one row per block.pairs entry, the two boxes side
     by side on wide screens (stacked when narrow), each box with its own "Draft from remarks",
     plus one "Draft all empty" for the section. Data stays in the plain str_n / weak_n fields. -->
<script>
  import FieldInput from './FieldInput.svelte'
  import DraftPreview from './DraftPreview.svelte'
  import { isBlank } from '../../lib/reporttemplate.js'
  import { componentNotes, hasNotes } from '../../lib/drafts.js'
  import { draftComponent } from '../../lib/draftrun.js'

  export let block // fields block with `pairs`
  export let template
  export let data // mutated in place, then onChange()
  export let disabled = false
  export let onChange = () => {}

  // one draft per box: key = `${pair.source}:strengths` | `${pair.source}:weaknesses`
  let busyKey = '' // box key being drafted, or 'all'
  let previews = {} // box key -> {lines, usedFallback}
  let summary = ''

  const SIDES = [
    { side: 'strengths', label: 'Strengths', fieldOf: (pair) => pair.strength },
    { side: 'weaknesses', label: 'Weaknesses', fieldOf: (pair) => pair.weakness },
  ]
  const boxKey = (pair, side) => `${pair.source}:${side}`

  $: fieldByKey = Object.fromEntries(block.fields.map((field) => [field.key, field]))
  // recomputed on every data change (data = data upstream) so buttons enable as marks arrive
  $: available = Object.fromEntries(block.pairs.map((pair) => [pair.source, hasNotes(componentNotes(template, data, pair.source))]))

  function sectionNumber(key) {
    return template.sections.find((section) => section.key === key)?.number ?? ''
  }
  function bothBlank(pair) {
    return isBlank(data.fields[pair.strength]) && isBlank(data.fields[pair.weakness])
  }
  function setField(key, value) {
    data.fields[key] = value
    onChange()
  }

  // the model drafts both lists in one answer; a box keeps only its own half
  async function draftBox(pair, side) {
    busyKey = boxKey(pair, side)
    summary = ''
    try {
      const draft = await draftComponent(template, data, pair)
      previews = { ...previews, [boxKey(pair, side)]: { lines: draft[side], usedFallback: draft.usedFallback } }
    } finally {
      busyKey = ''
    }
  }
  function closePreview(key) {
    const { [key]: _closed, ...rest } = previews
    previews = rest
  }
  // "Use my words": per box, the text a previewed draft replaced (while still untouched)
  let undo = {}
  function useMine(key, fieldKey) {
    data.fields[fieldKey] = undo[key].before
    undo = { ...undo, [key]: undefined }
    onChange()
  }
  const canUndo = (key, value) => Boolean(undo[key]) && value === undo[key].after
  function usePreview(key, fieldKey) {
    const drafted = previews[key].lines.join('\n')
    if (!isBlank(data.fields[fieldKey])) undo = { ...undo, [key]: { before: data.fields[fieldKey], after: drafted } }
    setField(fieldKey, drafted)
    closePreview(key)
  }

  // one request at a time (free Workers AI quota + keeps the phone/web behaviour identical);
  // only rows with nothing typed yet, so nothing is overwritten without a preview.
  async function draftAllEmpty() {
    busyKey = 'all'
    summary = ''
    let drafted = 0
    let fromMarks = 0
    try {
      for (const pair of block.pairs) {
        if (!available[pair.source] || !bothBlank(pair)) continue
        const draft = await draftComponent(template, data, pair)
        data.fields[pair.strength] = draft.strengths.join('\n')
        data.fields[pair.weakness] = draft.weaknesses.join('\n')
        onChange()
        drafted += 1
        if (draft.usedFallback) fromMarks += 1
      }
    } finally {
      busyKey = ''
    }
    const plural = drafted === 1 ? '' : 's'
    summary = drafted === 0
      ? 'Nothing to draft — every component with marks already has text'
      : `Drafted ${drafted} component${plural}${fromMarks ? ` (${fromMarks} from marks only)` : ''}`
  }
</script>

<div class="pairs-head">
  <button type="button" class="btn" on:click={draftAllEmpty} disabled={disabled || busyKey !== ''}>
    {busyKey === 'all' ? 'Drafting…' : 'Draft all empty'}
  </button>
  {#if summary}<span class="summary">{summary}</span>{/if}
</div>

{#each block.pairs as pair, index (pair.source)}
  <section class="pair">
    <h4><span class="pair-no">{index + 1}.</span>{pair.component}</h4>
    {#if !available[pair.source]}<p class="hint">Nothing marked in section {sectionNumber(pair.source)} yet</p>{/if}
    <div class="pair-boxes">
      {#each SIDES as { side, label, fieldOf } (side)}
        {@const key = boxKey(pair, side)}
        {@const fieldKey = fieldOf(pair)}
        <div>
          <div class="box-actions">
            <button type="button" class="btn-link" on:click={() => draftBox(pair, side)}
              disabled={disabled || busyKey !== '' || !available[pair.source]}>
              {busyKey === key ? 'Drafting…' : 'Draft from remarks'}
            </button>
            {#if !disabled && canUndo(key, data.fields[fieldKey])}<button type="button" class="btn-link" on:click={() => useMine(key, fieldKey)}>Use my words</button>{/if}
          </div>
          {#if previews[key]}
            <DraftPreview columns={[{ label, lines: previews[key].lines }]} usedFallback={previews[key].usedFallback}
              replaces={!isBlank(data.fields[fieldKey])}
              on:use={() => usePreview(key, fieldKey)} on:discard={() => closePreview(key)} />
          {/if}
          <FieldInput field={{ ...fieldByKey[fieldKey], label }} value={data.fields[fieldKey] ?? ''} {disabled}
            on:change={(e) => setField(fieldKey, e.detail)} />
        </div>
      {/each}
    </div>
  </section>
{/each}

<style>
  .pairs-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 4px; }
  .summary { font-size: 12px; color: var(--muted); }
  .pair { padding-top: 12px; margin-top: 12px; border-top: 1px solid var(--outline); }
  .box-actions { display: flex; gap: 10px; justify-content: flex-end; }
  h4 { margin: 0 0 6px; font-size: 14px; }
  .pair-no { color: var(--muted); margin-right: 6px; }
  .btn-link { font-size: 12px; white-space: nowrap; }
  .hint { margin: -2px 0 8px; font-size: 12px; color: var(--muted); }
  .pair-boxes { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
  .pair-boxes :global(textarea) { min-height: 88px; }
  @media (max-width: 720px) { .pair-boxes { grid-template-columns: 1fr; gap: 0; } }
</style>
