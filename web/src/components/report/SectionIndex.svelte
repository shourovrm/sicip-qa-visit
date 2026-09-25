<!-- grouped vertical section index -- used instead of SectionChips (a single scrolling row) once
     a template has more sections than fit comfortably in a chip strip (spec section 8: "> 13
     sections", which today only qa-v1's 15 sections trigger -- surprise-v1 has exactly 13 and
     stays on SectionChips). Groups by `section.group` (qa-v1: "profile"/"criteria"/
     "conclusions"); a template with no `group` on its sections falls back to one ungrouped list. -->
<script>
  export let sections // template.sections
  export let progressSections // progress.sections: {[key]: {answered,total,done,flagged,notSeen?}}

  const GROUP_LABELS = { profile: 'Centre profile', criteria: 'Quality criteria', conclusions: 'Feedback & conclusions' }

  // [{label, sections}] preserving first-seen group order; ungrouped sections (no `group` key)
  // land in one group with a null label (rendered as no heading).
  $: groups = sections.reduce((acc, section) => {
    const key = section.group ?? null
    let g = acc.find((x) => x.key === key)
    if (!g) { g = { key, label: key ? (GROUP_LABELS[key] ?? key) : null, sections: [] }; acc.push(g) }
    g.sections.push(section)
    return acc
  }, [])

  function jump(key) {
    const el = document.getElementById(`section-${key}`)
    if (!el) return
    el.open = true
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
</script>

<nav class="index" aria-label="Sections">
  {#each groups as g (g.key ?? 'ungrouped')}
    {#if g.label}<div class="group-label">{g.label}</div>{/if}
    {#each g.sections as section (section.key)}
      {@const p = progressSections[section.key]}
      <button type="button" class="row" class:done={p.done} class:flagged={p.flagged} on:click={() => jump(section.key)}>
        <span class="dot" class:done={p.done} class:flagged={p.flagged}></span>
        <span class="num">{section.number ?? section.letter}</span>
        <span class="short">{section.short}</span>
        {#if p.total > 0}<span class="count">{p.answered}/{p.total}{#if p.notSeen} &middot; {p.notSeen} not seen{/if}</span>{/if}
      </button>
    {/each}
  {/each}
</nav>

<style>
  .index { display: flex; flex-direction: column; gap: 1px; padding: 6px 0; max-height: 260px; overflow-y: auto; border: 1px solid var(--outline); border-radius: var(--radius-card); background: var(--surface); }
  .group-label { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; color: var(--muted); padding: 8px 12px 2px; }
  .row { display: flex; align-items: center; gap: 8px; width: 100%; text-align: left; border: none; background: none; padding: 6px 12px; cursor: pointer; font-size: 13px; color: var(--ink); }
  .row:hover { background: var(--canvas); }
  .dot { flex: none; width: 8px; height: 8px; border-radius: 50%; background: var(--outline); }
  .dot.done { background: var(--tone-yes-fg); }
  .dot.flagged { background: var(--tone-no-fg); }
  .num { color: var(--muted); font-variant-numeric: tabular-nums; font-weight: 700; min-width: 16px; }
  .short { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .count { flex: none; font-size: 11px; color: var(--muted); font-variant-numeric: tabular-nums; }
</style>
