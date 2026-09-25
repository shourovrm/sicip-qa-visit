<!-- visit reports list (Draft/Submitted tabs, admin gets an officer filter like Tours.svelte) +
     "New report" modal (type + own visit picker) + the full-page ReportEditor swapped in for
     list when a report is open. One report per (visit,type) among non-deleted rows -- picking a
     visit/type that already has one opens it instead of creating a duplicate. -->
<script>
  import { listReports, listReportsByVisit, listVisits, createReport } from '../lib/db.js'
  import { officer, isAdmin } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'
  import { templateFor, dbTypeFor, newReportData, computeProgress } from '../lib/reporttemplate.js'
  import Dropdown from '../components/Dropdown.svelte'
  import ReportEditor from '../components/report/ReportEditor.svelte'
  import { openReportPrint } from '../lib/reporthtml.js'
  import { openQaReportPrint } from '../lib/qareporthtml.js'
  // docx lib is heavy: load it only when someone asks for a word file
  const downloadDocx = (...args) => import('../lib/reportdocx.js').then((m) => m.downloadReportDocx(...args))
  const downloadQaDocx = (...args) => import('../lib/qareportdocx.js').then((m) => m.downloadQaReportDocx(...args))

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

  $: filtered = reports.filter((r) => r.status === tab)
  // only Monitoring Visit visits get a report (spec section 1); which template(s) apply comes
  // from the visit's own visit_type, not a type picked here.
  $: myVisits = visits
    .filter((v) => v.officer_id === $officer?.id && v.purpose === 'Monitoring Visit')
    .sort((a, b) => (a.start_date < b.start_date ? 1 : -1))

  function instituteFor(r) {
    return visits.find((v) => v.id === r.visit_id)?.institute ?? r.data?.fields?.ti_name ?? '—'
  }
  function progressFor(r) {
    return computeProgress(templateFor(r.type), r.data)
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

  function openNew() {
    newType = ''
    newVisitId = ''
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
    const data = newReportData(tmpl, visit, $officer?.name ?? '')
    const row = await createReport({
      officer_id: $officer.id, visit_id: newVisitId, type: dbType,
      template_version: tmpl.version, data, status: 'draft',
    })
    reports = [row, ...reports]
    showNew = false
    current = row
  }

  // ---- editor open/close + list sync ----
  function open(r) { current = r }
  function closeEditor() { current = null }
  function onSave(e) { reports = reports.map((r) => (r.id === e.detail.id ? e.detail : r)) }
  function onDelete(e) { reports = reports.filter((r) => r.id !== e.detail.id); current = null }

  $: currentVisit = current ? visits.find((v) => v.id === current.visit_id) ?? null : null
  $: currentTemplate = current ? templateFor(current.type) : null
  $: currentReadonly = current ? current.status === 'submitted' && !$isAdmin : false
</script>

{#if current}
  <ReportEditor report={current} template={currentTemplate} visit={currentVisit} officerName={$officer?.name ?? ''}
    readonly={currentReadonly} onPrint={printReport} onDocx={docxReport}
    on:close={closeEditor} on:save={onSave} on:submit={onSave} on:delete={onDelete} />
{:else}
  <h1>Reports</h1>

  {#if $isAdmin}
    <div class="field" style="width:260px">
      <label for="officer-filter">Officer</label>
      <Dropdown id="officer-filter" value={officerFilter} options={[['', 'My reports'], ...$officers.map((o) => [o.id, o.name])]} on:change={onOfficerFilterChange} />
    </div>
  {/if}

  <div class="row-wrap tabs">
    <div class="seg">
      <button class:active={tab === 'draft'} on:click={() => (tab = 'draft')}>In progress ({reports.filter((r) => r.status === 'draft').length})</button>
      <button class:active={tab === 'submitted'} on:click={() => (tab = 'submitted')}>Submitted ({reports.filter((r) => r.status === 'submitted').length})</button>
    </div>
    {#if !officerFilter}<button class="btn btn-primary" on:click={openNew}>＋ New report</button>{/if}
  </div>

  {#if loading}
    <p class="muted">Loading…</p>
  {:else}
    <table class="card">
      <thead>
        <tr><th>Type</th><th>Institute</th><th>Progress</th><th>Flags</th><th>{tab === 'draft' ? 'Created' : 'Submitted'}</th><th></th></tr>
      </thead>
      <tbody>
        {#if filtered.length === 0}<tr><td colspan="6" class="muted">No reports.</td></tr>{/if}
        {#each filtered as r (r.id)}
          {@const p = progressFor(r)}
          {@const flagTotal = p.flagsTicked.length + p.customFlags.length}
          <tr>
            <td>{typeLabel(r.type)}</td>
            <td>{instituteFor(r)}</td>
            <td>{p.sectionsDone}/{p.sectionsCounted} sections</td>
            <td>{#if flagTotal}<span class="flag-count">{flagTotal}</span>{:else}—{/if}</td>
            <td>{new Date(tab === 'draft' ? r.created_at : r.submitted_at).toLocaleDateString()}</td>
            <td><button class="btn-link" on:click={() => open(r)}>Open</button></td>
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
        <div class="row">
          <button type="submit" class="btn btn-primary" disabled={!newVisitId || !newType}>Start</button>
          <button type="button" class="btn" on:click={() => (showNew = false)}>Cancel</button>
        </div>
      </form>
    </div>
  {/if}
{/if}

<style>
  .hint { margin: 6px 0 0; font-size: 13px; color: var(--muted); }
  h1 { color: var(--primary); }
  .tabs { justify-content: space-between; margin: 12px 0; }
  .seg { display: flex; gap: 4px; background: var(--surface); border: 1px solid var(--outline); border-radius: var(--radius-pill); padding: 3px; }
  .seg button { border: none; background: none; padding: 6px 14px; border-radius: var(--radius-pill); cursor: pointer; font-weight: 700; color: var(--muted); }
  .seg button.active { background: var(--primary); color: var(--on-primary); }
  .seg button:disabled { opacity: 0.5; cursor: not-allowed; }
  .flag-count { display: inline-block; min-width: 20px; padding: 1px 7px; border-radius: var(--radius-pill); background: var(--tone-no-bg); color: var(--tone-no-fg); font-weight: 700; font-size: 12px; }
  .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 10; }
  .modal { width: 380px; }
  h2 { font-size: 15px; margin: 0 0 12px; }
  .opt { display: flex; align-items: center; gap: 10px; width: 100%; text-align: left; padding: 10px 12px; margin-bottom: 6px; border: 1px solid var(--outline); border-radius: var(--radius-card); background: var(--surface); cursor: default; font: inherit; }
  button.opt { cursor: pointer; }
  .opt.on { border-color: var(--primary); background: var(--primary-container); }
  .opt .radio { flex: none; width: 16px; height: 16px; border-radius: 50%; border: 2px solid var(--outline); }
  .opt.on .radio { border-color: var(--primary); background: var(--primary); box-shadow: inset 0 0 0 3px var(--surface); }
  .opt small { display: block; color: var(--muted); font-weight: 400; }
</style>
