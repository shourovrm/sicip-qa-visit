// static picklists -- ported verbatim from android/app/src/main/java/bd/sicip/qavisit/data/seed/Seeds.kt
// change = app update at this scale (9 officers); fine, no DB table needed.

export const DISTRICTS = [
  'Bagerhat', 'Bandarban', 'Barguna', 'Barisal', 'Bhola', 'Bogura', 'Brahmanbaria', 'Chandpur',
  'Chapai Nawabganj', 'Chattogram', 'Chuadanga', "Cox's Bazar", 'Cumilla', 'Dhaka', 'Dinajpur',
  'Faridpur', 'Feni', 'Gaibandha', 'Gazipur', 'Gopalganj', 'Habiganj', 'Jamalpur', 'Jashore',
  'Jhalokathi', 'Jhenaidah', 'Joypurhat', 'Khagrachari', 'Khulna', 'Kishoreganj', 'Kurigram',
  'Kushtia', 'Lakshmipur', 'Lalmonirhat', 'Madaripur', 'Magura', 'Manikganj', 'Meherpur',
  'Moulvibazar', 'Munshiganj', 'Mymensingh', 'Naogaon', 'Narail', 'Narayanganj', 'Narsingdi',
  'Natore', 'Netrokona', 'Nilphamari', 'Noakhali', 'Pabna', 'Panchagarh', 'Patuakhali',
  'Pirojpur', 'Rajbari', 'Rajshahi', 'Rangamati', 'Rangpur', 'Satkhira', 'Shariatpur', 'Sherpur',
  'Sirajganj', 'Sunamganj', 'Sylhet', 'Tangail', 'Thakurgaon',
]

export const ASSOCIATIONS = [
  'AEOSIB', 'BACCO', 'BACI', 'BAPA', 'BASIS', 'BBSME', 'BEIOA', 'BGMEA', 'BIGM', 'BITAC',
  'BJMA', 'BKMEA', 'BMET', 'BRTC', 'BTMA', 'BWCCI', 'DTE', 'EDC-BRACU', 'EDC-BUTEX', 'EDC-EWU',
  'EDC-IBA', 'IDCOL', 'Kumudini', 'FLAXA', 'PKSF', 'REHAB', 'ISC-TH', 'WEAB', 'BPI', 'BSIA', 'Others',
]

export const PURPOSES = [
  'Capacity Assessment', 'Monitoring Visit', 'Summative Assessment Monitoring',
  'ToT Monitoring', 'Trainer Engagement', 'Others',
]

// visit.visit_type: only set when purpose is Monitoring Visit (visit form shows a 2-way segmented
// control); every other purpose stores null. Decides which report template the visit's report
// uses (see web/src/lib/reporttemplate.js templateFor) -- see supabase/migrations/011_visit_type.sql.
export const VISIT_TYPES = [
  { id: 'surprise', label: 'Surprise visit' },
  { id: 'qa', label: 'QA visit' },
]

// mode -> its valid classes; empty list = free-text class ("Other")
export const TRANSPORT = {
  'Bus': ['AC', 'Non-AC'],
  'Train': ['Snigdha', 'AC Berth', 'AC Seat', 'Shovon'],
  'Launch': ['Single AC Cabin', 'Non-AC Cabin', 'AC Seat'],
  'Air': ['Economy'],
  'Uber Car': ['Rented'],
  'CNG': ['Rented'],
  'Uber Bike': ['Rented'],
  'Pathao Bike': ['Rented'],
  'Pathao Car': ['Rented'],
  'Autorickshaw': ['Rented'],
  'N/A': [], // no mode claimed -- bill prints '-' for mode/class/fare
  'Other': [],
}

// exact remark text the "Ticket/ receipt attached" tick box writes; local bill skips legs carrying it
export const TICKET_REMARK = 'Ticket/ receipt attached'


// every officer currently holds this designation -- hardcoded, not a DB column (see BillHtml.kt precedent)
export const DESIGNATION = 'Program Officer (QA)'
