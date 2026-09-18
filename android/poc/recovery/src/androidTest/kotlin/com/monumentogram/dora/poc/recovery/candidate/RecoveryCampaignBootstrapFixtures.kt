@file:Suppress(
    "LongMethod",
    "MagicNumber",
    "MaxLineLength",
    "TooGenericExceptionCaught",
    "WildcardImport",
)

package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import com.monumentogram.dora.poc.recovery.bootstrap.*
import com.monumentogram.dora.poc.recovery.contract.*
import com.monumentogram.dora.poc.recovery.controller.AliasObservation
import com.monumentogram.dora.poc.recovery.controller.ConfirmationResult
import com.monumentogram.dora.poc.recovery.controller.KeyConfirmationSnapshot
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController
import com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryQuarantineJournal
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryRunBootstrapJournal
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryBootstrapStorage
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import org.json.JSONArray
import org.json.JSONObject

/** Actual platform bootstrap returns are held for an external host signal; no simulated kill. */
internal object RecoveryCampaignBootstrapFixtures {
    fun execute(
        context: Context,
        value: KeyConfirmationValue,
        caseId: String,
        observer: (String) -> Unit,
    ): BootstrapResult {
        require(caseId in (1..6).map { "KCB-%02d".format(it) })
        val actualCrypto = AndroidRecoveryBootstrapCrypto()
        val actualStorage = AndroidOsRecoveryBootstrapStorage(context)
        val actualJournal = AndroidRecoveryRunBootstrapJournal(context)
        var observed = false
        fun at(expected: String) {
            if (caseId == expected) {
                check(!observed)
                observed = true
                observer(caseId)
                error("External kill barrier returned without process death")
            }
        }
        val crypto =
            object : RecoveryBootstrapCrypto by actualCrypto {
                override fun createNewAlias(runId: RunId): BootstrapAliasCreation {
                    val result = actualCrypto.createNewAlias(runId)
                    if (result is BootstrapAliasCreation.Created) at("KCB-01")
                    return result
                }
            }
        val storage =
            object : RecoveryBootstrapStorage by actualStorage {
                override fun write(
                    handle: BootstrapWriteHandle,
                    bytes: ByteArray,
                    offset: Int,
                    count: Int,
                ): Int {
                    val written = actualStorage.write(handle, bytes, offset, count)
                    if (written == count) at("KCB-02")
                    return written
                }

                override fun fsyncTemp(handle: BootstrapWriteHandle) {
                    actualStorage.fsyncTemp(handle)
                    at("KCB-03")
                }

                override fun renameTempToFinal(runId: RunId) {
                    actualStorage.renameTempToFinal(runId)
                    at("KCB-04")
                }

                override fun fsyncConfirmationParent(runId: RunId) {
                    actualStorage.fsyncConfirmationParent(runId)
                    at("KCB-05")
                }
            }
        val journal =
            object : RecoveryRunBootstrapJournal {
                override fun beginNonExclusive(): RecoveryRunBootstrapTransaction {
                    val transaction = actualJournal.beginNonExclusive()
                    return object : RecoveryRunBootstrapTransaction by transaction {
                        private var marked = false

                        override fun markSuccessful() {
                            transaction.markSuccessful()
                            marked = true
                        }

                        override fun end() {
                            transaction.end()
                            if (marked) at("KCB-06")
                        }
                    }
                }
            }
        val result =
            RecoveryKeyBootstrapController(
                    crypto,
                    storage,
                    journal,
                    BootstrapEvidenceSink {
                        error("KCB external kill fixture reached forbidden KC13 event")
                    },
                )
                .bootstrap(value)
        // Returning a failed bootstrap preserves diagnostics but cannot be reported as a kill.
        check(result !is BootstrapResult.Committed) { "KCB fixture unexpectedly completed" }
        return result
    }

