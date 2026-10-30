@file:Suppress("MagicNumber")

package com.monumentogram.dora.transcript

import java.io.ByteArrayOutputStream
import java.io.InputStream

private val compositePartRoute =
    Regex(
        "^/v1/runs/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/parts/([0-9]|[1-3][0-9]|4[0-7])$"
    )

/** A completed part can return its result on resume as well as on the child status route. */
internal fun readCloudResponse(
    input: InputStream,
    path: String,
    compositeJobs: Set<String>,
): ByteArray {
    val composite =
        compositePartRoute.matches(path) ||
            compositeJobs.any {
                path == "/v1/jobs/$it" || path.startsWith("/v1/jobs/$it/")
            }
    val limit = if (composite) 8 * 1024 * 1024 else 2 * 1024 * 1024
    val output = ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    try {
        while (true) {
            val count = input.read(chunk)
            if (count < 0) break
            require(output.size() + count <= limit)
            output.write(chunk, 0, count)
        }
        return output.toByteArray()
    } finally {
        chunk.fill(0)
    }
}
