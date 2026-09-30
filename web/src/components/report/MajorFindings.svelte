<!-- surprise v2 Major findings: every remarks line of the report is offered (issues first);
     the AI pre-selects the major ones once per candidate list while nothing is picked yet.
     Picked findings are data.findings = [{src, text}] (src = the line it came from, '' when
     typed), each editable with Improve wording. data.findingsAi = the candidate list the AI
     last ran for, so reopening the report never re-runs it by itself. -->
<script>
  import { onMount } from 'svelte'
  import ImproveWording from './ImproveWording.svelte'
  import { findingCandidates } from '../../lib/sectionremarks.js'
  import { draftMajorFindings } from '../../lib/draftrun.js'

  export let block // {type:'findings', key, heading?, note?}
  export let template
  export let data // mutated in place, then onChange()
  export let disabled = false
  export let onChange = () => {}

  let busy = false
  let usedFallback = false
  let undo = null
  $: canUndo = undo !== null && data.findings === undo.after

  $: candidates = findingCandidates(template, data)
  $: candidatesSource = candidates.map((l) => l.text).join('\n')
  $: picks = data.findings ?? []
  $: pickedSources = new Set(picks.map((f) => f.src))
  $: others = candidates.filter((l) => !pickedSources.has(l.text))

  function setPicks(next) {
    data.findings = next
    onChange()
  }

  // AI suggestions replace the picks that came from the list; typed findings stay
  async function suggest() {
    busy = true
    try {
      const result = await draftMajorFindings(candidates)
      const current = data.findings ?? []
      const kept = result.findings.map((s) => current.find((f) => f.src === s.src) ?? s)
      const next = [...kept, ...current.filter((f) => !f.src)]
      // the automatic first pre-select replaces nothing, so only a re-suggest is undoable
      if (current.length > 0) undo = { before: current, after: next }
      data.findingsAi = candidatesSource
      setPicks(next)
      usedFallback = result.usedFallback
    } finally {
      busy = false
    }
  }

  onMount(() => {
    if (!disabled && picks.length === 0 && candidates.length > 0 && data.findingsAi !== candidatesSource) suggest()
  })

  const setText = (index, text) => setPicks(picks.map((f, i) => (i === index ? { ...f, text } : f)))
</script>

<div class="findings">
  <h4>{block.heading ?? 'Major findings'}</h4>
  {#if block.note}<p class="note">{block.note}</p>{/if}
  {#if busy}<p class="muted">Choosing the major findings…</p>{/if}
  {#if usedFallback}<p class="muted">AI unavailable — every issue was selected</p>{/if}
  {#if picks.length === 0 && !busy}<p class="muted">No finding selected yet.</p>{/if}

  {#each picks as finding, index (index)}
    <div class="pick">
      <input type="checkbox" checked {disabled} aria-label="Remove this finding"
        on:change={() => setPicks(picks.filter((_, i) => i !== index))} />
      <div class="pick-body">
        <textarea rows="2" value={finding.text} {disabled} on:input={(e) => setText(index, e.target.value)}></textarea>
        <ImproveWording text={finding.text} label="Major findings" {disabled} on:change={(e) => setText(index, e.detail)} />
      </div>
    </div>
  {/each}

  {#if !disabled}
    <div class="row">
      <button type="button" class="btn" on:click={() => setPicks([...picks, { src: '', text: '' }])}>＋ Add finding</button>
      <button type="button" class="btn" on:click={suggest} disabled={busy || candidates.length === 0}>Suggest again</button>
      {#if canUndo}<button type="button" class="btn-link" on:click={() => { setPicks(undo.before); undo = null }}>Use my words</button>{/if}
    </div>
  {/if}

  {#if others.length}
    <h5>Other points from this report</h5>
    {#each others as line (line.text)}
      <label class="other">
        <input type="checkbox" checked={false} {disabled} on:change={() => setPicks([...picks, { src: line.text, text: line.text }])} />
        <span>{#if line.neg}<b class="issue">Issue</b> {/if}{line.text}</span>
      </label>
    {/each}
  {/if}
</div>

<style>
  h4 { margin: 0 0 4px; font-size: 13px; }
  h5 { margin: 16px 0 6px; font-size: 12px; color: var(--muted); text-transform: uppercase; letter-spacing: 0.03em; }
  .note, .muted { font-size: 12px; color: var(--muted); margin: 0 0 8px; }
  .pick { display: flex; gap: 8px; align-items: flex-start; margin-bottom: 8px; }
  .pick input[type='checkbox'] { margin-top: 10px; }
  .pick-body { flex: 1; min-width: 0; }
  .pick textarea { width: 100%; font-size: 13px; }
  .row { display: flex; gap: 8px; margin: 4px 0 8px; }
  .other { display: flex; gap: 8px; align-items: flex-start; font-size: 13px; padding: 4px 0; cursor: pointer; }
  .other input { margin-top: 3px; }
  .issue { color: var(--danger); font-size: 11px; text-transform: uppercase; }
</style>
