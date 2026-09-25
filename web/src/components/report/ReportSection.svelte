<!-- one collapsible section (letter badge, title, note, blocks). Owns rendering only -- every
     mutation is applied straight into `data` (passed by reference) and then `onChange()` is
     called so the parent can reassign `data = data` and kick the autosave timer. Progress for
     the header badge/count is computed once in the parent and passed in as `progress`, not
     recomputed here, so this stays a plain prop (not a $:-through-helper-fn dependency -- see
     DECISIONS.md Svelte gotcha). -->
<script>
  import ChecklistItem from './ChecklistItem.svelte'
  import FieldInput from './FieldInput.svelte'
  import CardsBlock from './CardsBlock.svelte'
  import FlagsBlock from './FlagsBlock.svelte'
  import CriteriaBlock from './CriteriaBlock.svelte'
  import RemarksEditor from './RemarksEditor.svelte'
  import StrengthsPairs from './StrengthsPairs.svelte'
  import FieldDraft from './FieldDraft.svelte'
  import PlanDraft from './PlanDraft.svelte'
  import { isBlank } from '../../lib/reporttemplate.js'

  export let section // template section
  export let template // full template -- CardsBlock needs it to jump to a linked block's source
  export let data // report data (fields/checks/cards/flags), mutated in place
  export let answers // template.answers
  export let progress // {answered, total, done, flagged} for this section
  export let disabled = false
  export let defaultOpen = false
  export let onChange = () => {}

  // end-of-section review: every criterion's remarks, each with Edit
  $: criteriaItems = section.blocks.filter((b) => b.type === 'criteria').flatMap((b) => b.items).filter((i) => !i.heading)
  let reviewOpen = false

  let open = defaultOpen

  // today's named courses (section A), in card order -- same set reporttemplate.js's
  // courseIds()/perCourse rules use. Passed to every checklist item; ChecklistItem itself
  // decides whether a given item actually renders per-course (needs item.perCourse + 2+ here).
  $: namedCourses = (data.cards?.courses ?? []).filter((c) => !isBlank(c.course))

  // dropdown suggestions for a courseRef field: "course · batch" (or just course), courses with
  // no course name yet excluded. Only the identity block's `batch` field uses this today.
  function courseRefOptionsFor(block) {
    const field = block.fields?.find((f) => f.kind === 'courseRef')
    if (!field) return []
    return (data.cards[field.optionsFrom] ?? [])
      .filter((card) => !isBlank(card.course))
      .map((card) => (card.batch ? `${card.course} · ${card.batch}` : card.course))
  }
</script>

