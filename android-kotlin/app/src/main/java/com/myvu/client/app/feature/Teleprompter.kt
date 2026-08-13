package com.myvu.client.app.feature

import com.myvu.client.app.AppLayer
import org.json.JSONException
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Teleprompter ("tici"), ported from applayer.open_teleprompter.
 */
object Teleprompter {
    const val OPEN_TO_CONTENT_DELAY_MS: Long = 400
    private const val DEFAULT_TITLE = "Prompter"

    @JvmStatic
    fun fileKeyFor(title: String): String = "1/$title"

    @JvmStatic
    @JvmOverloads
    @Throws(JSONException::class)
    fun buildOpen(text: String, title: String = DEFAULT_TITLE): String {
        val fileKey = fileKeyFor(title)
        val ext = JSONObject()
            .put("blockNotification", true)
            .put("currentPage", 0)
            .put("fileKey", fileKey)
            .put("msgId", UUID.randomUUID().toString())
            .put("nextTotalParagraphSize", 0)
            .put("paragraphIndex", 0)
            .put("prevTotalParagraphSize", 0)
            .put("screenLocation", 0)
            .put("sourceByteSize", text.toByteArray(StandardCharsets.UTF_8).size)
            .put("sourceTextOffset", 0)
            .put("ticiMode", 0)
            .put("ticiSpeed", 10000)
            .put("totalPage", 1)
            .put("totalPart", 1)
            .put("totalTextLength", text.length)
            .put("version", 2)

        return JSONObject()
            .put("action", "app")
            .put("data", JSONObject()
                .put("launchMode", "scene")
                .put("action", "open_app")
                .put("pkg", AppLayer.PKG_TICI)
                .put("app_name", AppLayer.PKG_TICI)
                .put("ext", ext.toString()))
            .toString()
    }

    @JvmStatic
    @JvmOverloads
    @Throws(JSONException::class)
    fun buildContent(text: String, title: String = DEFAULT_TITLE): String {
        val content = JSONObject()
            .put("currentPage", 0)
            .put("fileKey", fileKeyFor(title))
            .put("msgId", UUID.randomUUID().toString())
            .put("part", 0)
            .put("sourceText", text)

        return JSONObject()
            .put("action", "tici")
            .put("data", JSONObject()
                .put("action", "send_content")
                .put("value", content.toString()))
            .toString()
    }

    @JvmStatic
    @JvmOverloads
    @Throws(JSONException::class)
    fun buildHighlight(index: Int, title: String = DEFAULT_TITLE): String {
        val value = JSONObject()
            .put("index", index)
            .put("fileKey", fileKeyFor(title))

        return JSONObject()
            .put("action", "tici")
            .put("data", JSONObject()
                .put("action", "highlight_index")
                .put("value", value.toString()))
            .toString()
    }
}
