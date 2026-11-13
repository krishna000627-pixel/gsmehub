package com.krishna.webhub.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krishna.webhub.core.CrashLog
import com.krishna.webhub.data.AppEntity
import com.krishna.webhub.data.AppRepo
import com.krishna.webhub.web.WebViewPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel : ViewModel() {
    /** null until the first read, so the empty state never flashes. */
    val apps: StateFlow<List<AppEntity>?> = AppRepo.apps
        .map<List<AppEntity>, List<AppEntity>?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val importing = MutableStateFlow<String?>(null)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val importDone = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun observe(id: String): Flow<AppEntity?> = AppRepo.dao.observe(id)

    private fun runImport(kind: String, block: suspend () -> Result<AppEntity>) {
        if (importing.value != null) return
        importing.value = kind
        viewModelScope.launch {
            val r = block()
            importing.value = null
            r.exceptionOrNull()?.let { CrashLog.append("import $kind failed", it) }
            messages.tryEmit(
                r.fold(
                    { "Imported ${it.name}" },
                    { "Import failed: ${it.message ?: "unknown error"}" }
                )
            )
            importDone.tryEmit(Unit)
        }
    }

    fun importZip(u: Uri) = runImport("zip") { AppRepo.importZip(u) }
    fun importFolder(u: Uri) = runImport("folder") { AppRepo.importFolder(u) }
    fun importHtml(u: Uri) = runImport("html") { AppRepo.importHtml(u) }
    fun importPasted(text: String) = runImport("paste") { AppRepo.importPasted(text) }

    fun save(app: AppEntity) {
        viewModelScope.launch { AppRepo.dao.update(app) }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            AppRepo.delete(id)
            WebViewPool.clearOrigin(id)
            messages.tryEmit("App deleted")
        }
    }

    fun clearData(id: String) {
        WebViewPool.clearOrigin(id)
        messages.tryEmit("App data cleared")
    }

    fun exportZip(id: String, uri: Uri) {
        viewModelScope.launch {
            val r = AppRepo.exportZip(id, uri)
            messages.tryEmit(if (r.isSuccess) "ZIP exported" else "Export failed: ${r.exceptionOrNull()?.message}")
        }
    }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            val r = runCatching {
                val text = AppRepo.backupJson()
                withContext(Dispatchers.IO) {
                    AppRepo.ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
                }
            }
            messages.tryEmit(if (r.isSuccess) "Backup saved" else "Backup failed: ${r.exceptionOrNull()?.message}")
        }
    }

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            val text = runCatching {
                withContext(Dispatchers.IO) {
                    AppRepo.ctx.contentResolver.openInputStream(uri)!!.use { String(it.readBytes()) }
                }
            }
            val res = text.fold({ AppRepo.restoreBackup(it) }, { Result.failure(it) })
            messages.tryEmit(
                res.fold(
                    { (ok, missing) ->
                        if (missing > 0) "Restored $ok apps, $missing not on this device" else "Restored $ok apps"
                    },
                    { "Restore failed: ${it.message}" }
                )
            )
        }
    }
}
