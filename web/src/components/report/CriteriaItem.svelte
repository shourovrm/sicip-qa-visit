<!-- one QA-report criterion (a non-heading `criteria` item): question text, the Annex-3
     evidence hint, one 3-way Seen/Not seen/N/A control per option (detail box when Seen and the
     option wants one, a remark box collapsed behind "Add remark" until tapped or non-empty),
     item-level "Evidence seen"/"Other remarks" boxes, and a live Remarks preview built the same
     way the printed report builds it (lib/remarks.js buildRemarks/printedRemarks -- pure fns,
     fixture-tested against shared/report-templates/fixtures/remarks-1.json). Parent owns
     data.criteria[item.id]; this only ever dispatches the new entry object, same pattern as
     ChecklistItem. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import AnswerSegmented from './AnswerSegmented.svelte'
  import { printedRemarks } from '../../lib/remarks.js'

  export let item // {id, no, text, evidence?, options:[{id,label,short?,src?,detail?,seen,not,na}]}
  export let entry = undefined // data.criteria[item.id] or undefined (nothing touched yet)
  export let disabled = false

  const dispatch = createEventDispatcher()

  const STATES = [
    { id: 'seen', label: 'Seen', tone: 'yes' },
    { id: 'not', label: 'Not seen', tone: 'no' },
    { id: 'na', label: 'N/A', tone: 'na' },
  ]

  $: opts = entry?.opts ?? {}
  $: evidence = entry?.evidence ?? ''
  $: note = entry?.note ?? ''
  $: bullets = printedRemarks(item, entry ?? {})

  // "Add remark" reveal state per option, local only (not persisted) -- same idea as
  // ChecklistItem's remarksOpen, one flag per option instead of one for the whole item.
  let openRemarks = {}

  function emit(next) {
    dispatch('change', next)
  }
  function setOptState(optId, v) {
    const current = opts[optId] ?? {}
    emit({ ...entry, opts: { ...opts, [optId]: { ...current, v } } })
  }
  function setOptDetail(optId, detail) {
    const current = opts[optId] ?? {}
    emit({ ...entry, opts: { ...opts, [optId]: { ...current, detail } } })
  }
  function setOptRemark(optId, remark) {
    const current = opts[optId] ?? {}
    emit({ ...entry, opts: { ...opts, [optId]: { ...current, remark } } })
  }
  function setEvidence(value) {
    emit({ ...entry, evidence: value })
  }
  function setNote(value) {
    emit({ ...entry, note: value })
  }
</script>

<div class="criterion">
  <div class="q"><span class="no">{item.no}</span>{item.text}</div>
  {#if item.evidence}<div class="ev-hint"><b>Evidence (Annex-3):</b> {item.evidence}</div>{/if}

  {#each item.options as option (option.id)}
    {@const state = opts[option.id] ?? {}}
    <div class="opt-row">
      <div class="opt-label">
        {option.label}
        {#each option.src ?? [] as src}<span class="src-tag src-{src.toLowerCase()}">{src}</span>{/each}
      </div>
      <AnswerSegmented options={STATES} value={state.v ?? ''} {disabled} compact
        on:change={(e) => setOptState(option.id, e.detail)} />
      {#if option.detail && state.v === 'seen'}
        <input class="detail-in" type="text" placeholder={option.detail} value={state.detail ?? ''} {disabled}
          on:input={(e) => setOptDetail(option.id, e.target.value)} />
      {/if}
      {#if state.remark || openRemarks[option.id]}
        <input class="remark-in" type="text" placeholder="Remark on this point" value={state.remark ?? ''} {disabled}
          on:input={(e) => setOptRemark(option.id, e.target.value)} />
      {:else}
        <button type="button" class="btn-link add-remark" {disabled} on:click={() => (openRemarks = { ...openRemarks, [option.id]: true })}>+ Add remark</button>
      {/if}
    </div>
  {/each}

  <div class="item-box">
    <label class="item-box-label" for="ev-{item.id}">Evidence seen</label>
    <input id="ev-{item.id}" type="text" placeholder="Documents, photos seen" value={evidence} {disabled} on:input={(e) => setEvidence(e.target.value)} />
  </div>
  <div class="item-box">
    <label class="item-box-label" for="note-{item.id}">Other remarks</label>
    <input id="note-{item.id}" type="text" placeholder="Anything else about this point" value={note} {disabled} on:input={(e) => setNote(e.target.value)} />
  </div>

  <div class="preview">
    <div class="preview-h">Remarks preview</div>
    {#if bullets.length === 0}
      <p class="preview-empty">Nothing yet. Mark an option to add a sentence.</p>
    {:else}
      <ul>{#each bullets as b}<li>{b}</li>{/each}</ul>
    {/if}
  </div>
</div>

<style>
  .criterion { padding: 14px 0; border-top: 1px solid var(--outline); }
  .criterion:first-child { border-top: none; }
  .q { font-size: 14px; font-weight: 700; margin-bottom: 4px; }
  .no { color: var(--muted); font-weight: 700; margin-right: 6px; }
  .ev-hint { font-size: 12px; color: var(--muted); margin-bottom: 10px; }
  .opt-row { padding: 8px 0; border-top: 1px dashed var(--outline); }
  .opt-row:first-of-type { border-top: none; }
  .opt-label { font-size: 13px; font-weight: 700; margin-bottom: 6px; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
  .src-tag { font-size: 10px; font-weight: 700; padding: 1px 6px; border-radius: var(--radius-pill); border: 1px solid var(--outline); color: var(--muted); }
  .src-a3 { color: var(--primary); border-color: var(--primary); }
  .detail-in, .remark-in { width: 100%; margin-top: 6px; font-size: 13px; }
  .add-remark { margin-top: 6px; font-size: 12px; }
  .item-box { margin-top: 10px; }
  .item-box-label { display: block; font-size: 12px; font-weight: 700; color: var(--muted); margin-bottom: 3px; }
  .item-box input { width: 100%; font-size: 13px; }
  .preview { margin-top: 12px; padding: 10px; border: 1px solid var(--outline); border-radius: var(--radius-card); background: var(--canvas); }
  .preview-h { font-size: 12px; font-weight: 700; color: var(--muted); margin-bottom: 4px; }
  .preview-empty { margin: 0; font-size: 12px; color: var(--muted); font-style: italic; }
  .preview ul { margin: 0; padding-left: 16px; font-size: 13px; }
  .preview li { margin: 2px 0; }
</style>
