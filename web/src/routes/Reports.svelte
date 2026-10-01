<!-- visit reports list (Draft/Submitted tabs, admin gets an officer filter like Tours.svelte) +
     "New report" modal (type + own visit picker) + the full-page ReportEditor swapped in for
     list when a report is open. One report per (visit,type) among non-deleted rows -- picking a
     visit/type that already has one opens it instead of creating a duplicate. -->
<script>
  import { listReports, listReportsByVisit, listVisits, createReport } from '../lib/db.js'
  import { officer, isAdmin } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'
  import { templateFor, dbTypeFor, newReportData, computeProgress, percentDone } from '../lib/reporttemplate.js'
  import Dropdown from '../components/Dropdown.svelte'
  import ReportEditor from '../components/report/ReportEditor.svelte'
  import TmsLinkPicker from '../components/report/TmsLinkPicker.svelte'
  import { tmsSession } from '../lib/tmsstore.js'
  import { loadCourseCatalog } from '../lib/tmsreport.js'
  import { runningCourseCards } from '../lib/tmscatalog.js'
  import { normalize } from '../lib/reporttemplate.js'
  import { openReportPrint } from '../lib/reporthtml.js'
  import { openNarrativePrint } from '../lib/narrativehtml.js'
  import { openQaReportPrint } from '../lib/qareporthtml.js'
  import { lazyAction } from '../lib/lazyaction.js'
  // docx lib is heavy: load it only when someone asks for a word file
  const downloadDocx = lazyAction(() => import('../lib/reportdocx.js'), 'downloadReportDocx')
  const downloadNarrativeDocx = lazyAction(() => import('../lib/narrativedocx.js'), 'downloadNarrativeDocx')
  const downloadQaDocx = lazyAction(() => import('../lib/qareportdocx.js'), 'downloadQaReportDocx')

  // report-row `type` -> its template's own `short` label ("Surprise visit" / "QA visit") --
  // never hardcode the label here, the template is the one source (spec section 1).
  function typeLabel(dbType) {
    return templateFor(dbType)?.short ?? dbType
  }
  // Print/Word pick the QA layout (Annex-3 + Remarks column) or the surprise layout by the
  // report's own template id -- see qareporthtml.js/qareportdocx.js (spec section 7).
  function printReport(template, data, meta) {
    return (template.id === 'qa' ? openQaReportPrint : openReportPrint)(template, data, meta)
  }
  function docxReport(template, data, meta) {
    return (template.id === 'qa' ? downloadQaDocx : downloadDocx)(template, data, meta)
  }

  let reports = []
  let visits = []
  let loading = true
  let tab = 'draft' // draft | submitted
  let officerFilter = '' // admin only; '' = own reports
  let current = null // report row open in the editor, or null for the list

  async function load() {
    loading = true
    const [allReports, allVisits] = await Promise.all([listReports(), listVisits()])
    const mine = $officer?.id
    const target = $isAdmin && officerFilter ? officerFilter : mine
    reports = allReports.filter((r) => r.officer_id === target)
    visits = allVisits
    loading = false
  }
  let loadStarted = false
  $: if ($officer && !loadStarted) { loadStarted = true; load() }

  function onOfficerFilterChange(e) {
    officerFilter = e.target.value
    load()
  }

  let search = ''
  $: needle = search.trim().toLowerCase()
  $: filtered = reports
    .filter((r) => r.status === tab)
    .filter((r) => !needle || [instituteFor(r), partnerFor(r), officerNameFor(r), typeLabel(r.type)].join(' ').toLowerCase().includes(needle))
  // only Monitoring Visit visits get a report (spec section 1); which template(s) apply comes
  // from the visit's own visit_type, not a type picked here.
  $: myVisits = visits
    .filter((v) => v.officer_id === $officer?.id && v.purpose === 'Monitoring Visit')
    .sort((a, b) => (a.start_date < b.start_date ? 1 : -1))

  function instituteFor(r) {
    return visits.find((v) => v.id === r.visit_id)?.institute ?? r.data?.fields?.ti_name ?? '—'
  }
  function percentFor(r) {
    const template = templateFor(r.type, r.template_version)
    return percentDone(template, computeProgress(template, r.data ?? {}))
  }
  function visitOf(r) {
    return visits.find((v) => v.id === r.visit_id) ?? null
  }
  function partnerFor(r) {
    return r.data?.fields?.provider || visitOf(r)?.association || '—'
  }
  function visitDateFor(r) {
    return r.data?.fields?.visit_date || r.data?.fields?.date_from || visitOf(r)?.start_date || ''
  }
  function officerNameFor(r) {
    return $officers.find((o) => o.id === r.officer_id)?.name ?? ($officer?.id === r.officer_id ? $officer.name : '')
  }
  const dateFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
  const savedFormat = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })
  function formatDate(value) {
    return value ? dateFormat.format(new Date(`${String(value).slice(0, 10)}T00:00`)) : '—'
  }
  function formatSaved(value) {
    return value ? savedFormat.format(new Date(value)) : '—'
  }

  // visit_type 'surprise'/'qa' -> that one template only; null (old rows created before the
  // visit_type split) -> offer both, same as the spec's fallback.
  function templateIdsFor(visit) {
    if (visit?.visit_type === 'surprise') return ['surprise']
    if (visit?.visit_type === 'qa') return ['qa']
    return ['surprise', 'qa']
  }

  // ---- new report modal ----
  let showNew = false
  let newType = ''
  let newVisitId = ''
  let newLink = null // data.tms picked in the TMS step, null = skipped / not signed in
  let skipTms = false
  let starting = false

  function openNew() {
    newType = ''
    newVisitId = ''
    newLink = null
    skipTms = false
    showNew = true
  }

  $: newVisit = visits.find((v) => v.id === newVisitId) ?? null
  $: newCandidates = newVisit ? templateIdsFor(newVisit) : []
  // a single-candidate visit (the normal case) picks its report automatically; a legacy
  // null-visit_type visit shows both as a radio choice -- reset newType whenever the visit changes.
  $: if (newCandidates.length === 1) newType = newCandidates[0]
  else if (!newCandidates.includes(newType)) newType = ''

  async function startNewReport() {
    if (!newVisitId || !newType) return
    const dbType = dbTypeFor(newType)
    // ask the server, not the filtered list: an admin viewing another officer has a partial list
    const existing = (await listReportsByVisit(newVisitId)).find((r) => r.type === dbType)
    if (existing) {
      showNew = false
      current = existing
      return
    }
    const tmpl = templateFor(newType)
    const visit = visits.find((v) => v.id === newVisitId)
    starting = true
    const data = await withTmsStart(tmpl, newReportData(tmpl, visit, $officer?.name ?? ''), visit)
    starting = false
    const row = await createReport({
      officer_id: $officer.id, visit_id: newVisitId, type: dbType,
      template_version: tmpl.version, data, status: 'draft',
    })
    reports = [row, ...reports]
    showNew = false
    current = row
  }

  // linked at creation: store the link; a surprise report also starts section A with the batches
  // running on the visit date. Any TMS failure just means a report without the prefill.
  async function withTmsStart(tmpl, data, visit) {
    if (!newLink || skipTms) return data
    const linked = { ...data, tms: newLink }
    if (tmpl.id !== 'surprise') return linked
    try {
      const cards = runningCourseCards(await loadCourseCatalog(newLink), visit?.start_date)
      if (cards.length) linked.cards = { ...linked.cards, courses: cards }
    } catch (e) {
      // keep the blank course card
    }
    return normalize(tmpl, linked)
  }

  // ---- editor open/close + list sync ----
  function open(r) { current = r }
  function closeEditor() { current = null }
  function onSave(e) { reports = reports.map((r) => (r.id === e.detail.id ? e.detail : r)) }
  function onConverted(e) { onSave(e); current = e.detail }
  function onDelete(e) { reports = reports.filter((r) => r.id !== e.detail.id); current = null }

  $: currentVisit = current ? visits.find((v) => v.id === current.visit_id) ?? null : null
  $: currentTemplate = current ? templateFor(current.type, current.template_version) : null
  $: currentReadonly = current ? current.status === 'submitted' && !$isAdmin : false
