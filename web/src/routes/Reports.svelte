<!-- visit reports list (Draft/Submitted tabs, admin gets an officer filter like Tours.svelte) +
     "New report" modal (type + own visit picker) + the full-page ReportEditor swapped in for
     list when a report is open. One report per (visit,type) among non-deleted rows -- picking a
     visit/type that already has one opens it instead of creating a duplicate. -->
<script>
  import { listReports, listReportsByVisit, listVisits, createReport } from '../lib/db.js'
  import { officer, isAdmin } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'
  import { templateFor, newReportData, computeProgress } from '../lib/reporttemplate.js'
  import Dropdown from '../components/Dropdown.svelte'
  import ReportEditor from '../components/report/ReportEditor.svelte'
  import { openReportPrint } from '../lib/reporthtml.js'
  // docx lib is heavy: load it only when someone asks for a word file
  const downloadDocx = (...args) => import('../lib/reportdocx.js').then((m) => m.downloadReportDocx(...args))

  const TYPE_LABELS = { surprise: 'Surprise visit', monitoring: 'Monitoring / QA' }

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
  // only visits whose purpose the chosen report type is for (template.purposes, e.g. Monitoring Visit)
  $: newTypePurposes = templateFor(newType)?.purposes ?? []
  $: myVisits = visits
    .filter((v) => v.officer_id === $officer?.id)
    .filter((v) => newTypePurposes.length === 0 || newTypePurposes.includes(v.purpose))
    .sort((a, b) => (a.start_date < b.start_date ? 1 : -1))

  function instituteFor(r) {
    return visits.find((v) => v.id === r.visit_id)?.institute ?? r.data?.fields?.ti_name ?? '—'
  }
  function progressFor(r) {
    return computeProgress(templateFor(r.type), r.data)
  }

  // ---- new report modal ----
  let showNew = false
  let newType = 'surprise'
  let newVisitId = ''

  function openNew() {
    newType = 'surprise'
    newVisitId = ''
    showNew = true
  }

  async function startNewReport() {
    if (!newVisitId) return
    // ask the server, not the filtered list: an admin viewing another officer has a partial list
    const existing = (await listReportsByVisit(newVisitId)).find((r) => r.type === newType)
    if (existing) {
      showNew = false
      current = existing
      return
    }
    const tmpl = templateFor(newType)
    const visit = visits.find((v) => v.id === newVisitId)
    const data = newReportData(tmpl, visit, $officer?.name ?? '')
    const row = await createReport({
      officer_id: $officer.id, visit_id: newVisitId, type: newType,
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
    readonly={currentReadonly} onPrint={openReportPrint} onDocx={downloadDocx}
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
            <td>{TYPE_LABELS[r.type] ?? r.type}</td>
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
          <label for="rtype">Type</label>
          <div class="seg">
            <button type="button" class:active={newType === 'surprise'} on:click={() => (newType = 'surprise')}>Surprise</button>
            <button type="button" disabled title="Coming in a later version">Monitoring / QA</button>
          </div>
        </div>
        <div class="field">
          <label for="rvisit">Visit</label>
          <Dropdown id="rvisit" bind:value={newVisitId} placeholder="Select a visit"
            options={myVisits.map((v) => [v.id, `${v.institute} — ${v.start_date}`])} />
          {#if myVisits.length === 0}
            <p class="hint">No matching visits. This report is for visits with purpose: {newTypePurposes.join(' or ')}.</p>
          {/if}
        </div>
        <div class="row">
          <button type="submit" class="btn btn-primary" disabled={!newVisitId}>Start</button>
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
</style>
