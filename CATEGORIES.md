# Visit categories (v1.5)

Category is the single source for a tour's bill allowances — span (Days/Nights), points, and
TA/DA nights/food are all keyed off the category code, not re-derived from raw dates in the bill.

| Category | Span (D/N) | Points | Nights allowance | Food allowance |
|----------|-----------|-------:|------------------:|----------------:|
| A***     | 8D7N      | 116    | 7                  | 7.5              |
| A**+     | 7D7N      | 112    | 7                  | 7.0              |
| A**      | 7D6N      | 100    | 6                  | 6.5              |
| A++*     | 6D6N      | 96     | 6                  | 6.0              |
| A++      | 6D5N      | 84     | 5                  | 5.5              |
| A+*      | 5D5N      | 80     | 5                  | 5.0              |
| A+       | 5D4N      | 68     | 4                  | 4.5              |
| A*       | 4D4N      | 64     | 4                  | 4.0              |
| A        | 4D3N      | 52     | 3                  | 3.5              |
| B+       | 3D3N      | 48     | 3                  | 3.0              |
| B        | 3D2N      | 36     | 2                  | 2.5              |
| C+       | 2D2N      | 32     | 2                  | 2.0              |
| C        | 2D1N      | 20     | 1                  | 1.5              |
| D+       | 1D1N      | 16     | 1                  | 1.0              |
| D        | 1 day / Dhaka non-metro | 4 | 0        | 0.5              |
| E        | Dhaka metro | 1    | 0                  | 0.0              |
| N/A      | Additional  | 0    | 0                  | 0.0              |

**Formula:** Days=4/Night=12 pts baseline; `accommodation = nights × 2000 BDT`;
`food = (nights + 0.5×(days − nights)) × 1500 BDT`.

**Rules:** Dhaka-metro visits score `E`; non-metro Dhaka or any single-day visit scores `D`; a
trip's primary visit carries the score, additional visits on the same trip score `N/A`; the
ladder caps at `A***` (8D7N or more).
