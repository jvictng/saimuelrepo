package com.AnimesCloud

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import java.util.*
import kotlinx.coroutines.delay

class AnimesCloud : MainAPI() {
    override var mainUrl = "https://animesonline.cloud"
    override var name = "AnimesCloud"
    override val hasMainPage = true
    override var lang = "pt-br"
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie)

    override val mainPage = mainPageOf(
        "genre/acao" to "Ação",
        "genre/aventura" to "Aventura",
        "genre/comedia" to "Comédia",
        "genre/terror" to "Terror",
        "genre/fantasia" to "Fantasia",
        "genre/drama" to "Drama",
        "genre/misterio" to "Mistério",
        "genre/ecchi" to "Ecchi"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl/${request.data}"
        val document = app.get(url).document
        val home = document.select("div.items.full article.item")
            .mapNotNull { it.toSearchResult() }
        
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select("div.data h3 a").text().trim()
        val href = fixUrl(this.select("div.data h3 a").attr("href"))
        val posterUrl = this.select("div.poster img").attr("src")
        val yearText = this.select("div.data span").text().trim()
        val year = extractYearFromText(yearText)
        
        val ratingText = this.select("div.rating").text().trim()
        val rating = ratingText.toFloatOrNull()
        
        val isMovie = this.hasClass("movies")
        val type = if (isMovie) TvType.AnimeMovie else TvType.Anime
        
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=${query.replace(" ", "+")}"
        val document = app.get(url).document
        return document.select("div.search-page div.result-item article")
            .mapNotNull { it.toSearchResultFromSearch() }
    }

    private fun Element.toSearchResultFromSearch(): SearchResponse? {
        val title = this.select("div.details div.title a").text().trim()
        val href = fixUrl(this.select("div.details div.title a").attr("href"))
        val posterUrl = this.select("div.image img").attr("src")
        val yearText = this.select("div.meta span.year").text().trim()
        val year = yearText.toIntOrNull()
        
        val ratingText = this.select("div.meta span.rating").text().replace("IMDb ", "").trim()
        val rating = ratingText.toFloatOrNull()
        
        val typeText = this.select("div.image span").text().trim()
        val type = if (typeText == "TV") TvType.Anime else TvType.AnimeMovie
        
        return newAnimeSearchResponse(title, href, type) {
            this.posterUrl = posterUrl
            this.year = year
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        
        val title = document.selectFirst("h1")?.text()?.trim() ?: ""
        

        
        delay(1000)
        
        val owlWrapper = document.selectFirst("div.owl-wrapper")
        
        if (owlWrapper != null) {
            val owlItems = owlWrapper.select("div.owl-item")
            
            if (owlItems.isNotEmpty()) {
                val firstItem = owlItems.first()
                val gItem = firstItem?.selectFirst("div.g-item")
                
                if (gItem != null) {
                    val linkElement = gItem.selectFirst("a")
                    val imgElement = gItem.selectFirst("img")
                    
                    if (linkElement != null) {
                        val hrefValue = linkElement.attr("href")
                    }
                    
                    if (imgElement != null) {
                        val srcValue = imgElement.attr("src")
                    }
                }
            }
        }
        
        var poster: String? = null
        
        val pageHtml = document.html()
        val posterRegex = Regex("""https://image\.tmdb\.org/t/p/original/[^"'\s]+""")
        val posterMatch = posterRegex.find(pageHtml)
        
        if (posterMatch != null) {
            poster = posterMatch.value
        }
        val description = extractDescription(document)
        val year = extractYear(document)
        val duration = extractDuration(document)
        val genres = extractGenres(document)
        val actors = extractActors(document)
        val trailer = extractTrailer(document, url)
        val rating = extractRating(document)
        
        val hasEpisodesSection = document.select("div#episodes").isNotEmpty()
        val isMovie = url.contains("/filme/") || !hasEpisodesSection
        val type = if (isMovie) TvType.AnimeMovie else TvType.Anime
        
        return if (isMovie) {
            newMovieLoadResponse(title, url, type, url) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                if (duration != null) addDuration(duration.toString())
                this.tags = genres
                addActors(actors)
                addTrailer(trailer)
                this.dataUrl = url
            }
        } else {
            val episodes = loadEpisodesFromPage(document, url)
            newAnimeLoadResponse(title, url, type) {
                this.posterUrl = poster
                this.plot = description
                this.year = year
                if (duration != null) addDuration(duration.toString())
                this.tags = genres
                addActors(actors)
                addTrailer(trailer)
                addEpisodes(DubStatus.Subbed, episodes[DubStatus.Subbed] ?: emptyList())
                addEpisodes(DubStatus.Dubbed, episodes[DubStatus.Dubbed] ?: emptyList())
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return AnimesCloudExtractor.extractVideoLinks(data, mainUrl, name, callback)
    }
    
    private fun extractYearFromText(text: String): Int? {
        val yearRegex = Regex("""(\d{4})""")
        val match = yearRegex.find(text)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
    
    private fun extractDescription(document: Document): String? {
        val descriptionElement = document.selectFirst("div.wp-content")
        return if (descriptionElement != null) {
            val paragraphs = descriptionElement.select("p").map { it.text().trim() }
            if (paragraphs.size >= 2) {
                paragraphs[1] 
            } else if (paragraphs.isNotEmpty()) {
                paragraphs[0]
            } else {
                descriptionElement.text().trim()
            }
        } else null
    }
    
    private fun extractYear(document: Document): Int? {
        val yearElements = document.select("p")
        for (element in yearElements) {
            val text = element.text()
            if (text.contains("Ano de Lançamento:")) {
                val yearRegex = Regex("""Ano de Lançamento:\s*(\d{4})""")
                val match = yearRegex.find(text)
                return match?.groupValues?.get(1)?.toIntOrNull()
            }
        }
        return null
    }
    
    private fun extractDuration(document: Document): Int? {
        val durationElement = document.selectFirst("div.custom_fields b.variante")
        if (durationElement != null && durationElement.text().contains("Duração do Episódio")) {
            val durationText = durationElement.nextElementSibling()?.select("span.valor")?.text()?.trim()
            if (durationText != null) {
                return parseDuration(durationText)
            }
        }
        return null
    }
    
    private fun extractGenres(document: Document): MutableList<String> {
        return document.select("div.sgeneros a")
            .map { it.text().trim() }
            .filter { 
                val lower = it.lowercase()
                lower != "letra d" && lower != "letra l" && 
                lower != "dublado" && lower != "legendado"
            }
            .distinct()
            .toMutableList()
    }
    
    private suspend fun extractActors(document: Document): MutableList<Actor> {
        val actors = mutableListOf<Actor>()
        val actorsSection = document.select("div.persons div.person")
        
        for (actorElement in actorsSection) {
            val actorName = actorElement.select("div.data div.name a").text().trim()
            val characterName = actorElement.select("div.data div.caracter").text().trim()
            val actorImage = actorElement.select("div.img img").attr("src")
            
            if (actorName.isNotEmpty()) {
                actors.add(Actor(actorName, actorImage))
            }
        }
        
        return actors
    }
    
    private suspend fun extractTrailer(document: Document, url: String): String? {
        try {
            val isMovie = url.contains("/filme/") || document.select("div#episodes").isEmpty()
            
            if (isMovie) {
                val playerOptions = document.select("ul#playeroptionsul li.dooplay_player_option")
                val trailerOption = playerOptions.find { it.attr("data-nume") == "trailer" }
                
                if (trailerOption != null) {
                    val dataPost = trailerOption.attr("data-post")
                    val dataType = trailerOption.attr("data-type")
                    
                    if (dataPost.isNotEmpty() && dataType == "movie") {
                        val trailerApiUrl = "$mainUrl/wp-json/dooplayer/v2/$dataPost/movie/trailer"
                        
                        try {
                            val trailerResponse = app.get(trailerApiUrl).text
                            val embedUrlMatch = Regex("\"embed_url\":\"([^\"]+)\"").find(trailerResponse)
                            if (embedUrlMatch != null) {
                                val embedUrl = embedUrlMatch.groupValues[1]
                                    .replace("\\/", "/")
                                    .replace("\\", "")
                                
                                if (embedUrl.contains("youtube.com")) {
                                    return embedUrl
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            
            val iframeElement = document.selectFirst("div.embed iframe")
            if (iframeElement != null) {
                val src = iframeElement.attr("src")
                if (src.contains("youtube.com")) {
                    return src
                }
            }
        } catch (e: Exception) {
        }
        return null
    }
    
    private fun extractRating(document: Document): Float? {
        val ratingElement = document.selectFirst("div.rating")
        val ratingText = ratingElement?.text()?.trim()
        return ratingText?.toFloatOrNull()
    }
    
    private suspend fun loadEpisodesFromPage(document: Document, baseUrl: String): MutableMap<DubStatus, List<Episode>> {
        val episodes = mutableMapOf<DubStatus, List<Episode>>()
        val episodeElements = document.select("div#episodes ul.episodios li")
        
        val episodeList = mutableListOf<Episode>()
        
        for (episodeElement in episodeElements) {
            val episodeUrl = fixUrl(episodeElement.select("div.episodiotitle a").attr("href"))
            val episodeTitle = episodeElement.select("div.episodiotitle a").text().trim()
            val episodeImage = episodeElement.select("div.imagen img").attr("src")
            val episodeNumberText = episodeElement.select("div.numerando").text().trim()
            
            val episodeNumberMatch = Regex("""(\d+)\s*-\s*(\d+)""").find(episodeNumberText)
            val seasonNumber = episodeNumberMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            val episodeNumber = episodeNumberMatch?.groupValues?.get(2)?.toIntOrNull() ?: 1
            
            val episode = newEpisode(episodeUrl) {
                this.name = episodeTitle
                this.episode = episodeNumber
                this.season = seasonNumber
                this.posterUrl = episodeImage
            }
            
            episodeList.add(episode)
        }
        
        val dubStatus = if (baseUrl.contains("dublado", ignoreCase = true)) {
            DubStatus.Dubbed
        } else {
            DubStatus.Subbed
        }
        
        episodes[dubStatus] = episodeList
        return episodes
    }
    
    private fun parseDuration(durationText: String): Int? {
        return try {
            val minutesMatch = Regex("""(\d+)\s*minutes?""").find(durationText)
            minutesMatch?.groupValues?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            null
        }
    }
    

}