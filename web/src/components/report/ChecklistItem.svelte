<!-- one checklist question: numbered text, segmented answer (template.answers options), and an
     expanding remarks box. parent owns data.checks[item.id]; this just reports the new
     {answer, remarks} pair on every change. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import AnswerSegmented from './AnswerSegmented.svelte'

  export let item // {id, text}
  export let index // 1-based, for the "n." prefix
  export let answers // template.answers: [{id,label,tone}]
  export let check = undefined // {answer, remarks} or undefined
  export let disabled = false

  const dispatch = createEventDispatcher()

  $: answer = check?.answer ?? ''
  $: remarks = check?.remarks ?? ''
  let remarksOpen = false // "+ Add remarks" reveal state, local only -- not persisted

  function setAnswer(event) {
    dispatch('change', { answer: event.detail, remarks })
  }
  function setRemarks(event) {
    dispatch('change', { answer, remarks: event.target.value })
  }
</script>

<div class="item">
  <div class="item-text"><span class="item-number">{index}.</span>{item.text}</div>
  <AnswerSegmented options={answers} value={answer} {disabled} on:change={setAnswer} />
  {#if remarks || remarksOpen}
    <textarea class="remarks" placeholder="Remarks" rows="1" value={remarks} on:input={setRemarks} {disabled}></textarea>
  {:else}
    <button type="button" class="btn-link add-remarks" on:click={() => (remarksOpen = true)} {disabled}>+ Add remarks</button>
  {/if}
</div>

<style>
  .item { padding: 14px 0; border-top: 1px solid var(--outline); }
  .item:first-child { border-top: none; }
  .item-text { font-size: 14px; margin-bottom: 8px; display: flex; gap: 8px; }
  .item-number { color: var(--muted); font-variant-numeric: tabular-nums; }
  .remarks { margin-top: 8px; font-size: 13px; min-height: 36px; }
  .add-remarks { margin-top: 6px; font-size: 12px; }
</style>
