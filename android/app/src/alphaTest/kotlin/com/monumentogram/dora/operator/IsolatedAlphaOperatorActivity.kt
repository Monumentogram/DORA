package com.monumentogram.dora.operator

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.ProductAudioSession
import java.util.concurrent.Executors

/** Alpha-only launcher. Intent extras never select a user, recording, path, or authority. */
class IsolatedAlphaOperatorActivity : Activity() {
    private val runtime
        get() = (application as DoraApplication).audioRuntime

    private val worker = Executors.newSingleThreadExecutor()
    private val operator by lazy {
        IsolatedAlphaOperator({ androidOperatorEnvironment(this) }) {
            (runtime.availability as? AudioAvailability.Available)?.session
        }
    }
    private lateinit var user: EditText
    private lateinit var topology: EditText
    private lateinit var recording: EditText
    private lateinit var status: TextView
    private lateinit var read: Button
    private lateinit var discover: Button
    private lateinit var next: Button
    private lateinit var delete: Button
    private var attempt = 0L
    private var nextCursor: String? = null

    @Suppress("LongMethod") // Bind the small native operator form and its controls in one place.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun copy(text: String) =
            TextView(this).apply {
                this.text = text
                content.addView(this)
            }
        fun field(hint: String) =
            EditText(this).apply {
                this.hint = hint
                isSingleLine = true
                setSaveEnabled(false)
                content.addView(this)
            }
        copy("Isolated Alpha operator — local metadata only")
        copy(
            "Run the host preflight first. Verify full SECONDARY " +
                "(not owner, guest, demo, restricted or any profile). Enter the host receipt below. " +
                "This entry records human verification; Android public APIs cannot independently " +
                "prove full-secondary topology on API 28–32."
        )
        user = field("Explicit secondary userId")
        topology = field("FULL_SECONDARY_USER:<userId>:<serial>")
        recording = field("Exact new test RecordingId")
        status = copy("Locked. No source selected.")
        read =
            Button(this).apply {
                text = "Authenticate and read full recording"
                setOnClickListener { readback() }
                content.addView(this)
            }
        discover =
            Button(this).apply {
                text = "Authenticate and list test-local RecordingIds"
                setOnClickListener { listRecordings("") }
                content.addView(this)
            }
        next =
            Button(this).apply {
                text = "Authenticate and list next page"
                isEnabled = false
                setOnClickListener { nextCursor?.let(::listRecordings) }
                content.addView(this)
            }
        delete =
            Button(this).apply {
                text = "Delete this verified recording…"
                isEnabled = false
                setOnClickListener { deleteVerified() }
                content.addView(this)
            }
        Button(this).apply {
            text = "Cancel / lock"
            setOnClickListener {
                attempt++
                nextCursor = null
                operator.cancel()
                runtime.lock()
                busy(false)
                status.text = "Locked / cancelled"
            }
            content.addView(this)
        }
        val changed =
            object : TextWatcher {
                override fun beforeTextChanged(
                    value: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) = Unit

                override fun onTextChanged(
                    value: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {
                    operator.cancel()
                    nextCursor = null
                    next.isEnabled = false
                    delete.isEnabled = false
                    status.text = "Identity changed. Verification must be repeated."
                }

                override fun afterTextChanged(value: Editable?) = Unit
            }
        listOf(user, topology, recording).forEach { it.addTextChangedListener(changed) }
        setContentView(
            ScrollView(this).apply {
                fitsSystemWindows = true
                addView(content)
            }
        )
    }

