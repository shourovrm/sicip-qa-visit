// models -- single source of truth for rewrite models. add a model = one more entry here,
// nowhere else (rewrite.js, index.js and the UI all read through this file).

// comparison 2026-09-25 (10 models x 8 real-style notes, prod prompt, usage.neurons measured):
// gemma-4-26b 0/16 fact errors, ~1.9 neurons/call; llama-4-scout 0/8, ~5.6; glm-4.7-flash one
// singular->plural drift, ~1.7, then live check wrote "3/15" as "15 out of 3" -> dropped. rejected: llama-3.3-70b (accurate, ~11 neurons, label echo),
// llama-3.1-8b ("3/15 trainees" -> "15th March"), llama-3.2-3b (flips "did not receive",
// invents Tk amounts), granite-4 ("2 mash" -> "2 masha"), gpt-oss-20b/120b (label echo,
// markdown), qwen3-30b (answer only in message.reasoning). `options` = extra AI.run inputs.
const NO_THINKING = { chat_template_kwargs: { enable_thinking: false } }

export const MODELS = [
  {
    key: 'gemma',
    id: '@cf/google/gemma-4-26b-a4b-it',
    label: 'Recommended',
    note: 'Most accurate in our tests · Bangla can take a few seconds',
    options: NO_THINKING,
  },
  {
    key: 'scout',
    id: '@cf/meta/llama-4-scout-17b-16e-instruct',
    label: 'Alternative',
    note: 'Also accurate · a little wordier',
  },
]

export const DEFAULT_MODEL_KEY = 'gemma'

// looks up a model by key. unknown or missing key falls back to the default entry so old
// clients and stale saved settings never break -- callers should never 400 on a bad key.
export function modelFor(key) {
  return MODELS.find((model) => model.key === key) || MODELS.find((model) => model.key === DEFAULT_MODEL_KEY)
}
