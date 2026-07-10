<!-- thin <select> wrapper: options = [[value,label], ...] or plain strings -->
<script>
  export let value
  export let options = []
  export let placeholder = ''
  export let disabled = false
  export let id = undefined

  function pair(o) {
    return Array.isArray(o) ? o : [o, o]
  }
</script>

<select {id} bind:value {disabled} on:change>
  {#if placeholder}<option value="">{placeholder}</option>{/if}
  {#each options as o}
    {@const [v, label] = pair(o)}
    <option value={v}>{label}</option>
  {/each}
</select>
