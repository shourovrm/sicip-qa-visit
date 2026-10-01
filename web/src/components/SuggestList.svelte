<!-- a datalist whose options say where they came from (TMS, Shared, Past visits); the browser
     shows `label` next to the value. options = [{value, label}] (lib/suggestoptions.js) -->
<script>
  export let id
  export let options = []
</script>

<datalist {id}>{#each options as option (option.value)}<option value={option.value} label={option.label || undefined} />{/each}</datalist>
