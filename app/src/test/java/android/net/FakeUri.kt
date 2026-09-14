package android.net

import android.os.Parcel

class FakeUri(private val uriString: String) : Uri() {
    override fun toString(): String = uriString
    override fun getScheme(): String? = if (uriString.contains("://")) uriString.substringBefore("://") else null
    override fun getPath(): String? = if (uriString.contains("://")) "/" + uriString.substringAfter("://").substringAfter("/", "") else uriString
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getAuthority(): String? = null
    override fun getUserInfo(): String? = null
    override fun getHost(): String? = null
    override fun getPort(): Int = -1
    override fun getQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getQueryParameter(key: String?): String? = null
    override fun getQueryParameters(key: String?): MutableList<String> = mutableListOf()
    override fun getEncodedPath(): String? = getPath()
    override fun getEncodedQuery(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getEncodedAuthority(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getSchemeSpecificPart(): String? = null
    override fun getEncodedSchemeSpecificPart(): String? = null
    override fun getPathSegments(): MutableList<String> = mutableListOf()
    override fun getLastPathSegment(): String? = null
    override fun buildUpon(): Builder? = null
    override fun compareTo(other: Uri?): Int = uriString.compareTo(other?.toString().orEmpty())
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}
}
