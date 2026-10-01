<!-- visit form institute box: free text. A Monitoring Visit by a TMS-signed-in officer is offered
     the TMS institutes of the chosen association first (tagged TMS), then past-visit names. -->
<script>
  import { tmsSession } from '../lib/tmsstore.js'
  import { loadAssociationInstitutes } from '../lib/tmsinstitutes.js'
  import { instituteOptions } from '../lib/suggestoptions.js'
  import SuggestInput from './SuggestInput.svelte'

  export let value = ''
  export let association = ''
  export let purpose = ''
  export let pastNames = [] // institute names of past visits
  export let readonly = false

  let tmsInstitutes = []
  let tmsError = ''
  let loading = false
  let latestRequest = 0

  $: wantsTms = purpose === 'Monitoring Visit'
  $: fetchTms(wantsTms && Boolean($tmsSession), association)
  $: options = instituteOptions(tmsInstitutes, pastNames)
  $: hasTmsOptions = tmsInstitutes.length > 0

  async function fetchTms(enabled, forAssociation) {
    const request = ++latestRequest // an older answer must not replace a newer association's
    tmsInstitutes = []
    tmsError = ''
    if (!enabled || readonly) return
    loading = true
    try {
      const institutes = await loadAssociationInstitutes(forAssociation)
      if (request === latestRequest) tmsInstitutes = institutes
    } catch (e) {
      if (request === latestRequest) tmsError = e.message
    } finally {
      if (request === latestRequest) loading = false
    }
  }
</script>

<label for="inst">Institute</label>
<SuggestInput id="inst" bind:value {options} required disabled={readonly} />
{#if wantsTms && !readonly}
  {#if !$tmsSession}
    <p class="muted hint">Sign in to TMS on <a href="#/profile">Profile</a> to pick institutes from TMS.</p>
  {:else if loading}
    <p class="muted hint">Loading TMS institutes…</p>
  {:else if tmsError}
    <p class="muted hint">TMS institutes unavailable: {tmsError}</p>
  {:else if hasTmsOptions}
    <p class="muted hint">Institutes tagged TMS come from the TMS list for {association}.</p>
  {:else}
    <p class="muted hint">No TMS institutes found for {association}.</p>
  {/if}
{/if}

<style>
  .hint { margin: 4px 0 0; font-size: 12px; }
</style>
