<!-- surprise-visit report editor -- ports the interaction/layout of
     ~/MEGA/SICIP/20260924-visit-templates-checklists/surprise-visit-checklist.html (app bar +
     progress, section chip strip, collapsible sections, segmented answers, repeatable cards,
     flags) onto the web app's own tokens (app.css) instead of the mockup's own CSS vars.

     Owns nothing persistent itself: `report` is an already-created DB row (Reports.svelte always
     creates the row before opening the editor, so this only ever UPDATEs); edits mutate a local
     `data` copy and autosave debounced to supabase. Print/Word buttons call the onPrint/onDocx
     props, which Reports.svelte wires to openReportPrint/downloadReportDocx -- no-op if unset,
     so this component still renders standalone (e.g. in a test) without those wired up. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { computeProgress, normalize } from '../../lib/reporttemplate.js'
  import { updateReportData, submitReport, softDeleteReport } from '../../lib/db.js'
  import ReportSection from './ReportSection.svelte'
  import SectionChips from './SectionChips.svelte'

  export let report // reports row (id, type, template_version, data, status, visit_id, ...)
  export let template // template JSON for report.type
  export let visit = null // matching visits row, for header context (may be null)
  export let officerName = ''
  export let readonly = false // owner viewing a submitted report; admins stay editable
  export let onPrint = null // (template, data, meta) => void, wired by Reports.svelte to openReportPrint
  export let onDocx = null // (template, data, meta) => void, wired by Reports.svelte to downloadReportDocx

  const dispatch = createEventDispatcher()

  // shallow-fill missing top-level keys only -- never drop unknown keys already in the row, so
  // a newer template version's extra fields survive a round trip through an older client. Each
  // check is also cloned one level deep: normalize()'s per-course step mutates a check's
  // `courses`/`answer` in place, and without this clone that would corrupt `report.data` (and
  // Reports.svelte's cached row) even for an item the user never touched this session.
  function ensureShape(raw) {
    return {
      fields: { ...(raw?.fields ?? {}) },
      checks: Object.fromEntries(Object.entries(raw?.checks ?? {}).map(([id, check]) => [id, { ...check }])),
      cards: { ...(raw?.cards ?? {}) },
      flags: [...(raw?.flags ?? [])],
    }
  }

  // normalize once on open too -- a report saved by an older client, or edited directly in the
  // DB, may have stale linked cards or per-course answers; normalize is idempotent so this is
  // cheap either way.
  let data = normalize(template, ensureShape(report.data))
  let saveState = 'saved' // saved | saving | offline
  let autosaveTimer = null
  let unsaved = false // an edit exists that no save request has picked up yet

  $: progress = computeProgress(template, data)
  $: disabled = readonly
  $: flagTotal = progress.flagsTicked.length + progress.customFlags.length

  function onChange() {
    normalize(template, data) // re-derive linked cards + per-course answers from their sources
    data = data // reassign so $: progress and every prop passing `data` sees the mutation
    if (disabled) return
    unsaved = true
    saveState = 'saving'
    clearTimeout(autosaveTimer)
    autosaveTimer = setTimeout(doSave, 800)
  }

  // saves run one at a time: two overlapping requests could reach the server out of order
  // and leave the older answers stored.
  let saveChain = Promise.resolve()
  function doSave() {
    saveChain = saveChain.then(async () => {
      if (!unsaved) return
      unsaved = false
      try {
        report = await updateReportData(report.id, data)
        // a newer edit may have arrived while this request was in flight
        if (!unsaved) saveState = 'saved'
        dispatch('save', report)
      } catch (e) {
        unsaved = true // keep it pending so the next flush retries
        saveState = 'offline'
      }
    })
    return saveChain
  }

  // flush a pending debounced edit now (close, submit, tab close).
  function flush() {
    if (!unsaved) return saveChain
    clearTimeout(autosaveTimer)
    return doSave()
  }

  async function close() {
    await flush()
    if (unsaved && !confirm('The latest answers are not saved (offline). Close anyway and lose them?')) return
    dispatch('close')
  }

  // warn before the tab closes with answers not yet on the server
  function beforeUnload(e) {
    if (!unsaved || disabled) return
    flush()
    e.preventDefault()
    e.returnValue = ''
  }

  async function submit() {
    if (!confirm('Submit this report? It becomes read-only once submitted.')) return
    await flush()
    if (saveState === 'offline') {
      alert('Could not save the latest answers. Check the connection and try Submit again.')
      return
    }
    try {
      report = await submitReport(report.id)
      dispatch('submit', report)
    } catch (e) {
      alert('Submit failed: ' + (e.message ?? e))
    }
  }

  async function del() {
    if (!confirm('Delete this draft report? This cannot be undone.')) return
    await softDeleteReport(report.id)
    dispatch('delete', report)
  }

  $: meta = {
    type: report.type,
    institute: data.fields?.ti_name || visit?.institute || '',
    visitDate: data.fields?.visit_date || visit?.start_date || '',
    officerName,
    status: report.status,
    submittedAt: report.submitted_at,
  }
