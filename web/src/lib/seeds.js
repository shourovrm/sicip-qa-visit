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
  'EDC-IBA', 'IDCOL', 'Kumudini', 'LFMEAB', 'PKSF', 'REHAB', 'ISC-TH', 'WEAB', 'BPI', 'Others',
]

export const PURPOSES = [
  'Capacity Assessment', 'Monitoring Visit', 'Summative Assessment Monitoring',
  'ToT Monitoring', 'Recruitment', 'Others',
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

export const LEAVE_TYPES = ['Casual', 'Sick', 'Emergency', 'Others']

// hardcoded per product spec -- not a DB column (see BillHtml.kt precedent)
export const DESIGNATION = 'Program Officer (QA)'
