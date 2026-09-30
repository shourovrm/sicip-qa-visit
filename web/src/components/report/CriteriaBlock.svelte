<!-- one QA-report `criteria` block (one criteria table, e.g. section 8's main table): optional
     sub-heading + intro line, then each item in order -- a `heading:true` item (e.g. "1. Skills
     and qualifications ... match the requirements") prints as a plain divider row with no
     controls, immediately followed by its lettered sub-items (a), b), c)...), matching the
     printed layout's own grouping. qa-v2 (template.evidenceRegister): each item's evidence is a
     numbered attachment in data.evidence, added/removed here through lib/evidence.js. -->
<script>
  import CriteriaItem from './CriteriaItem.svelte'
  import { evidenceSuggestions, itemEvidence, withEvidenceAdded, withEvidenceRemoved } from '../../lib/evidence.js'
  import { runningCourses } from '../../lib/reporttemplate.js'

  export let block // {type:"criteria", key, heading?, intro?, items:[...]}
  export let template
  export let data // whole report data, mutated in place
  export let disabled = false
  export let onChange = () => {}

  $: criteriaData = data.criteria ?? (data.criteria = {})
  $: register = Boolean(template.evidenceRegister)
  $: courses = runningCourses(data)

  // withEvidenceAdded/Removed return new objects; copy their evidence + criteria back in place
  function applyEvidence(next) {
    data.evidence = next.evidence ?? []
    data.criteria = next.criteria
    onChange()
  }
</script>

<div class="crit-block" class:with-heading={Boolean(block.heading)}>
  {#if block.heading}<h4 class="block-heading">{block.heading}</h4>{/if}
  {#if block.intro}<p class="block-intro">({block.intro})</p>{/if}
  {#each block.items as item (item.id)}
    {#if item.heading}
      <div class="parent-heading"><span class="no">{item.no}</span>{item.text}</div>
    {:else}
      <CriteriaItem {item} entry={criteriaData[item.id]} {disabled} {courses}
        evidenceEntries={register ? itemEvidence(data, item.id) : null}
        evidenceSuggestions={register ? evidenceSuggestions(template, data, item.id) : []}
        on:change={(e) => { criteriaData[item.id] = e.detail; onChange() }}
        on:add={(e) => applyEvidence(withEvidenceAdded(data, item.id, e.detail))}
        on:remove={(e) => applyEvidence(withEvidenceRemoved(data, item.id, e.detail))} />
    {/if}
  {/each}
</div>

<style>
  /* a section with a second criteria block (v1 section 8: main table + its 8.1 sub-table)
     gets a visual break before that block's own heading */
  .crit-block.with-heading { margin-top: 18px; padding-top: 14px; border-top: 2px solid var(--outline); }
  .block-heading { margin: 0 0 4px; font-size: 14px; }
  .block-intro { margin: 0 0 8px; font-size: 13px; font-style: italic; color: var(--muted); }
  .parent-heading { font-weight: 700; font-size: 13px; margin: 12px 0 2px; padding: 8px 10px; background: var(--canvas); border-radius: var(--radius-card); }
  .parent-heading:first-child { margin-top: 0; }
  .parent-heading .no { color: var(--muted); margin-right: 6px; }
</style>
