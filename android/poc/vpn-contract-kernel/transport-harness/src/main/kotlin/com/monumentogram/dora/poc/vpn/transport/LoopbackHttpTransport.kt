package com.monumentogram.dora.poc.vpn.transport

import com.monumentogram.dora.poc.vpn.contract.ClientState
import com.monumentogram.dora.poc.vpn.contract.ContractCatalog
import com.monumentogram.dora.poc.vpn.contract.ContractOracle
import com.monumentogram.dora.poc.vpn.contract.Sha256Hex
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal const val LOOPBACK_HOST = "127.0.0.1"
internal const val MAX_HTTP_BYTES = 16 * 1024
internal const val MAX_ATTEMPTS = 3
internal const val MAX_RETRY_ELAPSED_MILLIS = 3_000L
internal const val MAX_RETRY_AFTER_MILLIS = 2_000L
internal const val LOCAL_BACKOFF_BASE_MILLIS = 100L
private const val HTTP_SUCCESS_MIN_STATUS = 200
private const val HTTP_SUCCESS_MAX_STATUS = 299
private val HTTP_SUCCESS_STATUS_RANGE = HTTP_SUCCESS_MIN_STATUS..HTTP_SUCCESS_MAX_STATUS
private const val MAX_FAULT_DIRECTIVES = 64
private const val REQUEST_ID_SEQUENCE_WIDTH = 6
internal val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(2)
internal val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(2)
@Suppress("MagicNumber") internal val SCENARIO_DEADLINE: Duration = Duration.ofSeconds(15)
internal val BOUNDED_CREDENTIAL_HEADER_DENYLIST =
    setOf(
        "authorization",
        "proxy-authorization",
        "cookie",
        "cookie2",
        "x-api-key",
        "api-key",
        "x-auth-token",
        "x-access-token",
    )
internal val SINGLETON_PROTOCOL_HEADERS =
    setOf(
        "content-type",
        "x-synthetic-tenant-id",
        "x-profile-binding-sha256",
        "idempotency-key",
        "x-content-sha256",
        "x-client-request-id",
        "idempotency-replayed",
    )

internal data class HarnessRequest(
    val uri: URI,
    val method: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
    val operationClass: String,
)

internal data class HarnessResponse(
    val status: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray = ByteArray(0),
)

internal enum class FaultAction {
    DROP_BEFORE_COMMIT,
    DROP_AFTER_COMMIT,
    RETURN_429,
    RETURN_429_WITHOUT_RETRY_AFTER,
    RETURN_429_MALFORMED_RETRY_AFTER,
    RETURN_429_OUT_OF_BUDGET_RETRY_AFTER,
    RETURN_503,
    RETURN_UPLOAD_URL_EXPIRED,
    TIMEOUT_BEFORE_COMMIT,
    EXTERNAL_REDIRECT,
    OVERSIZED_RESPONSE,
}

internal data class FaultDirective(
    val faultId: String,
    val operationClass: String,
    val action: FaultAction,
)

internal data class FaultLedgerEntry(
    val faultId: String,
    val operationClass: String,
    val action: FaultAction,
    val ordinal: Int,
)

internal class FrozenFaultQueue(directives: List<FaultDirective> = emptyList()) {
    private val queue = ArrayDeque(directives)
    private val ledger = mutableListOf<FaultLedgerEntry>()

    @Synchronized
    fun peek(operationClass: String): FaultDirective? {
        val next = queue.firstOrNull() ?: return null
        return next.takeIf { it.operationClass == operationClass }
    }

    @Synchronized
    fun consume(expected: FaultDirective) {
        check(queue.firstOrNull() == expected) { "Frozen fault order changed" }
        queue.removeFirst()
        ledger +=
            FaultLedgerEntry(
                expected.faultId,
                expected.operationClass,
                expected.action,
                ledger.size + 1,
            )
    }

    @Synchronized fun remaining(): List<FaultDirective> = queue.toList()

    @Synchronized fun contentFreeLedger(): List<FaultLedgerEntry> = ledger.toList()
}

internal enum class ClientFaultAction {
    CONNECT_FAILURE_BEFORE_SEND,
    SIMULATED_ROUTE_WAIT_BEFORE_SEND,
}

internal data class ClientFaultDirective(
    val faultId: String,
    val operationClass: String,
    val action: ClientFaultAction,
)

