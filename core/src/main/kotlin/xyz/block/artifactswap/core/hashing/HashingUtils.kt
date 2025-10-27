package xyz.block.artifactswap.core.hashing

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

private val HEX_ARRAY = "0123456789ABCDEF".toCharArray()

/**
 * Hashes a sequence of source file paths using the provided message digest.
 * Returns the hex-encoded hash and the count of files hashed.
 */
fun Sequence<Path>.hashSources(messageDigest: MessageDigest): Pair<String, Int> {
    val pathCount = onEach { path ->
        messageDigest.update(path.readBytes())
    }.count()
    return messageDigest.digest().toHex() to pathCount
}

private fun ByteArray.toHex(): String {
    val hexChars = CharArray(size * 2)
    indices.forEach {
        val v = get(it).toInt() and 0xFF
        hexChars[it * 2] = HEX_ARRAY[v ushr 4]
        hexChars[it * 2 + 1] = HEX_ARRAY[v and 0x0F]
    }
    return String(hexChars)
}

/**
 * Converts an artifact name to a project path by replacing underscores with colons.
 */
fun String.artifactToProject(): String {
    return ":${replace('_', ':')}"
}
