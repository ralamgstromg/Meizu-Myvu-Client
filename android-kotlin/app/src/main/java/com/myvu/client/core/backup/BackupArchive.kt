package com.myvu.client.core.backup

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Zip I/O for backups: streaming writes with SHA-256 per entry and hardened extraction. */
internal object BackupArchive {

    const val MAX_ENTRIES = 10_000
    const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024

    private const val BUFFER_SIZE = 64 * 1024

    /** Writes entries straight into a zip, collecting `path -> sha256` for the manifest. */
    class Writer(private val zos: ZipOutputStream) {
        private val buffer = ByteArray(BUFFER_SIZE)
        val checksums = linkedMapOf<String, String>()

        fun putFile(path: String, file: File) {
            file.inputStream().use { putStream(path, it, file.lastModified()) }
        }

        fun putBytes(path: String, bytes: ByteArray) {
            putStream(path, bytes.inputStream(), System.currentTimeMillis())
        }

        /** Unhashed entry: used for the manifest itself. */
        fun putManifest(path: String, bytes: ByteArray) {
            zos.putNextEntry(ZipEntry(path))
            zos.write(bytes)
            zos.closeEntry()
        }

        private fun putStream(path: String, input: InputStream, time: Long) {
            val digest = MessageDigest.getInstance("SHA-256")
            zos.putNextEntry(ZipEntry(path).apply { this.time = time })
            var n = input.read(buffer)
            while (n != -1) {
                digest.update(buffer, 0, n)
                zos.write(buffer, 0, n)
                n = input.read(buffer)
            }
            zos.closeEntry()
            checksums[path] = digest.digest().toHex()
        }
    }

    fun write(out: OutputStream, block: (Writer) -> Unit) {
        ZipOutputStream(out.buffered(BUFFER_SIZE)).use { block(Writer(it)) }
    }

    /**
     * Extracts [zipStream] into [targetDir], rejecting path traversal (Zip Slip) and
     * archives over [maxEntries] entries or [maxTotalBytes] uncompressed bytes.
     * Returns `path -> sha256` of every extracted file.
     */
    fun extract(
        zipStream: InputStream,
        targetDir: File,
        maxEntries: Int = MAX_ENTRIES,
        maxTotalBytes: Long = MAX_TOTAL_BYTES
    ): Map<String, String> {
        val rootPath = targetDir.canonicalPath + File.separator
        val buffer = ByteArray(BUFFER_SIZE)
        val checksums = linkedMapOf<String, String>()
        var entries = 0
        var total = 0L

        ZipInputStream(BufferedInputStream(zipStream, BUFFER_SIZE)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (++entries > maxEntries) throw IOException("Respaldo inválido: demasiadas entradas")
                val file = File(targetDir, entry.name)
                if (!file.canonicalPath.startsWith(rootPath)) {
                    throw SecurityException("Entrada ZIP inválida: ${entry.name}")
                }
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    val digest = MessageDigest.getInstance("SHA-256")
                    FileOutputStream(file).buffered(BUFFER_SIZE).use { out ->
                        var n = zis.read(buffer)
                        while (n != -1) {
                            total += n
                            if (total > maxTotalBytes) throw IOException("Respaldo inválido: tamaño excesivo")
                            digest.update(buffer, 0, n)
                            out.write(buffer, 0, n)
                            n = zis.read(buffer)
                        }
                    }
                    checksums[entry.name] = digest.digest().toHex()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return checksums
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