internal data class ClientFaultLedgerEntry(
    val faultId: String,
    val operationClass: String,
    val action: ClientFaultAction,
    val ordinal: Int,
)

internal class FrozenClientFaultQueue(directives: List<ClientFaultDirective> = emptyList()) {
    private val queue = ArrayDeque(directives)
    private val ledger = mutableListOf<ClientFaultLedgerEntry>()

    init {
        require(directives.size <= MAX_FAULT_DIRECTIVES) { "Client fault queue exceeded bound" }
        directives.forEach { directive ->
            require(directive.faultId.matches(Regex("^[A-Z0-9-]+$"))) {
                "Client fault IDs must be deterministic and content-free"
            }
            require(directive.operationClass in ContractCatalog.operationsByClass) {
                "Client fault operation must exist in the frozen contract"
            }
            val allowed =
                when (directive.action) {
                    ClientFaultAction.CONNECT_FAILURE_BEFORE_SEND ->
                        directive.faultId == "VPN-FLT-005" &&
                            directive.operationClass == "INIT_OR_REFRESH_UPLOAD"
                    ClientFaultAction.SIMULATED_ROUTE_WAIT_BEFORE_SEND ->
                        directive.faultId in
                            setOf("VPN-FLT-022", "VPN-FLT-023", "VPN-FLT-024", "VPN-FLT-025")
                }
            require(allowed) { "Client fault directive exceeded the I3 contract subset" }
        }
    }

    @Synchronized
    fun injectIfDue(operationClass: String) {
        val next = queue.firstOrNull() ?: return
        if (next.operationClass != operationClass) return
        queue.removeFirst()
        ledger +=
            ClientFaultLedgerEntry(next.faultId, next.operationClass, next.action, ledger.size + 1)
        throw SyntheticClientNetworkFailure(next.action)
    }

    @Synchronized fun remaining(): List<ClientFaultDirective> = queue.toList()

    @Synchronized fun contentFreeLedger(): List<ClientFaultLedgerEntry> = ledger.toList()
}

internal class SyntheticClientNetworkFailure(val action: ClientFaultAction) :
    IOException("Synthetic client network category")

@Suppress("MagicNumber")
internal class NamedThreadFactory(
    private val prefix: String,
    private val daemon: Boolean,
) : ThreadFactory {
    private val sequence = AtomicInteger()
    private val threads = CopyOnWriteArrayList<Thread>()

    override fun newThread(task: Runnable): Thread =
        Thread(task, "$prefix-${sequence.incrementAndGet()}").also {
            it.isDaemon = daemon
            threads += it
        }

    fun liveNonDaemonThreads(): List<String> =
        threads.filter { it.isAlive && !it.isDaemon }.map { it.name }

    fun awaitNoLiveNonDaemonThreads(timeout: Duration): Boolean {
        val deadline = System.nanoTime() + timeout.toNanos()
        threads
            .filter { !it.isDaemon }
            .forEach { thread ->
                val remainingNanos = deadline - System.nanoTime()
                if (thread.isAlive && remainingNanos > 0) {
                    thread.join(
                        TimeUnit.NANOSECONDS.toMillis(remainingNanos),
                        (remainingNanos % 1_000_000).toInt(),
                    )
                }
            }
        return liveNonDaemonThreads().isEmpty()
    }
}

internal class ExplicitNoProxySelector : ProxySelector() {
    private val selections = AtomicInteger()

    override fun select(uri: URI): List<Proxy> {
        selections.incrementAndGet()
        return listOf(Proxy.NO_PROXY)
    }

    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit

    fun selectionCount(): Int = selections.get()
}

internal class PoisonDefaultProxySelector : ProxySelector() {
    private val calls = AtomicInteger()

    override fun select(uri: URI): List<Proxy> {
        calls.incrementAndGet()
        error("Default ProxySelector must not be consulted")
    }

    override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) {
        calls.incrementAndGet()
        error("Default ProxySelector must not receive failures")
    }

    fun callCount(): Int = calls.get()
}

