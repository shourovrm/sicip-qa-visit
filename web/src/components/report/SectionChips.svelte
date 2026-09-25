<!-- sticky horizontal chip strip: one chip per section, green once done, red once flagged. tap
     opens + scrolls to that section (mirrors the mockup's section-nav, minus the scroll-spy
     "current" highlight -- not worth the IntersectionObserver plumbing for a single-page editor
     that's rarely longer than one screen of chips). -->
<script>
  export let sections // template.sections
  export let progressSections // progress.sections: {[key]: {answered,total,done,flagged}}

  function jump(key) {
    const el = document.getElementById(`section-${key}`)
    if (!el) return
    el.open = true
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
</script>

<nav class="chips" aria-label="Sections">
  {#each sections as section (section.key)}
    {@const p = progressSections[section.key]}
    <button type="button" class="chip" class:done={p.done} class:flagged={p.flagged} on:click={() => jump(section.key)}>
      <span class="letter">{section.letter ?? section.number}</span>{section.short}
      {#if section.optional}<span class="optional">Optional</span>{/if}
      {#if p.total > 0}<span class="count">{p.answered}/{p.total}{#if p.notSeen} &middot; {p.notSeen} not seen{/if}</span>{/if}
    </button>
  {/each}
</nav>

<style>
  .chips { display: flex; gap: 6px; overflow-x: auto; padding: 8px 0; scrollbar-width: none; }
  .chips::-webkit-scrollbar { display: none; }
  .chip {
    flex: none;
    display: inline-flex;
    align-items: center;
    gap: 5px;
    padding: 5px 10px;
    border-radius: var(--radius-pill);
    border: 1px solid var(--outline);
    background: var(--surface);
    color: var(--muted);
    font-size: 12px;
    font-weight: 700;
    cursor: pointer;
    white-space: nowrap;
  }
  .chip .letter { color: var(--ink); }
  .chip.done { border-color: transparent; background: var(--tone-yes-bg); color: var(--tone-yes-fg); }
  .chip.done .letter { color: var(--tone-yes-fg); }
  .chip.flagged { border-color: transparent; background: var(--tone-no-bg); color: var(--tone-no-fg); }
  .chip.flagged .letter { color: var(--tone-no-fg); }
  .count { font-variant-numeric: tabular-nums; }
  .optional { font-size: 10px; text-transform: uppercase; opacity: 0.75; }
</style>
