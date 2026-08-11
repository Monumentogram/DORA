package com.monumentogram.dora.poc.search

import android.content.Context
import com.monumentogram.dora.poc.search.query.QueryStatus
import com.monumentogram.dora.poc.search.query.SearchFilters
import com.monumentogram.dora.poc.search.query.SearchMode
import org.json.JSONArray
import org.json.JSONObject

data class DatasetContract(
    val manifestId: String,
    val datasetVersion: String,
    val generatorVersion: String,
    val seed: Long,
    val conversationCount: Int,
    val transcriptRowCount: Int,
    val segmentsPerConversation: Int,
    val logicalDatasetSha256: String,
)

data class ExpectedMapping(
    val segmentId: Long,
    val conversationId: Long,
    val startMs: Long,
    val endMs: Long,
    val textSha256: String,
)

data class QueryCase(
    val id: String,
    val category: String,
    val rawQuery: String,
    val mode: SearchMode,
    val filters: SearchFilters,
    val latencyEligible: Boolean,
    val expectedStatus: QueryStatus,
    val expectedMatch: String?,
    val expectedRejectionCode: String?,
    val expectedTokens: List<String>,
    val expectedCount: Long,
    val expectedMappings: List<ExpectedMapping>,
)

data class QueryCampaignContract(
    val manifestId: String,
    val warmupPerQuery: Int,
    val repetitionsPerQuery: Int,
    val safetyRepetitions: Int,
    val scheduleSeed: Long,
    val resultLimit: Int,
    val cases: List<QueryCase>,
)

data class MutationContract(
    val manifestId: String,
    val contractSha256: String,
    val operations: JSONArray,
)

object BenchmarkContracts {
    fun readDataset(context: Context): DatasetContract {
        val root = readJson(context, "dataset-manifest.json")
        val contract = root.getJSONObject("contract")
        val expected = root.getJSONObject("expected")
        return DatasetContract(
            manifestId = root.getString("manifestId"),
            datasetVersion = contract.getString("datasetVersion"),
            generatorVersion = contract.getString("generatorVersion"),
            seed = contract.getLong("seed"),
            conversationCount = contract.getInt("conversationCount"),
            transcriptRowCount = contract.getInt("transcriptRowCount"),
            segmentsPerConversation = contract.getInt("segmentsPerConversation"),
            logicalDatasetSha256 = expected.getString("logicalDatasetSha256"),
        )
    }

    fun readQueries(context: Context): QueryCampaignContract {
        val root = readJson(context, "query-manifest.json")
        val contract = root.getJSONObject("contract")
        val campaign = contract.getJSONObject("campaign")
        val cases = contract.getJSONArray("queries").mapObjects(::parseQueryCase)
        return QueryCampaignContract(
            manifestId = root.getString("manifestId"),
            warmupPerQuery = campaign.getInt("warmupPerLatencyEligibleQuery"),
            repetitionsPerQuery = campaign.getInt("measuredRepetitionsPerLatencyEligibleQuery"),
            safetyRepetitions = campaign.getInt("safetyRepetitionsPerNonLatencyQuery"),
            scheduleSeed = campaign.getLong("scheduleSeed"),
            resultLimit = campaign.getInt("resultLimit"),
            cases = cases,
        )
    }

    fun readMutations(context: Context): MutationContract {
        val root = readJson(context, "mutation-manifest.json")
        return MutationContract(
            manifestId = root.getString("manifestId"),
            contractSha256 = root.getString("contractSha256"),
            operations = root.getJSONObject("contract").getJSONArray("operations"),
        )
    }

    fun readAssetText(context: Context, name: String): String =
        context.assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun readJson(context: Context, name: String): JSONObject =
        JSONObject(readAssetText(context, name))

    private fun parseQueryCase(value: JSONObject): QueryCase {
        val filters = value.getJSONObject("filters")
        val normalization = value.getJSONObject("normalization")
        val oracle = value.getJSONObject("oracle")
        return QueryCase(
            id = value.getString("id"),
            category = value.getString("category"),
            rawQuery = value.getString("rawQuery"),
            mode = SearchMode.valueOf(value.getString("mode")),
            filters =
                SearchFilters(
                    conversationId = filters.optionalLong("conversationId"),
                    sourceType = filters.optionalString("sourceType"),
                    startedAtFromMs = filters.optionalLong("startedAtFromMs"),
                    startedAtToMs = filters.optionalLong("startedAtToMs"),
                ),
            latencyEligible = value.getBoolean("latencyEligible"),
            expectedStatus = QueryStatus.valueOf(normalization.getString("status")),
            expectedMatch = normalization.optionalString("compiledMatch"),
            expectedRejectionCode = normalization.optionalString("rejectionCode"),
            expectedTokens = normalization.getJSONArray("tokens").mapStrings(),
            expectedCount = oracle.getLong("expectedCount"),
            expectedMappings =
                oracle.getJSONArray("expectedFirstMappings").mapObjects { mapping ->
                    ExpectedMapping(
                        segmentId = mapping.getLong("segmentId"),
                        conversationId = mapping.getLong("conversationId"),
                        startMs = mapping.getLong("startMs"),
                        endMs = mapping.getLong("endMs"),
                        textSha256 = mapping.getString("textSha256"),
                    )
                },
        )
    }

    private fun JSONObject.optionalString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private fun JSONObject.optionalLong(name: String): Long? =
        if (isNull(name)) null else getLong(name)

    private fun JSONArray.mapStrings(): List<String> =
        buildList(length()) {
            for (index in 0 until length()) add(getString(index))
        }

    private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        buildList(length()) {
            for (index in 0 until length()) add(transform(getJSONObject(index)))
        }
}
