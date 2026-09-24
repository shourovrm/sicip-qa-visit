<!-- segmented answer control shared by checklist items, choice fields and card choice fields.
     coloured only once chosen (tone from template.answers / field.options); tapping the already
     chosen option clears it -- ports the mockup's pointerdown/click dance from
     surprise-visit-checklist.html into a plain click handler (no native radios needed here). -->
<script>
  import { createEventDispatcher } from 'svelte'

  export let options = [] // [{id, label, tone}]
  export let value = ''
  export let disabled = false
  export let compact = false // per-course rows (CHANGE SET 3): narrower, still >=48px tall

  const dispatch = createEventDispatcher()

  function choose(id) {
    if (disabled) return
    dispatch('change', value === id ? '' : id)
  }
</script>

<div class="segmented" class:disabled class:compact role="radiogroup">
  {#each options as opt (opt.id)}
    <button
      type="button"
      class="opt tone-{opt.tone}"
      class:chosen={value === opt.id}
      {disabled}
      role="radio"
      aria-checked={value === opt.id}
      on:click={() => choose(opt.id)}
    >{opt.label}</button>
  {/each}
</div>

<style>
  .segmented { display: flex; border: 1px solid var(--outline); border-radius: 8px; overflow: hidden; background: var(--surface); }
  .segmented.disabled { opacity: 0.7; }
  .opt {
    flex: 1 1 0;
    min-width: 0;
    min-height: 40px;
    padding: 6px 4px;
    border: none;
    border-left: 1px solid var(--outline);
    background: none;
    color: var(--muted);
    font-weight: 700;
    font-size: 13px;
    line-height: 1.2;
    cursor: pointer;
  }
  /* per-course rows stack several of these controls -- keep them narrow but never shrink the
     tap target below 48px (spec: "4 compact answer buttons >=48dp") */
  .segmented.compact .opt { min-height: 48px; padding: 4px 2px; font-size: 11px; }
  .opt:first-child { border-left: none; }
  .opt:disabled { cursor: not-allowed; }
  .opt.chosen.tone-yes { background: var(--tone-yes-bg); color: var(--tone-yes-fg); }
  .opt.chosen.tone-no { background: var(--tone-no-bg); color: var(--tone-no-fg); }
  .opt.chosen.tone-partial { background: var(--tone-partial-bg); color: var(--tone-partial-fg); }
  .opt.chosen.tone-na { background: var(--tone-na-bg); color: var(--tone-na-fg); }
</style>
