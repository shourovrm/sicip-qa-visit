import { defineConfig } from 'vite'
import { svelte } from '@sveltejs/vite-plugin-svelte'
import { fileURLToPath } from 'node:url'

// reports feature imports the template JSON straight from ../shared (single source of
// questions, shared with android) -- vite's dev server refuses to serve files outside the
// project root by default, so widen server.fs.allow to the repo root.
const repoRoot = fileURLToPath(new URL('..', import.meta.url))

export default defineConfig({
  plugins: [svelte()],
  server: { host: '127.0.0.1', port: 5173, fs: { allow: [repoRoot] } },
})
