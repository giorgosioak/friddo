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
private const val GITHUB_RELEASES_PER_PAGE = 50
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

    fun deleteFridaServer(): Boolean {
        val file = File(context.filesDir.resolve("friddo"), "frida-server")
        return if (file.exists()) {
            file.delete()
        } else false
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
        readCachedReleases()?.cache?.releases ?: emptyList()
    }

    suspend fun getCachedReleaseCache(): RemoteReleaseCache? = withContext(Dispatchers.IO) {
        readCachedReleases()?.cache
    }

    suspend fun shouldRefreshReleaseCache(maxAgeMs: Long = RELEASES_CACHE_MAX_AGE_MS): Boolean =
        withContext(Dispatchers.IO) {
            val cache = readCachedReleases() ?: return@withContext true
            !cache.hasPaginationMetadata ||
                    System.currentTimeMillis() - cache.cache.fetchedAt >= maxAgeMs
        }

    // Fetch the newest GitHub releases page, using disk cache unless a refresh is forced.
    suspend fun fetchReleases(forceRefresh: Boolean = false): List<RemoteRelease> = withContext(Dispatchers.IO) {
        fetchReleaseCache(forceRefresh).releases
    }

    suspend fun fetchReleaseCache(forceRefresh: Boolean = false): RemoteReleaseCache = withContext(Dispatchers.IO) {
        val cached = readCachedReleases()

        if (!forceRefresh && cached != null && cached.hasPaginationMetadata) {
            val cacheAge = System.currentTimeMillis() - cached.cache.fetchedAt
            if (cacheAge < RELEASES_CACHE_MAX_AGE_MS) {
                return@withContext cached.cache
            }
        }

        val firstPage = fetchReleasesFromNetwork(page = 1)
        if (firstPage != null) {
            val freshCache = firstPage.toCache()
            writeCachedReleases(freshCache)
            return@withContext freshCache
        }

        cached?.cache ?: emptyReleaseCache()
    }

    suspend fun fetchNextReleasePage(): RemoteReleaseCache = withContext(Dispatchers.IO) {
        val cached = readCachedReleases()?.cache
        val current = cached ?: RemoteReleaseCache(
            fetchedAt = 0L,
            releases = emptyList(),
            nextPage = 1,
            nextUrl = null,
            hasMore = true
        )

        if (!current.hasMore) {
            return@withContext current
        }

        val page = current.nextPage ?: current.nextUrl?.let(::pageFromUrl) ?: 1
        val nextPage = fetchReleasesFromNetwork(page = page, url = current.nextUrl)
            ?: return@withContext current

        val updatedCache = RemoteReleaseCache(
            fetchedAt = System.currentTimeMillis(),
            releases = dedupeReleasesByTag(current.releases + nextPage.releases),
            nextPage = nextPage.nextPage,
            nextUrl = nextPage.nextUrl,
            hasMore = nextPage.hasMore
        )
        writeCachedReleases(updatedCache)
        updatedCache
    }

    private fun fetchReleasesFromNetwork(page: Int, url: String? = null): ReleaseNetworkPage? {
        return try {
            val req = Request.Builder()
                .url(url ?: releasesPageUrl(page))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Friddo-App")
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "GitHub releases request failed: HTTP ${resp.code}")
                    return@use null
                }
                val body = resp.body.string()
                val arr = JSONArray(body)

                val releases = (0 until arr.length()).mapNotNull { i ->
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

                    val release = RemoteRelease(
                        tag = tag,
                        name = o.optString("name", tag),
                        publishedAt = o.optString("published_at", ""),
                        changelog = o.optString("body", ""),
                        assets = assets
                    )
                    release.takeIf { it.hasAndroidServerAsset() }
                }

                val nextUrl = nextUrlFromLinkHeader(resp.header("Link"))
                ReleaseNetworkPage(
                    releases = releases,
                    nextPage = nextUrl?.let(::pageFromUrl) ?: nextUrl?.let { page + 1 },
                    nextUrl = nextUrl,
                    hasMore = nextUrl != null
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "fetchReleases failed", t)
            null
        }
    }

    private fun readCachedReleases(): CachedRemoteReleases? {
        val cacheFile = releasesCacheFile()
        if (!cacheFile.exists()) return null

        return try {
            val json = JSONObject(cacheFile.readText())
            val fetchedAt = json.optLong("fetchedAt", 0L)
            val releasesJson = json.optJSONArray("releases") ?: JSONArray()
            val hasPaginationMetadata =
                json.has("nextPage") || json.has("nextUrl") || json.has("hasMore")

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

            val nextUrl = if (json.has("nextUrl") && !json.isNull("nextUrl")) {
                json.optString("nextUrl").takeIf { it.isNotBlank() }
            } else {
                null
            }
            val nextPage = when {
                json.has("nextPage") && !json.isNull("nextPage") -> json.optInt("nextPage")
                nextUrl != null -> pageFromUrl(nextUrl)
                !hasPaginationMetadata && releases.isNotEmpty() -> 1
                else -> null
            }
            val hasMore = if (json.has("hasMore")) {
                json.optBoolean("hasMore", nextPage != null || nextUrl != null)
            } else {
                releases.isNotEmpty()
            }

            CachedRemoteReleases(
                cache = RemoteReleaseCache(
                    fetchedAt = fetchedAt,
                    releases = releases,
                    nextPage = nextPage,
                    nextUrl = nextUrl,
                    hasMore = hasMore
                ),
                hasPaginationMetadata = hasPaginationMetadata
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read releases cache", e)
            null
        }
    }

    private fun writeCachedReleases(cache: RemoteReleaseCache) {
        try {
            val releasesJson = JSONArray()
            cache.releases.forEach { release ->
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
                    put("fetchedAt", cache.fetchedAt)
                    put("releases", releasesJson)
                    put("nextPage", cache.nextPage ?: JSONObject.NULL)
                    put("nextUrl", cache.nextUrl ?: JSONObject.NULL)
                    put("hasMore", cache.hasMore)
                }.toString()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write releases cache", e)
        }
    }

    private fun emptyReleaseCache() = RemoteReleaseCache(
        fetchedAt = 0L,
        releases = emptyList(),
        nextPage = null,
        nextUrl = null,
        hasMore = false
    )

    private fun releasesPageUrl(page: Int): String =
        "$GITHUB_RELEASES_URL?per_page=$GITHUB_RELEASES_PER_PAGE&page=$page"

    private fun nextUrlFromLinkHeader(linkHeader: String?): String? {
        if (linkHeader.isNullOrBlank()) return null

        return linkHeader.split(",")
            .firstOrNull { part -> part.substringAfter(";").contains("rel=\"next\"") }
            ?.substringBefore(";")
            ?.trim()
            ?.removePrefix("<")
            ?.removeSuffix(">")
            ?.takeIf { it.isNotBlank() }
    }

    private fun pageFromUrl(url: String): Int? =
        Regex("[?&]page=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun dedupeReleasesByTag(releases: List<RemoteRelease>): List<RemoteRelease> {
        val byTag = LinkedHashMap<String, RemoteRelease>()
        releases.forEach { release ->
            if (!byTag.containsKey(release.tag)) {
                byTag[release.tag] = release
            }
        }
        return byTag.values.toList()
    }

    /**
     * Download & extract matching asset for the provided release and abi.
     * Returns InstalledVersion on success.
     */
    suspend fun downloadAndInstall(release: RemoteRelease, desiredAbi: String): InstalledVersion? =
        withContext(Dispatchers.IO) {
            try {
                val fridaAbi = getFridaAbiFormat(desiredAbi)

                val asset = release.androidServerAssetFor(fridaAbi) ?: return@withContext null

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
    fun androidServerAssetFor(fridaAbi: String): ReleaseAsset? =
        assets.find { it.isAndroidServerAssetFor(fridaAbi) }

    fun hasAndroidServerAsset(): Boolean =
        assets.any { it.isAndroidServerAsset() }

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

data class RemoteReleaseCache(
    val fetchedAt: Long,
    val releases: List<RemoteRelease>,
    val nextPage: Int?,
    val nextUrl: String?,
    val hasMore: Boolean
)

private data class CachedRemoteReleases(
    val cache: RemoteReleaseCache,
    val hasPaginationMetadata: Boolean
)

private data class ReleaseNetworkPage(
    val releases: List<RemoteRelease>,
    val nextPage: Int?,
    val nextUrl: String?,
    val hasMore: Boolean
) {
    fun toCache() = RemoteReleaseCache(
        fetchedAt = System.currentTimeMillis(),
        releases = releases,
        nextPage = nextPage,
        nextUrl = nextUrl,
        hasMore = hasMore
    )
}

data class ReleaseAsset(
    val name: String,
    val url: String,
    val size: Long
) {
    fun isAndroidServerAsset(): Boolean {
        val lowerName = name.lowercase()
        return lowerName.contains("frida-server") &&
                lowerName.contains("android") &&
                lowerName.endsWith(".xz")
    }

    fun isAndroidServerAssetFor(fridaAbi: String): Boolean {
        val lowerName = name.lowercase()
        return isAndroidServerAsset() &&
                lowerName.endsWith("android-${fridaAbi.lowercase()}.xz")
    }

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
