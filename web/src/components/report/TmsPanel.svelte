<!-- report editor side panel: the report's TMS link, the batches running on the visit date and
     "Refresh from TMS". Signed out -> a signed-out card pointing at Profile; not linked -> the
     link picker. Fetching is the editor's job; this only shows state and raises events. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { runningBatches } from '../../lib/tmscatalog.js'
  import { isLinked } from '../../lib/tmsreport.js'
  import TmsLinkPicker from './TmsLinkPicker.svelte'

  export let signedIn = false
  export let tms = null // data.tms
  export let catalog = null
  export let visitDate = ''
  export let association = ''
  export let instituteText = ''
  export let loading = false
  export let error = ''
  export let disabled = false

  const dispatch = createEventDispatcher()
  let picking = false
  let picked = null

  $: running = catalog ? runningBatches(catalog, visitDate) : []

  function saveLink() {
    if (!picked) return
    dispatch('link', picked)
    picking = false
  }
</script>

<section class="panel">
  <h3>TMS link</h3>
  {#if !signedIn}
    <p class="small">Signed out of TMS. <a href="#/profile">Sign in on Profile</a> to get course, batch and trainee suggestions.</p>
    {#if isLinked(tms)}<p class="small">Linked to {tms.name} ({tms.institute_id}).</p>{/if}
  {:else if isLinked(tms) && !picking}
    <dl>
      <dt>Institute</dt><dd>{tms.name || '—'} ({tms.institute_id})</dd>
      <dt>Running</dt>
      <dd>
        {#if loading}loading…
        {:else if running.length}{running.map((b) => `${b.courseName} batch ${b.number}`).join(', ')}
        {:else if catalog}none on {visitDate || 'the visit date'}
        {:else}—{/if}
      </dd>
    </dl>
    {#if error}<p class="err">{error}</p>{/if}
    <div class="row-wrap buttons">
      <button type="button" class="btn" on:click={() => dispatch('refresh')} disabled={loading}>Refresh from TMS</button>
      {#if !disabled}<button type="button" class="btn-link" on:click={() => (picking = true)}>Change</button>{/if}
    </div>
  {:else if disabled}
    <p class="small">Not linked to a TMS institute.</p>
  {:else if picking || !isLinked(tms)}
    <p class="small">Link this report to its TMS institute for course, batch and trainee suggestions.</p>
    <TmsLinkPicker {association} {instituteText} on:pick={(e) => (picked = e.detail)} />
    <div class="row-wrap buttons">
      <button type="button" class="btn" disabled={!picked} on:click={saveLink}>Link</button>
      {#if picking}<button type="button" class="btn-link" on:click={() => (picking = false)}>Cancel</button>{/if}
    </div>
  {/if}
</section>

<style>
  .panel { background: var(--surface); border: 1px solid var(--outline); border-radius: var(--radius-card); padding: 14px 16px; }
  h3 { margin: 0 0 10px; font-size: 12px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }
  dl { display: grid; grid-template-columns: 82px 1fr; gap: 6px 10px; margin: 0 0 10px; font-size: 14px; }
  dt { color: var(--muted); }
  dd { margin: 0; }
  .small { font-size: 13px; color: var(--muted); margin: 0 0 8px; }
  .buttons { gap: 12px; }
  .btn { border-radius: 8px; border: 1px solid var(--outline); background: var(--surface); font-weight: 600; }
</style>
