<!-- surprise v2: a section's templated Remarks -- the built bullets (issues marked) plus Edit.
     An edit (one point per line, with Improve wording) is stored as data.remarks[key] =
     {source, text}; source = the built lines it replaced, so changing an answer later makes it
     stale and the built bullets come back (lib/sectionremarks.js printedRemarkLines).
     A `manual` block (K, L) builds nothing: one plain textarea, stored as {source:'', text},
     printed one bullet per non-blank line. -->
<script>
  import ImproveWording from './ImproveWording.svelte'
  import { buildRemarkLines, printedRemarkLines } from '../../lib/sectionremarks.js'

  export let block // {type:'remarks', key, heading?}
  export let section
  export let template
  export let data // mutated in place, then onChange()
  export let disabled = false
  export let onChange = () => {}

  let editing = false
  let draft = ''

  // `data` passed in so Svelte sees the dependency (no hidden reads through helper fns)
  $: built = buildRemarkLines(template, section, block, data).map((l) => l.text).join('\n')
  $: lines = printedRemarkLines(template, section, block, data)
  $: entry = data.remarks?.[block.key]
  $: stale = Boolean(entry?.text) && entry.source !== built

  function startEdit() {
    draft = lines.map((l) => l.text).join('\n')
    editing = true
  }
  function save() {
    data.remarks = { ...(data.remarks ?? {}), [block.key]: { source: built, text: draft.trim() } }
    onChange()
    editing = false
  }
  // manual block: the textarea writes straight through
  function setManual(text) {
    data.remarks = { ...(data.remarks ?? {}), [block.key]: { source: '', text } }
    onChange()
  }
  function reset() {
    const { [block.key]: _dropped, ...rest } = data.remarks ?? {}
    data.remarks = rest
    onChange()
    editing = false
  }
</script>

<div class="remarks">
  <h4>{block.heading ?? 'Remarks'}{#if block.manual}<span class="hint"> — printed under the table, one bullet per line</span>{/if}</h4>
  {#if block.manual}
    <textarea rows="4" value={entry?.text ?? ''} {disabled} placeholder="One point per line"
      on:input={(e) => setManual(e.target.value)}></textarea>
    <ImproveWording text={entry?.text ?? ''} label={section.title} {disabled} on:change={(e) => setManual(e.detail)} />
  {:else if editing}
    <textarea rows={Math.max(3, lines.length + 1)} bind:value={draft} placeholder="One point per line"></textarea>
    <ImproveWording text={draft} label={section.title} {disabled} on:change={(e) => (draft = e.detail)} />
    <div class="actions">
      {#if entry?.text}<button type="button" class="btn-link" on:click={reset}>Reset</button>{/if}
      <button type="button" class="btn-link" on:click={() => (editing = false)}>Cancel</button>
      <button type="button" class="btn btn-primary" on:click={save}>Save</button>
    </div>
  {:else}
    {#if lines.length === 0}
      <p class="empty">Nothing yet. Answer the items above and their sentences appear here.</p>
    {:else}
      <ul>{#each lines as line}<li class:neg={line.neg}>{line.text}</li>{/each}</ul>
      {#if !disabled}<button type="button" class="btn-link edit" on:click={startEdit}>Edit</button>{/if}
    {/if}
    {#if stale}<p class="stale">Answers changed after your edit, so the remarks were rebuilt. Edit to write them again.</p>{/if}
  {/if}
</div>

<style>
  .remarks { border: 1px solid var(--outline); border-left: 3px solid var(--primary); border-radius: 8px; padding: 10px 12px; margin-top: 12px; }
  h4 { margin: 0 0 6px; font-size: 13px; }
  ul { margin: 0; padding-left: 18px; font-size: 13px; }
  li { margin: 2px 0; }
  li.neg::marker { color: var(--danger); }
  .empty { margin: 0; font-size: 12px; color: var(--muted); font-style: italic; }
  .edit { font-size: 12px; margin-top: 4px; }
  textarea { width: 100%; font-size: 13px; }
  .actions { display: flex; justify-content: flex-end; gap: 10px; align-items: center; margin-top: 6px; }
  .hint { font-weight: 400; color: var(--muted); }
  .stale { margin: 4px 0 0; font-size: 12px; color: var(--danger); }
</style>
