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
  import { createEventDispatcher, onMount } from 'svelte'
  import { computeProgress, percentDone, normalize, needsConversion, templateFor, TEMPLATES } from '../../lib/reporttemplate.js'
  import { getReport, updateReport, updateReportIfUnchanged, submitReport, retractReport, softDeleteReport } from '../../lib/db.js'
  import { mergeReportData } from '../../lib/reportmerge.js'
  import { convertSurpriseV1ToV2, revertSurpriseV2ToV1 } from '../../lib/reportconvert.js'
  import { convertQaV1ToV2 } from '../../lib/qaconvert.js'
  import ReportSection from './ReportSection.svelte'
  import SectionChips from './SectionChips.svelte'
  import SectionIndex from './SectionIndex.svelte'
  import SectionRail from './SectionRail.svelte'
  import TmsPanel from './TmsPanel.svelte'
  import TmsLinkBar from './TmsLinkBar.svelte'
  import { tmsSession } from '../../lib/tmsstore.js'
  import { instituteAddress, isLinked, loadCourseCatalog, loadTmsSnapshot, loadTraineeHints, loadTrainerHints } from '../../lib/tmsreport.js'
  import { runningCourseCards } from '../../lib/tmscatalog.js'
  import { phoneFill, suggestionsFor, traineeBatchOf, traineeKey } from '../../lib/suggest.js'
  import { designationFill } from '../../lib/tmstrainers.js'
  import { applyTmsPrefill, usesTmsFill, useTmsValue } from '../../lib/tmsprefill.js'
  import { openFindingsBox } from '../../lib/findingsbox.js'
  import { addSharedSuggestions, listSharedSuggestions } from '../../lib/db.js'

  export let report // reports row (id, type, template_version, data, status, visit_id, ...)
  export let template // template JSON for report.type
  export let visit = null // matching visits row, for header context (may be null)
  export let officerName = ''
  export let readonly = false // owner viewing a submitted report; admins stay editable
  export let onPrint = null // (template, data, meta) => void, wired by Reports.svelte to openReportPrint
  export let onDocx = null // (template, data, meta) => void, wired by Reports.svelte to downloadReportDocx
  export let onNarrative = null // (template, data, meta) => void: surprise v2 sentence-style print
  export let onNarrativeDocx = null // same, as a Word file

  const dispatch = createEventDispatcher()

  // shallow-fill missing top-level keys only -- never drop unknown keys already in the row, so
  // a newer template version's extra fields survive a round trip through an older client. Each
  // check is also cloned one level deep: normalize()'s per-course step mutates a check's
  // `courses`/`answer` in place, and without this clone that would corrupt `report.data` (and
  // Reports.svelte's cached row) even for an item the user never touched this session.
  function ensureShape(raw) {
    return {
      ...(raw ?? {}), // unknown/newer top-level keys (surprise v2 remarks, findings, ...) survive
      fields: { ...(raw?.fields ?? {}) },
      checks: Object.fromEntries(Object.entries(raw?.checks ?? {}).map(([id, check]) => [id, { ...check }])),
      cards: { ...(raw?.cards ?? {}) },
      flags: [...(raw?.flags ?? [])],
      // criteria (QA report): one level deeper than checks -- opts is its own nested object, so
      // it needs its own clone too, or normalize()'s mutations could corrupt report.data the
      // same way an unshared `checks[id]` would.
      criteria: Object.fromEntries(Object.entries(raw?.criteria ?? {}).map(([id, entry]) => [id, { ...entry, opts: { ...(entry?.opts ?? {}) } }])),
    }
  }

  // normalize once on open too -- a report saved by an older client, or edited directly in the
  // DB, may have stale linked cards or per-course answers; normalize is idempotent so this is
  // cheap either way.
  let data = normalize(template, ensureShape(report.data))
  // reports from before the Major findings box: their picked list becomes the box text
  // (data.findings itself is kept); saved with the next edit
  const findingsBlock = template.sections.flatMap((s) => s.blocks).find((b) => b.type === 'findings')
  const findingsMoved = findingsBlock ? openFindingsBox(findingsBlock, data) : false
  // the server copy this editor last saw -- merge base, so a save never overwrites what the
  // phone (or another tab) wrote meanwhile (2026-09-30 lost-edits incident)
  let base = structuredClone(report.data ?? {})
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
        const sent = structuredClone(data)
        const saved = await saveMerged(sent)
        report = saved.row
        base = structuredClone(saved.row.data)
        // show what the other platform wrote; edits typed while saving stay on top
        data = normalize(template, mergeReportData(sent, data, saved.data))
        // a newer edit may have arrived while this request was in flight
        if (!unsaved) saveState = 'saved'
        dispatch('save', report)
        shareNewSuggestions(sent)
      } catch (e) {
        unsaved = true // keep it pending so the next flush retries
        saveState = 'offline'
      }
    })
    return saveChain
  }

  // write `local`, first three-way merging in anything the server got since `base`; the write
  // is conditional on the row not moving again, retried (re-read + merge) if it did
  async function saveMerged(local) {
    for (let attempt = 0; attempt < 3; attempt++) {
      const server = await getReport(report.id)
      const merged = server.updated_at === report.updated_at ? local : normalize(template, mergeReportData(base, local, server.data ?? {}))
      const row = await updateReportIfUnchanged(report.id, { data: merged }, server.updated_at)
      if (row) return { row, data: merged }
    }
    throw new Error('report kept changing on the server')
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

  // owner or admin can take a submitted report back to draft at any time
  $: canReopen = report.status === 'submitted'

  async function reopen() {
    if (!confirm('Move this report back to draft so it can be edited? It must be submitted again afterwards.')) return
    try {
      report = await retractReport(report.id)
      dispatch('reopened', report)
    } catch (e) {
      alert('Could not move the report back to draft: ' + (e.message ?? e))
    }
  }

  async function submit() {
    if (!confirm(`Submit this report? Please check it first. You can take it back to draft at any time to edit it.`)) return
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

  // old-format report -> latest template (surprise: lib/reportconvert.js, QA: lib/qaconvert.js);
  // the parent re-opens the row so the editor restarts on the new template
  $: isSurprise = report.type === 'surprise'
  async function convert() {
    const moves = isSurprise
      ? 'Answers move to the new sections (e.g. key findings become major findings).'
      : 'Answers move to the new sections (e.g. the MoU details become the MoU table, evidence gets numbers).'
    if (!confirm(`Convert this report to the new format? ${moves} Old answers the new format no longer asks for stay saved but are not shown.`)) return
    await flush()
    if (saveState === 'offline') {
      alert('Could not save the latest answers. Check the connection and try again.')
      return
    }
    try {
      const latest = TEMPLATES[report.type]
      const converted = isSurprise ? convertSurpriseV1ToV2(latest, data) : convertQaV1ToV2(latest, data)
      report = await updateReport(report.id, { data: converted, template_version: latest.version })
      dispatch('converted', report)
    } catch (e) {
      alert('Convert failed: ' + (e.message ?? e))
    }
  }

  // v2 surprise report -> back to v1 (every key kept, v2 answers copied back); parent re-opens
  async function revert() {
    if (!confirm('Revert this report to the old format? Major findings, recommendations, officers, trainers present and job calls are copied back to their old places; the new-format answers stay saved, so you can convert again later.')) return
    await flush()
    if (saveState === 'offline') {
      alert('Could not save the latest answers. Check the connection and try again.')
      return
    }
    try {
      const v1 = templateFor('surprise', 1)
      report = await updateReport(report.id, { data: revertSurpriseV2ToV1(v1, data), template_version: v1.version })
      dispatch('converted', report)
    } catch (e) {
      alert('Revert failed: ' + (e.message ?? e))
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
    visitDate: data.fields?.visit_date || data.fields?.date_from || visit?.start_date || '',
    officerName,
    status: report.status,
    submittedAt: report.submitted_at,
  }

  // QA report only: template.sections has any `criteria` block -- gates the "AI remarks" button
  // (spec section 8: "web: editor head"). A template with more sections than a chip strip fits
  // comfortably (today only qa-v1's 15) gets the grouped vertical SectionIndex instead of
  // SectionChips (spec section 8: "> 13 sections").
  $: useSectionIndex = template.sections.length > 13
  // surprise v2 has remarks blocks -> it also prints as a narrative report
  $: canRevert = !disabled && report.type === 'surprise' && Number(report.template_version) === TEMPLATES.surprise.version
  $: hasNarrative = template.sections.some((s) => s.blocks.some((b) => b.type === 'remarks'))

  // ---- suggestions (template field `suggest`): TMS lists + the shared equipment list, fetched
  // when the editor opens and kept in memory only; nothing here is saved to the report except
  // the value the officer picks ----
  let catalog = null // lib/tmscatalog.js course catalog of the linked institute
  let trainees = new Map() // "courseId:batchId" -> [{name, mobile}]
  let trainers = [] // lib/tmstrainers.js hints of the linked institute
  let tmsFill = null // {filled, differences} of the last TMS prefill (lib/tmsprefill.js)
  let sharedLists = {} // list name -> values, e.g. {equipment: [...]}
  let tmsLoading = false
  let tmsError = ''
  const traineeLoads = new Set()

  $: usesTrainers = template.sections.some((s) => s.blocks.some((b) => (b.fields ?? []).some((f) => f.suggest === 'tmsTrainer')))

  async function loadTms() {
    catalog = null
    trainers = []
    tmsError = ''
    if (!$tmsSession || !isLinked(data.tms)) return
    tmsLoading = true
    try {
      catalog = await loadCourseCatalog(data.tms)
      if (usesTrainers) loadTrainerHints(data.tms).then((hints) => { trainers = hints }).catch(() => {})
    } catch (e) {
      tmsError = e.message
    } finally {
      tmsLoading = false
    }
  }

  // TMS figures into EMPTY fields (A/1 address, C attendance cards, QA 1.40/1.50/1.60); filled
  // fields that differ are listed in the TMS panel with "Use"
  let fillLoading = false
  async function prefillFromTms() {
    if (disabled || !$tmsSession || !isLinked(data.tms) || !usesTmsFill(template)) return
    fillLoading = true
    try {
      const [snapshot, address] = await Promise.all([loadTmsSnapshot(data.tms, meta.visitDate), instituteAddress(data.tms)])
      const addressFound = Boolean(address) && !data.tms.address
      if (addressFound) data.tms = { ...data.tms, address }
      tmsFill = applyTmsPrefill(template, data, { snapshot, institute: { address } })
      if (tmsFill.filled > 0 || addressFound) onChange()
    } catch (e) {
      tmsError = e.message
    } finally {
      fillLoading = false
    }
  }
  function useTms(e) {
    for (const difference of e.detail) useTmsValue(data, difference)
    const used = new Set(e.detail)
    tmsFill = { ...tmsFill, differences: tmsFill.differences.filter((d) => !used.has(d)) }
    onChange()
  }

  // template lists named "shared:<list>" by a field's suggest
  $: sharedListNames = [...new Set(template.sections.flatMap((s) => s.blocks).flatMap((b) => b.fields ?? [])
    .map((f) => f.suggest).filter((k) => k?.startsWith('shared:')).map((k) => k.slice(7)))]

  onMount(() => {
    if (findingsMoved && !disabled) onChange()
    loadTms().then(prefillFromTms)
    for (const list of sharedListNames) {
      listSharedSuggestions(list).then((values) => { sharedLists = { ...sharedLists, [list]: values } }).catch(() => {})
    }
  })
  // sign in / out elsewhere (Profile, a 401) -> fetch again or drop the lists
  let seenSignedIn = Boolean($tmsSession)
  $: if (Boolean($tmsSession) !== seenSignedIn) { seenSignedIn = Boolean($tmsSession); loadTms().then(prefillFromTms) }

  function requestTrainees(batch) {
    const key = traineeKey(batch)
    if (!batch || trainees.has(key) || traineeLoads.has(key)) return
    traineeLoads.add(key)
    loadTraineeHints(data.tms, batch)
      .then((hints) => { trainees = new Map(trainees).set(key, hints) })
      .catch(() => {})
      .finally(() => traineeLoads.delete(key))
  }

  $: suggest = (field, card) => {
    if (field.suggest === 'tmsTrainee' && catalog) requestTrainees(traineeBatchOf(card, catalog))
    return suggestionsFor(field, card, { catalog, trainees, trainers, equipment: sharedLists.equipment ?? [] })
  }
  $: fill = (fields, card, key, value) => {
    const batch = catalog ? traineeBatchOf(card, catalog) : null
    const phone = phoneFill(fields, card, key, value, batch ? trainees.get(traineeKey(batch)) : null)
    if (phone) return { phone }
    const designation = designationFill(fields, card, key, value, trainers, batch)
    return designation ? { designation } : null
  }

  function linkTms(e) {
    data.tms = { ...(data.tms ?? {}), ...e.detail }
    onChange()
    trainees = new Map()
    tmsFill = null
    loadTms().then(fillCoursesIfEmpty).then(prefillFromTms)
  }
  function refreshTms() {
    trainees = new Map()
    loadTms().then(fillCoursesIfEmpty).then(prefillFromTms)
  }
  // section A still empty -> the batches running on the visit date become its course cards
  function fillCoursesIfEmpty() {
    if (!catalog || disabled) return
    if ((data.cards?.courses ?? []).some((c) => String(c.course ?? '').trim())) return
    const cards = runningCourseCards(catalog, data.fields?.visit_date || visit?.start_date)
    if (!cards.length || !template.sections.some((s) => s.blocks.some((b) => b.key === 'courses'))) return
    data.cards.courses = cards
    onChange()
  }

  // after a save: offer this report's new shared-list values (equipment names) to everyone.
  // fire-and-forget; each value is sent once per editor session
  const sharedSent = new Set()
  function sharedValues(source) {
    const values = []
    for (const block of template.sections.flatMap((s) => s.blocks)) {
      if (block.type !== 'cards') continue
      for (const field of block.fields.filter((f) => f.suggest === 'shared:equipment')) {
        for (const card of source.cards?.[block.key] ?? []) values.push(String(card[field.key] ?? '').trim())
      }
    }
    return values.filter(Boolean)
  }
  sharedValues(data).forEach((v) => sharedSent.add(v.toLowerCase()))
  function shareNewSuggestions(source) {
    const fresh = sharedValues(source).filter((v) => !sharedSent.has(v.toLowerCase()))
    if (!fresh.length) return
    fresh.forEach((v) => sharedSent.add(v.toLowerCase()))
    addSharedSuggestions('equipment', fresh).catch(() => {})
  }

  // laptop layout: one section at a time in the middle column, picked from the left rail
  let currentKey = template.sections[0]?.key
  $: currentIndex = Math.max(0, template.sections.findIndex((s) => s.key === currentKey))
  $: previousSection = template.sections[currentIndex - 1] ?? null
  $: nextSection = template.sections[currentIndex + 1] ?? null
  function selectSection(key) {
    currentKey = key
    window.scrollTo({ top: 0 })
  }
  // CardsBlock's "Add courses in section A" link asks for a section by key
  function onSectionRequest(e) {
    if (template.sections.some((s) => s.key === e.detail)) selectSection(e.detail)
  }

  // overall progress = answered items over all counted items; open = sections not done yet
  $: countedSections = template.sections.filter((s) => !s.optional && progress.sections[s.key]?.total > 0)
  $: percent = percentDone(template, progress)
  $: openLetters = countedSections.filter((s) => !progress.sections[s.key].done).map((s) => s.letter ?? s.number)
  $: partner = data.fields?.provider || visit?.association || ''
</script>

<svelte:window on:beforeunload={beforeUnload} on:report-section={onSectionRequest} />

<div class="editor">
  <aside class="rail-col">
    <SectionRail title={template.short ? `${template.short} report` : template.title} sections={template.sections}
      progressSections={progress.sections} {currentKey} on:select={(e) => selectSection(e.detail)} />
  </aside>
  <div class="chips-narrow">
    {#if useSectionIndex}
      <SectionIndex sections={template.sections} progressSections={progress.sections} />
    {:else}
      <SectionChips sections={template.sections} progressSections={progress.sections} />
    {/if}
  </div>

  <main class="middle">
    {#if needsConversion(report) && !disabled}
      <div class="convert">
        {#if isSurprise}
          <span><b>This report uses the old format.</b> Convert it to get templated remarks, the trainers table, the equipment list, major findings and recommendations. Your answers are kept.</span>
        {:else}
          <span><b>This report uses the old format.</b> Convert it to get the officer, registration and MoU tables, numbered evidence and the new criteria. Your answers are kept.</span>
        {/if}
        <button type="button" class="btn" on:click={convert}>Convert to new format</button>
      </div>
    {/if}
    {#if !disabled && !isLinked(data.tms)}
      <!-- re-created on entering/leaving section A so the picker opens by itself there -->
      {#key currentIndex === 0}
        <TmsLinkBar signedIn={Boolean($tmsSession)} association={partner} instituteText={meta.institute} expanded={currentIndex === 0} on:link={linkTms} />
      {/key}
    {/if}
    {#if progress.customFlags.length > 0}
      <ul class="custom-flags">{#each progress.customFlags as text}<li>{text}</li>{/each}</ul>
    {/if}
    {#each template.sections as section (section.key)}
      {#if section.key === currentKey}
        <ReportSection {section} {template} {data} answers={template.answers} progress={progress.sections[section.key]}
          {disabled} defaultOpen={true} {onChange} {suggest} {fill} />
      {/if}
    {/each}
    <div class="pager">
      {#if previousSection}
        <button type="button" class="btn" on:click={() => selectSection(previousSection.key)}>← {previousSection.letter ?? previousSection.number} {previousSection.title}</button>
      {:else}<span></span>{/if}
      {#if nextSection}
        <button type="button" class="btn btn-next" on:click={() => selectSection(nextSection.key)}>Next: {nextSection.letter ?? nextSection.number} {nextSection.title} →</button>
      {/if}
    </div>
  </main>

  <aside class="side">
    <section class="panel">
      <h3>Report</h3>
      <dl>
        <dt>Institute</dt><dd>{meta.institute || '—'}</dd>
        <dt>Partner</dt><dd>{partner || '—'}</dd>
        <dt>Visit date</dt><dd>{meta.visitDate || '—'}</dd>
        <dt>Status</dt>
        <dd>
          <span class="status" class:submitted={report.status === 'submitted'}>{report.status === 'submitted' ? 'Submitted' : 'Draft'}</span>
          <span class="save-state">
            {#if disabled}read-only
            {:else if saveState === 'saving'}saving…
            {:else if saveState === 'offline'}offline, not saved
            {:else}saved{/if}
          </span>
        </dd>
      </dl>
      {#if report.status === 'submitted'}<p class="small">Submitted {new Date(report.submitted_at).toLocaleString()}</p>{/if}
    </section>

    <section class="panel">
      <h3>Progress</h3>
      <div class="percent">{percent}%</div>
      <div class="progress-track"><div class="progress-fill" style="width:{percent}%"></div></div>
      <p class="small">
        {progress.sectionsDone}/{progress.sectionsCounted} sections done{#if openLetters.length}; open: {openLetters.join(', ')}{/if}
      </p>
      {#if flagTotal > 0}<span class="flag-pill">{flagTotal} flag{flagTotal === 1 ? '' : 's'}</span>{/if}
    </section>

    <TmsPanel signedIn={Boolean($tmsSession)} tms={data.tms} {catalog} visitDate={meta.visitDate} association={partner}
      instituteText={meta.institute} loading={tmsLoading || fillLoading} error={tmsError} {disabled} fill={tmsFill}
      on:link={linkTms} on:refresh={refreshTms} on:use={useTms} />

    <section class="panel actions">
      <h3>Export</h3>
      <button type="button" class="btn" on:click={() => onPrint?.(template, data, meta)}>{hasNarrative ? 'Print / PDF — Form' : 'Print / PDF'}</button>
      {#if hasNarrative}<button type="button" class="btn" on:click={() => onNarrative?.(template, data, meta)}>Print / PDF — Narrative</button>{/if}
      <button type="button" class="btn" on:click={() => onDocx?.(template, data, meta)}>{hasNarrative ? 'Word — Form' : 'Word'}</button>
      {#if hasNarrative}<button type="button" class="btn" on:click={() => onNarrativeDocx?.(template, data, meta)}>Word — Narrative</button>{/if}
      {#if !disabled && report.status === 'draft'}
        <button type="button" class="btn btn-primary" on:click={submit}>Review & submit</button>
      {/if}
      {#if canReopen}<button type="button" class="btn" on:click={reopen}>Back to draft</button>{/if}
      <div class="minor">
        <button type="button" class="btn-link" on:click={close}>Close</button>
        {#if canRevert}<button type="button" class="btn-link" on:click={revert} title="Switch this report back to the old questions">Old format</button>{/if}
        {#if !disabled && report.status === 'draft'}<button type="button" class="btn-link danger" on:click={del}>Delete</button>{/if}
      </div>
    </section>
  </aside>
</div>

<style>
  .editor { display: grid; grid-template-columns: 270px minmax(0, 1fr) 290px; gap: 28px; align-items: start; }
  .rail-col { position: sticky; top: 12px; max-height: calc(100vh - 24px); overflow-y: auto; }
  .chips-narrow { display: none; }
  .side { position: sticky; top: 12px; display: flex; flex-direction: column; gap: 14px; max-height: calc(100vh - 24px); overflow-y: auto; }
  .middle { min-width: 0; }
  .panel { background: var(--surface); border: 1px solid var(--outline); border-radius: var(--radius-card); padding: 14px 16px; }
  .panel h3 { margin: 0 0 10px; font-size: 12px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }
  dl { display: grid; grid-template-columns: 82px 1fr; gap: 6px 10px; margin: 0; font-size: 14px; }
  dt { color: var(--muted); }
  dd { margin: 0; }
  .status { display: inline-block; padding: 1px 10px; border-radius: var(--radius-pill); background: var(--tone-partial-bg); color: var(--tone-partial-fg); font-weight: 700; font-size: 13px; }
  .status.submitted { background: var(--status-success-bg); color: var(--status-success-fg); }
  .save-state { font-size: 13px; color: var(--muted); }
  .small { margin: 8px 0 0; font-size: 13px; color: var(--muted); }
  .percent { font-size: 30px; font-weight: 800; margin-bottom: 6px; }
  .progress-track { height: 6px; border-radius: 3px; background: var(--outline); overflow: hidden; }
  .progress-fill { height: 100%; background: var(--tone-yes-fg); transition: width 200ms; }
  .flag-pill { display: inline-block; margin-top: 8px; font-size: 12px; font-weight: 700; padding: 3px 10px; border-radius: var(--radius-pill); background: var(--tone-no-bg); color: var(--tone-no-fg); }
  .actions { display: flex; flex-direction: column; gap: 8px; }
  .actions .btn { width: 100%; border-radius: 8px; border-color: var(--outline); background: var(--surface); font-weight: 600; }
  .actions .btn-primary { background: var(--accent); color: var(--on-accent); border-color: transparent; font-weight: 700; }
  .minor { display: flex; gap: 16px; justify-content: center; margin-top: 4px; font-size: 14px; }
  .danger { color: var(--danger); }
  .custom-flags { margin: 0 0 8px; padding-left: 18px; font-size: 13px; color: var(--tone-no-fg); }
  .convert { display: flex; gap: 12px; align-items: center; justify-content: space-between; flex-wrap: wrap; font-size: 14px; background: var(--surface); border: 1px solid var(--outline); border-radius: 8px; padding: 10px 12px; margin: 0 0 12px; }
  .pager { display: flex; justify-content: space-between; gap: 12px; margin: 20px 0 40px; padding-top: 16px; border-top: 1px solid var(--outline); }
  .btn-next { background: var(--primary); color: var(--on-primary); }

  /* narrow screens: rail -> chip strip on top, side panel under the section */
  @media (max-width: 1100px) {
    .editor { grid-template-columns: minmax(0, 1fr); gap: 12px; }
    .rail-col { display: none; }
    .chips-narrow { display: block; }
    .side { position: static; max-height: none; }
  }
</style>