</script>

<svelte:window on:beforeunload={beforeUnload} />

<div class="editor">
  <header class="head">
    <div class="head-row">
      <div class="head-title">
        <h1>{template.title}</h1>
        <p class="subtitle">{meta.institute || template.subtitle}</p>
      </div>
      <button type="button" class="btn" on:click={close}>Close</button>
    </div>
    <div class="progress-row">
      <div class="progress-track"><div class="progress-fill" style="width:{progress.sectionsCounted ? (100 * progress.sectionsDone) / progress.sectionsCounted : 0}%"></div></div>
      <span class="progress-count">{progress.sectionsDone}/{progress.sectionsCounted} sections done</span>
      {#if flagTotal > 0}<span class="flag-pill">{flagTotal} flag{flagTotal === 1 ? '' : 's'}</span>{/if}
      {#if report.status === 'submitted'}<span class="submitted-pill">Submitted {new Date(report.submitted_at).toLocaleString()}</span>{/if}
    </div>
    {#if progress.customFlags.length > 0}
      <ul class="custom-flags">{#each progress.customFlags as text}<li>{text}</li>{/each}</ul>
    {/if}
    <SectionChips sections={template.sections} progressSections={progress.sections} />
  </header>

  <main class="sections">
    {#each template.sections as section, index (section.key)}
      <ReportSection {section} {template} {data} answers={template.answers} progress={progress.sections[section.key]}
        {disabled} defaultOpen={index === 0} {onChange} />
    {/each}
  </main>

  <div class="action-bar">
    <div class="save-state">
      {#if disabled}Read-only
      {:else if saveState === 'saving'}Saving…
      {:else if saveState === 'offline'}Offline — not saved
      {:else}Saved{/if}
    </div>
    {#if !disabled && report.status === 'draft'}
      <button type="button" class="btn-link danger" on:click={del}>Delete</button>
      <button type="button" class="btn btn-primary" on:click={submit}>Submit</button>
    {/if}
    <button type="button" class="btn" on:click={() => onPrint?.(template, data, meta)}>Print / PDF</button>
    <button type="button" class="btn" on:click={() => onDocx?.(template, data, meta)}>Word</button>
  </div>
</div>

<style>
  .editor { padding-bottom: 72px; } /* clears the fixed action bar */
  .head { position: sticky; top: 0; z-index: 5; background: var(--canvas); padding: 8px 0; margin: -8px 0 12px; }
  .head-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
  .head-title h1 { margin: 0; font-size: 18px; color: var(--primary); }
  .subtitle { margin: 2px 0 0; font-size: 13px; color: var(--muted); }
  .progress-row { display: flex; align-items: center; gap: 10px; margin: 10px 0 4px; flex-wrap: wrap; }
  .progress-track { flex: 1; min-width: 120px; height: 6px; border-radius: 3px; background: var(--outline); overflow: hidden; }
  .progress-fill { height: 100%; background: var(--accent); transition: width 200ms; }
  .progress-count { font-size: 12px; font-weight: 700; color: var(--muted); white-space: nowrap; }
  .flag-pill, .submitted-pill { font-size: 12px; font-weight: 700; padding: 3px 10px; border-radius: var(--radius-pill); white-space: nowrap; }
  .flag-pill { background: var(--tone-no-bg); color: var(--tone-no-fg); }
  .submitted-pill { background: var(--status-success-bg); color: var(--status-success-fg); }
  .custom-flags { margin: 0 0 8px; padding-left: 18px; font-size: 13px; color: var(--tone-no-fg); }

  .action-bar {
    position: fixed;
    left: 0; right: 0; bottom: 0;
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 10px 16px;
    background: var(--surface);
    border-top: 1px solid var(--outline);
    z-index: 10;
  }
  .save-state { flex: 1; font-size: 12px; color: var(--muted); }
  .danger { color: var(--danger); }
</style>
