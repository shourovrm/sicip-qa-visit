<!-- left rail of the laptop report editor: every section with its letter badge, progress count
     (or a tick once done) and the current one highlighted. Clicking a row switches the middle
     column to that section. QA sections keep their group headings (profile / criteria / ...). -->
<script>
  import { createEventDispatcher } from 'svelte'

  export let title // e.g. "Surprise visit report"
  export let sections // template.sections
  export let progressSections // progress.sections: {[key]: {answered,total,done,flagged,notSeen?}}
  export let currentKey

  const dispatch = createEventDispatcher()
  const GROUP_LABELS = { profile: 'Centre profile', criteria: 'Quality criteria', conclusions: 'Feedback & conclusions' }
</script>

<nav class="rail" aria-label="Sections">
  <div class="rail-title">{title}</div>
  {#each sections as section, index (section.key)}
    {@const p = progressSections[section.key]}
    {#if section.group && section.group !== sections[index - 1]?.group}
      <div class="group-label">{GROUP_LABELS[section.group] ?? section.group}</div>
    {/if}
    <button type="button" class="row" class:on={section.key === currentKey} class:flagged={p.flagged}
      aria-current={section.key === currentKey ? 'step' : undefined} on:click={() => dispatch('select', section.key)}>
      <span class="badge">{section.letter ?? section.number}</span>
      <span class="name">{section.title}</span>
      {#if p.done}<span class="tick" aria-label="done">✓</span>
      {:else if p.total > 0}<span class="count">{p.answered}/{p.total}</span>{/if}
    </button>
  {/each}
</nav>

<style>
  .rail { display: flex; flex-direction: column; gap: 2px; }
  .rail-title { font-size: 12px; font-weight: 700; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); padding: 4px 10px 10px; }
  .group-label { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.04em; color: var(--muted); padding: 12px 10px 4px; }
  .row { display: flex; align-items: center; gap: 10px; width: 100%; padding: 8px 10px; border: none; border-radius: 8px; background: none; color: var(--ink); text-align: left; cursor: pointer; font-size: 15px; }
  .row:hover { background: var(--surface); }
  .row.on { background: var(--primary-container); color: var(--on-primary-container); font-weight: 700; }
  .badge { flex: none; width: 24px; height: 24px; border-radius: 6px; background: var(--ink); color: var(--canvas); display: grid; place-items: center; font-size: 12px; font-weight: 700; }
  .name { flex: 1; min-width: 0; line-height: 1.25; }
  .tick { color: var(--tone-yes-fg); font-weight: 700; }
  .count { font-size: 12px; color: var(--muted); font-variant-numeric: tabular-nums; }
</style>
