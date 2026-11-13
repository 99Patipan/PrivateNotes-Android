package th.privatenotes

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import th.privatenotes.data.NOTE_COLOR_COUNT
import th.privatenotes.data.Note
import th.privatenotes.data.NotesDatabase
import th.privatenotes.data.PlainNote
import th.privatenotes.security.BackupCrypto
import th.privatenotes.security.BiometricAuth
import th.privatenotes.security.CryptoManager
import th.privatenotes.security.KeystoreVault
import th.privatenotes.security.LockManager
import th.privatenotes.security.PinResult
import java.io.ByteArrayOutputStream
import java.io.InputStream

enum class Confirm { DeleteNote, DiscardDraft, Restore }
enum class BackupDialog { None, Export, ImportPassword }

enum class SortMode(val label: String) {
    Modified("เรียงตามเวลาที่แก้ไข"),
    Color("เรียงตามสี"),
    Title("เรียงตามชื่อ")
}

class NotesViewModel(app: Application) : AndroidViewModel(app) {
    private val db = NotesDatabase.get(app)
    private val dao = db.noteDao()
    private val crypto = CryptoManager()
    private val lock = LockManager(app)

    // ---- lock screen ----
    var setup by mutableStateOf(!lock.isConfigured())
        private set
    var unlocked by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf("")
        private set
    var biometricEnabled by mutableStateOf(lock.isBiometricEnabled())
        private set
    /** Wall-clock time (ms) when the PIN lockout ends; 0 when not locked out. */
    var lockoutEndsAt by mutableStateOf(
        lock.remainingLockoutMs().let { if (it > 0) System.currentTimeMillis() + it else 0L }
    )
        private set
    var toast by mutableStateOf<String?>(null)
        private set

    // ---- notes ----
    var notes by mutableStateOf<List<PlainNote>>(emptyList())
        private set
    var unreadable by mutableStateOf(0)
        private set
    var search by mutableStateOf("")
    var dark by mutableStateOf<Boolean?>(null)
    var sort by mutableStateOf(SortMode.Modified)
    var colorFilter by mutableStateOf<Int?>(null)

    // ---- editor (kept in memory here so rotation does not lose the draft) ----
    var editorOpen by mutableStateOf(false)
        private set
    var editingId by mutableStateOf<Long?>(null)
        private set
    var draftTitle by mutableStateOf("")
    var draftBody by mutableStateOf("")
    var draftColor by mutableStateOf(0)
    private var baseTitle = ""
    private var baseBody = ""
    private var baseColor = 0
    private val isDirty: Boolean
        get() = draftTitle != baseTitle || draftBody != baseBody || draftColor != baseColor

    // ---- dialogs / backup ----
    var confirm by mutableStateOf<Confirm?>(null)
        private set
    var backupDialog by mutableStateOf(BackupDialog.None)
        private set
    var pendingRestore by mutableStateOf<List<PlainNote>?>(null)
        private set
    private var pendingImport: ByteArray? = null
    private var exportPassword: CharArray? = null

    /** True while a system picker is open, so the auto-lock does not discard the pending work. */
    private var holdLock = false
    private var stoppedAt = 0L

    fun consumeToast() { toast = null }

    // ================= unlock / lock =================

    fun submitPin(pin: String, confirmPin: String) {
        if (busy) return
        if (pin.length < 6) { message = "PIN ต้องมีอย่างน้อย 6 หลัก"; return }
        if (setup && pin != confirmPin) { message = "PIN สองช่องไม่ตรงกัน"; return }
        busy = true
        message = ""
        val chars = pin.toCharArray()
        viewModelScope.launch {
            try {
                if (setup) {
                    val dek = crypto.newDataKey()
                    try {
                        withContext(Dispatchers.Default) { lock.setupPin(chars, dek) }
                        crypto.setDataKey(dek)
                    } finally {
                        dek.fill(0)
                    }
                    setup = false
                    onUnlocked()
                } else {
                    when (val r = withContext(Dispatchers.Default) { lock.unlockWithPin(chars) }) {
                        is PinResult.Success -> {
                            crypto.setDataKey(r.dek)
                            r.dek.fill(0)
                            lockoutEndsAt = 0L
                            onUnlocked()
                        }
                        PinResult.Wrong -> {
                            message = wrongMessage()
                            val ms = lock.remainingLockoutMs()
                            lockoutEndsAt = if (ms > 0) System.currentTimeMillis() + ms else 0L
                        }
                        is PinResult.Locked -> {
                            message = lockedMessage(r.remainingMs)
                            lockoutEndsAt = System.currentTimeMillis() + r.remainingMs
                        }
                    }
                }
            } catch (e: IllegalArgumentException) {
                message = e.message ?: "PIN ไม่ถูกต้อง"
            } catch (e: Exception) {
                message = "เกิดข้อผิดพลาด: ${e.message}"
            } finally {
                chars.fill('\u0000')
                busy = false
            }
        }
    }

