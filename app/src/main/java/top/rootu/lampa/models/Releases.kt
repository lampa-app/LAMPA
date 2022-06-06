package top.rootu.lampa.models

class Releases : ArrayList<Release>()

data class Release(
    val url: String,
    val assets_url: String,
    val upload_url: String,
    val html_url: String,
    val id: Int,
    val author: Any?,
    val node_id: String,
    val tag_name: String,
    val target_commitish: String,
    val name: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val created_at: String,
    val published_at: String,
    val assets: ArrayList<Asset>,
    val tarball_url: String,
    val zipball_url: String,
    val body: String,
)

data class Asset(
    val url: String,
    val id: Int,
    val node_id: String,
    val name: String,
    val label: String?,
    val uploader: Any?,
    val content_type: String,
    val state: String,
    val size: Int,
    val download_count: Int,
    val created_at: String,
    val updated_at: String,
    val browser_download_url: String,
)