</script>

{#if current}
  <!-- keyed on the version: converting v1 -> v2 restarts the editor on the new template -->
  {#key `${current.id}:${current.template_version}`}
    <ReportEditor report={current} template={currentTemplate} visit={currentVisit} officerName={$officer?.name ?? ''}
      readonly={currentReadonly} onPrint={printReport} onDocx={docxReport} onNarrative={openNarrativePrint} onNarrativeDocx={downloadNarrativeDocx}
      on:close={closeEditor} on:save={onSave} on:submit={onSave} on:reopened={onConverted} on:delete={onDelete} on:converted={onConverted} />
  {/key}
{:else}
  <div class="list-head">
    <div>
      <h1>Reports</h1>
      <p class="muted sub">Surprise and QA visit reports</p>
    </div>
    <div class="list-tools">
      <div class="seg">
        <button class:active={tab === 'draft'} on:click={() => (tab = 'draft')}>Drafts ({reports.filter((r) => r.status === 'draft').length})</button>
        <button class:active={tab === 'submitted'} on:click={() => (tab = 'submitted')}>Submitted ({reports.filter((r) => r.status === 'submitted').length})</button>
      </div>
      <input type="text" class="search" placeholder="Search institute, partner, officer…" bind:value={search} aria-label="Search reports" />
      {#if $isAdmin}
        <div class="officer-filter">
          <Dropdown id="officer-filter" value={officerFilter} options={[['', 'My reports'], ...$officers.map((o) => [o.id, o.name])]} on:change={onOfficerFilterChange} />
        </div>
      {/if}
      {#if !officerFilter}<button class="btn btn-primary" on:click={openNew}>＋ New report</button>{/if}
    </div>
  </div>

  {#if loading}
    <p class="muted">Loading…</p>
  {:else}
    <table class="card list">
      <thead>
        <tr><th>Institute</th><th>Partner</th><th>Type</th><th>Visit date</th><th>Officer</th><th>Progress</th><th>TMS</th><th>Last saved</th><th></th></tr>
      </thead>
      <tbody>
        {#if filtered.length === 0}<tr><td colspan="9" class="muted">No reports.</td></tr>{/if}
        {#each filtered as r (r.id)}
          {@const percent = percentFor(r)}
          <tr class="clickable" on:click={() => open(r)}>
            <td><b>{instituteFor(r)}</b>{#if visitOf(r)?.district}<div class="muted small">{visitOf(r).district}</div>{/if}</td>
            <td>{partnerFor(r)}</td>
            <td>{typeLabel(r.type)}</td>
            <td>{formatDate(visitDateFor(r))}</td>
            <td>{officerNameFor(r) || '—'}</td>
            <td><span class="bar"><span style="width:{percent}%"></span></span> {percent}%</td>
            <td>{#if r.data?.tms}<span class="linked">Linked</span>{:else}—{/if}</td>
            <td>{formatSaved(r.updated_at)}</td>
            <td><button class="btn open" on:click|stopPropagation={() => open(r)}>Open</button></td>
          </tr>
        {/each}
      </tbody>
    </table>
  {/if}

  {#if showNew}
    <!-- svelte-ignore a11y-click-events-have-key-events -->
    <!-- svelte-ignore a11y-no-static-element-interactions -->
    <div class="modal-backdrop" on:click|self={() => (showNew = false)}>
      <form class="card modal" on:submit|preventDefault={startNewReport}>
        <h2>New report</h2>
        <div class="field">
          <label for="rvisit">Visit</label>
          <Dropdown id="rvisit" bind:value={newVisitId} placeholder="Select a visit"
            options={myVisits.map((v) => [v.id, `${v.institute} — ${v.start_date}`])} />
          {#if myVisits.length === 0}
            <p class="hint">No matching visits. Only Monitoring Visit visits get a report.</p>
          {/if}
        </div>
        {#if newVisit}
          <div class="field">
            <label for="rtype">Report</label>
            {#if newCandidates.length === 1}
              <!-- decided already by the visit's own monitoring type -- nothing to pick -->
              <div class="opt on"><span class="radio" /><div><b>{templateFor(newCandidates[0]).short}</b><small>{templateFor(newCandidates[0]).sections.length} sections · set by the visit's monitoring type</small></div></div>
            {:else}
              <!-- old visit, created before the monitoring-type split -- offer both -->
              {#each newCandidates as id}
                <button type="button" class="opt" class:on={newType === id} on:click={() => (newType = id)}>
                  <span class="radio" /><div><b>{templateFor(id).short}</b><small>{templateFor(id).sections.length} sections</small></div>
                </button>
              {/each}
            {/if}
          </div>
        {/if}
        {#if newVisit && newType}
          <div class="field">
            <label for="tms-partner">TMS institute</label>
            {#if !$tmsSession}
              <p class="hint">Signed out of TMS: the report starts without TMS data. Sign in on Profile to link it.</p>
            {:else if skipTms}
              <p class="hint">Not linked. <button type="button" class="btn-link" on:click={() => (skipTms = false)}>Link to TMS</button></p>
            {:else}
              {#key newVisitId}
                <TmsLinkPicker association={newVisit.association ?? ''} instituteText={newVisit.institute ?? ''} on:pick={(e) => (newLink = e.detail)} />
              {/key}
              <p class="hint">{newType === 'surprise' ? 'Courses running on the visit date fill section A.' : 'Links the report for TMS figures.'}
                <button type="button" class="btn-link" on:click={() => (skipTms = true)}>Skip</button></p>
            {/if}
          </div>
        {/if}
        <div class="row">
          <button type="submit" class="btn btn-primary" disabled={!newVisitId || !newType || starting}>{starting ? 'Starting…' : 'Start'}</button>
          <button type="button" class="btn" on:click={() => (showNew = false)}>Cancel</button>
        </div>
      </form>
    </div>
  {/if}
{/if}

<style>
  .hint { margin: 6px 0 0; font-size: 13px; color: var(--muted); }
  h1 { color: var(--primary); }
  .list-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; flex-wrap: wrap; margin: 4px 0 16px; }
  .list-head h1 { margin: 0; }
  .sub { margin: 2px 0 0; }
  .list-tools { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
  .search { width: 300px; }
  .officer-filter { width: 220px; }
  .list { padding: 0; }
  .list td { vertical-align: middle; padding: 12px 14px; }
  .list th { padding: 12px 14px; }
  .clickable { cursor: pointer; }
  .clickable:hover { background: var(--canvas); }
  .small { font-size: 13px; }
  .bar { display: inline-block; width: 110px; height: 6px; border-radius: 3px; background: var(--outline); overflow: hidden; vertical-align: middle; margin-right: 6px; }
  .bar span { display: block; height: 100%; background: var(--tone-yes-fg); }
  .linked { display: inline-block; padding: 2px 10px; border-radius: var(--radius-pill); background: var(--primary-container); color: var(--on-primary-container); font-weight: 700; font-size: 13px; }
  .open { padding: 4px 12px; border-radius: 8px; background: var(--surface); border-color: var(--outline); font-weight: 600; }
  .seg { display: flex; gap: 4px; background: var(--surface); border: 1px solid var(--outline); border-radius: var(--radius-pill); padding: 3px; }
  .seg button { border: none; background: none; padding: 6px 14px; border-radius: var(--radius-pill); cursor: pointer; font-weight: 700; color: var(--muted); }
  .seg button.active { background: var(--primary); color: var(--on-primary); }
  .seg button:disabled { opacity: 0.5; cursor: not-allowed; }
  .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 10; }
  .modal { width: 560px; max-width: calc(100vw - 32px); }
  h2 { font-size: 15px; margin: 0 0 12px; }
  .opt { display: flex; align-items: center; gap: 10px; width: 100%; text-align: left; padding: 10px 12px; margin-bottom: 6px; border: 1px solid var(--outline); border-radius: var(--radius-card); background: var(--surface); cursor: default; font: inherit; }
  button.opt { cursor: pointer; }
  .opt.on { border-color: var(--primary); background: var(--primary-container); }
  .opt .radio { flex: none; width: 16px; height: 16px; border-radius: 50%; border: 2px solid var(--outline); }
  .opt.on .radio { border-color: var(--primary); background: var(--primary); box-shadow: inset 0 0 0 3px var(--surface); }
  .opt small { display: block; color: var(--muted); font-weight: 400; }
</style>