    fun clearMessage() { message = "" }

    private fun wrongMessage(): String {
        val ms = lock.remainingLockoutMs()
        return if (ms > 0) lockedMessage(ms)
        else "PIN ไม่ถูกต้อง (ผิดแล้ว ${lock.failedAttempts()} ครั้ง)"
    }

    private fun lockedMessage(ms: Long): String {
        val s = (ms + 999) / 1000
        val text = if (s >= 60) "${(s + 59) / 60} นาที" else "$s วินาที"
        return "ลองผิดหลายครั้งเกินไป กรุณารอ $text"
    }

    private suspend fun onUnlocked() {
        unlocked = true
        message = ""
        loadNotes()
    }

    private suspend fun loadNotes() {
        val result = withContext(Dispatchers.Default) {
            var bad = 0
            val list = dao.all().mapNotNull { n ->
                runCatching { crypto.openNote(n) }.onFailure { bad++ }.getOrNull()
            }
            list to bad
        }
        if (!unlocked) return // locked while loading: never repopulate plaintext
        notes = result.first
        unreadable = result.second
    }

    fun lockNow() {
        crypto.lock()
        unlocked = false
        notes = emptyList()
        unreadable = 0
        search = ""
        colorFilter = null
        editorOpen = false
        editingId = null
        draftTitle = ""
        draftBody = ""
        draftColor = 0
        baseTitle = ""
        baseBody = ""
        baseColor = 0
        confirm = null
        backupDialog = BackupDialog.None
        pendingImport = null
        pendingRestore = null
        exportPassword?.fill('\u0000')
        exportPassword = null
        message = ""
    }

    override fun onCleared() {
        crypto.lock()
    }

    // ---- auto-lock (called from the Activity lifecycle) ----

    fun onStopped(changingConfig: Boolean) {
        if (!changingConfig) stoppedAt = SystemClock.elapsedRealtime()
    }

    fun onStarted() {
        val t = stoppedAt
        stoppedAt = 0L
        if (unlocked && !holdLock && t != 0L &&
            SystemClock.elapsedRealtime() - t > AUTO_LOCK_GRACE_MS
        ) lockNow()
    }

    // ================= biometric =================

    fun unlockWithBiometric(activity: FragmentActivity) {
        if (busy || unlocked || !biometricEnabled) return
        val iv = lock.biometricIv()
        val blob = lock.biometricBlob()
        if (iv == null || blob == null) { disableBiometric(); return }
        val cipher = try {
            KeystoreVault.biometricDecryptCipher(iv)
        } catch (e: KeyPermanentlyInvalidatedException) {
            disableBiometric()
            message = "ลายนิ้วมือในเครื่องมีการเปลี่ยนแปลง กรุณาใช้ PIN แล้วเปิดใช้ลายนิ้วมืออีกครั้ง"
            return
        } catch (e: Exception) {
            disableBiometric()
            message = "ใช้ลายนิ้วมือไม่ได้ กรุณาใช้ PIN"
            return
        }
        BiometricAuth.authenticate(
            activity, cipher, "ปลดล็อก Private Notes", "ยืนยันตัวตนด้วยลายนิ้วมือ",
            onSuccess = { c ->
                try {
                    val dek = c.doFinal(blob)
                    crypto.setDataKey(dek)
                    dek.fill(0)
                    viewModelScope.launch { onUnlocked() }
                } catch (e: Exception) {
                    message = "ปลดล็อกด้วยลายนิ้วมือไม่สำเร็จ กรุณาใช้ PIN"
                }
            },
            onFailure = { msg -> if (msg != null) message = msg }
        )
    }

