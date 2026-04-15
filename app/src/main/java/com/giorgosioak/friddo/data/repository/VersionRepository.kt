package com.giorgosioak.friddo.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import com.giorgosioak.friddo.data.local.PreferencesKeys
import com.giorgosioak.friddo.ui.screens.settings.settingsDataStore
import com.giorgosioak.friddo.service.LogType
import com.giorgosioak.friddo.service.ServerStateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.log10
import kotlin.math.pow

private const val TAG = "VersionRepository"
private const val GITHUB_RELEASES_URL = "https://api.github.com/repos/frida/frida/releases"
private val RELEASES_CACHE_MAX_AGE_MS = TimeUnit.HOURS.toMillis(12)

class VersionRepository(private val context: Context) {

    companion object {
        private val http by lazy {
            OkHttpClient.Builder()
                .followRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .build()
        }
    }

    // DataStore key for active version (set/get)
    private val dataStore = context.settingsDataStore
    val activeVersionFlow: Flow<String?> = dataStore.data
        .map { prefs ->
            prefs[PreferencesKeys.Settings.ACTIVE_FRIDA_VERSION]
        }
    // default storage dir
    fun defaultBinsDir(): File = File(context.filesDir, "frida_bins").apply { mkdirs() }
    private fun releasesCacheFile(): File = File(context.filesDir, "frida_releases_cache.json")

    // List installed versions by reading the bins dir
    suspend fun listInstalledVersions(): List<InstalledVersion> = withContext(Dispatchers.IO) {
        val base = defaultBinsDir()
        if (!base.exists()) return@withContext emptyList()
        return@withContext base.listFiles { f -> f.isDirectory }?.mapNotNull { dir ->
            val dirName = dir.name
            val fridaAbi = dirName.substringAfterLast("-")

            val binary = dir.listFiles()?.firstOrNull { it.name.contains("frida-server") }?.absolutePath
            val metadataFile = File(dir, "metadata.json")

            if (metadataFile.exists()) {
                try {
                    val json = JSONObject(metadataFile.readText())
                    InstalledVersion(
                        tag = json.getString("tag"),
                        arch = fridaAbi,
                        name = json.optString("name", json.getString("tag")),
                        publishedAt = json.optString("publishedAt", ""),
                        changelog = json.optString("changelog", ""),
                        size = json.optLong("size", 0L),
                        installedAt = dir.lastModified(),
                        path = binary
                    )
                } catch (_: Exception) {
                    null
                }
            } else {
                // Fallback for versions installed before this update
                val tag = dirName.substringBeforeLast("-")
                InstalledVersion(
                    tag = tag,
                    arch = fridaAbi,
                    name = tag,
                    publishedAt = "",
                    changelog = "",
                    size = 0L,
                    installedAt = dir.lastModified(),
                    path = binary
                )
            }
        } ?: emptyList()
    }

    // RENAMED function to convert system ABI to Frida's asset naming format
    fun getFridaAbiFormat(abi: String): String {
        // normalize to commonly used suffixes in frida assets
        return when {
            abi.startsWith("arm64") -> "arm64"
            abi.startsWith("arm") -> "arm"
            abi.contains("86_64") || abi.contains("x86_64") -> "x86_64"
            abi.contains("86") -> "x86"
            else -> "arm64" // Default to the most common
        }
    }

    suspend fun getCachedReleases(): List<RemoteRelease> = withContext(Dispatchers.IO) {
        readCachedReleases()?.releases ?: emptyList()
    }

    suspend fun shouldRefreshReleaseCache(maxAgeMs: Long = RELEASES_CACHE_MAX_AGE_MS): Boolean =
        withContext(Dispatchers.IO) {
            val cache = readCachedReleases() ?: return@withContext true
            System.currentTimeMillis() - cache.fetchedAt >= maxAgeMs
        }

    // Fetch releases from GitHub, using disk cache unless a refresh is forced.
    suspend fun fetchReleases(forceRefresh: Boolean = false): List<RemoteRelease> = withContext(Dispatchers.IO) {
        val cached = readCachedReleases()

        if (!forceRefresh && cached != null) {
            val cacheAge = System.currentTimeMillis() - cached.fetchedAt
            if (cacheAge < RELEASES_CACHE_MAX_AGE_MS) {
                return@withContext cached.releases
            }
        }

        val freshReleases = fetchReleasesFromNetwork()
        if (freshReleases.isNotEmpty()) {
            writeCachedReleases(freshReleases)
            return@withContext freshReleases
        }

        cached?.releases ?: emptyList()
    }

