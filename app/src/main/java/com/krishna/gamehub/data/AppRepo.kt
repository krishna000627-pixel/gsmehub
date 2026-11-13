package com.krishna.gamehub.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.room.Room
import com.krishna.gamehub.core.Actions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object AppRepo {
    lateinit var ctx: Context
        private set
    private lateinit var db: Db

    val dao: AppDao get() = db.dao()
    val apps: Flow<List<AppEntity>> get() = dao.observeAll()
    val appsDir: File get() = File(ctx.filesDir, "apps")
    val shotsDir: File get() = File(ctx.filesDir, "shots").also { it.mkdirs() }

    private val rasterExt = setOf("png", "jpg", "jpeg", "webp", "gif")

    fun init(c: Context) {
        ctx = c.applicationContext
        db = Room.databaseBuilder(ctx, Db::class.java, "gamehub.db").build()
        appsDir.mkdirs()
        appsDir.listFiles()?.filter { it.name.startsWith(".tmp-") }?.forEach { it.deleteRecursively() }
    }

    fun dirOf(id: String) = File(appsDir, id)
    fun hostOf(id: String) = "$id.appassets.androidplatform.net"

    private fun newId() = UUID.randomUUID().toString().replace("-", "").take(10)

    // ---------- import ----------

    suspend fun importZip(uri: Uri): Result<AppEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val id = newId()
            val tmp = File(appsDir, ".tmp-$id").apply { deleteRecursively(); mkdirs() }
            try {
                val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
                input.use { unzip(it, tmp) }
                finish(id, tmp, displayName(uri)?.removeSuffix(".zip")?.removeSuffix(".ZIP"))
            } catch (e: Throwable) {
                tmp.deleteRecursively()
                throw e
            }
        }
    }

    suspend fun importFolder(tree: Uri): Result<AppEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val id = newId()
            val tmp = File(appsDir, ".tmp-$id").apply { deleteRecursively(); mkdirs() }
            try {
                val root = DocumentFile.fromTreeUri(ctx, tree) ?: throw IOException("Cannot open folder")
                copyTree(root, tmp)
                finish(id, tmp, root.name)
            } catch (e: Throwable) {
                tmp.deleteRecursively()
                throw e
            }
        }
    }

    suspend fun importHtml(uri: Uri): Result<AppEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val id = newId()
            val tmp = File(appsDir, ".tmp-$id").apply { deleteRecursively(); mkdirs() }
            try {
                val input = ctx.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
                input.use { i -> File(tmp, "index.html").outputStream().use { o -> i.copyTo(o) } }
                val name = displayName(uri)?.substringBeforeLast('.', "")?.ifBlank { null }
                finish(id, tmp, name)
            } catch (e: Throwable) {
                tmp.deleteRecursively()
                throw e
            }
        }
    }

    suspend fun importPasted(html: String): Result<AppEntity> = withContext(Dispatchers.IO) {
        runCatching {
            if (html.isBlank()) throw IOException("Nothing to import")
            val id = newId()
            val tmp = File(appsDir, ".tmp-$id").apply { deleteRecursively(); mkdirs() }
            try {
                File(tmp, "index.html").writeText(html)
                finish(id, tmp, "Pasted app")
            } catch (e: Throwable) {
                tmp.deleteRecursively()
                throw e
            }
        }
    }

    private suspend fun finish(id: String, tmp: File, fallbackName: String?): AppEntity {
        flatten(tmp)
        val entity = buildEntity(id, tmp, fallbackName)
        if (!tmp.renameTo(dirOf(id))) throw IOException("Could not store app")
        dao.upsert(entity)
        return entity
    }

    private fun unzip(input: InputStream, dest: File) {
        val root = dest.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(input)).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val out = File(dest, e.name)
                if (!out.canonicalPath.startsWith(root)) throw IOException("Unsafe path in ZIP")
                val skip = e.name.startsWith("__MACOSX/") || File(e.name).name.startsWith("._")
                if (e.isDirectory) {
                    if (!skip) out.mkdirs()
                } else if (!skip) {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zin.copyTo(it) }
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
    }

    private fun copyTree(src: DocumentFile, dest: File) {
        dest.mkdirs()
        for (child in src.listFiles()) {
            val name = child.name ?: continue
            if (name.contains('/') || name == ".." || name == ".") continue
            val target = File(dest, name)
            if (child.isDirectory) {
                copyTree(child, target)
            } else {
                ctx.contentResolver.openInputStream(child.uri)?.use { i ->
                    target.outputStream().use { o -> i.copyTo(o) }
                }
            }
        }
    }

    private fun flatten(dir: File) {
        val kids = dir.listFiles()?.filter { it.name != "__MACOSX" } ?: return
        if (kids.size == 1 && kids[0].isDirectory) {
            val inner = File(dir, ".inner")
            kids[0].renameTo(inner)
            inner.listFiles()?.forEach { it.renameTo(File(dir, it.name)) }
            inner.deleteRecursively()
        }
        File(dir, "__MACOSX").deleteRecursively()
    }

    private fun displayName(uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    // ---------- metadata ----------

    private fun findEntry(dir: File): String {
        if (File(dir, "index.html").isFile) return "index.html"
        val html = dir.walkTopDown()
            .filter { it.isFile && (it.extension.equals("html", true) || it.extension.equals("htm", true)) }
            .sortedBy { it.relativeTo(dir).path.length }
            .firstOrNull() ?: throw IOException("No HTML file found")
        return html.relativeTo(dir).invariantSeparatorsPath
    }

    private fun head(f: File): String = runCatching {
        f.inputStream().use { s ->
            val buf = ByteArray(65536)
            val n = s.read(buf)
            if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
        }
    }.getOrDefault("")

    private fun decode(s: String) = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").trim()

    private fun rasterFile(dir: File, base: File, ref: String): String? {
        if (ref.startsWith("http", true) || ref.startsWith("data:", true) || ref.startsWith("//")) return null
        val clean = ref.substringBefore('?').substringBefore('#')
        if (clean.isBlank()) return null
        val f = if (clean.startsWith("/")) File(dir, clean.trimStart('/')) else File(base, clean)
        if (!f.isFile || f.extension.lowercase() !in rasterExt) return null
        val root = dir.canonicalPath + File.separator
        if (!f.canonicalPath.startsWith(root)) return null
        return f.relativeTo(dir).invariantSeparatorsPath
    }

    private fun findIcon(dir: File, entry: String, html: String): String? {
        val base = File(dir, entry).parentFile ?: dir
        val opts = setOf(RegexOption.IGNORE_CASE)
        val link = Regex("""<link\b[^>]*?rel\s*=\s*["'][^"']*icon[^"']*["'][^>]*?href\s*=\s*["']([^"']+)["']""", opts)
        val img = Regex("""<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["']""", opts)
        link.findAll(html).forEach { m -> rasterFile(dir, base, m.groupValues[1])?.let { return it } }
        img.findAll(html).forEach { m -> rasterFile(dir, base, m.groupValues[1])?.let { return it } }
        return dir.walkTopDown().maxDepth(3)
            .filter { it.isFile && it.extension.lowercase() in rasterExt }
            .sortedWith(
                compareBy<File>(
                    { !(it.name.contains("icon", true) || it.name.contains("logo", true)) },
                    { it.relativeTo(dir).path.length }
                )
            )
            .firstOrNull()?.relativeTo(dir)?.invariantSeparatorsPath
    }

    private fun buildEntity(id: String, dir: File, fallbackName: String?): AppEntity {
        val entry = findEntry(dir)
        val json = File(dir, "gamehub.json").takeIf { it.isFile }
            ?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val html = head(File(dir, entry))
        val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)?.groupValues?.get(1)?.let { decode(it) }?.takeIf { it.isNotBlank() }

        val iconRef = json?.optString("icon", "")?.trim()?.trimStart('/')?.takeIf { it.isNotBlank() }
            ?.let { ref -> rasterFile(dir, dir, ref) }
        val icon = iconRef ?: findIcon(dir, entry, html)

        val orientation = when (json?.optString("orientation", "auto")?.lowercase()) {
            "portrait" -> "portrait"
            "landscape" -> "landscape"
            "sensor" -> "sensor"
            else -> "auto"
        }
        val shake = when (val s = json?.opt("shake")) {
            is Boolean -> if (s) Actions.ONSHAKE else Actions.NONE
            is String -> Actions.normalize(s)
            else -> Actions.DEFAULT
        }
        return AppEntity(
            id = id,
            name = json?.optString("name", "")?.trim()?.takeIf { it.isNotBlank() }
                ?: title ?: fallbackName?.takeIf { it.isNotBlank() } ?: "Untitled app",
            version = json?.optString("version", "")?.trim() ?: "",
            icon = icon,
            entry = entry,
            orientation = orientation,
            fullscreen = json?.optBoolean("fullscreen", false) ?: false,
            keepAwake = json?.optBoolean("keepAwake", false) ?: false,
            shakeAction = shake,
            sizeBytes = dirSize(dir)
        )
    }

    fun metaJson(a: AppEntity): JSONObject = JSONObject()
        .put("name", a.name)
        .put("version", a.version)
        .put("icon", a.icon ?: "")
        .put("orientation", a.orientation)
        .put("fullscreen", a.fullscreen)
        .put("keepAwake", a.keepAwake)
        .put("shake", a.shakeAction)

    fun dirSize(f: File): Long =
        if (f.isFile) f.length() else f.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    // ---------- manage ----------

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.delete(id)
        dirOf(id).deleteRecursively()
        File(shotsDir, "$id.jpg").delete()
    }

    suspend fun exportZip(id: String, out: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val app = dao.get(id) ?: error("App not found")
            val dir = dirOf(id)
            val os = ctx.contentResolver.openOutputStream(out, "wt") ?: throw IOException("Cannot write file")
            os.use {
                ZipOutputStream(BufferedOutputStream(it)).use { z ->
                    z.putNextEntry(ZipEntry("gamehub.json"))
                    z.write(metaJson(app).toString(2).toByteArray())
                    z.closeEntry()
                    dir.walkTopDown().filter { f -> f.isFile }.forEach { f ->
                        val rel = f.relativeTo(dir).invariantSeparatorsPath
                        if (rel != "gamehub.json") {
                            z.putNextEntry(ZipEntry(rel))
                            f.inputStream().use { i -> i.copyTo(z) }
                            z.closeEntry()
                        }
                    }
                }
            }
        }
    }

    suspend fun storageStats(): Pair<Long, Long> = withContext(Dispatchers.IO) {
        dirSize(appsDir) to dirSize(ctx.cacheDir)
    }

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    // ---------- backup ----------

    suspend fun backupJson(): String = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        dao.all().forEach { a ->
            arr.put(
                JSONObject().put("id", a.id).put("name", a.name).put("version", a.version)
                    .put("orientation", a.orientation).put("fullscreen", a.fullscreen)
                    .put("keepAwake", a.keepAwake).put("allowInternet", a.allowInternet)
                    .put("shakeAction", a.shakeAction).put("pinned", a.pinned)
            )
        }
        JSONObject().put("format", "gamehub-backup").put("version", 1)
            .put("settings", Prefs.toJson()).put("apps", arr).toString(2)
    }

    /** Returns restored to missing counts. Files are not part of the backup. */
    suspend fun restoreBackup(text: String): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        runCatching {
            val root = JSONObject(text)
            if (root.optString("format") != "gamehub-backup") throw IOException("Not a GameHub backup")
            root.optJSONObject("settings")?.let { Prefs.restore(it) }
            val arr = root.optJSONArray("apps") ?: JSONArray()
            var restored = 0
            var missing = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val cur = dao.get(o.optString("id"))
                if (cur == null) {
                    missing++
                } else {
                    dao.update(
                        cur.copy(
                            name = o.optString("name", cur.name).ifBlank { cur.name },
                            version = o.optString("version", cur.version),
                            orientation = o.optString("orientation", cur.orientation),
                            fullscreen = o.optBoolean("fullscreen", cur.fullscreen),
                            keepAwake = o.optBoolean("keepAwake", cur.keepAwake),
                            allowInternet = o.optBoolean("allowInternet", cur.allowInternet),
                            shakeAction = Actions.normalize(o.optString("shakeAction", cur.shakeAction)),
                            pinned = o.optBoolean("pinned", cur.pinned)
                        )
                    )
                    restored++
                }
            }
            restored to missing
        }
    }
}
