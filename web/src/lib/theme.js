// dark/light toggle, persisted to localStorage. light default per DESIGN.md.
import { writable } from 'svelte/store'

const KEY = 'sicip-theme'
const initial = localStorage.getItem(KEY) === 'dark' ? 'dark' : 'light'

export const theme = writable(initial)

theme.subscribe((t) => {
  document.documentElement.setAttribute('data-theme', t)
  localStorage.setItem(KEY, t)
})

export function toggleTheme() {
  theme.update((t) => (t === 'dark' ? 'light' : 'dark'))
}