    private fun fetchReleasesFromNetwork(): List<RemoteRelease> {
        return try {
            val req = Request.Builder()
                .url(GITHUB_RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Friddo-App")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val body = resp.body.string()
                val arr = JSONArray(body)

                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    val tag = o.getString("tag_name")
                    val assetsJson = o.optJSONArray("assets")

                    val assets = mutableListOf<ReleaseAsset>()
                    if (assetsJson != null) {
                        for (j in 0 until assetsJson.length()) {
                            val a = assetsJson.getJSONObject(j)
                            assets.add(ReleaseAsset(
                                name = a.getString("name"),
                                url = a.getString("browser_download_url"),
                                size = a.optLong("size", 0L)
                            ))
                        }
                    }

                    RemoteRelease(
                        tag = tag,
                        name = o.optString("name", tag),
                        publishedAt = o.optString("published_at", ""),
                        changelog = o.optString("body", ""),
                        assets = assets
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "fetchReleases failed", t)
            emptyList()
        }
    }

    private fun readCachedReleases(): CachedRemoteReleases? {
        val cacheFile = releasesCacheFile()
        if (!cacheFile.exists()) return null

        return try {
            val json = JSONObject(cacheFile.readText())
            val fetchedAt = json.optLong("fetchedAt", 0L)
            val releasesJson = json.optJSONArray("releases") ?: JSONArray()

            val releases = (0 until releasesJson.length()).map { i ->
                val releaseJson = releasesJson.getJSONObject(i)
                val assetsJson = releaseJson.optJSONArray("assets") ?: JSONArray()

                val assets = (0 until assetsJson.length()).map { j ->
                    val assetJson = assetsJson.getJSONObject(j)
                    ReleaseAsset(
                        name = assetJson.getString("name"),
                        url = assetJson.getString("url"),
                        size = assetJson.optLong("size", 0L)
                    )
                }

                RemoteRelease(
                    tag = releaseJson.getString("tag"),
                    name = releaseJson.optString("name", releaseJson.getString("tag")),
                    publishedAt = releaseJson.optString("publishedAt", ""),
                    changelog = releaseJson.optString("changelog", ""),
                    assets = assets
                )
            }

            CachedRemoteReleases(
                fetchedAt = fetchedAt,
                releases = releases
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read releases cache", e)
            null
        }
    }

    private fun writeCachedReleases(releases: List<RemoteRelease>) {
        try {
            val releasesJson = JSONArray()
            releases.forEach { release ->
                val assetsJson = JSONArray()
                release.assets.forEach { asset ->
                    assetsJson.put(
                        JSONObject().apply {
                            put("name", asset.name)
                            put("url", asset.url)
                            put("size", asset.size)
                        }
                    )
                }

                releasesJson.put(
                    JSONObject().apply {
                        put("tag", release.tag)
                        put("name", release.name)
                        put("publishedAt", release.publishedAt)
                        put("changelog", release.changelog)
                        put("assets", assetsJson)
                    }
                )
            }

            releasesCacheFile().writeText(
                JSONObject().apply {
                    put("fetchedAt", System.currentTimeMillis())
                    put("releases", releasesJson)
                }.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write releases cache", e)
        }
    }

    /**
     * Download & extract matching asset for the provided release and abi.
     * Returns InstalledVersion on success.
     */
    suspend fun downloadAndInstall(release: RemoteRelease, desiredAbi: String): InstalledVersion? =
        withContext(Dispatchers.IO) {
            try {
                val fridaAbi = getFridaAbiFormat(desiredAbi)

                val asset = release.assets.find { asset ->
                    val name = asset.name.lowercase()
                    name.contains("frida-server") &&
                            name.contains("android") &&
                            name.contains(fridaAbi) &&
                            name.endsWith(".xz")
                } ?: return@withContext null

                // Folder is now version-arch (e.g., 17.8.1-arm64)
                val versionDir = File(defaultBinsDir(), "${release.tag}-$fridaAbi")
                if (!versionDir.exists()) versionDir.mkdirs()

                val finalBinaryFile = File(versionDir, "frida-server")

                // Save metadata
                val metadataFile = File(versionDir, "metadata.json")
                val metadataJson = JSONObject().apply {
                    put("tag", release.tag)
                    put("name", release.name)
                    put("publishedAt", release.publishedAt)
                    put("changelog", release.changelog)
                    put("size", asset.size)
                }
                metadataFile.writeText(metadataJson.toString())

                val installedVersion = InstalledVersion(
                    tag = release.tag,
                    arch = fridaAbi,
                    name = release.name,
                    publishedAt = release.publishedAt,
                    changelog = release.changelog,
                    size = asset.size,
                    installedAt = versionDir.lastModified(),
                    path = finalBinaryFile.absolutePath,
                )

                if (finalBinaryFile.exists()) {
                    return@withContext installedVersion
                }

                val request = Request.Builder().url(asset.url).build()
                val response = http.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null

                val bodyStream = response.body.byteStream()
                XZInputStream(bodyStream).use { xzIn ->
                    FileOutputStream(finalBinaryFile).use { fileOut ->
                        xzIn.copyTo(fileOut)
                    }
                }

                finalBinaryFile.setExecutable(true, false)
                try {
                    Runtime.getRuntime().exec("chmod 755 ${finalBinaryFile.absolutePath}").waitFor()
                } catch (_: Exception) {
                }

                if (getActiveVersionTag() == null) {
                    setActiveVersion(installedVersion)
                }

                return@withContext installedVersion
            } catch (e: Exception) {
                Log.e(TAG, "downloadAndInstall failed", e)
                null
            }
        }


    // Mark active version in DataStore AND copy it to the friddo/ folder for execution
    // Mark active version in DataStore using the unique folder name (tag-arch)
    suspend fun setActiveVersion(installedVersion: InstalledVersion) = withContext(Dispatchers.IO) {
        try {
            val uniqueId = "${installedVersion.tag}-${installedVersion.arch}"

            // 1. Update preference with the unique identifier
            dataStore.edit { prefs -> prefs[PreferencesKeys.Settings.ACTIVE_FRIDA_VERSION] = uniqueId }

            // 2. Locate source: frida_bins/17.8.1-arm64-v8a/frida-server
            val sourceDir = File(defaultBinsDir(), uniqueId)
            val sourceFile = File(sourceDir, "frida-server")

            if (!sourceFile.exists()) {
                Log.e(TAG, "Source not found: ${sourceFile.absolutePath}")
                return@withContext
            }

            val targetDir = File(context.filesDir, "friddo")
            if (!targetDir.exists()) targetDir.mkdirs()
            val targetFile = File(targetDir, "frida-server")

            sourceFile.copyTo(targetFile, overwrite = true)
            targetFile.setExecutable(true, false)

            ServerStateManager.addLog(LogType.INFO,"Active: ${installedVersion.tag} (${installedVersion.arch})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set active version", e)
            ServerStateManager.addLog(LogType.ERROR,"Failed to switch: ${e.message}")
        }
    }

    suspend fun getActiveVersionTag(): String? = withContext(Dispatchers.IO) {
        val prefs = dataStore.data.first()
        prefs[PreferencesKeys.Settings.ACTIVE_FRIDA_VERSION]
    }

    // delete installed using unique folder name (tag-arch)
    suspend fun deleteInstalled(version: InstalledVersion): Boolean = withContext(Dispatchers.IO) {
        try {
            val uniqueId = "${version.tag}-${version.arch}"
            val dir = File(defaultBinsDir(), uniqueId)

            if (!dir.exists()) {
                Log.e(TAG, "Directory not found: ${dir.absolutePath}")
                return@withContext false
            }

            val success = dir.deleteRecursively()

            // Optional: If the deleted version was the active one, clear the preference
            val prefs = dataStore.data.first()
            if (prefs[PreferencesKeys.Settings.ACTIVE_FRIDA_VERSION] == uniqueId) {
                dataStore.edit { it.remove(PreferencesKeys.Settings.ACTIVE_FRIDA_VERSION) }
            }

            success
        } catch (t: Throwable) {
            Log.e(TAG, "deleteInstalled failed", t)
            false
        }
    }
}

// --- Data models ---
data class InstalledVersion(
    val tag: String,
    val name: String,
    val arch: String,
    val publishedAt: String,
    val changelog: String,
    val size: Long,
    val installedAt: Long,
    val path: String?,
) {
    // Returns: 14.9 MB
    val formattedSize: String
        get() {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
            return String.format(
                Locale.getDefault(),
                "%.1f %s",
                size / 1024.0.pow(digitGroups.toDouble()),
                units[digitGroups]
            )
        }

    // Returns: 2023-09-05
    val publishedAtISO: String
        get() = publishedAt.substringBefore("T").ifEmpty { "Unknown" }

    // Returns: Sep 05, 2023
    val publishedAtMedium: String
        get() {
            if (publishedAt.isEmpty()) return "Unknown Date"
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(publishedAt)

                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                date?.let { outputFormat.format(it) } ?: publishedAtISO
            } catch (_: Exception) {
                publishedAtISO
            }
        }
}

data class RemoteRelease(
    val tag: String,
    val name: String,
    val publishedAt: String,
    val changelog: String,
    val assets: List<ReleaseAsset>
) {
    // Returns: 2023-09-05
    val publishedAtISO: String
        get() = publishedAt.substringBefore("T").ifEmpty { "Unknown" }

    // Returns: Sep 05, 2023
    val publishedAtMedium: String
        get() {
            if (publishedAt.isEmpty()) return "Unknown Date"
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                inputFormat.timeZone = TimeZone.getTimeZone("UTC")
                val date = inputFormat.parse(publishedAt)

                val outputFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                date?.let { outputFormat.format(it) } ?: publishedAtISO
            } catch (_: Exception) {
                publishedAtISO
            }
        }
}

private data class CachedRemoteReleases(
    val fetchedAt: Long,
    val releases: List<RemoteRelease>
)

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long
) {
    // Returns: 14.9 MB
    val formattedSize: String
        get() {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
            return String.format(
                Locale.getDefault(),
                "%.1f %s",
                size / 1024.0.pow(digitGroups.toDouble()),
                units[digitGroups]
            )
        }

}
