package cvam.dignity.juganua.common

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PinnedToolInfo(
    val title: String,
    val key: String,
    val subtitle: String = "Pinned Tool"
)

object UsageTracker {
    private const val PREF_NAME = "juganua_prefs"
    private const val KEY_FAVORITES = "pinned_favorites_json"
    private const val KEY_ANIM_TYPE = "animation_preference"

    const val ANIM_FADE = "Fade"
    const val ANIM_SLIDE = "Slide"
    const val ANIM_ZOOM = "Zoom"

    const val ID_AADHAAR_QR = "aadhaar_qr"
    const val ID_AADHAAR_STATUS = "aadhaar_status"
    const val ID_AADHAAR_LOGIN = "aadhaar_login"
    const val ID_PDF_UNLOCKER = "pdf_unlocker"
    const val ID_ID_CARD_SPLITTER = "extract_id"
    const val ID_MERGE_PDF = "merge_pdf"
    const val ID_PASSPORT_PHOTO = "passport_photo"
    const val ID_ARTICLE_SCAN = "article_scan"
    const val ID_RPLI_CALC = "rpli_calc"
    const val ID_IPPB_CARD_QR = "ippb_card_qr"
    const val ID_OFFLINE_SHARE = "offline_share"
    const val ID_WA_CHECKER = "wa_checker"
    // NEW TOOL ID
    const val ID_IPPB_REGISTER = "ippb_register"

    val composableRegistry = mutableMapOf<String, @Composable (
        selectedUri: Any?,
        allUris: List<Any>?,
        onBack: () -> Unit,
        onNavigate: (String, Any?) -> Unit
    ) -> Unit>()

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getFavorites(context: Context): Map<String, PinnedToolInfo> {
        val json = getPrefs(context).getString(KEY_FAVORITES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, PinnedToolInfo>>() {}.type
            Gson().fromJson(json, type)
        } catch (e: Exception) { emptyMap() }
    }

    fun pinFavorite(context: Context, tool: PinnedToolInfo) {
        val favs = getFavorites(context).toMutableMap(); favs[tool.key] = tool
        val json = Gson().toJson(favs); getPrefs(context).edit().putString(KEY_FAVORITES, json).apply()
    }

    fun unpinFavorite(context: Context, key: String) {
        val favs = getFavorites(context).toMutableMap(); favs.remove(key)
        val json = Gson().toJson(favs); getPrefs(context).edit().putString(KEY_FAVORITES, json).apply()
    }

    fun isPinned(context: Context, key: String): Boolean = getFavorites(context).containsKey(key)
}