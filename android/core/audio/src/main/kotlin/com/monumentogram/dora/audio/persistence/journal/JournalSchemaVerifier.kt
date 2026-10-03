package com.monumentogram.dora.audio.persistence.journal

import androidx.sqlite.db.SupportSQLiteDatabase
import com.monumentogram.dora.poc.recovery.contract.Sha256Value

/** Room's identity hash alone does not detect DDL tampering with an unchanged master row. */
internal object JournalSchemaVerifier {
    fun verify(database: SupportSQLiteDatabase, version: Int = SEGMENTATION_SCHEMA_VERSION) {
        val actual = mutableMapOf<String, String>()
        database
            .query(
                "SELECT name, sql FROM sqlite_master " +
                    "WHERE type IN ('table','index','trigger','view') AND name NOT LIKE 'sqlite_%'"
            )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0)
                    if (name == "android_metadata" || name == "room_master_table") continue
                    val sql =
                        cursor
                            .getString(1)
                            .replace("IF NOT EXISTS ", "")
                            .trim()
                            .replace(Regex("\\s+"), " ")
                    check(
                        actual.put(
                            name,
                            Sha256Value.calculate(sql.toByteArray(Charsets.UTF_8)).toLowercaseHex(),
                        ) == null
                    )
                }
            }
        check(version in 1..SEGMENTATION_SCHEMA_VERSION)
        val schema =
            expected +
                (if (version >= 2) originalReferenceSchema else emptyMap()) +
                (if (version >= SEGMENTATION_SCHEMA_VERSION)
                    mapOf("audio_segmentation" to schemaDigest(SegmentationMigration.TABLE_SQL))
                else emptyMap())
        check(actual == schema) { "Journal schema rejected" }
        database.query("PRAGMA foreign_key_check").use {
            check(!it.moveToFirst()) { "Journal references rejected" }
        }
    }

    // Generated from the reviewed Room v1 schema export. Changes require schema review and tests.
    private val originalReferenceSchema =
        mapOf(
            "original_audio_reference" to schemaDigest(OriginalAudioMigration.TABLE_SQL),
            "index_original_audio_reference_digest" to
                schemaDigest(OriginalAudioMigration.INDEX_SQL),
        )

    private fun schemaDigest(sql: String) =
        Sha256Value.calculate(
                sql.replace("IF NOT EXISTS ", "")
                    .trim()
                    .replace(Regex("\\s+"), " ")
                    .toByteArray(Charsets.UTF_8)
            )
            .toLowercaseHex()

    private val expected =
        mapOf(
            "vault_binding" to "a8f2669b469cb337dac64600fb58e817b7830250988453e4115da41a3bdd0135",
            "index_vault_binding_ownerId_vaultId" to
                "44186f47f4442bf2455fbf1114fe3d76e89146165845999a17f7859089f83139",
            "audio_asset" to "7b8f4f025ec8c2b4bd21573b860516efa6c371b8f0e4d6a8505216f13abc8552",
            "index_audio_asset_ownerId_vaultId" to
                "0fb80d451dd680f664f1a395242c94aa713d86698f8932e4fc017323be68eb41",
            "index_audio_asset_recordingId_assetId_sessionId" to
                "6994b167ae06cfff002947c461596e594f6a3ebab0050a38f898162191d4ffe0",
            "physical_source" to "4e028f427c66f9f603cdaa9ccf73a8295ce454b73c59cd6934b3e2dd1b6df26e",
            "index_physical_source_assetId_physicalId_physicalFirstFrame" to
                "9ccc11a00cedf4846d790e74429cde3b197bb38f96a43962a933ff77a26a3cad",
            "unit_claim" to "eebd8bc2d9028d13d63df37fe3303f1d32e6f8e4f747610043b8ec440546c1d1",
            "index_unit_claim_assetId_ordinal" to
                "1440a9a20d222a21ec8e28ee568707047d86e1c8103acd0fa0309dbd1cceb31a",
            "index_unit_claim_assetId_firstFrame" to
                "0779d1184724893d773cfdfb4d8254f67dd329f9eac58a571aafdf3066a52d67",
            "index_unit_claim_assetId_physicalId_physicalFirstFrame" to
                "3e9e8171b3605a462d9c4464284d5ceeb4730afb6c26f2b165d2104c8f5ac548",
            "audio_intent" to "8da50748a1a16880005445b8a8302022a580337141dde82e9729e678dd69acf0",
            "index_audio_intent_appendRunId" to
                "026bf7a0e840683a886949674ef89440b6627151a7544c7260c9ed453fcc078b",
            "bootstrap" to "15ad8469be42cadc23cbf49b9da5cb544bbfb1b1ce18ff01f36caa09034534e2",
            "index_bootstrap_aliasHash" to
                "770b24aa1f4049106284280e7e0877ab733a98ce2738edfc507521d14cb5fc2d",
            "manifest" to "b7e32373b280a787531c1fbaa6a59249aa5f2c4587e3248a13d7a2a3a74a1944",
            "index_manifest_runId_generation_digest" to
                "e721a0d60ee5a0f252d0ea6ccc6a0dd1c13a0361dab40cc82a586d1f8956d67c",
            "microfile" to "eb2c9ec5bddcea43cee36bab2ea69f3aea19006fcf82361e52eeecd081b53185",
            "index_microfile_runId_unitIndex" to
                "714caa50680e1cc3259081f91413bfe53825e7958146eb336452441f8e81e8cb",
            "index_microfile_runId_generation_manifestDigest" to
                "5ced8e63304b1919e03a977ccab393022d0b0f2b55166464d653930fa7e753c5",
            "finalization_source" to
                "54097d20a4ef4eb572fa2d817471ca696a2cda363ab800ccff3bc806616dae9e",
            "index_finalization_source_runId_generation_manifestDigest" to
                "a21a2ce0f5e0931e81e1396c3cfb27da1d0781c1e9cced1cc3687fe70ae0bf73",
            "index_finalization_source_runId" to
                "19ff93f3c8b2f268a3da5df13b188768bde59e076f52757537baffec5d25de82",
            "quarantine_intent" to
                "3de26d11dca53815df310929c5ca8010c605899bee9eaa1f8d53cf651ed464f2",
            "index_quarantine_intent_runId_sourceName_sourceDigest" to
                "d921e4d68efe89abe33e94f6b04ddad5fefd185408df358e534e5b3bce003ed2",
            "index_quarantine_intent_runId_destinationName" to
                "056de7e496639ba00b652e68e2535c77ebbc806635640fc04b2a7285445badaf",
            "index_quarantine_intent_bootstrapRunId" to
                "a152514cf88c56002d2ec6febbe78252171a330ffa7ed26ccd94e67787a3a7c2",
            "deletion_tombstone" to
                "451cc17e7bc2b0f38e0082acfaaa5545dfae326e9a48ecebe72f638f41f1fdda",
            "deletion_target" to "5b124ab50d2130604732fd6ce47cf565784447cf2520b0c5eba8db31132fbd7b",
            "index_deletion_target_runId" to
                "cc6c99265dd195214debacbd05c592e55b37a98697b1b09eb289295a70db4abb",
        )
}
