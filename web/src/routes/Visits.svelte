<!-- Scheduled|Completed tabs, Personal|Team scope, filters, edit own (admin edits any), soft delete -->
<script>
  import { onMount } from 'svelte'
  import { listVisits, createVisit, updateVisit, softDeleteVisit } from '../lib/db.js'
  import { officer, isAdmin } from '../lib/auth.js'
  import { officers } from '../lib/officers.js'
  import { DISTRICTS, ASSOCIATIONS, PURPOSES } from '../lib/seeds.js'
  import { CATEGORY_LABELS, autoCategoryFromDates } from '../lib/scoring.js'
  import Dropdown from '../components/Dropdown.svelte'

  let visits = []
  let loading = true
  let tab = 'scheduled' // scheduled | done
  let scope = 'mine' // mine | team
  let fDistrict = '', fCategory = '', fPurpose = '', fOfficer = '', fFrom = '', fTo = ''
  let editing = null // visit being created/edited (copy); editing.id null == create mode
  let saveErr = ''

  async function load() {
    loading = true
    visits = await listVisits()
    loading = false
  }
  onMount(load)

  $: mine = $officer?.id
  $: filtered = visits
    .filter((v) => v.status === tab)
    .filter((v) => (scope === 'mine' ? v.officer_id === mine : true))
    .filter((v) => !fDistrict || v.district === fDistrict)
    .filter((v) => !fCategory || v.category === fCategory)
    .filter((v) => !fPurpose || v.purpose === fPurpose)
    .filter((v) => !fOfficer || v.officer_id === fOfficer)
    .filter((v) => !fFrom || v.start_date >= fFrom)
    .filter((v) => !fTo || v.start_date <= fTo)

  function canEdit(v) {
    return v.officer_id === mine || $isAdmin
  }

  // global autosuggest sources -- distinct across every synced visit (any officer), same
  // reasoning as android's VisitDao.distinctInstitutes/distinctRefs.
  $: instituteOptions = [...new Set(visits.map((v) => v.institute))].filter(Boolean).sort()
  $: refOptions = [...new Set(visits.map((v) => v.ref_no))].filter(Boolean).sort()

  function startEdit(v) {
    editing = { ...v }
    saveErr = ''
  }

  const todayIso = new Date().toISOString().slice(0, 10)
  function startCreate() {
    editing = {
      id: null, institute: '', association: ASSOCIATIONS[0], district: DISTRICTS[0], dhaka_metro: null,
      purpose: PURPOSES[0], ref_no: '', ref_date: '', start_date: todayIso, end_date: todayIso,
      category: 'N/A', status: 'scheduled', remarks: '',
    }
    saveErr = ''
  }

  // typing/picking a ref no that matches an existing visit pulls in its ref_date, same as
  // android's ref-no PickerDropdown onSelect (exact match only, most recently updated wins).
  function refNoChanged() {
    const match = visits
      .filter((v) => v.ref_no === editing.ref_no && v.ref_date)
      .sort((a, b) => (b.updated_at ?? '').localeCompare(a.updated_at ?? ''))[0]
    if (match) editing.ref_date = match.ref_date
  }

  async function save() {
    saveErr = ''
    try {
      const patch = {
        institute: editing.institute, association: editing.association, district: editing.district,
        dhaka_metro: editing.district === 'Dhaka' ? editing.dhaka_metro : null,
        purpose: editing.purpose, ref_no: editing.ref_no || null, ref_date: editing.ref_date || null,
        start_date: editing.start_date, end_date: editing.end_date, remarks: editing.remarks || null,
      }
      // category only editable once the visit is done, matching android
      if (editing.status === 'done') {
        patch.category = editing.category
        patch.category_override = true
      }
      if (editing.id) {
        const updated = await updateVisit(editing.id, patch)
        visits = visits.map((v) => (v.id === updated.id ? updated : v))
      } else {
        // new visits keep computing+saving the auto category silently (no field shown until
        // there's something to review, matching android's VisitForm) -- status stays scheduled.
        const auto = autoCategoryFromDates(editing.start_date, editing.end_date, editing.district, editing.dhaka_metro)
        const created = await createVisit({ ...patch, officer_id: mine, status: 'scheduled', category: auto, category_override: false })
        visits = [created, ...visits]
      }
      editing = null
    } catch (e) {
      saveErr = e.message
    }
  }

  async function del(v) {
    if (!confirm(`Delete visit "${v.institute}"?`)) return
    await softDeleteVisit(v.id)
    visits = visits.filter((x) => x.id !== v.id)
  }
</script>

<h1>Visits</h1>