<details class="section" id="section-{section.key}" class:done={progress.done} class:flagged={progress.flagged} bind:open>
  <summary>
    <span class="letter" class:done={progress.done} class:flagged={progress.flagged}>{section.letter ?? section.number}</span>
    <span class="title">{section.title}</span>
    {#if section.optional}<span class="optional-tag">Optional</span>{/if}
    {#if progress.total > 0}<span class="count">{progress.answered}/{progress.total}{#if progress.notSeen} &middot; {progress.notSeen} not seen{/if}</span>{/if}
    <span class="chevron">▾</span>
  </summary>
  <div class="body">
    {#if section.note}<p class="note">{section.note}</p>{/if}
    {#each section.blocks as block, blockIndex (blockIndex)}
      {#if block.type === 'fields' && block.pairs}
        <div class="block">
          <StrengthsPairs {block} {template} {data} {disabled} {onChange} />
        </div>
      {:else if block.type === 'fields'}
        <div class="block">
          {#each block.fields as field (field.key)}
            {#if field.draftFrom}<FieldDraft {field} {template} {data} {disabled} {onChange} />{/if}
            <FieldInput {field} value={data.fields[field.key] ?? ''} {disabled}
              on:change={(e) => { data.fields[field.key] = e.detail; onChange() }} />
          {/each}
        </div>
      {:else if block.type === 'checklist'}
        <div class="block">
          {#if block.heading}<h4 class="subheading">{block.heading}</h4>{/if}
          {#each block.items as item, i (item.id)}
            <ChecklistItem {item} index={i + 1} {answers} check={data.checks[item.id]} {disabled} courses={namedCourses}
              on:change={(e) => { data.checks[item.id] = e.detail; onChange() }} />
          {/each}
        </div>
      {:else if block.type === 'cards'}
        <div class="block">
          {#if block.heading}<h4 class="subheading">{block.heading}</h4>{/if}
          {#if block.note}<p class="note">{block.note}</p>{/if}
          {#if block.draftFrom}<PlanDraft {block} {template} {data} {disabled} {onChange} />{/if}
          <CardsBlock {block} {template} cards={data.cards[block.key] ?? []} {disabled}
            courseOptions={courseRefOptionsFor(block)}
            on:change={(e) => { data.cards[block.key] = e.detail; onChange() }} />
        </div>
      {:else if block.type === 'flags'}
        <div class="block">
          <FlagsBlock {block} ticked={data.flags ?? []} {disabled}
            on:change={(e) => { data.flags = e.detail; onChange() }} />
        </div>
      {:else if block.type === 'criteria'}
        <div class="block">
          <CriteriaBlock {block} criteriaData={data.criteria ?? (data.criteria = {})} {disabled} {onChange} />
        </div>
      {/if}
    {/each}
    {#if criteriaItems.length}
      <div class="block">
        <button type="button" class="btn" on:click={() => (reviewOpen = !reviewOpen)}>{reviewOpen ? 'Hide section remarks' : 'Review section remarks'}</button>
        {#if reviewOpen}
          <ol class="review">
            {#each criteriaItems as item (item.id)}
              <li>
                <div class="review-q"><span class="review-no">{item.no}</span>{item.text}</div>
                <RemarksEditor {item} entry={data.criteria?.[item.id]} {disabled}
                  on:change={(e) => { (data.criteria ?? (data.criteria = {}))[item.id] = e.detail; onChange() }} />
              </li>
            {/each}
          </ol>
        {/if}
      </div>
    {/if}
  </div>
</details>

<style>
  .review { list-style: none; margin: 10px 0 0; padding: 0; }
  .review li { padding: 10px 0; border-top: 1px solid var(--outline); }
  .review-q { font-size: 13px; font-weight: 700; margin-bottom: 4px; }
  .review-no { color: var(--muted); margin-right: 6px; }
  .section { background: var(--surface); border: 1px solid var(--outline); border-radius: var(--radius-card); margin-bottom: 10px; }
  summary { list-style: none; cursor: pointer; display: flex; align-items: center; gap: 10px; padding: 14px 16px; }
  summary::-webkit-details-marker { display: none; }
  .letter { flex: none; width: 28px; height: 28px; border-radius: 8px; background: var(--primary-container); color: var(--on-primary-container); display: grid; place-items: center; font-size: 13px; font-weight: 700; }
  .letter.done { background: var(--tone-yes-bg); color: var(--tone-yes-fg); }
  .letter.flagged { background: var(--tone-no-bg); color: var(--tone-no-fg); }
  .title { flex: 1; min-width: 0; font-weight: 700; }
  .optional-tag { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; color: var(--muted); background: var(--canvas); border: 1px solid var(--outline); border-radius: var(--radius-pill); padding: 2px 8px; }
  .count { font-size: 12px; font-weight: 700; color: var(--muted); font-variant-numeric: tabular-nums; }
  .chevron { color: var(--muted); transition: transform 150ms; }
  details[open] .chevron { transform: rotate(180deg); }
  .body { padding: 0 16px 16px; }
  .note { font-size: 13px; color: var(--muted); margin: 0 0 8px; }
  .block + .block { margin-top: 8px; }
  .subheading { margin: 16px 0 4px; padding-top: 12px; border-top: 1px solid var(--outline); font-size: 13px; }
</style>
