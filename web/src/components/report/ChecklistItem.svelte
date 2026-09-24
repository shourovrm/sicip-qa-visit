<!-- one checklist question: numbered text, segmented answer, and an expanding shared remarks box.
     parent owns data.checks[item.id]; this reports the new check object on every change and lets
     normalize() (called after every edit, see ReportEditor) derive the overall `answer` --
     this component never computes that itself.

     A `perCourse: true` item with 2+ named courses in section A (CHANGE SET 3) renders one
     answer row per course (course name + "Batch x", compact 4-button segmented) instead of the
     single row, plus a "Per course" tag next to the question; the remarks box stays shared. With
     0-1 named courses (or `courses` not passed) it's the normal single-answer item. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import AnswerSegmented from './AnswerSegmented.svelte'

  export let item // {id, text, perCourse?}
  export let index // 1-based, for the "n." prefix
  export let answers // template.answers: [{id,label,tone}]
  export let check = undefined // {answer, remarks, courses?} or undefined
  export let disabled = false
  export let courses = [] // today's named courses [{_id, course, batch}], section A order

  const dispatch = createEventDispatcher()

  $: answer = check?.answer ?? ''
  $: remarks = check?.remarks ?? ''
  $: perCourseMode = Boolean(item.perCourse) && courses.length >= 2
  let remarksOpen = false // "+ Add remarks" reveal state, local only -- not persisted

  function setAnswer(event) {
    dispatch('change', { answer: event.detail, remarks })
  }
  function setCourseAnswer(courseId, value) {
    // normalize() re-derives `answer` from `courses` on the next edit cycle -- this only ever
    // writes the one course's value.
    dispatch('change', { answer, remarks, courses: { ...(check?.courses ?? {}), [courseId]: value } })
  }
  function setRemarks(event) {
    dispatch('change', { answer, remarks: event.target.value, courses: check?.courses })
  }
</script>

<div class="item">
  <div class="item-text">
    <span class="item-number">{index}.</span>{item.text}
    {#if item.perCourse}<span class="per-course-tag">Per course</span>{/if}
  </div>
  {#if perCourseMode}
    {#each courses as c (c._id)}
      <div class="course-row">
        <div class="course-row-label">{c.course}<span class="course-row-batch">Batch {c.batch || '—'}</span></div>
        <AnswerSegmented options={answers} value={check?.courses?.[c._id] ?? ''} {disabled} compact
          on:change={(e) => setCourseAnswer(c._id, e.detail)} />
      </div>
    {/each}
  {:else}
    <AnswerSegmented options={answers} value={answer} {disabled} on:change={setAnswer} />
  {/if}
  {#if remarks || remarksOpen}
    <textarea class="remarks" placeholder="Remarks" rows="1" value={remarks} on:input={setRemarks} {disabled}></textarea>
  {:else}
    <button type="button" class="btn-link add-remarks" on:click={() => (remarksOpen = true)} {disabled}>+ Add remarks</button>
  {/if}
</div>

<style>
  .item { padding: 14px 0; border-top: 1px solid var(--outline); }
  .item:first-child { border-top: none; }
  .item-text { font-size: 14px; margin-bottom: 8px; display: flex; align-items: center; gap: 8px; }
  .item-number { color: var(--muted); font-variant-numeric: tabular-nums; }
  .per-course-tag { font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.03em; color: var(--muted); background: var(--canvas); border: 1px solid var(--outline); border-radius: var(--radius-pill); padding: 1px 7px; }
  .course-row { margin-bottom: 8px; }
  .course-row-label { display: flex; align-items: baseline; gap: 6px; font-size: 13px; font-weight: 700; margin-bottom: 4px; }
  .course-row-batch { font-size: 11px; font-weight: 600; color: var(--muted); }
  .remarks { margin-top: 8px; font-size: 13px; min-height: 36px; }
  .add-remarks { margin-top: 6px; font-size: 12px; }
</style>
