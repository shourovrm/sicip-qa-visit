<!-- "AI remarks" -- QA report only (spec section 6, "web: editor head" -- one button for the
     whole report, not per-section). Builds the queue of eligible criteria across every section
     (isEligibleForAiRemarks: nonempty bullets, AND either typed text or 3+ bullets, AND the last
     AI result is stale), runs web/worker's mode:"remarks" ONE REQUEST AT A TIME (never in
     parallel -- a criterion's bullets must never get mixed with another's), and shows each result
     for the officer to accept or keep before moving on. A 429 (daily quota) waits 10s and retries
     the SAME item once; a second 429 stops the whole queue so the officer can try again later.
     The number/date guard (outputKeepsNumbers) silently skips a bad rewrite and moves on -- the
     fixed sentences already in `data.criteria` are never touched by a skip. -->
<script>
  import { onDestroy, createEventDispatcher } from 'svelte'
  import { buildRemarks, isEligibleForAiRemarks, outputKeepsNumbers } from '../../lib/remarks.js'
  import { rewriteRemarks } from '../../lib/rewrite.js'

  export let template
  export let data // report data, mutated in place (data.criteria)
  export let disabled = false
  export let onChange = () => {}

  const dispatch = createEventDispatcher()

  const RETRY_WAIT_MS = 10000

  // one row per eligible criterion, built once when the panel opens -- editing an item while the
  // queue is running does not retroactively add/remove rows (avoids the queue shifting under the
  // officer mid-review); re-open "AI remarks" to pick up anything newly eligible.
  function buildQueue() {
    const rows = []
    for (const section of template.sections) {
      for (const block of section.blocks) {
        if (block.type !== 'criteria') continue
        for (const item of block.items) {
          if (item.heading) continue
          const entry = data.criteria?.[item.id] ?? {}
          if (!isEligibleForAiRemarks(item, entry)) continue
          rows.push({ sectionShort: section.short, item, status: 'waiting', suggestion: null, note: '' })
        }
      }
    }
    return rows
  }

  let queue = buildQueue()
  let index = 0 // row currently working/ready/awaiting review
  let cancelled = false
  let queueError = '' // set when the queue stops early (second consecutive quota hit)

  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms))
  }

  async function processFrom(startIndex) {
    for (let i = startIndex; i < queue.length; i++) {
      if (cancelled) return
      index = i
      const row = queue[i]
      row.status = 'working'
      queue = queue
      const entry = data.criteria?.[row.item.id] ?? {}
      const bulletsJoined = buildRemarks(row.item, entry).join('\n')

      let attempt = 0
      let text = null
      let lastError = null
      while (attempt < 2 && text === null && !cancelled) {
        attempt += 1
        try {
          text = await rewriteRemarks(bulletsJoined, row.item.text)
        } catch (e) {
          lastError = e
          if (e.message === 'Daily limit reached — try again tomorrow' && attempt < 2) {
            row.status = 'waiting'
            queue = queue
            await sleep(RETRY_WAIT_MS)
          }
        }
      }
      if (cancelled) return

      if (text === null) {
        // a second quota hit (or any other failure) stops the queue -- everything after this
        // row stays "waiting" so re-opening "AI remarks" later picks up right here.
        row.status = 'error'
        row.note = lastError?.message ?? 'Could not improve wording'
        queueError = row.note
        queue = queue
        return
      }

      if (!outputKeepsNumbers(bulletsJoined, text)) {
        row.status = 'skipped'
        row.note = 'Kept original: numbers changed'
        queue = queue
        continue // guard failure needs no officer decision -- move straight to the next item
      }

      row.status = 'ready'
      row.suggestion = text
      queue = queue
      return // wait for the officer's Use AI version / Keep original before continuing
    }
  }

  function useAiVersion(row) {
    const entry = data.criteria?.[row.item.id] ?? {}
    const bulletsJoined = buildRemarks(row.item, entry).join('\n')
    data.criteria[row.item.id] = { ...entry, ai: { source: bulletsJoined, text: row.suggestion } }
    row.status = 'accepted'
    queue = queue
    onChange()
    processFrom(index + 1)
  }

  function keepOriginal(row) {
    row.status = 'kept'
    queue = queue
    processFrom(index + 1)
  }

  function retryQueue() {
    queueError = ''
    processFrom(index)
  }

  processFrom(0)

  onDestroy(() => {
    cancelled = true // "stop on leaving" -- an in-flight request is left to finish quietly, its result just isn't applied
  })

  $: current = queue[index]