    private fun listRecordings(after: String) {
        val request = ++attempt
        nextCursor = null
        if (!operator.admit(user.text.toString(), topology.text.toString())) {
            status.text = "STOP / ISOLATION_IDENTITY_REJECTED (owner user 0 is forbidden)"
            return
        }
        busy(true)
        runtime.requestRecordingRecovery(this, after) { result ->
            if (!active(request)) return@requestRecordingRecovery
            val session = (runtime.availability as? AudioAvailability.Available)?.session
            if (result is AudioResult.Value && session != null && current(session)) {
                // Existing authenticated runtime caps each page at 20 and owns the cursor.
                val page = result.value
                nextCursor = page.lastOrNull()?.cursor
                status.text =
                    "TEST_LOCAL_RECORDING_IDS (maximum 20; listing grants no deletion)\n" +
                        page.joinToString("\n") {
                            "${it.identity?.recordingId?.value ?: "UNAVAILABLE"} frames=${it.recoveredFrames}"
                        }
            } else status.text = "STOP / AUTHENTICATED_TEST_LOCAL_LIST_UNAVAILABLE"
            busy(false)
        }
    }

    private fun readback() {
        val request = ++attempt
        delete.isEnabled = false
        if (!operator.admit(user.text.toString(), topology.text.toString())) {
            status.text = "STOP / ISOLATION_IDENTITY_REJECTED (owner user 0 is forbidden)"
            return
        }
        val exact = recording.text.toString()
        busy(true)
        runtime.requestOpen(this, AudioOpenMode.OPEN_EXISTING) { availability ->
            if (request != attempt || isDestroyed || isFinishing) return@requestOpen
            if (availability !is AudioAvailability.Available) {
                busy(false)
                status.text = "STOP / AUTHENTICATION_OR_EXISTING_VAULT_UNAVAILABLE"
            } else
                worker.execute {
                    val result = operator.readback(exact)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && request == attempt) {
                            busy(false)
                            if (result is AudioResult.Value && current(availability.session)) {
                                status.text =
                                    "READBACK_OK frames=${result.value.frames} units=${result.value.units} " +
                                        "durable=FINALIZED"
                                delete.isEnabled = true
                            } else status.text = "STOP / READBACK_FAILED_OR_REVOKED"
                        }
                    }
                }
        }
    }

    private fun deleteVerified() {
        val request = ++attempt
        val exact = recording.text.toString()
        val session = (runtime.availability as? AudioAvailability.Available)?.session ?: return
        busy(true)
        worker.execute {
            val selected = operator.consumeDeletion(exact)
            runOnUiThread {
                if (request != attempt) return@runOnUiThread
                if (selected !is AudioResult.Value || !current(session) || !active(request)) {
                    busy(false)
                    status.text = "STOP / DELETION_IDENTITY_OR_PROOF_REJECTED"
                } else
                    runtime.requestAudioDeletion(this, session, selected.value) { result ->
                        if (result is AudioResult.Failed) {
                            busy(false)
                            status.text = "STOP / DELETION_CANCELLED_OR_INCOMPLETE"
                        } else
                            worker.execute {
                                val checked = session.inspectAudioDeletion(selected.value)
                                runOnUiThread {
                                    if (!isFinishing && !isDestroyed && request == attempt) {
                                        busy(false)
                                        status.text =
                                            if (
                                                checked is AudioResult.Value &&
                                                    checked.value.complete &&
                                                    current(session)
                                            )
                                                "DELETION_OK tombstone=USER_DELETED " +
                                                    "audioArtifacts=ABSENT keyReferences=ABSENT"
                                            else "STOP / DELETION_NOT_VERIFIED"
                                    }
                                }
                            }
                    }
            }
        }
    }

    private fun current(session: ProductAudioSession) =
        (runtime.availability as? AudioAvailability.Available)?.session === session

    private fun active(request: Long) = !isFinishing && !isDestroyed && request == attempt

    private fun busy(value: Boolean) {
        user.isEnabled = !value
        topology.isEnabled = !value
        recording.isEnabled = !value
        read.isEnabled = !value
        discover.isEnabled = !value
        next.isEnabled = !value && nextCursor != null
        delete.isEnabled = false
    }

    override fun onPause() {
        operator.cancel()
        nextCursor = null
        next.isEnabled = false
        delete.isEnabled = false
        status.text = "Locked. Verification must be repeated."
        super.onPause()
    }

    override fun onDestroy() {
        operator.cancel()
        worker.shutdown()
        super.onDestroy()
    }
}