    /**
     * Reload real SQLite and files, authenticate orphans and use the existing durable quarantine.
     * The caller retains external kill evidence and compares this observation with the case oracle.
     */
    // Every observed bootstrap state has an explicit retained disposition.
    @Suppress("CyclomaticComplexMethod")
    fun recover(context: Context, value: KeyConfirmationValue): JSONObject {
        val runId = value.runId
        val crypto = AndroidRecoveryBootstrapCrypto()
        val aliasBefore = crypto.aliasExists(runId)
        val database = AndroidRecoveryJournalDatabase.writable(context)
        val initialRows =
            database
                .rawQuery(
                    "SELECT count(*) FROM recovery_run_bootstrap_v1 WHERE run_id=?",
                    arrayOf(runId.toCanonicalString()),
                )
                .use { cursor ->
                    check(cursor.moveToFirst())
                    cursor.getInt(0)
                }
        check(initialRows in 0..1)
        val namespaces = AndroidOsRecoveryBootstrapStorage(context).inspectNamespaces(runId)
        val source = AndroidRecoveryReconciliationSource(context)
        // This source's default expected candidate is microfile; the actual stored row is
        // untouched.
        // Alias-only bootstrap has no run directory yet; do not manufacture a directory just to
        // read it.
        val snapshot =
            if (initialRows == 0 && !namespaces.temporary.occupied && !namespaces.final.occupied)
                KeyConfirmationSnapshot(
                    value,
                    null,
                    null,
                    false,
                    if (aliasBefore) AliasObservation.PRESENT else AliasObservation.ABSENT,
                )
            else source.loadConfirmation(runId).copy(expected = value)
        val confirmation = RecoveryKeyConfirmationController().evaluate(snapshot)
        val facts =
            JSONObject()
                .put("recipe", "KCB_RESTART")
                .put("aliasPresentBefore", aliasBefore)
                .put("temporaryPresentBefore", snapshot.temporaryPresent)
                .put("finalPresentBefore", snapshot.finalArtifact != null)
                .put("durableRunRowPresent", snapshot.durableRow != null)
                .put("publicationStarted", false)
                .put("acceptedEnd", 0)
                .put("committedEnd", 0)
                .put("recoveredEnd", 0)
        val storage = AndroidOsRecoveryReconciliationStorage(context)
        val journal = AndroidRecoveryQuarantineJournal(context)
        val quarantine =
            RecoveryQuarantineController(storage, journal, RecoveryQuarantineEvidenceSink {})
        val receipts = JSONArray()
        fun retain(result: QuarantineResult) {
            when (result) {
                is QuarantineResult.Completed ->
                    receipts.put(
                        JSONObject()
                            .put("intentId", result.row.intentId.toLowercaseHex())
                            .put("completed", true)
                    )
                else ->
                    error(
                        "Bootstrap quarantine did not return a completed durable receipt: ${result.javaClass.simpleName}"
                    )
            }
        }
        // Restart pending/existing transactions first so repeated recovery does not allocate
        // another intent.
        // The existing loadAll helper is microfile-specific; use its exact-id reader for both
        // candidates.
        val persisted =
            database
                .rawQuery(
                    "SELECT hex(intent_id) FROM recovery_quarantine_intent_v4 WHERE run_id=? AND candidate_id=? ORDER BY intent_id",
                    arrayOf(runId.toCanonicalString(), value.candidate.contractId),
                )
                .use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) add(
                            requireNotNull(
                                journal.load(
                                    Sha256Value.fromLowercaseHex(cursor.getString(0).lowercase())
                                )
                            )
                        )
                    }
                }
        val persistedNames = persisted.map { it.input.sourceRelativeName }.toSet()
        for (row in persisted) {
            require(
                row.input.candidate == value.candidate &&
                    row.input.artifactRole == RecoveryQuarantineArtifactRole.KEY_CONFIRMATION
            )
            retain(
                quarantine.quarantine(row.input, row.recordedObservedState, row.bootstrapBinding)
            )
        }
        when (confirmation) {
            is ConfirmationResult.Validated -> {
                requireNotNull(snapshot.durableRow)
                facts
                    .put("classification", "VALIDATED")
                    .put("confirmationAuthenticated", true)
                    .put(
                        "durableBootstrapIdentity",
                        snapshot.durableRow.ciphertextSha256.toLowercaseHex(),
                    )
                check(!snapshot.temporaryPresent && persisted.isEmpty())
            }
            is ConfirmationResult.Rejected -> {
                facts
                    .put("classification", confirmation.classification.name)
                    .put("phase", confirmation.phase.name)
                require(
                    confirmation.classification ==
                        KeyRecoveryClassification.INCOMPLETE_KEY_BOOTSTRAP &&
                        snapshot.durableRow == null
                )
                val final = snapshot.finalArtifact
                if (final != null && final.relativeName !in persistedNames) {
                    val bytes = final.ciphertextSnapshot()
                    val authenticated =
                        RecoveryRunAeadProvider()
                            .openExisting(runId)
                            .decryptKeyConfirmation(bytes, value)
                    check(authenticated == KeyConfirmationDecryption.Success(value))
                    facts.put("orphanConfirmationAuthenticated", true)
                    retain(
                        quarantine.quarantine(
                            RecoveryQuarantineIntentInput(
                                value.candidate,
                                runId,
                                final.relativeName,
                                RecoveryQuarantineArtifactRole.KEY_CONFIRMATION,
                                bytes.size.toULong(),
                                Sha256Value.calculate(bytes),
                            ),
                            RecoveryQuarantineObservedState.FINAL_ORPHAN,
                            QuarantineBootstrapBinding.ABSENT,
                        )
                    )
                }
                val temp =
                    if (snapshot.temporaryPresent)
                        storage.loadActiveArtifact(runId, "key-confirmation/run.kc.tmp", 4096)
                    else null
                if (temp != null) {
                    val bytes = temp.snapshot()
                    retain(
                        quarantine.quarantine(
                            RecoveryQuarantineIntentInput(
                                value.candidate,
                                runId,
                                temp.relativeName,
                                RecoveryQuarantineArtifactRole.KEY_CONFIRMATION,
                                bytes.size.toULong(),
                                Sha256Value.calculate(bytes),
                            ),
                            if (final != null) RecoveryQuarantineObservedState.TEMP_AND_FINAL
                            else RecoveryQuarantineObservedState.TEMP_ONLY,
                            QuarantineBootstrapBinding.ABSENT,
                        )
                    )
                }
                facts.put("temporaryPromoted", false)
            }
            is ConfirmationResult.Absent -> facts.put("classification", "ABSENT")
            is ConfirmationResult.Unclassified ->
                error("Bootstrap recovery diagnostic: ${confirmation.diagnostic}")
        }
        val aliasAfter = crypto.aliasExists(runId)
        check(aliasAfter == aliasBefore)
        database
            .rawQuery(
                "SELECT count(*) FROM recovery_run_bootstrap_v1 WHERE run_id=?",
                arrayOf(runId.toCanonicalString()),
            )
            .use { cursor ->
                check(cursor.moveToFirst())
                val rows = cursor.getInt(0)
                check(rows == if (snapshot.durableRow == null) 0 else 1)
                facts.put("runRowCountAfter", rows)
            }
        facts
            .put("aliasPresentAfter", aliasAfter)
            .put("recoveryGeneratedOrReplacedAlias", false)
            .put("quarantineReceipts", receipts)
        return facts
    }
}
