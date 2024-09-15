package dev.letconst.susan

data class SearchResultItem(
    val id: Int,
    val name: String,
    val typeName: String,
    val remarks: String
)

data class PlatformUrls(
    val platform: String,
    val urls: List<EpisodeItem>
)

data class VideoDetail(
    val id: Int,
    val name: String,
    val blurb: String? = null,
    val pic: String? = null,
    val genres: String? = null,
    val area: String? = null,
    val year: String? = null,
    val remarks: String? = null,
    val score: String? = null,
    val episodes: List<PlatformUrls>? = null
)