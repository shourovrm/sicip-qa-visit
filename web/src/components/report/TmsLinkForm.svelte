<!-- TMS institute picker + Link button. Raises `link` with the data.tms object, `cancel` when
     the officer backs out (only shown when `cancellable`). Shared by the link bar, the side panel
     and section A so the three always behave the same. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import TmsLinkPicker from './TmsLinkPicker.svelte'

  export let association = ''
  export let instituteText = ''
  export let cancellable = false

  const dispatch = createEventDispatcher()
  let picked = null
</script>

<TmsLinkPicker {association} {instituteText} on:pick={(e) => (picked = e.detail)} />
<div class="buttons">
  <button type="button" class="btn" disabled={!picked} on:click={() => dispatch('link', picked)}>Link</button>
  {#if cancellable}<button type="button" class="btn-link" on:click={() => dispatch('cancel')}>Cancel</button>{/if}
</div>

<style>
  .buttons { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
  .btn { border-radius: 8px; border: 1px solid var(--outline); background: var(--surface); font-weight: 600; }
</style>
