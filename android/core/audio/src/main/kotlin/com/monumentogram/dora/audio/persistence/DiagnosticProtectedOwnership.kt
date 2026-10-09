package com.monumentogram.dora.audio.persistence

import androidx.sqlite.db.SupportSQLiteDatabase
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RunId

/** Exact successor claims are checked before Room callbacks and by the isolated reader. */
internal object DiagnosticProtectedOwnership {
    fun verify(db: SupportSQLiteDatabase, policy: DiagnosticSourcePolicy) {
        val claims = policy.successorClaims()
        if (claims.isEmpty()) return
        val asset = claims.first().assetId
        verifyClaims(db, asset, claims)
        claims.forEach { verifyBootstrap(db, it.runId) }
        verifyPhysicalSources(db, asset, claims)
        verifyPreservedState(db, asset)
    }

    private fun verifyClaims(
        db: SupportSQLiteDatabase,
        asset: String,
        claims: List<DiagnosticProtectedClaim>,
    ) {
        val actual =
            db.query(
                    "SELECT runId,assetId,physicalId,ordinal,firstFrame,frames," +
                        "physicalFirstFrame,sourceFrameOffset FROM unit_claim " +
                        "WHERE assetId=? AND committed=1 ORDER BY ordinal",
                    arrayOf(asset),
                )
                .use { row ->
                    buildList {
                        while (row.moveToNext()) add(
                            DiagnosticProtectedClaim(
                                row.getString(row.getColumnIndexOrThrow("runId")),
                                row.getString(row.getColumnIndexOrThrow("assetId")),
                                row.getString(row.getColumnIndexOrThrow("physicalId")),
                                row.getInt(row.getColumnIndexOrThrow("ordinal")),
                                row.getLong(row.getColumnIndexOrThrow("firstFrame")),
                                row.getLong(row.getColumnIndexOrThrow("frames")),
                                row.getLong(row.getColumnIndexOrThrow("physicalFirstFrame")),
                                row.getLong(row.getColumnIndexOrThrow("sourceFrameOffset")),
                            )
                        )
                    }
                }
        check(actual == claims)
        db.query("SELECT count(*) FROM unit_claim WHERE assetId=?", arrayOf(asset)).use {
            check(it.moveToFirst() && it.getInt(0) == claims.size)
        }
    }

    private fun verifyBootstrap(db: SupportSQLiteDatabase, runId: String) {
        db.query(
                "SELECT candidateId,aliasHash,state FROM bootstrap WHERE runId=?",
                arrayOf(runId),
            )
            .use { row ->
                check(
                    row.moveToFirst() &&
                        row.getString(0) == RecoveryCandidate.MICROFILE.contractId &&
                        row.getString(1) ==
                            CanonicalRecoveryAlias.sha256(RunId.fromCanonicalString(runId))
                                .toLowercaseHex() &&
                        row.getString(2) == "VALID" &&
                        !row.moveToNext()
                )
            }
    }

    private fun verifyPhysicalSources(
        db: SupportSQLiteDatabase,
        asset: String,
        claims: List<DiagnosticProtectedClaim>,
    ) {
        val physical =
            db.query(
                    "SELECT assetId,physicalId,physicalFirstFrame FROM physical_source WHERE assetId=?",
                    arrayOf(asset),
                )
                .use { row ->
                    buildSet {
                        while (row.moveToNext()) add(
                            Triple(row.getString(0), row.getString(1), row.getLong(2))
                        )
                    }
                }
        check(
            physical ==
                claims.map { Triple(it.assetId, it.physicalId, it.physicalFirstFrame) }.toSet()
        )
        val physicalIds = physical.map { it.second }.toSet()
        db.query("SELECT assetId,physicalId FROM physical_source").use { row ->
            while (row.moveToNext()) if (row.getString(1) in physicalIds)
                check(row.getString(0) == asset)
        }
    }

    private fun verifyPreservedState(db: SupportSQLiteDatabase, asset: String) {
        db.query("SELECT finalized FROM audio_asset WHERE assetId=?", arrayOf(asset)).use {
            check(it.moveToFirst() && it.getInt(0) == 0 && !it.moveToNext())
        }
        listOf(
                "deletion_tombstone",
                "deletion_target",
                "finalization_source",
                "original_audio_reference",
            )
            .forEach { table ->
                db.query("SELECT count(*) FROM $table WHERE assetId=?", arrayOf(asset)).use {
                    check(it.moveToFirst() && it.getInt(0) == 0)
                }
            }
    }
}

internal data class DiagnosticProtectedClaim(
    val runId: String,
    val assetId: String,
    val physicalId: String,
    val ordinal: Int,
    val firstFrame: Long,
    val frames: Long,
    val physicalFirstFrame: Long,
    val sourceFrameOffset: Long,
)
