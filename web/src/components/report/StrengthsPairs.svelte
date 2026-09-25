<!-- s13 component-wise strengths & weaknesses: one row per block.pairs entry, the two boxes side
     by side on wide screens (stacked when narrow), each row with "Draft from remarks" plus one
     "Draft all empty" for the section. Data stays in the plain str_n / weak_n fields. -->
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

  let busyKey = '' // pair.source being drafted, or 'all'
  let previews = {} // pair.source -> {strengths, weaknesses, usedFallback}
  let summary = ''

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
  function apply(pair, draft) {
    data.fields[pair.strength] = draft.strengths.join('\n')
    data.fields[pair.weakness] = draft.weaknesses.join('\n')
    onChange()
  }

  async function draftOne(pair) {
    busyKey = pair.source
    summary = ''
    try {
      previews = { ...previews, [pair.source]: await draftComponent(template, data, pair) }
    } finally {
      busyKey = ''
    }
  }
  function closePreview(pair) {
    const { [pair.source]: _closed, ...rest } = previews
    previews = rest
  }
  function usePreview(pair) {
    apply(pair, previews[pair.source])
    closePreview(pair)
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
        apply(pair, draft)
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
    <div class="pair-head">
      <h4><span class="pair-no">{index + 1}.</span>{pair.component}</h4>
      <button type="button" class="btn-link" on:click={() => draftOne(pair)}
        disabled={disabled || busyKey !== '' || !available[pair.source]}>
        {busyKey === pair.source ? 'Drafting…' : 'Draft from remarks'}
      </button>
    </div>
    {#if !available[pair.source]}<p class="hint">Nothing marked in section {sectionNumber(pair.source)} yet</p>{/if}
    {#if previews[pair.source]}
      <DraftPreview
        columns={[{ label: 'Strengths', lines: previews[pair.source].strengths }, { label: 'Weaknesses', lines: previews[pair.source].weaknesses }]}
        usedFallback={previews[pair.source].usedFallback} replaces={!bothBlank(pair)}
        on:use={() => usePreview(pair)} on:discard={() => closePreview(pair)} />
    {/if}
    <div class="pair-boxes">
      <div>
        <FieldInput field={{ ...fieldByKey[pair.strength], label: 'Strengths' }} value={data.fields[pair.strength] ?? ''} {disabled}
          on:change={(e) => setField(pair.strength, e.detail)} />
      </div>
      <div>
        <FieldInput field={{ ...fieldByKey[pair.weakness], label: 'Weaknesses' }} value={data.fields[pair.weakness] ?? ''} {disabled}
          on:change={(e) => setField(pair.weakness, e.detail)} />
      </div>
    </div>
  </section>
{/each}

<style>
  .pairs-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 4px; }
  .summary { font-size: 12px; color: var(--muted); }
  .pair { padding-top: 12px; margin-top: 12px; border-top: 1px solid var(--outline); }
  .pair-head { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; }
  h4 { margin: 0 0 6px; font-size: 14px; }
  .pair-no { color: var(--muted); margin-right: 6px; }
  .btn-link { font-size: 12px; white-space: nowrap; }
  .hint { margin: -2px 0 8px; font-size: 12px; color: var(--muted); }
  .pair-boxes { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
  .pair-boxes :global(textarea) { min-height: 88px; }
  @media (max-width: 720px) { .pair-boxes { grid-template-columns: 1fr; gap: 0; } }
</style>