internal class HarnessRunRequestIdAllocator(namespace: String) {
    private val namespace = namespace.also {
        require(it.matches(Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$"))) {
            "Request ID namespace must be deterministic and content-free"
        }
    }
    private val sequence = AtomicInteger()
    private val emitted = CopyOnWriteArrayList<String>()

    fun next(): String {
        val ordinal = sequence.incrementAndGet()
        val requestId =
            "client-request-$namespace-${ordinal.toString().padStart(REQUEST_ID_SEQUENCE_WIDTH, '0')}"
        check(emitted.none { it == requestId }) { "Harness-run request ID collision" }
        emitted += requestId
        return requestId
    }

    fun requestIds(): List<String> = emitted.toList()
}

@Suppress("MagicNumber", "TooGenericExceptionCaught")
internal class HermeticLoopbackClient(
    val endpoint: URI,
    private val requestIdAllocator: HarnessRunRequestIdAllocator,
    private val noProxySelector: ExplicitNoProxySelector = ExplicitNoProxySelector(),
    private val clientFaults: FrozenClientFaultQueue = FrozenClientFaultQueue(),
) : AutoCloseable {
    init {
        validateEndpoint(endpoint)
    }

    private val threadFactory = NamedThreadFactory("dora-vpn-loopback-client", daemon = false)
    private val executor = Executors.newFixedThreadPool(2, threadFactory)
    private val client =
        try {
            HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .proxy(noProxySelector)
                .executor(executor)
                .version(HttpClient.Version.HTTP_1_1)
                .build()
        } catch (failure: Throwable) {
            executor.shutdownNow()
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                failure.addSuppressed(IllegalStateException("Client constructor cleanup failed"))
            }
            throw failure
        }
    private val sends = AtomicInteger()
    private val emittedRequestIds = mutableListOf<String>()

    @Synchronized
    fun send(request: HarnessRequest): HarnessResponse {
        validateRequest(request)
        val requestId = requestIdAllocator.next()
        require(requestId.matches(Regex("^client-request-[a-z0-9-]+-[0-9]{6,}$"))) {
            "Client request ID must be deterministic and content-free"
        }
        require(requestId !in emittedRequestIds) { "Client request IDs must be unique" }
        val builder = HttpRequest.newBuilder(request.uri).timeout(REQUEST_TIMEOUT)
        request.headers.entries
            .sortedBy { it.key.lowercase(Locale.ROOT) }
            .forEach { (name, value) ->
                builder.header(name, value)
            }
        builder.header("X-Client-Request-Id", requestId)
        val publisher =
            if (request.body.isEmpty()) HttpRequest.BodyPublishers.noBody()
            else HttpRequest.BodyPublishers.ofByteArray(request.body)
        sends.incrementAndGet()
        emittedRequestIds += requestId
        clientFaults.injectIfDue(request.operationClass)
        val response =
            client.send(
                builder.method(request.method, publisher).build(),
                HttpResponse.BodyHandlers.ofInputStream(),
            )
        val responseBytes =
            response.body().use { stream ->
                stream.readNBytes(MAX_HTTP_BYTES + 1).also { bytes ->
                    check(bytes.size <= MAX_HTTP_BYTES) {
                        "Loopback response exceeded byte bound"
                    }
                }
            }
        return HarnessResponse(response.statusCode(), response.headers().map(), responseBytes)
    }

    fun sendCount(): Int = sends.get()

    fun explicitProxySelectionCount(): Int = noProxySelector.selectionCount()

    fun contentFreeClientFaultLedger(): List<ClientFaultLedgerEntry> =
        clientFaults.contentFreeLedger()

    fun remainingClientFaults(): List<ClientFaultDirective> = clientFaults.remaining()

    @Synchronized fun requestIds(): List<String> = emittedRequestIds.toList()

    private fun validateRequest(request: HarnessRequest) {
        validateEndpoint(request.uri)
        require(request.uri.port == endpoint.port) { "Only the bound ephemeral port is allowed" }
        require(request.uri.rawQuery == null) { "Query strings are outside the harness contract" }
        require(request.uri.rawFragment == null) { "Fragments are forbidden" }
        require(request.uri.rawUserInfo == null) { "URI userinfo is forbidden" }
        require(request.body.size <= MAX_HTTP_BYTES) { "Loopback request exceeded byte bound" }
        require(request.method in setOf("GET", "POST", "PUT", "DELETE")) {
            "Unsupported harness method"
        }
        require(request.operationClass in ContractCatalog.operationsByClass) {
            "Unknown frozen operation class"
        }
        val normalizedHeaderNames = request.headers.keys.map { it.lowercase(Locale.ROOT) }
        require(normalizedHeaderNames.distinct().size == normalizedHeaderNames.size) {
            "Duplicate request header names are forbidden"
        }
        require("x-client-request-id" !in normalizedHeaderNames) {
            "The transport owns client request IDs"
        }
        require("idempotency-replayed" !in normalizedHeaderNames) {
            "Replay markers are response-only"
        }
        require(normalizedHeaderNames.none { it in BOUNDED_CREDENTIAL_HEADER_DENYLIST }) {
            "Credential-bearing headers are forbidden"
        }
    }

