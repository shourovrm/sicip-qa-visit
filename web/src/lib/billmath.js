// TA/DA bill math: TA sum, per-leg night-stay/food-day defaults, amount in words.
// ported 1:1 from android/app/src/main/java/bd/sicip/qavisit/domain/BillMath.kt
// nights/food per trip are resolved by the caller (scoring.js suggestedNights/suggestedFood,
// keyed off category) -- BillMath itself only sums what it's given.

// one itinerary row; depDate used only for per-leg day-grouping defaults
export function leg(fare, depDate = '') {
  return { fare, depDate }
}

export function ta(legs) {
  return legs.reduce((sum, l) => sum + l.fare, 0)
}

// one finished trip batched into a bill. nights/food set by caller at build time (from category);
// archived bills freeze whatever was resolved at submit time (see billsnapshot.js).
export function makeTrip(legs, startDate, endDate, nights = null, food = null) {
  return {
    legs, startDate, endDate,
    nights, food,
    get resolvedNights() { return this.nights ?? 0 },
    get resolvedFood() { return this.food ?? 0 },
  }
}

// a bill batches multiple finished trips: TA is every leg's fare, accommodation/food are
// summed per-trip nights/food times the fixed rate.
export function billTotals(trips) {
  const taTotal = trips.reduce((sum, t) => sum + ta(t.legs), 0)
  const accommodation = trips.reduce((sum, t) => sum + t.resolvedNights * 2000, 0)
  const food = trips.reduce((sum, t) => sum + t.resolvedFood * 1500, 0)
  return { ta: taTotal, accommodation, food, net: taTotal + accommodation + food }
}

// per-leg [nightStay, foodDay] defaults for the itinerary preview, day-grouped like the official
// bill: only the first leg of a calendar day carries that day's value; last day halved/zeroed.
export function legDefaults(legs, endDate) {
  const seenDays = new Set()
  return legs.map((l) => {
    if (seenDays.has(l.depDate)) return [0, 0]
    seenDays.add(l.depDate)
    return l.depDate === endDate ? [0, 0.5] : [1, 1]
  })
}

const ONES = ['Zero', 'One', 'Two', 'Three', 'Four', 'Five', 'Six', 'Seven', 'Eight', 'Nine', 'Ten',
  'Eleven', 'Twelve', 'Thirteen', 'Fourteen', 'Fifteen', 'Sixteen', 'Seventeen', 'Eighteen', 'Nineteen']
const TENS = ['', '', 'Twenty', 'Thirty', 'Forty', 'Fifty', 'Sixty', 'Seventy', 'Eighty', 'Ninety']

// 0..99 in words; empty string for 0 (caller decides whether to show it)
function twoDigitWords(n) {
  if (n === 0) return ''
  if (n < 20) return ONES[n]
  if (n % 10 === 0) return TENS[Math.floor(n / 10)]
  return `${TENS[Math.floor(n / 10)]} ${ONES[n % 10]}`
}

// BD-style numbering: crore (1e7), lakh (1e5), thousand (1e3), hundred (1e2).
// ponytail: crore group capped at 2 digits (up to 99 crore) -- plenty for a TA/DA bill.
export function amountInWords(net) {
  if (net === 0) return 'Zero'
  let n = net
  const crore = Math.floor(n / 10000000); n %= 10000000
  const lakh = Math.floor(n / 100000); n %= 100000
  const thousand = Math.floor(n / 1000); n %= 1000
  const hundred = Math.floor(n / 100); n %= 100
  const rest = n

  const parts = []
  if (crore > 0) parts.push(`${twoDigitWords(crore)} Crore`)
  if (lakh > 0) parts.push(`${twoDigitWords(lakh)} Lakh`)
  if (thousand > 0) parts.push(`${twoDigitWords(thousand)} Thousand`)
  if (hundred > 0) parts.push(`${ONES[hundred]} Hundred`)
  if (rest > 0) parts.push(twoDigitWords(rest))
  return parts.join(' ')
}
