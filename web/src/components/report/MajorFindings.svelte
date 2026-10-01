<!-- surprise v2 N Major findings (spec 2026-10-02 item 6): every written line of the report as a
     checklist in report order. Unticking leaves a line where it is. "Add selected to Major
     findings" writes the ticked lines into ONE editable box (lib/findingsbox.js keys: box =
     data.findingsText, ticks = data.findingsTicked); the box prints as the numbered list and the
     recommendations draft reads it. "Suggest with AI" only ticks the lines the AI picks. -->
<script>
  import ImproveWording from './ImproveWording.svelte'
  import { reportLines } from '../../lib/sectionremarks.js'
  import { draftMajorFindings } from '../../lib/draftrun.js'
  import { addSelectedText, setTicked, tickedLines } from '../../lib/findingsbox.js'

  export let block // {type:'findings', key, heading?, note?, boxKey, tickedKey}
  export let template
  export let data // mutated in place, then onChange()
  export let disabled = false
  export let onChange = () => {}

  let busy = false
  let aiNote = ''

  $: lines = reportLines(template, data)
  $: ticked = new Set(tickedLines(block, data, lines))
  $: boxKey = block.boxKey ?? 'findingsText'
  $: boxText = data[boxKey] ?? ''

  function tick(next) {
    setTicked(block, data, next)
    onChange()
  }
  function toggle(text) {
    const next = new Set(ticked)
    if (next.has(text)) next.delete(text)
    else next.add(text)
    tick(lines.map((l) => l.text).filter((t) => next.has(t)))
  }

  function setBox(text) {
    data[boxKey] = text
    onChange()
  }

  function addSelected() {
    const text = addSelectedText(block, data, lines)
    if (String(boxText).trim() && !confirm('Replace the text in Major findings with the selected lines?')) return
    setBox(text)
  }

  // the AI chooses from the issues-first list; its picks become ticks, nothing is written
  async function suggest() {
    busy = true
    aiNote = ''
    try {
      const issuesFirst = [...lines.filter((l) => l.neg), ...lines.filter((l) => !l.neg)]
      const result = await draftMajorFindings(issuesFirst)
      const picked = new Set(result.findings.map((f) => f.src))
      tick(lines.map((l) => l.text).filter((t) => picked.has(t)))
      aiNote = result.usedFallback ? 'AI unavailable — every issue was ticked.' : ''
    } finally {
      busy = false
    }
  }
</script>

<div class="findings">
  <h4>{block.heading ?? 'Major findings'}</h4>
  {#if block.note}<p class="note">{block.note}</p>{/if}

  {#if lines.length === 0}
    <p class="muted">No written points in this report yet.</p>
  {:else}
    {#if !disabled}
      <div class="row">
        <button type="button" class="btn-link" on:click={() => tick(lines.map((l) => l.text))}>Select all</button>
        <button type="button" class="btn-link" on:click={() => tick([])}>Deselect all</button>
        <button type="button" class="btn-link" on:click={suggest} disabled={busy}>{busy ? 'Choosing…' : 'Suggest with AI'}</button>
        <span class="count">{ticked.size} of {lines.length} selected</span>
      </div>
    {/if}
    {#if aiNote}<p class="muted">{aiNote}</p>{/if}
    <ul class="lines">
      {#each lines as line (line.text)}
        <li>
          <label>
            <input type="checkbox" checked={ticked.has(line.text)} {disabled} on:change={() => toggle(line.text)} />
            <span>{#if line.neg}<b class="issue">Issue</b> {/if}{line.text}</span>
          </label>
        </li>
      {/each}
    </ul>
    {#if !disabled}
      <button type="button" class="btn" on:click={addSelected} disabled={ticked.size === 0}>Add selected to Major findings</button>
    {/if}
  {/if}

  <label class="box-label" for="findings-box">Major findings <span class="muted">(one finding per line)</span></label>
  <textarea id="findings-box" rows="6" value={boxText} {disabled} on:input={(e) => setBox(e.target.value)}></textarea>
  <ImproveWording text={boxText} label="Major findings" {disabled} on:change={(e) => setBox(e.detail)} />
</div>

<style>
  h4 { margin: 0 0 4px; font-size: 13px; }
  .note, .muted { font-size: 12px; color: var(--muted); margin: 0 0 8px; }
  .row { display: flex; flex-wrap: wrap; gap: 14px; align-items: center; margin: 4px 0 8px; }
  .count { font-size: 12px; color: var(--muted); }
  .lines { list-style: none; margin: 0 0 10px; padding: 0; max-height: 420px; overflow-y: auto; border: 1px solid var(--outline); border-radius: 8px; }
  .lines li { border-top: 1px solid var(--outline); }
  .lines li:first-child { border-top: 0; }
  .lines label { display: flex; gap: 8px; align-items: flex-start; font-size: 13px; padding: 6px 10px; cursor: pointer; }
  .lines input { margin-top: 3px; }
  .issue { color: var(--danger); font-size: 11px; text-transform: uppercase; }
  .box-label { display: block; font-weight: 700; font-size: 13px; margin: 14px 0 4px; }
  textarea { width: 100%; font-size: 13px; }
</style>