    fun enableBiometric(activity: FragmentActivity) {
        if (!unlocked || busy) return
        val dek = crypto.dataKeyCopy() ?: return
        val cipher = try {
            KeystoreVault.createBiometricEncryptCipher()
        } catch (e: Exception) {
            dek.fill(0)
            toast = "เปิดใช้ลายนิ้วมือไม่ได้: ${e.message}"
            return
        }
        BiometricAuth.authenticate(
            activity, cipher, "เปิดใช้ลายนิ้วมือ", "สแกนเพื่อยืนยัน",
            onSuccess = { c ->
                try {
                    val blob = c.doFinal(dek)
                    lock.saveBiometric(c.iv, blob)
                    biometricEnabled = true
                    toast = "เปิดใช้ลายนิ้วมือแล้ว"
                } catch (e: Exception) {
                    KeystoreVault.deleteBiometricKey()
                    toast = "เปิดใช้ลายนิ้วมือไม่สำเร็จ"
                } finally {
                    dek.fill(0)
                }
            },
            onFailure = { msg ->
                dek.fill(0)
                KeystoreVault.deleteBiometricKey()
                if (msg != null) toast = msg
            }
        )
    }

    fun disableBiometric() {
        lock.clearBiometric()
        KeystoreVault.deleteBiometricKey()
        biometricEnabled = false
    }

    // ================= editor =================

    fun openEditor(note: PlainNote?) {
        editingId = note?.id
        baseTitle = note?.title ?: ""
        baseBody = note?.body ?: ""
        baseColor = note?.color ?: (colorFilter ?: 0)
        draftTitle = baseTitle
        draftBody = baseBody
        draftColor = baseColor
        editorOpen = true
    }

    fun requestCloseEditor() {
        if (isDirty) confirm = Confirm.DiscardDraft else closeEditor()
    }

    private fun closeEditor() {
        editorOpen = false
        editingId = null
        draftTitle = ""
        draftBody = ""
        draftColor = 0
        baseTitle = ""
        baseBody = ""
        baseColor = 0
    }

    fun requestDelete() {
        if (editingId != null) confirm = Confirm.DeleteNote
    }

