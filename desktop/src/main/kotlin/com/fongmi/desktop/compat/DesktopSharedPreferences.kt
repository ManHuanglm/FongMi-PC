package com.fongmi.desktop.compat

import android.content.SharedPreferences
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 桌面端 SharedPreferences 实现：
 * 文件格式与安卓 SharedPreferences XML 一致，落地 %APPDATA%/FongMiTV/prefs.xml，key 与安卓端保持一致
 */
class DesktopSharedPreferences private constructor(private val file: File) : SharedPreferences {

    companion object {

        @Volatile
        private var instance: DesktopSharedPreferences? = null

        @JvmStatic
        fun getInstance(): DesktopSharedPreferences {
            return instance ?: synchronized(this) {
                instance ?: DesktopSharedPreferences(File(DesktopPaths.configDir(), "prefs.xml")).also { instance = it }
            }
        }
    }

    private val values = ConcurrentHashMap<String, Any>()
    private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    init {
        load()
    }

    private fun load() {
        if (!file.exists()) return
        runCatching {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val document = factory.newDocumentBuilder().parse(file)
            val nodes = document.getElementsByTagName("entry") ?: return
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val element = node as? org.w3c.dom.Element ?: continue
                val key = element.getAttribute("name") ?: continue
                val type = element.getAttribute("type")
                val text = element.textContent
                values[key] = when (type) {
                    "int" -> text.toIntOrNull() ?: 0
                    "long" -> text.toLongOrNull() ?: 0L
                    "float" -> text.toFloatOrNull() ?: 0f
                    "boolean" -> text.toBooleanStrictOrNull() ?: false
                    else -> text
                }
            }
        }
    }

    @Synchronized
    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val document = factory.newDocumentBuilder().newDocument()
            val map = document.createElement("map")
            values.entries.sortedBy { it.key }.forEach { (key, value) ->
                val entry = document.createElement("entry")
                entry.setAttribute("name", key)
                when (value) {
                    is Int -> entry.setAttribute("type", "int")
                    is Long -> entry.setAttribute("type", "long")
                    is Float -> entry.setAttribute("type", "float")
                    is Boolean -> entry.setAttribute("type", "boolean")
                }
                entry.appendChild(document.createTextNode(value.toString()))
                map.appendChild(entry)
            }
            document.appendChild(map)
            val transformer = javax.xml.transform.TransformerFactory.newInstance().newTransformer()
            transformer.transform(javax.xml.transform.dom.DOMSource(document), javax.xml.transform.stream.StreamResult(file))
        }
    }

    override fun getAll(): Map<String, *> = HashMap(values)

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = EditorImpl()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.add(listener)
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }

    private inner class EditorImpl : SharedPreferences.Editor {

        private val changes = LinkedHashMap<String, Any?>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { changes[key] = value }

        override fun putStringSet(key: String, values: MutableSet<String>?): SharedPreferences.Editor = this

        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { changes[key] = value }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { changes[key] = value }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { changes[key] = value }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { changes[key] = value }

        override fun remove(key: String): SharedPreferences.Editor = apply { changes[key] = null }

        override fun clear(): SharedPreferences.Editor = apply { values.clear() }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            synchronized(this@DesktopSharedPreferences) {
                changes.forEach { (key, value) ->
                    if (value == null) values.remove(key) else values[key] = value
                }
                persist()
                changes.clear()
            }
        }
    }
}
