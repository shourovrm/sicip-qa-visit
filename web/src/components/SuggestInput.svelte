<!-- free-text input with its own suggestion list: each option shows its value plus a source tag
     (TMS, Shared, Past visits). Replaces <datalist>, which Firefox renders as the label alone.
     options = [{value, label}] (lib/suggestoptions.js); reports every new value via on:change. -->
<script>
  import { createEventDispatcher } from 'svelte'
  import { matchingOptions } from '../lib/suggestoptions.js'

  export let value = ''
  export let options = []
  export let id = undefined
  export let type = 'text'
  export let inputmode = undefined
  export let placeholder = ''
  export let disabled = false
  export let required = false

  const dispatch = createEventDispatcher()
  let open = false
  let highlighted = -1

  $: shown = open && !disabled ? matchingOptions(options, value) : []
  $: if (highlighted >= shown.length) highlighted = shown.length - 1

  function setValue(next) {
    value = next
    dispatch('change', next)
  }

  function onInput(event) {
    open = true
    highlighted = -1
    setValue(event.target.value)
  }

  function pick(option) {
    setValue(option.value)
    open = false
    highlighted = -1
  }

  function onKeydown(event) {
    if (event.key === 'ArrowDown') {
      event.preventDefault()
      open = true
      highlighted = Math.min(highlighted + 1, shown.length - 1)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      highlighted = Math.max(highlighted - 1, 0)
    } else if (event.key === 'Enter' && highlighted >= 0 && shown[highlighted]) {
      // pick instead of submitting the surrounding form
      event.preventDefault()
      pick(shown[highlighted])
    } else if (event.key === 'Escape') {
      open = false
    }
  }
</script>

<div class="suggest-input">
  <input {id} {type} {inputmode} {placeholder} {disabled} {required} {value} autocomplete="off"
    role="combobox" aria-expanded={shown.length > 0} aria-autocomplete="list"
    on:input={onInput} on:focus={() => (open = true)} on:blur={() => (open = false)} on:keydown={onKeydown} />
  {#if shown.length}
    <ul class="menu" role="listbox">
      {#each shown as option, index (option.value)}
        <!-- mousedown, not click: it lands before the input's blur closes the list -->
        <li role="option" aria-selected={index === highlighted} class:highlighted={index === highlighted}
          on:mousedown|preventDefault={() => pick(option)} on:mouseenter={() => (highlighted = index)}>
          <span class="value">{option.value}</span>
          {#if option.label}<span class="tag">{option.label}</span>{/if}
        </li>
      {/each}
    </ul>
  {/if}
</div>

<style>
  .suggest-input { position: relative; }
  .suggest-input input { width: 100%; }
  .menu {
    position: absolute; z-index: 20; left: 0; right: 0; top: calc(100% + 2px);
    margin: 0; padding: 4px 0; list-style: none; max-height: 280px; overflow-y: auto;
    background: var(--surface); border: 1px solid var(--outline); border-radius: 8px;
    box-shadow: 0 10px 24px rgba(20, 20, 60, 0.16);
  }
  li { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 7px 10px; cursor: pointer; font-size: 14px; }
  li.highlighted { background: var(--status-visit-bg); color: var(--status-visit-fg); }
  .value { overflow-wrap: anywhere; }
  .tag {
    flex: none; font-size: 11px; font-weight: 600; padding: 1px 8px; border-radius: 99px;
    background: var(--status-office-bg); color: var(--status-office-fg);
  }
</style>
