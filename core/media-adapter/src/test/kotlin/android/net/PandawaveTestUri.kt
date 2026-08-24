package android.net

import android.os.Parcel

internal object PandawaveTestUri : Uri() {
    private const val VALUE = "https://example.test/track-1.mp3"

    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun getAuthority(): String = "example.test"
    override fun getEncodedAuthority(): String = "example.test"
    override fun getEncodedFragment(): String? = null
    override fun getEncodedPath(): String = "/track-1.mp3"
    override fun getEncodedQuery(): String? = null
    override fun getEncodedSchemeSpecificPart(): String = "//example.test/track-1.mp3"
    override fun getEncodedUserInfo(): String? = null
    override fun getFragment(): String? = null
    override fun getHost(): String = "example.test"
    override fun getLastPathSegment(): String = "track-1.mp3"
    override fun getPath(): String = "/track-1.mp3"
    override fun getPathSegments(): List<String> = listOf("track-1.mp3")
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getScheme(): String = "https"
    override fun getSchemeSpecificPart(): String = "//example.test/track-1.mp3"
    override fun getUserInfo(): String? = null
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun toString(): String = VALUE
    override fun equals(other: Any?): Boolean = other is Uri && other.toString() == VALUE
    override fun hashCode(): Int = VALUE.hashCode()
    override fun describeContents(): Int = 0
    override fun writeToParcel(parcel: Parcel, flags: Int) = Unit
}