    private fun validateEndpoint(uri: URI) {
        require(uri.scheme == "http") { "Only cleartext loopback HTTP is allowed" }
        require(uri.host == LOOPBACK_HOST) {
            "Only the exact numeric IPv4 loopback host is allowed"
        }
        require(uri.port in 1..65535) { "An explicit bound port is required" }
        require(uri.rawUserInfo == null) { "URI userinfo is forbidden" }
        require(uri.rawFragment == null) { "URI fragments are forbidden" }
        val expectedAuthority = "$LOOPBACK_HOST:${uri.port}"
        require(uri.rawAuthority == expectedAuthority) { "Loopback authority must be canonical" }
    }

    override fun close() {
        executor.shutdownNow()
        check(executor.awaitTermination(2, TimeUnit.SECONDS)) {
            "Loopback client executor did not terminate"
        }
        check(threadFactory.awaitNoLiveNonDaemonThreads(Duration.ofSeconds(2))) {
            "Loopback client thread leak"
        }
    }
}

internal data class RetryEvent(
    val attempt: Int,
    val category: String,
    val logicalDelayMillis: Long,
    val delaySource: RetryDelaySource,
    val nextAttemptAtMillis: Long,
    val elapsedBudgetRemainingMillis: Long,
)

internal enum class RetryDelaySource {
    RETRY_AFTER,
    LOCAL_POLICY,
    SIMULATED_NETWORK,
}

internal class DeterministicRetryScheduler(restoredElapsedMillis: Long = 0L) {
    private val events = mutableListOf<RetryEvent>()
    private var elapsedMillis = restoredElapsedMillis

    init {
        require(restoredElapsedMillis in 0..MAX_RETRY_ELAPSED_MILLIS) {
            "Restored retry elapsed budget is outside the frozen profile"
        }
    }

    fun schedule(
        attempt: Int,
        category: String,
        logicalDelayMillis: Long,
        delaySource: RetryDelaySource,
    ) {
        require(logicalDelayMillis > 0) { "Retry delay must be positive" }
        require(logicalDelayMillis <= remainingElapsedBudgetMillis()) {
            "Retry delay exceeded elapsed budget"
        }
        elapsedMillis += logicalDelayMillis
        events +=
            RetryEvent(
                attempt,
                category,
                logicalDelayMillis,
                delaySource,
                elapsedMillis,
                remainingElapsedBudgetMillis(),
            )
    }

    fun events(): List<RetryEvent> = events.toList()

    fun elapsedMillis(): Long = elapsedMillis

    fun remainingElapsedBudgetMillis(): Long = MAX_RETRY_ELAPSED_MILLIS - elapsedMillis
}

internal class RetryBudgetExhausted(
    val category: String,
    val attempts: Int,
    val logicalElapsedMillis: Long,
    val terminalState: ClientState,
    val visibleSubstatus: String?,
    val automaticRetryScheduled: Boolean = false,
) : IllegalStateException("Finite retry budget exhausted")

internal enum class RetryTerminalContext {
    ORDINARY,
    DELETE_PENDING,
}

internal data class RetryDelayDecision(
    val delayMillis: Long,
    val source: RetryDelaySource,
)

