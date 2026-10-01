<!-- slim call-to-action at the top of the editor: shown only while the report is not linked to a
     TMS institute. Signed in -> "Link institute" opens the picker right here (open by default in
     section A); signed out -> a pointer to Profile. Disappears once linked. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import TmsLinkForm from './TmsLinkForm.svelte'

  export let signedIn = false
  export let association = ''
  export let instituteText = ''
  export let expanded = false // start with the picker open

  const dispatch = createEventDispatcher()
  let open = expanded
</script>

<div class="bar" role="region" aria-label="TMS link">
  {#if !signedIn}
    <p><b>Not linked to TMS.</b> <a href="#/profile">Sign in to TMS on Profile</a> to link this report to its institute.</p>
  {:else if open}
    <p><b>Link this report to its TMS institute</b> to get course, batch and trainee suggestions from TMS.</p>
    <TmsLinkForm {association} {instituteText} cancellable={!expanded} on:link={(e) => dispatch('link', e.detail)} on:cancel={() => (open = false)} />
  {:else}
    <p><b>Not linked to TMS.</b> Link its institute for course, batch and trainee suggestions.</p>
    <button type="button" class="btn btn-primary" on:click={() => (open = true)}>Link institute</button>
  {/if}
</div>

<style>
  .bar { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 14px; margin: 0 0 12px; padding: 10px 14px; border-radius: 8px;
    background: var(--surface); border: 1px solid var(--accent); border-left-width: 5px; font-size: 14px; }
  .bar :global(.picker) { flex: 1 1 100%; }
  p { margin: 0; flex: 1 1 260px; }
</style>
