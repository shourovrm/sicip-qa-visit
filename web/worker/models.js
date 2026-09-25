// models -- single source of truth for rewrite models. add a model = one more entry here,
// nowhere else (rewrite.js, index.js and the UI all read through this file).

// comparison notes (moved here from rewrite.js): llama-3.1-8b-instruct-fp8,
// llama-3.3-70b-instruct-fp8-fast and mistral-small-3.1-24b-instruct compared on 5 real remarks
// (English/Bangla/Banglish) via wrangler dev --remote (gemma-3-12b-it: account not allowed,
// error 5018). 8b flipped a negation ("pai nai" = did not receive -> came out as "receives");
// mistral echoed the field label in bold markdown into the output. 70b-fast had zero factual
// errors and no leaked formatting.
export const MODELS = [
  {
    key: 'quality',
    id: '@cf/meta/llama-3.3-70b-instruct-fp8-fast',
    label: 'Best quality',
    note: 'Most accurate · about 200 uses a day for everyone',
  },
]

export const DEFAULT_MODEL_KEY = 'quality'

// looks up a model by key. unknown or missing key falls back to the default entry so old
// clients and stale saved settings never break -- callers should never 400 on a bad key.
export function modelFor(key) {
  return MODELS.find((model) => model.key === key) || MODELS.find((model) => model.key === DEFAULT_MODEL_KEY)
}
