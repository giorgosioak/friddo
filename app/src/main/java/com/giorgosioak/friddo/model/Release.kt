package com.giorgosioak.friddo.model

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Url
import java.time.Instant

data class Release (
    // Variables
    @SerializedName("id")  var id: Int,
    @SerializedName("name") var name: String,
    @SerializedName("tag_name") var tagName: String,
    @SerializedName("html_url") var htmlUrl: String,
    @SerializedName("created_at") var createdAt: Instant,
    @SerializedName("assets") var assets: List<Asset>
)

data class Asset (
    @SerializedName("name") var name: String = "",
    @SerializedName("url") var url: String = "",
    @SerializedName("size") var size: Int = 0,
    @SerializedName("download_url") var downloadUrl: String = ""
)

// TODO: get data from github ( https://api.github.com/repos/frida/frida/releases )
// TODO: show them in ui
interface GitHubService {
    @GET("repos/{owner}/{repo}/releases")
    fun getReleases(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): Observable<List<Release>>
}

object RetrofitClient {
    private const val BASE_URL = "https://api.github.com/"

    val instance: GitHubService by lazy {
        val gson = GsonBuilder().setLenient().create()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava3CallAdapterFactory.createWithScheduler(Schedulers.io()))
            .build()
            .create(GitHubService::class.java)
    }
}