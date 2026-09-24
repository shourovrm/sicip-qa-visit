<!-- one Field (spec: {key,label,kind,required?,options?}) -- used both for section `fields` blocks
     and for each field inside a `cards` entry. All stored values are strings, even numbers (see
     spec "Report data"); this just picks the right control for `kind` and reports the raw
     string on every change. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import AnswerSegmented from './AnswerSegmented.svelte'

  export let field
  export let value = ''
  export let disabled = false

  const dispatch = createEventDispatcher()

  function onInput(event) {
    dispatch('change', event.target.value)
  }
  function onChoice(event) {
    dispatch('change', event.detail)
  }
</script>

<!-- svelte-ignore a11y-label-has-associated-control -- the choice branch renders AnswerSegmented
     (a div, not a labelable control); every other branch is a real input/select/textarea -->
<label class="field">
  <span class="field-label">{field.label}{field.required ? ' *' : ''}</span>
  {#if field.kind === 'choice'}
    <AnswerSegmented options={field.options} {value} {disabled} on:change={onChoice} />
  {:else if field.kind === 'select'}
    <select {value} {disabled} on:change={onInput}>
      <option value=""></option>
      {#each field.options as opt}<option value={opt}>{opt}</option>{/each}
    </select>
  {:else if field.kind === 'longtext'}
    <textarea rows="2" placeholder={field.placeholder ?? ''} {value} {disabled} on:input={onInput}></textarea>
  {:else if field.kind === 'number'}
    <input type="number" inputmode="numeric" placeholder={field.placeholder ?? ''} {value} {disabled} on:input={onInput} />
  {:else if field.kind === 'date'}
    <input type="date" {value} {disabled} on:input={onInput} />
  {:else if field.kind === 'time'}
    <input type="time" {value} {disabled} on:input={onInput} />
  {:else if field.kind === 'phone'}
    <input type="tel" placeholder={field.placeholder ?? ''} {value} {disabled} on:input={onInput} />
  {:else}
    <input type="text" placeholder={field.placeholder ?? ''} {value} {disabled} on:input={onInput} />
  {/if}
</label>

<style>
  .field { display: block; margin-bottom: 12px; }
  .field-label { display: block; font-size: 13px; font-weight: 700; color: var(--muted); margin-bottom: 4px; }
</style>
