<!-- shared travel-leg add/edit modal: Bills.svelte (bill prep) and Home.svelte (Add travel on
     the active tour) both mount this, so the mode/class/fare/ticket fields live in one place.
     parent owns legForm state + persistence (createLeg/updateLeg via lib/legs.js helpers). -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { TRANSPORT, TICKET_REMARK } from '../lib/seeds.js'
  import Dropdown from './Dropdown.svelte'

  export let legForm
  export let places = []
  export let legErr = ''

  const dispatch = createEventDispatcher()
  $: legClasses = legForm.mode !== 'Other' ? TRANSPORT[legForm.mode] ?? [] : []
</script>

<!-- svelte-ignore a11y-click-events-have-key-events -->
<!-- svelte-ignore a11y-no-static-element-interactions -->
<div class="modal-backdrop" on:click|self={() => dispatch('cancel')}>
  <form class="card modal" on:submit|preventDefault={() => dispatch('save')}>
    <h2>{legForm.id ? 'Edit' : 'Add'} travel</h2>
    <div class="row">
      <div class="field"><label for="dd">Departure date</label><input id="dd" type="date" bind:value={legForm.dep_date} required /></div>
      <div class="field"><label for="dt">Time</label><input id="dt" type="time" bind:value={legForm.dep_time} required /></div>
    </div>
    <div class="field"><label for="dp">Departure place</label><input id="dp" type="text" list="leg-place-list" bind:value={legForm.dep_place} required /></div>
    <div class="row">
      <div class="field"><label for="ad">Arrival date</label><input id="ad" type="date" bind:value={legForm.arr_date} required /></div>
      <div class="field"><label for="at">Time</label><input id="at" type="time" bind:value={legForm.arr_time} required /></div>
    </div>
    <div class="field"><label for="ap">Arrival place</label><input id="ap" type="text" list="leg-place-list" bind:value={legForm.arr_place} required /></div>
    <datalist id="leg-place-list">{#each places as p}<option value={p} />{/each}</datalist>
    <div class="field">
      <label for="md">Mode</label>
      <Dropdown id="md" bind:value={legForm.mode} options={[...Object.keys(TRANSPORT)]} />
    </div>
    {#if legForm.mode === 'Other'}
      <div class="field"><label for="om">Mode (free text)</label><input id="om" type="text" bind:value={legForm.otherMode} /></div>
      <div class="field"><label for="cl">Class</label><input id="cl" type="text" bind:value={legForm.class} /></div>
    {:else if legClasses.length}
      <div class="field"><label for="cl2">Class</label><Dropdown id="cl2" bind:value={legForm.class} options={legClasses} /></div>
    {/if}
    {#if legForm.mode !== 'N/A'}
      <div class="field"><label for="fare">Fare (Tk)</label><input id="fare" type="number" step="0.01" bind:value={legForm.fare} /></div>
    {/if}
    <div class="field"><label for="rm">Remarks</label><input id="rm" type="text" bind:value={legForm.remarks} /></div>
    <div class="field checkbox-row">
      <label><input type="checkbox" bind:checked={legForm.ticket} /> {TICKET_REMARK}</label>
    </div>
    {#if legErr}<p class="err">{legErr}</p>{/if}
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
