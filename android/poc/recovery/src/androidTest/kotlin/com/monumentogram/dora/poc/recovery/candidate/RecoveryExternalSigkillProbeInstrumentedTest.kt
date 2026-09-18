package com.monumentogram.dora.poc.recovery.candidate

import android.app.Application
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** A bounded, read-only target-process rendezvous for a host-issued external SIGKILL. */
@RunWith(AndroidJUnit4::class)
class RecoveryExternalSigkillProbeInstrumentedTest {
    @Test
    fun awaitExternalSignal() {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        val nonce =
            RecoveryExternalSigkillProbeGate.requireAccepted(
                arguments.getString("recoveryPhysicalSigkillProbe"),
                arguments.getString("recoveryDeviceProfile"),
                arguments.getString("recoveryProbeNonce"),
            )
        RecoveryPreflightInstrumentationIdentity.requireAccepted(arguments)

        val targetContext = instrument.targetContext
        val packageName = targetContext.packageName
        val processName = Application.getProcessName()
        val cmdline =
            File("/proc/self/cmdline")
                .readBytes()
                .takeWhile { it != 0.toByte() }
                .toByteArray()
                .toString(Charsets.UTF_8)
        val selinuxContext =
            RecoveryExternalSigkillProbeGate.normalizeSelinuxContext(
                File("/proc/self/attr/current").readText()
            )
        val pid = Process.myPid()
        val uid = Process.myUid()
        require(processName == packageName && cmdline == processName) {
            "Probe must run in the target app process"
        }
        require(uid == targetContext.applicationInfo.uid && selinuxContext.isNotBlank()) {
            "Target process identity proof is incomplete"
        }

        val identity =
            JSONObject()
                .put("nonce", nonce)
                .put("pid", pid)
                .put("uid", uid)
                .put("cmdline", cmdline)
                .put("selinuxContext", selinuxContext)
                .put("packageName", packageName)
                .put("processName", processName)
        val deadline = SystemClock.elapsedRealtime() + MAX_WAIT_MS
        emitStatus("PHYSICAL_SIGKILL_PROBE_READY", identity)
        while (true) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            SystemClock.sleep(minOf(remaining, POLL_MS))
        }
        emitStatus("PHYSICAL_SIGKILL_PROBE_TIMEOUT", identity)
        throw AssertionError("External SIGKILL was not observed before probe timeout")
    }

    private fun emitStatus(marker: String, identity: JSONObject) {
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                0,
                Bundle().apply { putString("stream", "$marker $identity\n") },
            )
    }

    private companion object {
        const val MAX_WAIT_MS = 30_000L
        const val POLL_MS = 250L
    }
}