    fun saveDraft() {
        val title = draftTitle
        val body = draftBody
        val color = draftColor
        val id = editingId
        if (id == null && title.isBlank() && body.isBlank()) { closeEditor(); return }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val s = crypto.sealNote(title, body)
                    val now = System.currentTimeMillis()
                    val existing = if (id != null) dao.byId(id) else null
                    if (existing == null) {
                        dao.insert(
                            Note(
                                titleCipher = s.titleCipher, bodyCipher = s.bodyCipher,
                                ivTitle = s.ivTitle, ivBody = s.ivBody, updatedAt = now,
                                color = color
                            )
                        )
                    } else {
                        dao.update(
                            existing.copy(
                                titleCipher = s.titleCipher, bodyCipher = s.bodyCipher,
                                ivTitle = s.ivTitle, ivBody = s.ivBody, updatedAt = now,
                                color = color
                            )
                        )
                    }
                }
                closeEditor()
                loadNotes()
            } catch (e: Exception) {
                toast = "บันทึกไม่สำเร็จ: ${e.message}"
            }
        }
    }

    private fun deleteEditing() {
        val id = editingId ?: return
        viewModelScope.launch {
            try {
                dao.delete(id)
                closeEditor()
                loadNotes()
            } catch (e: Exception) {
                toast = "ลบไม่สำเร็จ: ${e.message}"
            }
        }
    }

    fun resolveConfirm(accept: Boolean) {
        val c = confirm ?: return
        confirm = null
        if (!accept) {
            if (c == Confirm.Restore) pendingRestore = null
            return
        }
        when (c) {
            Confirm.DeleteNote -> deleteEditing()
            Confirm.DiscardDraft -> closeEditor()
            Confirm.Restore -> applyRestore()
        }
    }

    // ================= backup / restore =================

    fun openExportDialog() { backupDialog = BackupDialog.Export }

    fun dismissBackupDialog() {
        backupDialog = BackupDialog.None
        pendingImport = null
    }

    /** Validates the passwords; returns true if the file picker may be launched. */
    fun beginExport(password: String, confirmPassword: String): Boolean {
        if (password.length < BackupCrypto.MIN_PASSWORD) {
            toast = "รหัสผ่านสำรองต้องมีอย่างน้อย ${BackupCrypto.MIN_PASSWORD} ตัวอักษร"
            return false
        }
        if (password != confirmPassword) {
            toast = "รหัสผ่านสองช่องไม่ตรงกัน"
            return false
        }
        exportPassword = password.toCharArray()
        holdLock = true
        return true
    }

    fun finishExport(uri: Uri?, resolver: ContentResolver) {
        holdLock = false
        val pw = exportPassword
        exportPassword = null
        if (pw == null) return
        if (uri == null) { pw.fill('\u0000'); return }
        val snapshot = notes
        val skipped = unreadable
        busy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val arr = JSONArray()
                    snapshot.forEach {
                        arr.put(
                            JSONObject()
                                .put("title", it.title)
                                .put("body", it.body)
                                .put("updatedAt", it.updatedAt)
                                .put("color", it.color)
                        )
                    }
                    val plain = arr.toString().toByteArray(Charsets.UTF_8)
                    try {
                        val encrypted = BackupCrypto.encrypt(plain, pw)
                        resolver.openOutputStream(uri, "wt")?.use { it.write(encrypted) }
                            ?: error("เขียนไฟล์สำรองไม่ได้")
                    } finally {
                        plain.fill(0)
                    }
                }
                backupDialog = BackupDialog.None
                val extra = if (skipped > 0) " (ข้าม $skipped รายการที่ถอดรหัสไม่ได้)" else ""
                toast = "สำรองข้อมูลแล้ว ${snapshot.size} บันทึก$extra"
            } catch (e: Exception) {
                toast = e.message ?: "สำรองข้อมูลไม่สำเร็จ"
            } finally {
                pw.fill('\u0000')
                busy = false
            }
        }
    }

    /** Call right before launching the "open document" picker. */
    fun beginImport() { holdLock = true }

    fun onImportPicked(uri: Uri?, resolver: ContentResolver) {
        holdLock = false
        if (uri == null) return
        busy = true
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { readLimited(it, MAX_BACKUP_BYTES) }
                        ?: error("อ่านไฟล์ไม่ได้")
                }
                pendingImport = bytes
                backupDialog = BackupDialog.ImportPassword
            } catch (e: Exception) {
                toast = e.message ?: "เปิดไฟล์ไม่ได้"
            } finally {
                busy = false
            }
        }
    }

    fun decryptImport(password: String) {
        val data = pendingImport ?: return
        if (busy) return
        busy = true
        val pw = password.toCharArray()
        viewModelScope.launch {
            try {
                val parsed = withContext(Dispatchers.Default) {
                    val plain = BackupCrypto.decrypt(data, pw)
                    try {
                        val arr = JSONArray(String(plain, Charsets.UTF_8))
                        List(arr.length()) { i ->
                            val o = arr.getJSONObject(i)
                            PlainNote(
                                0,
                                o.optString("title"),
                                o.optString("body"),
                                o.optLong("updatedAt", System.currentTimeMillis()),
                                o.optInt("color", 0).coerceIn(0, NOTE_COLOR_COUNT - 1)
                            )
                        }
                    } finally {
                        plain.fill(0)
                    }
                }
                pendingRestore = parsed
                pendingImport = null
                backupDialog = BackupDialog.None
                confirm = Confirm.Restore
            } catch (e: JSONException) {
                toast = "ข้อมูลในไฟล์สำรองไม่ถูกต้อง"
            } catch (e: Exception) {
                toast = e.message ?: "กู้คืนไม่สำเร็จ"
            } finally {
                pw.fill('\u0000')
                busy = false
            }
        }
    }

    private fun applyRestore() {
        val list = pendingRestore ?: return
        busy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) {
                    val sealed = list.map { n ->
                        val s = crypto.sealNote(n.title, n.body)
                        Note(
                            titleCipher = s.titleCipher, bodyCipher = s.bodyCipher,
                            ivTitle = s.ivTitle, ivBody = s.ivBody, updatedAt = n.updatedAt,
                            color = n.color
                        )
                    }
                    db.withTransaction {
                        dao.deleteAll()
                        dao.insertAll(sealed)
                    }
                }
                pendingRestore = null
                loadNotes()
                toast = "กู้คืนข้อมูลสำเร็จ ${list.size} บันทึก"
            } catch (e: Exception) {
                toast = "กู้คืนไม่สำเร็จ: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun readLimited(stream: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n < 0) break
            total += n
            require(total <= max) { "ไฟล์ใหญ่เกินไป (สูงสุด ${max / 1024 / 1024} MB)" }
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val AUTO_LOCK_GRACE_MS = 30_000L
        const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    }
}
