package cvam.dignity.juganua.common

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class PinnedToolInfo(
    val title: String,
    val key: String,
    val subtitle: String = ""
)

object UsageTracker {

    // Dashboard / core tools
    const val ID_PDF_UNLOCKER = "pdf_unlocker"
    const val ID_MERGE_PDF = "merge_pdf"
    const val ID_ID_CARD_SPLITTER = "id_card_splitter"
    const val ID_IPPB_CARD_QR = "ippb_card_qr"

    // Share / Intent Hub tools
    const val ID_PASSPORT_PHOTO = "passport_photo"
    const val ID_OFFLINE_SHARE = "offline_share"

    private const val PREFS_NAME = "juganua_usage"
    private const val KEY_USAGE = "tool_usage"
    private const val KEY_FAVORITES = "favorites"

    /*
     * Kept for compatibility with existing code.
     * MainActivity no longer needs a dynamic composable registry.
     */
    val composableRegistry =
        mutableMapOf<String, Any>()

    fun recordUsage(context: Context, toolKey: String) {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val usage = getUsage(context).toMutableMap()
        usage[toolKey] = (usage[toolKey] ?: 0) + 1

        val json = JSONObject()

        usage.forEach { (key, value) ->
            json.put(key, value)
        }

        prefs.edit()
            .putString(KEY_USAGE, json.toString())
            .apply()
    }

    fun getUsage(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val raw = prefs.getString(KEY_USAGE, null)
            ?: return emptyMap()

        return try {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, Int>()

            json.keys().forEach { key ->
                result[key] = json.optInt(key, 0)
            }

            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun pinFavorite(
        context: Context,
        tool: PinnedToolInfo
    ) {
        val favorites = getFavorites(context).toMutableMap()

        favorites[tool.key] = tool

        saveFavorites(context, favorites)
    }

    fun unpinFavorite(
        context: Context,
        key: String
    ) {
        val favorites = getFavorites(context).toMutableMap()

        favorites.remove(key)

        saveFavorites(context, favorites)
    }

    fun isPinned(
        context: Context,
        key: String
    ): Boolean {
        return getFavorites(context).containsKey(key)
    }

    fun getFavorites(
        context: Context
    ): Map<String, PinnedToolInfo> {
        val prefs = context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

        val raw = prefs.getString(
            KEY_FAVORITES,
            null
        ) ?: return emptyMap()

        return try {
            val array = JSONArray(raw)
            val result = mutableMapOf<String, PinnedToolInfo>()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)

                val key = item.optString("key")
                val title = item.optString("title")
                val subtitle = item.optString("subtitle")

                if (key.isNotBlank()) {
                    result[key] = PinnedToolInfo(
                        title = title,
                        key = key,
                        subtitle = subtitle
                    )
                }
            }

            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveFavorites(
        context: Context,
        favorites: Map<String, PinnedToolInfo>
    ) {
        val array = JSONArray()

        favorites.values.forEach { tool ->
            array.put(
                JSONObject().apply {
                    put("title", tool.title)
                    put("key", tool.key)
                    put("subtitle", tool.subtitle)
                }
            )
        }

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_FAVORITES,
                array.toString()
            )
            .apply()
    }
}