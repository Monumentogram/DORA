package com.monumentogram.dora.poc.vpn.transport

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
)

internal data class HarnessResponse(
    val status: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray = ByteArray(0),
)

internal enum class FaultAction {
    DROP_AFTER_COMMIT,
    RETURN_429,
    RETURN_503,
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
)

internal class DeterministicRetryScheduler {
    private val events = mutableListOf<RetryEvent>()

    fun schedule(attempt: Int, category: String, logicalDelayMillis: Long) {
        require(logicalDelayMillis > 0) { "Retry delay must be positive" }
        events += RetryEvent(attempt, category, logicalDelayMillis)
    }

    fun events(): List<RetryEvent> = events.toList()
}

internal class RetryBudgetExhausted(
    val category: String,
    val attempts: Int,
) : IllegalStateException("Finite retry budget exhausted")

@Suppress("MagicNumber")
internal class RetryingLoopbackTransport(
    private val client: HermeticLoopbackClient,
    private val scheduler: DeterministicRetryScheduler,
    private val maxAttempts: Int = MAX_ATTEMPTS,
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
                if (attempt == maxAttempts) throw RetryBudgetExhausted(category, attempt)
                val delay =
                    if (response.status == 429) {
                        val retryAfter =
                            response.headers.entries
                                .firstOrNull { it.key.equals("retry-after", ignoreCase = true) }
                                ?.value
                                ?.singleOrNull()
                                ?.toLongOrNull()
                        require(retryAfter != null && retryAfter > 0) {
                            "429 requires a positive Retry-After"
                        }
                        retryAfter * 1_000
                    } else {
                        attempt * 100L
                    }
                scheduler.schedule(attempt, category, delay)
            } catch (failure: IOException) {
                val category =
                    if (failure is HttpTimeoutException) "REQUEST_TIMEOUT" else "UNKNOWN_COMMIT"
                if (attempt == maxAttempts) throw RetryBudgetExhausted(category, attempt)
                scheduler.schedule(attempt, category, attempt * 100L)
            } catch (failure: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Loopback request interrupted", failure)
            }
        }
        error("Unreachable retry state")
    }
}

internal data class ServerLifecycleProbes(
    val afterBindBeforeStart: ((Int) -> Unit)? = null,
    val beforeStop: (() -> Unit)? = null,
)

@Suppress("MagicNumber", "TooGenericExceptionCaught")
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

    @Suppress("LongMethod")
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
            when (directive?.action) {
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
                    val committed =
                        service.handle(
                            method,
                            path,
                            headers,
                            body,
                        )
                    if (committed.status in 200..299) {
                        faults.consume(directive)
                        truncateCommittedResponse(exchange, committed)
                    } else {
                        write(exchange, committed)
                    }
                }
                null ->
                    write(
                        exchange,
                        service.handle(
                            method,
                            path,
                            headers,
                            body,
                        ),
                    )
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
            require(directives.map { it.faultId }.distinct().size == directives.size) {
                "Fault IDs must be unique"
            }
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
