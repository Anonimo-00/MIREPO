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
    override var mainUrl = "https://pelisplushd.la"
    override var name = "PelisPlusHD"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "es"
    override val hasMainPage = true
    override val hasQuickSearch = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document

        val items = doc.select("article, .post, .item, .movie-card, .movie-item, .movies-list .item")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }

        return newHomePageResponse(
            listOf(
                HomePageList("Destacadas", items, isHorizontal = true)
            )
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val doc = app.get("$mainUrl/search?s=$encoded").document

        return doc.select("article, .post, .item, .movie-card, .movie-item, .movies-list .item")
            .mapNotNull { it.toSearchResponse() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1, .entry-title, .title, .movie-title")?.text()?.trim()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()
            ?: "PelisPlusHD"

        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("img")?.attr("src")
            ?: doc.selectFirst("img")?.attr("data-src")
            ?: ""

        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")
            ?: doc.selectFirst(".description, .sinopsis, .entry-content, .movie-description")?.text()?.trim()
            ?: ""

        val year = Regex("(19|20)\\d{2}").find(doc.text())?.value?.toIntOrNull()

        val isSeries = doc.selectFirst(".episodes, .temporada, .season, .seasons, [class*=season], [class*=episode]") != null ||
            doc.text().contains("temporada", ignoreCase = true) ||
            url.contains("/serie", ignoreCase = true) ||
            url.contains("/series", ignoreCase = true)

        if (isSeries) {
            val episodes = doc.select("a[href]")
                .mapNotNull { link ->
                    val href = link.attr("href")
                    val text = link.text().trim()
                    if (href.isBlank() || text.isBlank() || !href.startsWith("http")) null else Episode(href, text)
                }
                .filter { episode ->
                    val u = episode.episodeUrl.lowercase()
                    u.contains("temporada") || u.contains("episodio") || u.contains("capitulo") || u.contains("episode") || u.contains("play") || u.contains("ver-")
                }
                .distinctBy { it.episodeUrl }
                .ifEmpty { listOf(Episode(url, title)) }

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
        val title = selectFirst("h2, h3, h1, .title, .entry-title, .movie-title")?.text()?.trim()
            ?: return null

        val poster = selectFirst("img")?.attr("src")
            ?: selectFirst("img")?.attr("data-src")
            ?: ""

        if (href.isBlank() || title.isBlank()) return null

        val uts = href.lowercase()
        val type = if (uts.contains("/serie") || uts.contains("/series") || select(".season, .episodes").isNotEmpty()) {
            TvType.TvSeries
        } else {
            TvType.Movie
        }

        return newMovieSearchResponse(title, href, type) {
            this.posterUrl = poster
        }
    }
}