@Suppress("MagicNumber")
internal class RetryingLoopbackTransport(
    private val client: HermeticLoopbackClient,
    private val scheduler: DeterministicRetryScheduler,
    private val maxAttempts: Int = MAX_ATTEMPTS,
    private val terminalContext: RetryTerminalContext = RetryTerminalContext.ORDINARY,
) {
    init {
        require(maxAttempts == MAX_ATTEMPTS) { "The I2 logical retry profile is frozen" }
    }

    @Suppress("InstanceOfCheckForException", "ThrowsCount")
    fun execute(request: HarnessRequest): HarnessResponse {
        for (attempt in 1..maxAttempts) {
            try {
                val response = client.send(request)
                val category =
                    when {
                        response.status == 429 -> "HTTP_429"
                        response.status in 500..599 -> "HTTP_5XX"
                        else -> return response
                    }
                if (attempt == maxAttempts) throw exhausted(category, attempt)
                val decision =
                    if (response.status == 429) retryAfterDecision(response, attempt)
                    else localDelayDecision(attempt)
                scheduleOrExhaust(attempt, category, decision)
            } catch (failure: SyntheticClientNetworkFailure) {
                val category =
                    when (failure.action) {
                        ClientFaultAction.CONNECT_FAILURE_BEFORE_SEND,
                        ClientFaultAction.SIMULATED_ROUTE_WAIT_BEFORE_SEND -> "WAIT_NETWORK"
                    }
                if (attempt == maxAttempts) throw exhausted(category, attempt)
                scheduleOrExhaust(
                    attempt,
                    category,
                    RetryDelayDecision(
                        localBackoffMillis(attempt),
                        RetryDelaySource.SIMULATED_NETWORK,
                    ),
                )
            } catch (failure: IOException) {
                val category =
                    if (failure is HttpTimeoutException) "REQUEST_TIMEOUT" else "UNKNOWN_COMMIT"
                if (attempt == maxAttempts) throw exhausted(category, attempt)
                scheduleOrExhaust(attempt, category, localDelayDecision(attempt))
            } catch (failure: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Loopback request interrupted", failure)
            }
        }
        error("Unreachable retry state")
    }

    private fun retryAfterDecision(response: HarnessResponse, attempt: Int): RetryDelayDecision {
        val values =
            response.headers.entries
                .singleOrNull { it.key.equals("retry-after", ignoreCase = true) }
                ?.value
        val token = values?.singleOrNull()
        val seconds = token?.takeIf { it.matches(Regex("[1-9][0-9]*")) }?.toLongOrNull()
        val retryAfterMillis = seconds?.takeIf { it <= Long.MAX_VALUE / 1_000L }?.times(1_000L)
        return if (
            retryAfterMillis != null &&
                retryAfterMillis <= MAX_RETRY_AFTER_MILLIS &&
                retryAfterMillis <= scheduler.remainingElapsedBudgetMillis()
        ) {
            RetryDelayDecision(retryAfterMillis, RetryDelaySource.RETRY_AFTER)
        } else {
            localDelayDecision(attempt)
        }
    }

    private fun localDelayDecision(attempt: Int): RetryDelayDecision =
        RetryDelayDecision(localBackoffMillis(attempt), RetryDelaySource.LOCAL_POLICY)

    private fun localBackoffMillis(attempt: Int): Long =
        Math.multiplyExact(attempt.toLong(), LOCAL_BACKOFF_BASE_MILLIS)

    private fun scheduleOrExhaust(
        attempt: Int,
        category: String,
        decision: RetryDelayDecision,
    ) {
        if (decision.delayMillis > scheduler.remainingElapsedBudgetMillis()) {
            throw exhausted(category, attempt)
        }
        scheduler.schedule(attempt, category, decision.delayMillis, decision.source)
    }

    private fun exhausted(category: String, attempts: Int): RetryBudgetExhausted =
        when (terminalContext) {
            RetryTerminalContext.ORDINARY ->
                RetryBudgetExhausted(
                    category,
                    attempts,
                    scheduler.elapsedMillis(),
                    ClientState.FAILED_FINAL,
                    visibleSubstatus = null,
                )
            RetryTerminalContext.DELETE_PENDING ->
                RetryBudgetExhausted(
                    category,
                    attempts,
                    scheduler.elapsedMillis(),
                    ClientState.DELETE_PENDING,
                    visibleSubstatus = "DELETE_MANUAL_RETRY_REQUIRED",
                )
        }
}

internal const val I3_RECOVERY_SNAPSHOT_KIND = "I3_CONTENT_FREE_CLIENT_RECOVERY_SIMULATION"

internal data class ClientRecoverySnapshot(
    val snapshotKind: String,
    val durableState: ClientState,
    val pendingOperation: ContentFreeOperationIdentity?,
    val reconciledResponseDigest: Sha256Hex?,
)

