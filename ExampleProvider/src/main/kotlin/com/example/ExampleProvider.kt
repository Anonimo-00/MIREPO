package com.example

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import org.jsoup.nodes.Element
import java.net.URLEncoder

class PelisPlusHDProvider : MainAPI() {
    override var mainUrl = "https://pelisplushd.mx"
    override var name = "PelisPlusHD"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "es"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val items = doc.select("article, .post, .item, .movie-card, .movie-item").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(
            listOf(
                HomePageList("Destacadas", items, isHorizontal = true)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val doc = app.get("$mainUrl/?s=$encodedQuery").document
        return doc.select("article, .post, .item, .movie-card, .movie-item").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1, .entry-title, .title")?.text()?.trim()
            ?: "PelisPlusHD"

        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("img")?.attr("src")
            ?: ""

        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst(".description, .sinopsis, .entry-content")?.text()?.trim()
            ?: ""

        val year = Regex("(19|20)\\d{2}").find(doc.text())?.value?.toIntOrNull()
        val isSeries = doc.selectFirst(".episodes, .season, .temporada") != null ||
            doc.text().contains("temporada", ignoreCase = true)

        if (isSeries) {
            val episodes = doc.select("a[href]")
                .mapNotNull { link ->
                    val href = link.attr("href")
                    val name = link.text().trim()
                    if (href.isBlank() || name.isBlank() || !href.startsWith("http")) null else {
                        Episode(href, name)
                    }
                }
                .distinctBy { it.episodeUrl }
                .takeIf { it.isNotEmpty() }
                ?: emptyList()

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.backgroundPosterUrl = poster
                this.plot = plot
                this.year = year
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.backgroundPosterUrl = poster
            this.plot = plot
            this.year = year
        }
    }

    private fun Element.toSearchResponse(): SearchResponse? {
        val href = selectFirst("a[href]")?.attr("href") ?: return null
        val title = selectFirst("h2, h3, h1, .title, .entry-title")?.text()?.trim() ?: return null
        val poster = selectFirst("img")?.attr("src")
            ?: selectFirst("img")?.attr("data-src")
            ?: ""

        if (href.isBlank() || title.isBlank()) return null

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = poster
        }
    }
}
