<!-- shared schedule/edit-visit modal: Visits.svelte (create+edit) and Home.svelte (+Visit on an
     upcoming card, Add visit on the active tour) all mount this, so the field list + ref-no
     autosuggest lookup live in one place. parent owns `editing` state + persistence
     (createVisit/updateVisit). -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { DISTRICTS, ASSOCIATIONS, PURPOSES } from '../lib/seeds.js'
  import { CATEGORY_LABELS } from '../lib/scoring.js'
  import Dropdown from './Dropdown.svelte'

  export let editing
  export let visits = [] // for institute/ref-no autosuggest -- pass the reactive array directly
  export let saveErr = ''

  const dispatch = createEventDispatcher()

  $: instituteOptions = [...new Set(visits.map((v) => v.institute))].filter(Boolean).sort()
  $: refOptions = [...new Set(visits.map((v) => v.ref_no))].filter(Boolean).sort()

  // typing/picking a ref no that matches an existing visit pulls in its ref_date (exact match
  // only, most recently updated wins) -- mirrors android's ref-no PickerDropdown onSelect.
  function refNoChanged() {
    const match = visits
      .filter((v) => v.ref_no === editing.ref_no && v.ref_date)
      .sort((a, b) => (b.updated_at ?? '').localeCompare(a.updated_at ?? ''))[0]
    if (match) editing.ref_date = match.ref_date
  }
</script>

<!-- svelte-ignore a11y-click-events-have-key-events -->
<!-- svelte-ignore a11y-no-static-element-interactions -->
<div class="modal-backdrop" on:click|self={() => dispatch('cancel')}>
  <form class="card modal" on:submit|preventDefault={() => dispatch('save')}>
    <h2>{editing.id ? 'Edit' : 'Schedule'} visit</h2>
    <div class="field">
      <label for="inst">Institute</label>
      <input id="inst" type="text" list="visit-institute-list" bind:value={editing.institute} required />
      <datalist id="visit-institute-list">{#each instituteOptions as i}<option value={i} />{/each}</datalist>
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
      <input id="ref" type="text" list="visit-ref-list" bind:value={editing.ref_no} on:input={refNoChanged} />
      <datalist id="visit-ref-list">{#each refOptions as r}<option value={r} />{/each}</datalist>
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
      <button type="button" class="btn" on:click={() => dispatch('cancel')}>Cancel</button>
    </div>
  </form>
</div>

<style>
  h2 { font-size: 15px; margin: 0 0 12px; }
  .modal-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 10; }
  .modal { width: 420px; max-height: 90vh; overflow: auto; }
</style>
