// pure helpers for picking a TMS partner/institute from what the officer typed or selected.
package bd.sicip.qavisit.data.tms

// partner whose short name equals the visit's association (case-insensitive), else null.
fun matchPartner(entities: List<TmsEntity>, association: String): TmsEntity? {
    val wanted = association.trim()
    if (wanted.isEmpty()) return null
    return entities.firstOrNull { it.shortName.trim().equals(wanted, ignoreCase = true) }
}

// every word of the query must appear (case-insensitive substring) in short name, name or address.
fun searchInstitutes(institutes: List<TmsInstitute>, query: String): List<TmsInstitute> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return institutes
    return institutes.filter { institute ->
        val haystack = "${institute.shortName} ${institute.name} ${institute.address}".lowercase()
        words.all { it in haystack }
    }
}

// spellings seen for the same district; TMS addresses are free text.
private val DISTRICT_SPELLINGS: List<Set<String>> = listOf(
    setOf("bogura", "bogra"),
    setOf("chattogram", "chittagong"),
    setOf("cumilla", "comilla"),
    setOf("barishal", "barisal"),
    setOf("jashore", "jessore"),
    setOf("cox's bazar", "coxs bazar", "cox bazar", "cox's bazaar"),
    setOf("moulvibazar", "maulvibazar", "moulvi bazar"),
    setOf("narsingdi", "narshingdi", "narsinghdi"),
    setOf("chapai nawabganj", "chapainawabganj", "nawabganj"),
    setOf("jhalokathi", "jhalakathi", "jhalokati"),
    setOf("jhenaidah", "jhenidah"),
    setOf("joypurhat", "jaipurhat"),
    setOf("lakshmipur", "laxmipur"),
    setOf("khagrachari", "khagrachhari"),
    setOf("narayanganj", "narayangonj"),
    setOf("munshiganj", "munshigonj"),
    setOf("kishoreganj", "kishorganj"),
    setOf("habiganj", "hobiganj"),
    setOf("netrokona", "netrakona"),
)

fun districtSpellings(district: String): Set<String> {
    val name = district.trim().lowercase()
    if (name.isEmpty()) return emptySet()
    return DISTRICT_SPELLINGS.firstOrNull { name in it } ?: setOf(name)
}

// institutes whose address or name mentions the district; the full list when none do.
fun filterByDistrict(institutes: List<TmsInstitute>, district: String): List<TmsInstitute> {
    val spellings = districtSpellings(district)
    if (spellings.isEmpty()) return institutes
    val inDistrict = institutes.filter { institute ->
        val text = "${institute.address} ${institute.name}".lowercase()
        spellings.any { it in text }
    }
    return inDistrict.ifEmpty { institutes }
}

// the one institute whose name or short name equals the report's institute text; null when zero or several.
fun autoLinkCandidate(institutes: List<TmsInstitute>, instituteText: String): TmsInstitute? {
    val wanted = instituteText.trim()
    if (wanted.isEmpty()) return null
    val equal = institutes.filter {
        it.name.trim().equals(wanted, ignoreCase = true) || it.shortName.trim().equals(wanted, ignoreCase = true)
    }
    return equal.singleOrNull()
}

// tranche to use without asking: newest active one, else newest at all.
fun defaultTranche(tranches: List<TmsTranche>): TmsTranche? =
    tranches.filter { it.active }.maxByOrNull { it.id } ?: tranches.maxByOrNull { it.id }