</script>

<div class="ai-panel card">
  <div class="ai-head">
    <h3>AI remarks</h3>
    <button type="button" class="btn-link" on:click={() => dispatch('close')}>Close</button>
  </div>

  {#if queue.length === 0}
    <p class="muted">Nothing ready for AI remarks yet -- mark some options first, or edit a criterion the AI already rewrote.</p>
  {:else}
    <ul class="rows">
      {#each queue as row, i (row.item.id)}
        <li class="row" class:current={i === index && row.status !== 'waiting'}>
          <span class="status status-{row.status}">
            {#if row.status === 'working'}Writing…
            {:else if row.status === 'ready'}Ready to check
            {:else if row.status === 'accepted'}Used AI version
            {:else if row.status === 'kept'}Kept original
            {:else if row.status === 'skipped'}{row.note}
            {:else if row.status === 'error'}Stopped: {row.note}
            {:else}Waiting{/if}
          </span>
          <span class="label">{row.sectionShort} · {row.item.no} {row.item.text}</span>
        </li>
      {/each}
    </ul>

    {#if current && current.status === 'ready'}
      <div class="review card">
        <div class="review-h">{current.sectionShort} · {current.item.no} {current.item.text}</div>
        <div class="cols">
          <div class="col">
            <div class="col-label">From options</div>
            <ul>{#each buildRemarks(current.item, data.criteria?.[current.item.id] ?? {}) as b}<li>{b}</li>{/each}</ul>
          </div>
          <div class="col">
            <div class="col-label ai">AI version</div>
            <ul>{#each current.suggestion.split('\n').map((l) => l.trim()).filter(Boolean) as b}<li>{b}</li>{/each}</ul>
          </div>
        </div>
        <div class="row-actions">
          <button type="button" class="btn" {disabled} on:click={() => keepOriginal(current)}>Keep original</button>
          <button type="button" class="btn btn-primary" {disabled} on:click={() => useAiVersion(current)}>Use AI version</button>
        </div>
      </div>
    {/if}

    {#if queueError}
      <p class="err">{queueError}</p>
      <button type="button" class="btn" on:click={retryQueue}>Try again</button>
    {/if}
  {/if}
</div>

<style>
  .ai-panel { margin: 0 0 14px; }
  .ai-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
  .ai-head h3 { margin: 0; font-size: 14px; }
  .rows { list-style: none; margin: 0 0 10px; padding: 0; max-height: 220px; overflow-y: auto; }
  .row { display: flex; align-items: baseline; gap: 8px; padding: 6px 0; border-top: 1px solid var(--outline); font-size: 12px; }
  .row:first-child { border-top: none; }
  .row.current { background: var(--canvas); }
  .status { flex: none; min-width: 110px; font-weight: 700; color: var(--muted); }
  .status-ready { color: var(--accent); }
  .status-accepted, .status-kept { color: var(--tone-yes-fg); }
  .status-error { color: var(--tone-no-fg); }
  .label { color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .review { margin-top: 10px; }
  .review-h { font-weight: 700; font-size: 13px; margin-bottom: 8px; }
  .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .col-label { font-size: 11px; font-weight: 700; text-transform: uppercase; color: var(--muted); margin-bottom: 4px; }
  .col-label.ai { color: var(--accent); }
  .cols ul { margin: 0; padding-left: 16px; font-size: 13px; }
  .row-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 10px; }
  .err { color: var(--tone-no-fg); font-size: 13px; }
</style>
