<!-- choose the TMS institute a report is about: partner preset from the visit association,
     institute preset when exactly one name matches. Emits `pick` with the data.tms link object
     (lib/tmsreport.js linkData) whenever both are chosen, `pick` null when cleared. -->
<script>
  import { createEventDispatcher, onMount } from 'svelte'
  import { linkData, loadInstitutes, loadLinkChoices } from '../../lib/tmsreport.js'

  export let association = ''
  export let instituteText = ''

  const dispatch = createEventDispatcher()
  let loading = true
  let error = ''
  let tranche = null
  let entities = []
  let entityId = ''
  let institutes = []
  let instituteId = ''

  onMount(async () => {
    try {
      const choices = await loadLinkChoices(association, instituteText)
      tranche = choices.tranche
      entities = [...choices.entities].sort((a, b) => String(a.entity_short_name).localeCompare(String(b.entity_short_name)))
      entityId = choices.partner ? String(choices.partner.id) : ''
      institutes = sortByName(choices.institutes)
      instituteId = choices.institute ? String(choices.institute.id) : ''
      emitPick()
    } catch (e) {
      error = e.message
    } finally {
      loading = false
    }
  })

  function sortByName(list) {
    return [...list].sort((a, b) => String(a.institute_name).localeCompare(String(b.institute_name)))
  }

  async function onPartner() {
    instituteId = ''
    institutes = []
    emitPick()
    if (!entityId || !tranche) return
    try {
      institutes = sortByName(await loadInstitutes(entityId, tranche.id))
    } catch (e) {
      error = e.message
    }
  }

  function emitPick() {
    const institute = institutes.find((i) => String(i.id) === instituteId)
    dispatch('pick', institute && tranche ? linkData(tranche, Number(entityId), institute) : null)
  }
</script>

<div class="picker">
  {#if loading}
    <p class="muted">Loading TMS partners…</p>
  {:else if error}
    <p class="err">{error}</p>
  {:else}
    <div class="two">
      <div class="field">
        <label for="tms-partner">TMS partner</label>
        <select id="tms-partner" bind:value={entityId} on:change={onPartner}>
          <option value="">Choose…</option>
          {#each entities as entity (entity.id)}<option value={String(entity.id)}>{entity.entity_short_name}</option>{/each}
        </select>
      </div>
      <div class="field">
        <label for="tms-institute">TMS institute</label>
        <select id="tms-institute" bind:value={instituteId} on:change={emitPick} disabled={!institutes.length}>
          <option value="">Choose…</option>
          {#each institutes as institute (institute.id)}<option value={String(institute.id)}>{institute.institute_name}</option>{/each}
        </select>
      </div>
    </div>
  {/if}
</div>

<style>
  .two { display: grid; grid-template-columns: 1fr 2fr; gap: 10px; }
  .field { margin-bottom: 6px; }
  p { margin: 4px 0; font-size: 14px; }
</style>