internal class DeterministicClientRecoveryLedger(snapshot: ClientRecoverySnapshot? = null) {
    private var durableState = snapshot?.durableState ?: ClientState.READY
    private var pendingOperation = snapshot?.pendingOperation
    private var reconciledResponseDigest = snapshot?.reconciledResponseDigest

    init {
        require(snapshot == null || snapshot.snapshotKind == I3_RECOVERY_SNAPSHOT_KIND) {
            "Only the frozen I3 recovery snapshot is accepted"
        }
        require(snapshot == null || snapshot.pendingOperation != null) {
            "A restored recovery snapshot must retain one pending operation"
        }
    }

    fun begin(state: ClientState, identity: ContentFreeOperationIdentity) {
        require(pendingOperation == null) { "Only one recovery operation may be pending" }
        durableState = state
        pendingOperation = identity
        reconciledResponseDigest = null
    }

    fun reconcile(
        identity: ContentFreeOperationIdentity,
        response: HarnessResponse,
        nextState: ClientState,
    ) {
        check(pendingOperation == identity) { "Recovery operation identity changed" }
        check(response.status in HTTP_SUCCESS_STATUS_RANGE) {
            "Recovery requires a canonical success outcome"
        }
        reconciledResponseDigest = ContractOracle.sha256Hex(response.body)
        durableState = nextState
        pendingOperation = null
    }

    fun snapshot(): ClientRecoverySnapshot =
        ClientRecoverySnapshot(
            I3_RECOVERY_SNAPSHOT_KIND,
            durableState,
            pendingOperation,
            reconciledResponseDigest,
        )
}

internal data class ServerLifecycleProbes(
    val afterBindBeforeStart: ((Int) -> Unit)? = null,
    val beforeStop: (() -> Unit)? = null,
)

