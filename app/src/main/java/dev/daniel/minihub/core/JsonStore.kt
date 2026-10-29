package dev.daniel.minihub.core

import android.content.Context
import org.json.JSONObject
import java.io.File

/** One JSON file per mini app in the app's private storage. Writes are atomic (tmp + rename). */
class JsonStore(context: Context, name: String) {
    private val dir = File(context.filesDir, "miniapps").apply { mkdirs() }
    private val file = File(dir, "$name.json")

    fun read(): JSONObject = try {
        if (file.exists()) JSONObject(file.readText()) else JSONObject()
    } catch (e: Exception) {
        JSONObject()
    }

    fun write(obj: JSONObject) {
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(obj.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(obj.toString())
            tmp.delete()
        }
    }
}
