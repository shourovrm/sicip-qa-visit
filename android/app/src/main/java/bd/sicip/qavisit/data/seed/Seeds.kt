// static picklists: districts/associations/purposes/transport. change = app update (9 users, fine).
package bd.sicip.qavisit.data.seed

// all 64 districts of Bangladesh, English spellings, alphabetical.
// spellings match this app's own historical Sheet data (Cumilla/Chattogram/Bogura/Jashore/
// Cox's Bazar/Chapai Nawabganj/Jhalokathi) so old + new visit rows stay consistent.
val DISTRICTS: List<String> = listOf(
    "Bagerhat", "Bandarban", "Barguna", "Barisal", "Bhola", "Bogura", "Brahmanbaria", "Chandpur",
    "Chapai Nawabganj", "Chattogram", "Chuadanga", "Cox's Bazar", "Cumilla", "Dhaka", "Dinajpur",
    "Faridpur", "Feni", "Gaibandha", "Gazipur", "Gopalganj", "Habiganj", "Jamalpur", "Jashore",
    "Jhalokathi", "Jhenaidah", "Joypurhat", "Khagrachari", "Khulna", "Kishoreganj", "Kurigram",
    "Kushtia", "Lakshmipur", "Lalmonirhat", "Madaripur", "Magura", "Manikganj", "Meherpur",
    "Moulvibazar", "Munshiganj", "Mymensingh", "Naogaon", "Narail", "Narayanganj", "Narsingdi",
    "Natore", "Netrokona", "Nilphamari", "Noakhali", "Pabna", "Panchagarh", "Patuakhali",
    "Pirojpur", "Rajbari", "Rajshahi", "Rangamati", "Rangpur", "Satkhira", "Shariatpur", "Sherpur",
    "Sirajganj", "Sunamganj", "Sylhet", "Tangail", "Thakurgaon",
)

val ASSOCIATIONS: List<String> = listOf(
    "AEOSIB", "BACCO", "BACI", "BAPA", "BASIS", "BBSME", "BEIOA", "BGMEA", "BIGM", "BITAC",
    "BJMA", "BKMEA", "BMET", "BRTC", "BTMA", "BWCCI", "DTE", "EDC-BRACU", "EDC-BUTEX", "EDC-EWU",
    "EDC-IBA", "IDCOL", "Kumudini", "LFMEAB", "PKSF", "REHAB", "ISC-TH", "WEAB", "BPI", "Others",
)

val PURPOSES: List<String> = listOf(
    "Capacity Assessment", "Monitoring Visit", "Summative Assessment Monitoring",
    "ToT Monitoring", "Recruitment", "Others",
)

// transport mode -> its classes; empty list = free-text class (e.g. "Other")
val TRANSPORT: Map<String, List<String>> = mapOf(
    "Bus" to listOf("AC", "Non-AC"),
    "Train" to listOf("Snigdha", "AC Berth", "AC Seat", "Shovon"),
    "Launch" to listOf("Single AC Cabin", "Non-AC Cabin", "AC Seat"),
    "Air" to listOf("Economy"),
    "Uber Car" to listOf("Rented"),
    "CNG" to listOf("Rented"),
    "Uber Bike" to listOf("Rented"),
    "Pathao Bike" to listOf("Rented"),
    "Pathao Car" to listOf("Rented"),
    "Autorickshaw" to listOf("Rented"),
    "N/A" to emptyList(), // no mode claimed -- bill prints '-' for mode/class/fare
    "Other" to emptyList(),
)

// exact remark text the "Ticket/ receipt attached" tick box writes; local bill skips legs carrying it
const val TICKET_REMARK = "Ticket/ receipt attached"
