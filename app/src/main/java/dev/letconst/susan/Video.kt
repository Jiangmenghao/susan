package dev.letconst.susan

data class EpisodeItem(
    val title: String,
    val url: String,
    val active: Boolean?
)

data class Episodes(
    val coverImage: String,
    val title: String,
    val subtitles: List<String>,
    val description: String,
    val episodes: List<EpisodeItem>
)

data class Video(
    val url: String,
    val name: String?,
    val current: String?,
    val next: String?,
    val ggdmapi: String?,
    val episodes: Episodes?
)