<div class="row-wrap tabs">
  <div class="seg">
    <button class:active={tab === 'scheduled'} on:click={() => (tab = 'scheduled')}>Scheduled</button>
    <button class:active={tab === 'done'} on:click={() => (tab = 'done')}>Completed</button>
  </div>
  <div class="seg">
    <button class:active={scope === 'mine'} on:click={() => (scope = 'mine')}>Personal</button>
    <button class:active={scope === 'team'} on:click={() => (scope = 'team')}>Team</button>
  </div>
  {#if tab === 'scheduled'}
    <button class="btn btn-primary" on:click={startCreate}>＋ Schedule visit</button>
  {/if}
</div>

<div class="card row-wrap filters">
  <Dropdown bind:value={fDistrict} options={DISTRICTS} placeholder="All districts" />
  <Dropdown bind:value={fCategory} options={Object.entries(CATEGORY_LABELS)} placeholder="All categories" />
  <Dropdown bind:value={fPurpose} options={PURPOSES} placeholder="All purposes" />
  {#if scope === 'team'}
    <Dropdown bind:value={fOfficer} options={$officers.map((o) => [o.id, o.name])} placeholder="All officers" />
  {/if}
  <input type="date" bind:value={fFrom} title="From" />
  <input type="date" bind:value={fTo} title="To" />
</div>

{#if loading}
  <p class="muted">Loading…</p>
{:else}
  <table class="card">
    <thead>
      <tr>
        <th>Institute</th><th>Association</th><th>District</th><th>Purpose</th>
        <th>Start</th><th>End</th><th>Category</th>{#if scope === 'team'}<th>Officer</th>{/if}<th></th>
      </tr>
    </thead>
    <tbody>
      {#if filtered.length === 0}<tr><td colspan="9" class="muted">No visits.</td></tr>{/if}
      {#each filtered as v (v.id)}
        <tr>
          <td>{v.institute}</td>
          <td>{v.association}</td>
          <td>{v.district}{v.district === 'Dhaka' ? (v.dhaka_metro ? ' (metro)' : ' (outside metro)') : ''}</td>
          <td>{v.purpose}</td>
          <td>{v.start_date}</td>
          <td>{v.end_date}</td>
          <td>{v.category}</td>
          {#if scope === 'team'}<td>{$officers.find((o) => o.id === v.officer_id)?.name ?? ''}</td>{/if}
          <td>
            {#if canEdit(v)}
              <button class="btn-link" on:click={() => startEdit(v)}>Edit</button>
              <button class="btn-link" on:click={() => del(v)}>Delete</button>
            {/if}
          </td>
        </tr>
      {/each}
    </tbody>
  </table>
{/if}

{#if editing}
  <!-- svelte-ignore a11y-click-events-have-key-events -->
  <!-- svelte-ignore a11y-no-static-element-interactions -->
  <div class="modal-backdrop" on:click|self={() => (editing = null)}>
    <form class="card modal" on:submit|preventDefault={save}>
      <h2>{editing.id ? 'Edit' : 'Schedule'} visit</h2>
      <div class="field">
        <label for="inst">Institute</label>
        <input id="inst" type="text" list="institute-list" bind:value={editing.institute} required />
        <datalist id="institute-list">{#each instituteOptions as i}<option value={i} />{/each}</datalist>
      </div>
      <div class="field"><label for="assoc">Association</label><Dropdown bind:value={editing.association} options={ASSOCIATIONS} /></div>
      <div class="field"><label for="dist">District</label><Dropdown bind:value={editing.district} options={DISTRICTS} /></div>
      {#if editing.district === 'Dhaka'}
        <div class="field">
          <label for="metro">Dhaka sub-option</label>
          <Dropdown id="metro" bind:value={editing.dhaka_metro} options={[[true, 'Inside metro'], [false, 'Outside metro']]} />
        </div>
      {/if}
      <div class="field"><label for="purp">Purpose</label><Dropdown bind:value={editing.purpose} options={PURPOSES} /></div>
      <div class="field">
        <label for="ref">Ref no</label>
        <input id="ref" type="text" list="ref-list" bind:value={editing.ref_no} on:input={refNoChanged} />
        <datalist id="ref-list">{#each refOptions as r}<option value={r} />{/each}</datalist>
      </div>
      <div class="field"><label for="refd">Ref date</label><input id="refd" type="date" bind:value={editing.ref_date} /></div>
      <div class="row">
        <div class="field"><label for="sd">Start date</label><input id="sd" type="date" bind:value={editing.start_date} required /></div>
        <div class="field"><label for="ed">End date</label><input id="ed" type="date" bind:value={editing.end_date} required /></div>
      </div>
      {#if editing.status === 'done'}
        <div class="field">
          <label for="cat">Category</label>
          <Dropdown id="cat" bind:value={editing.category} options={Object.entries(CATEGORY_LABELS)} />
        </div>
      {/if}
      <div class="field"><label for="rem">Remarks</label><textarea id="rem" bind:value={editing.remarks}></textarea></div>
      {#if saveErr}<p class="err">{saveErr}</p>{/if}
      <div class="row">
        <button type="submit" class="btn btn-primary">Save</button>
        <button type="button" class="btn" on:click={() => (editing = null)}>Cancel</button>
      </div>
    </form>
  </div>
{/if}

<style>
  h1 { color: var(--primary); }
  .tabs { justify-content: space-between; margin-bottom: 12px; }
  .seg { display: flex; gap: 4px; background: var(--surface); border: 1px solid var(--outline); border-radius: var(--radius-pill); padding: 3px; }
  .seg button { border: none; background: none; padding: 6px 14px; border-radius: var(--radius-pill); cursor: pointer; font-weight: 700; color: var(--muted); }
  .seg button.active { background: var(--primary); color: var(--on-primary); }
  .filters { margin-bottom: 16px; }
  .filters :global(select), .filters input { width: auto; }
  .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 10; }
  .modal { width: 420px; max-height: 90vh; overflow: auto; }
</style>
