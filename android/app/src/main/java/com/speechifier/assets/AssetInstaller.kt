package com.speechifier.assets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream

/** Coarse state of an asset install, surfaced to the download UI. */
sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(
        val label: String,
        val fileFraction: Float,
        val fileIndex: Int,
        val fileCount: Int,
    ) : InstallState
    data object Done : InstallState
    data class Failed(val message: String) : InstallState
}

/**
 * Downloads (and unzips bundle) assets for a voice, reporting progress. Single-file
 * assets (model, vocab, voice `.bin`) download directly; `.zip` bundles (G2P data)
 * are extracted into their directory and marked with a `.ready` sentinel.
 */
class AssetInstaller {

    suspend fun install(entries: List<AssetEntry>, onState: (InstallState) -> Unit) {
        val missing = entries.filterNot { it.present }
        if (missing.isEmpty()) {
            onState(InstallState.Done)
            return
        }
        try {
            missing.forEachIndexed { index, entry ->
                val n = index + 1
                onState(InstallState.Downloading(entry.label, 0f, n, missing.size))
                if (entry.url.endsWith(".zip")) {
                    installZip(entry) { f -> onState(InstallState.Downloading(entry.label, f, n, missing.size)) }
                } else {
                    Downloader.download(entry.url, entry.dest) { p ->
                        onState(InstallState.Downloading(entry.label, p.fraction, n, missing.size))
                    }
                }
            }
            onState(InstallState.Done)
        } catch (e: Exception) {
            onState(InstallState.Failed(e.message ?: "Download failed"))
        }
    }

    /** Download a zip into the marker's directory, extract, then write the `.ready` marker. */
    private suspend fun installZip(entry: AssetEntry, onFraction: (Float) -> Unit) {
        val dir = entry.dest.parentFile ?: error("zip entry needs a parent dir")
        dir.mkdirs()
        val zip = File(dir, "_download.zip")
        Downloader.download(entry.url, zip) { p -> onFraction(p.fraction) }
        withContext(Dispatchers.IO) {
            unzip(zip, dir)
            zip.delete()
            entry.dest.writeText("ok") // .ready marker
        }
    }

    private fun unzip(zip: File, targetDir: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)
                // Guard against zip-slip path traversal.
                require(outFile.canonicalPath.startsWith(targetDir.canonicalPath)) {
                    "Unsafe zip entry: ${entry.name}"
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