@Suppress("MagicNumber", "TooGenericExceptionCaught", "TooManyFunctions")
internal class HermeticLoopbackServer
private constructor(
    private val service: SyntheticContractService,
    private val server: HttpServer,
    private val executor: ThreadPoolExecutor,
    private val threadFactory: NamedThreadFactory,
    private val faults: FrozenFaultQueue,
    private val lifecycleProbes: ServerLifecycleProbes,
) : AutoCloseable {
    private val timeouts = CopyOnWriteArrayList<CountDownLatch>()
    private val closed = AtomicBoolean()

    val port: Int
        get() = server.address.port

    val endpoint: URI
        get() = URI("http://$LOOPBACK_HOST:$port")

    fun contentFreeFaultLedger(): List<FaultLedgerEntry> = faults.contentFreeLedger()

    fun remainingFaults(): List<FaultDirective> = faults.remaining()

    private fun handle(exchange: HttpExchange) {
        val method = exchange.requestMethod
        val path = exchange.requestURI.rawPath
        var body = ByteArray(0)
        var headers = emptyMap<String, String>()
        try {
            body = readBounded(exchange)
            headers = normalizedHeaders(exchange)
            val operationClass = service.operationClass(method, path)
            val directive = operationClass?.let(faults::peek)
            if (directive == null) {
                write(exchange, service.handle(method, path, headers, body))
            } else {
                handleFault(exchange, method, path, headers, body, directive)
            }
        } catch (_: Throwable) {
            try {
                write(
                    exchange,
                    service.transportError(
                        method,
                        path,
                        headers,
                        body,
                        500,
                        "HARNESS_INTERNAL",
                    ),
                )
            } catch (_: Throwable) {
                exchange.close()
            }
        }
    }

    @Suppress("LongMethod", "LongParameterList")
    private fun handleFault(
        exchange: HttpExchange,
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray,
        directive: FaultDirective,
    ) {
        when (directive.action) {
            FaultAction.RETURN_429 -> {
                faults.consume(directive)
                write(
                    exchange,
                    HarnessResponse(
                        429,
                        mapOf("Retry-After" to listOf("1")),
                        service
                            .transportError(
                                method,
                                path,
                                headers,
                                body,
                                429,
                                "RETRY_LATER",
                            )
                            .body,
                    ),
                )
            }
            FaultAction.RETURN_429_WITHOUT_RETRY_AFTER -> {
                faults.consume(directive)
                write(
                    exchange,
                    service.transportError(
                        method,
                        path,
                        headers,
                        body,
                        429,
                        "RETRY_LATER",
                    ),
                )
            }
            FaultAction.RETURN_429_MALFORMED_RETRY_AFTER -> {
                faults.consume(directive)
                write(
                    exchange,
                    HarnessResponse(
                        429,
                        mapOf("Retry-After" to listOf("not-a-number")),
                        service
                            .transportError(
                                method,
                                path,
                                headers,
                                body,
                                429,
                                "RETRY_LATER",
                            )
                            .body,
                    ),
                )
            }
            FaultAction.RETURN_429_OUT_OF_BUDGET_RETRY_AFTER -> {
                faults.consume(directive)
                write(
                    exchange,
                    HarnessResponse(
                        429,
                        mapOf("Retry-After" to listOf("999999999999")),
                        service
                            .transportError(
                                method,
                                path,
                                headers,
                                body,
                                429,
                                "RETRY_LATER",
                            )
                            .body,
                    ),
                )
            }
            FaultAction.RETURN_503 -> {
                faults.consume(directive)
                write(
                    exchange,
                    service.transportError(
                        method,
                        path,
                        headers,
                        body,
                        503,
                        "UNAVAILABLE",
                    ),
                )
            }
            FaultAction.TIMEOUT_BEFORE_COMMIT -> {
                faults.consume(directive)
                holdUntilCleanup(exchange)
            }
            FaultAction.RETURN_UPLOAD_URL_EXPIRED -> {
                faults.consume(directive)
                write(
                    exchange,
                    service.transportError(
                        method,
                        path,
                        headers,
                        body,
                        410,
                        "UPLOAD_URL_EXPIRED",
                    ),
                )
            }
            FaultAction.EXTERNAL_REDIRECT -> {
                faults.consume(directive)
                write(
                    exchange,
                    HarnessResponse(
                        302,
                        mapOf("Location" to listOf("https://external.invalid/forbidden")),
                        ByteArray(0),
                    ),
                )
            }
            FaultAction.OVERSIZED_RESPONSE -> {
                faults.consume(directive)
                writeOversizedResponseProbe(exchange)
            }
            FaultAction.DROP_AFTER_COMMIT -> {
                val committed = service.handle(method, path, headers, body)
                if (committed.status in HTTP_SUCCESS_STATUS_RANGE) {
                    faults.consume(directive)
                    truncateCommittedResponse(exchange, committed)
                } else {
                    write(exchange, committed)
                }
            }
            FaultAction.DROP_BEFORE_COMMIT -> {
                faults.consume(directive)
                exchange.close()
            }
        }
    }

    private fun normalizedHeaders(exchange: HttpExchange): Map<String, String> =
        exchange.requestHeaders.entries.associate { (name, values) ->
            name.lowercase(Locale.ROOT) to values.single()
        }

    private fun readBounded(exchange: HttpExchange): ByteArray {
        val bytes = exchange.requestBody.use { it.readNBytes(MAX_HTTP_BYTES + 1) }
        require(bytes.size <= MAX_HTTP_BYTES) { "Loopback request exceeded server byte bound" }
        return bytes
    }

    private fun holdUntilCleanup(exchange: HttpExchange) {
        val latch = CountDownLatch(1)
        timeouts += latch
        try {
            latch.await(SCENARIO_DEADLINE.toSeconds(), TimeUnit.SECONDS)
        } finally {
            exchange.close()
            timeouts -= latch
        }
    }

    private fun write(exchange: HttpExchange, response: HarnessResponse) {
        check(response.body.size <= MAX_HTTP_BYTES) {
            "Loopback response exceeded server byte bound"
        }
        response.headers.forEach { (name, values) ->
            values.forEach { exchange.responseHeaders.add(name, it) }
        }
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(response.status, response.body.size.toLong())
        exchange.responseBody.use { it.write(response.body) }
        exchange.close()
    }

    private fun truncateCommittedResponse(exchange: HttpExchange, response: HarnessResponse) {
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.responseHeaders.set("Connection", "close")
        exchange.sendResponseHeaders(response.status, (response.body.size + 1).toLong())
        exchange.close()
    }

    private fun writeOversizedResponseProbe(exchange: HttpExchange) {
        val bytes = ByteArray(MAX_HTTP_BYTES + 1) { 0x53 }
        exchange.responseHeaders.set("Content-Type", "application/octet-stream")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
        exchange.close()
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        timeouts.forEach(CountDownLatch::countDown)
        var failure: Throwable? = null
        failure = captureFailure(failure) { lifecycleProbes.beforeStop?.invoke() }
        failure = captureFailure(failure) { server.stop(0) }
        failure = captureFailure(failure) { executor.shutdownNow() }
        failure =
            captureFailure(failure) {
                check(executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    "Loopback server executor did not terminate"
                }
            }
        failure =
            captureFailure(failure) {
                check(threadFactory.awaitNoLiveNonDaemonThreads(Duration.ofSeconds(2))) {
                    "Loopback server thread leak"
                }
            }
        failure?.let { throw it }
    }

    companion object {
        fun create(
            service: SyntheticContractService,
            directives: List<FaultDirective> = emptyList(),
            lifecycleProbes: ServerLifecycleProbes = ServerLifecycleProbes(),
        ): HermeticLoopbackServer {
            validateFactoryInput(directives)
            val faults = FrozenFaultQueue(directives)
            val loopback =
                InetAddress.getByAddress(byteArrayOf(127.toByte(), 0, 0, 1)).also { address ->
                    check(address is Inet4Address && address.isLoopbackAddress) {
                        "Server factory requires numeric IPv4 loopback"
                    }
                }
            var server: HttpServer? = null
            var executor: ThreadPoolExecutor? = null
            var threadFactory: NamedThreadFactory? = null
            try {
                server = HttpServer.create(InetSocketAddress(loopback, 0), 16)
                threadFactory = NamedThreadFactory("dora-vpn-loopback-server", daemon = false)
                executor =
                    ThreadPoolExecutor(
                        3,
                        3,
                        0,
                        TimeUnit.MILLISECONDS,
                        ArrayBlockingQueue(16),
                        threadFactory,
                    )
                check(server.address.address.hostAddress == LOOPBACK_HOST) {
                    "Server bind must be numeric"
                }
                check(server.address.port != 0) { "OS must assign an ephemeral port" }
                server.executor = executor
                lifecycleProbes.afterBindBeforeStart?.invoke(server.address.port)
                val owned =
                    HermeticLoopbackServer(
                        service,
                        server,
                        executor,
                        threadFactory,
                        faults,
                        lifecycleProbes,
                    )
                server.createContext("/") { exchange -> owned.handle(exchange) }
                server.start()
                return owned
            } catch (failure: Throwable) {
                val cleanupFailure = releaseFactoryResources(server, executor, threadFactory)
                if (cleanupFailure != null) failure.addSuppressed(cleanupFailure)
                throw failure
            }
        }

        private fun validateFactoryInput(directives: List<FaultDirective>) {
            require(directives.size <= MAX_FAULT_DIRECTIVES) { "Server fault queue exceeded bound" }
            require(
                directives.all {
                    it.faultId.matches(Regex("^[A-Z0-9-]+$")) &&
                        it.operationClass.matches(Regex("^[A-Z0-9_]+$"))
                }
            ) {
                "Fault directives must be deterministic and content-free"
            }
        }

        private fun releaseFactoryResources(
            server: HttpServer?,
            executor: ThreadPoolExecutor?,
            threadFactory: NamedThreadFactory?,
        ): Throwable? {
            var failure: Throwable? = null
            failure = captureFailure(failure) { server?.start() }
            failure = captureFailure(failure) { server?.stop(0) }
            failure = captureFailure(failure) { executor?.shutdownNow() }
            failure =
                captureFailure(failure) {
                    if (executor != null) {
                        check(executor.awaitTermination(2, TimeUnit.SECONDS)) {
                            "Server factory executor did not terminate"
                        }
                    }
                }
            failure =
                captureFailure(failure) {
                    if (threadFactory != null) {
                        check(threadFactory.awaitNoLiveNonDaemonThreads(Duration.ofSeconds(2))) {
                            "Server factory thread leak"
                        }
                    }
                }
            return failure
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private fun captureFailure(current: Throwable?, block: () -> Unit): Throwable? =
    try {
        block()
        current
    } catch (failure: Throwable) {
        if (current == null) failure else current.apply { addSuppressed(failure) }
    }

internal fun URI.child(path: String): URI {
    require(path.startsWith('/')) { "Harness path must be absolute" }
    return resolve(path)
}

@Suppress("FunctionOnlyReturningConstant", "UNUSED_PARAMETER")
internal fun contentFreeFailureMarker(failure: Throwable): String =
    "FAIL hermetic-loopback-transport-harness"
