// in-memory cache over TmsCatalog so pickers reopen instantly; lives as long as the process.
package bd.sicip.qavisit.data.tms

class TmsLookup(private val catalog: TmsCatalog) {
    private var tranches: List<TmsTranche>? = null
    private var entities: List<TmsEntity>? = null
    private val institutesByPartner = mutableMapOf<Pair<Long, Long>, List<TmsInstitute>>()

    suspend fun tranches(): List<TmsTranche> = tranches ?: catalog.tranches().also { tranches = it }

    suspend fun entities(): List<TmsEntity> = entities ?: catalog.entities().also { entities = it }

    suspend fun institutes(entityId: Long, trancheId: Long): List<TmsInstitute> =
        institutesByPartner[entityId to trancheId]
            ?: catalog.institutes(entityId, trancheId).also { institutesByPartner[entityId to trancheId] = it }
}
