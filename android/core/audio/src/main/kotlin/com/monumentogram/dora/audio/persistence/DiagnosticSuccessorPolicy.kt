@file:Suppress("MagicNumber", "LongMethod")

package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/** Versioned exact47+1 extension; the V1 contract remains unchanged. */
internal object DiagnosticSuccessorPolicy {
    fun decode(data: JSONObject): DiagnosticSourcePolicy {
        listOf("predecessorPolicySha256", "custodyMappingSha256").forEach {
            check(data.getString(it).matches(Regex("[a-f0-9]{64}")))
        }
        val owner = uuid(data.getString("ownerId"))
        val vault = uuid(data.getString("vaultId"))
        val old = sources(data.getJSONArray("predecessorSources"), 47)
        val added = source(data.getJSONObject("additionalSource"))
        val all = sources(data.getJSONArray("protectedSources"), 48)
        check(added !in old && all == old + added)
        val previous = strings(data.getJSONArray("predecessorIdentifiers"))
        val identifiers = strings(data.getJSONArray("historicalIdentifiers"))
        val oldComponents = old.flatMap(::components).toSet()
        check(previous.containsAll(oldComponents) && identifiers.containsAll(previous))
        val forbidden = previous + oldComponents + owner + vault
        val sourceComponents = components(added)
        check(sourceComponents.toSet().size == 3 && sourceComponents.none { it in forbidden })
        val runs = data.getJSONArray("additionalRunBindings")
        check(runs.length() == 394)
        val claims =
            (0 until runs.length()).map { index ->
                decodeClaim(runs.getJSONObject(index), index, added.assetId.value, vault)
            }
        val runIds = claims.map { it.runId }.toSet()
        val physicalIds = claims.map { it.physicalId }.toSet()
        check(
            runIds.size == 394 && physicalIds.size == 4 && runIds.intersect(physicalIds).isEmpty()
        )
        val newIds = sourceComponents.toSet() + runIds + physicalIds
        check(
            newIds.size == 401 &&
                newIds.intersect(forbidden).isEmpty() &&
                identifiers == previous + newIds
        )
        verifyPhysicalOrder(claims)
        val physical = data.getJSONArray("additionalPhysicalSources")
        check(physical.length() == 4)
        val physicalRows =
            (0 until physical.length())
                .map {
                    val row = physical.getJSONObject(it)
                    Triple(
                        row.getString("assetId"),
                        uuid(row.getString("physicalId")),
                        row.getLong("physicalFirstFrame"),
                    )
                }
                .toSet()
        check(
            physicalRows.size == 4 &&
                physicalRows ==
                    claims
                        .map {
                            Triple(it.assetId, it.physicalId, it.physicalFirstFrame)
                        }
                        .toSet()
        )
        return DiagnosticSourcePolicy.protected(all, identifiers)
            .boundTo(owner, vault)
            .withSuccessorClaims(claims)
    }

    private fun decodeClaim(
        row: JSONObject,
        index: Int,
        asset: String,
        vault: String,
    ): DiagnosticProtectedClaim {
        val run = uuid(row.getString("runId"))
        val physical = uuid(row.getString("physicalId"))
        check(row.getString("assetId") == asset)
        val claim =
            DiagnosticProtectedClaim(
                run,
                asset,
                physical,
                row.getInt("ordinal"),
                row.getLong("firstFrame"),
                row.getLong("frames"),
                row.getLong("physicalFirstFrame"),
                row.getLong("sourceFrameOffset"),
            )
        check(
            claim.ordinal == index && claim.firstFrame == index * 80_000L && claim.frames == 80_000L
        )
        check(
            claim.physicalFirstFrame >= 0 &&
                claim.sourceFrameOffset >= 0 &&
                Math.addExact(claim.physicalFirstFrame, claim.sourceFrameOffset) == claim.firstFrame
        )
        val keyUri = CanonicalRecoveryAlias.forRun(RunId.fromCanonicalString(run))
        check(
            row.getString("canonicalKeyUri") == keyUri &&
                row.getString("canonicalKeyUriSha256") == sha(keyUri)
        )
        check(
            row.getString("physicalKeystoreAlias") ==
                "dora.vault.run.v1." + sha("DORA/recovery-physical-alias/v1/$vault/$run")
        )
        return claim
    }

    private fun verifyPhysicalOrder(claims: List<DiagnosticProtectedClaim>) {
        val seenPhysical = mutableSetOf<String>()
        var previousPhysical: String? = null
        claims.forEach { claim ->
            if (seenPhysical.add(claim.physicalId)) {
                check(claim.sourceFrameOffset == 0L && claim.physicalFirstFrame == claim.firstFrame)
            } else {
                check(previousPhysical == claim.physicalId)
            }
            previousPhysical = claim.physicalId
        }
    }

    private fun sources(array: JSONArray, count: Int): Set<AudioIdentity> {
        check(array.length() == count)
        return (0 until count)
            .map { source(array.getJSONObject(it)) }
            .toSet()
            .also { check(it.size == count) }
    }

    private fun source(row: JSONObject) =
        AudioIdentity(
            RecordingId(uuid(row.getString("recordingId"))),
            AudioAssetId(uuid(row.getString("assetId"))),
            uuid(row.getString("sessionId")),
        )

    private fun components(identity: AudioIdentity) =
        listOf(identity.recordingId.value, identity.assetId.value, identity.sessionId)

    private fun strings(array: JSONArray): Set<String> {
        check(array.length() in 1..100_000)
        return (0 until array.length())
            .map { uuid(array.getString(it)) }
            .toSet()
            .also { check(it.size == array.length()) }
    }

    private fun uuid(value: String) = RunId.fromCanonicalString(value).toCanonicalString()

    private fun sha(value: String) =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.US_ASCII))
            .joinToString("") { "%02x".format(it) }
}
