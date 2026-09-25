<!-- "Improve wording" -- inline before/after rewrite helper shown under a longtext field or the
     checklist remarks box. Never writes anything itself: "Use this" only dispatches the new
     text, so the parent applies it through the exact same on:change path a keystroke would
     (autosave, normalize/syncLinks, etc all still run for it). No modal -- panel opens inline
     under the button, avoiding stacking over the section/card it belongs to. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { rewriteText } from '../../lib/rewrite.js'

  export let text = '' // current field value
  export let label = '' // field's own label, sent to the Worker for prompt context
  export let disabled = false // e.g. a read-only/submitted report

  const dispatch = createEventDispatcher()

  let loading = false
  let error = ''
  let suggestion = null // rewritten text once a request succeeds, cleared on Use this/Keep mine
  let online = navigator.onLine

  function setOnline() {
    online = true
  }
  function setOffline() {
    online = false
  }

  async function improve() {
    error = ''
    loading = true
    try {
      suggestion = await rewriteText(text, label)
    } catch (e) {
      error = e.message
    } finally {
      loading = false
    }
  }

  function useThis() {
    dispatch('change', suggestion)
    suggestion = null
  }
  function keepMine() {
    suggestion = null
  }
</script>

<svelte:window on:online={setOnline} on:offline={setOffline} />

{#if (text ?? '').trim()}
  <div class="improve">
    {#if !suggestion}
      <button
        type="button"
        class="btn-link improve-btn"
        on:click={improve}
        disabled={disabled || loading || !online}
      >
        {#if loading}Improving…{:else if !online}Improve wording (Offline){:else}Improve wording{/if}
      </button>
      {#if error}<p class="improve-error">{error}</p>{/if}
    {:else}
      <div class="improve-panel">
        <div class="improve-col">
          <span class="improve-col-label">Yours</span>
          <p class="improve-text">{text}</p>
        </div>
        <div class="improve-col">
          <span class="improve-col-label">Suggested</span>
          <p class="improve-text">{suggestion}</p>
        </div>
        <div class="improve-actions">
          <button type="button" class="btn" on:click={keepMine}>Keep mine</button>
          <button type="button" class="btn btn-primary" on:click={useThis}>Use this</button>
        </div>
      </div>
    {/if}
  </div>
{/if}

<style>
  .improve { margin: -6px 0 12px; }
  .improve-btn { font-size: 12px; }
  .improve-error { margin: 4px 0 0; font-size: 12px; color: var(--muted); }
  .improve-panel {
    margin-top: 6px;
    padding: 10px;
    border: 1px solid var(--outline);
    border-radius: 10px;
    background: var(--canvas);
  }
  .improve-col { margin-bottom: 8px; }
  .improve-col-label {
    display: block;
    font-size: 11px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.03em;
    color: var(--muted);
    margin-bottom: 2px;
  }
  .improve-text { margin: 0; font-size: 13px; white-space: pre-wrap; }
  .improve-actions { display: flex; justify-content: flex-end; gap: 8px; }
</style>
