<!-- choose the TMS institute a report is about: partner preset from the visit association,
     institute preset when exactly one name matches. Emits `pick` with the data.tms link object
     (lib/tmsreport.js linkData) whenever both are chosen, `pick` null when cleared. -->
<script>
  import { createEventDispatcher, onMount } from 'svelte'
  import SuggestInput from '../SuggestInput.svelte'
  import { linkData, loadInstitutes, loadLinkChoices } from '../../lib/tmsreport.js'
  import { labelled } from '../../lib/suggestoptions.js'

  export let association = ''
  export let instituteText = ''

  const dispatch = createEventDispatcher()
  let loading = true
  let error = ''
  let tranche = null
  let entities = []
  let entityId = ''
  let partnerText = ''
  let institutes = []
  let instituteChoice = ''

  const sameText = (a, b) => String(a ?? '').trim().toLowerCase() === String(b ?? '').trim().toLowerCase()

  onMount(async () => {
    try {
      const choices = await loadLinkChoices(association, instituteText)
      tranche = choices.tranche
      entities = [...choices.entities].sort((a, b) => String(a.entity_short_name).localeCompare(String(b.entity_short_name)))
      entityId = choices.partner ? String(choices.partner.id) : ''
      partnerText = choices.partner ? String(choices.partner.entity_short_name) : ''
      institutes = sortByName(choices.institutes)
      instituteChoice = choices.institute ? choiceName(choices.institute, institutes) : ''
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

  // what the institute box shows for a row: its name, plus the TMS number when two rows share it
  function choiceName(institute, list) {
    const name = String(institute.institute_name ?? '').trim()
    const sharesName = list.some((other) => other.id !== institute.id && sameText(other.institute_name, name))
    return sharesName ? `${name} (${institute.training_institute_no})` : name
  }

  $: partnerOptions = labelled(entities.map((entity) => entity.entity_short_name), '')
  $: instituteOptions = labelled(institutes.map((institute) => choiceName(institute, institutes)), '')

  // the partner is chosen once the typed text equals a partner's short name
  async function onPartnerText(typed) {
    partnerText = typed
    const partner = entities.find((entity) => sameText(entity.entity_short_name, typed))
    const nextEntityId = partner ? String(partner.id) : ''
    if (nextEntityId === entityId) return
    entityId = nextEntityId
    instituteChoice = ''
    institutes = []
    emitPick()
    if (!entityId || !tranche) return
    try {
      const loaded = sortByName(await loadInstitutes(entityId, tranche.id))
      // a slower earlier request must not overwrite the list of a partner picked after it
      if (entityId === nextEntityId) institutes = loaded
    } catch (e) {
      error = e.message
    }
  }

  function onInstituteText(typed) {
    instituteChoice = typed
    emitPick()
  }

  function emitPick() {
    const institute = institutes.find((row) => sameText(choiceName(row, institutes), instituteChoice))
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
        <SuggestInput id="tms-partner" value={partnerText} options={partnerOptions} placeholder="Type to search…"
          on:change={(e) => onPartnerText(e.detail)} />
      </div>
      <div class="field">
        <label for="tms-institute">TMS institute</label>
        <SuggestInput id="tms-institute" value={instituteChoice} options={instituteOptions} disabled={!institutes.length}
          placeholder={entityId ? 'Type to search…' : 'Choose the partner first'} on:change={(e) => onInstituteText(e.detail)} />
      </div>
    </div>
  {/if}
</div>

<style>
  .two { display: grid; grid-template-columns: 1fr 2fr; gap: 10px; }
  .field { margin-bottom: 6px; }
  p { margin: 4px 0; font-size: 14px; }
</style>
