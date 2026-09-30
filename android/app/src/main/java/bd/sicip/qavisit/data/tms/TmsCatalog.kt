// pickers' source data: phases, partners, institutes. thin mapping over TmsApi.
package bd.sicip.qavisit.data.tms

import kotlinx.serialization.json.JsonObject

data class TmsTranche(val id: Long, val label: String, val active: Boolean)
data class TmsEntity(val id: Long, val shortName: String)
data class TmsInstitute(
    val id: Long,
    val instituteNo: String,
    val shortName: String,
    val name: String,
    val address: String,
    val entityId: Long,
)

class TmsCatalog(private val api: TmsApi) {
    suspend fun tranches(): List<TmsTranche> =
        api.getList("configurations/tranche/list").map { it.objectOrEmpty() }.map {
            TmsTranche(it["id"].lenientLong(), it["label"].lenientText(), it["active_status"].lenientInt() == 1)
        }

    suspend fun entities(): List<TmsEntity> =
        api.getList("entity/list?entity_id=").map { it.objectOrEmpty() }.map {
            TmsEntity(it["id"].lenientLong(), it["entity_short_name"].lenientText())
        }

    suspend fun institutes(entityId: Long, trancheId: Long): List<TmsInstitute> =
        api.getList("institute/filterList?entity=$entityId&tranche=$trancheId&courseType=&course=&district=")
            .map { it.objectOrEmpty() }.map { it.toInstitute(entityId) }

    private fun JsonObject.toInstitute(fallbackEntityId: Long) = TmsInstitute(
        id = this["id"].lenientLong(),
        instituteNo = this["training_institute_no"].lenientText(),
        shortName = this["short_name"].lenientText(),
        name = this["institute_name"].lenientText(),
        address = this["address"].lenientText(),
        entityId = this["entity_id"].lenientLong().takeIf { it != 0L } ?: fallbackEntityId,
    )
}
