package th.privatenotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import th.privatenotes.Confirm
import th.privatenotes.NotesViewModel
import th.privatenotes.security.BiometricAuth

// ===================== Lock screen =====================

@Composable
fun LockScreen(
    setup: Boolean,
    busy: Boolean,
    message: String,
    showBiometric: Boolean,
    onSubmit: (pin: String, confirm: String) -> Unit,
    onBiometric: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val canSubmit = !busy && pin.length >= 6 && (!setup || confirm.length >= 6)

    Column(
        Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            if (setup) "ตั้ง PIN สำหรับปกป้องบันทึก" else "ปลดล็อก Private Notes",
            style = MaterialTheme.typography.titleLarge
        )
        if (setup) {
            Spacer(Modifier.height(4.dp))
            Text(
                "ตัวเลข 6–12 หลัก หากลืม PIN จะกู้บันทึกได้จากไฟล์สำรองเท่านั้น",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(16.dp))
        PinField("PIN", pin) { pin = it }
        if (setup) {
            Spacer(Modifier.height(8.dp))
            PinField("ยืนยัน PIN", confirm) { confirm = it }
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            enabled = canSubmit,
            onClick = {
                onSubmit(pin, confirm)
                pin = ""
                confirm = ""
            }
        ) { Text(if (setup) "ตั้ง PIN" else "ปลดล็อก") }
        if (busy) {
            Spacer(Modifier.height(12.dp))
            CircularProgressIndicator()
        }
        if (showBiometric && !setup) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBiometric, enabled = !busy) {
                Icon(Icons.Default.Fingerprint, null)
                Spacer(Modifier.width(8.dp))
                Text("ใช้ลายนิ้วมือ")
            }
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= 12 && it.all { c -> c.isDigit() }) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

// ===================== Notes list =====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    vm: NotesViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onToggleBiometric: () -> Unit
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val visible = remember(vm.notes, vm.search) {
        vm.notes.filter {
            it.title.contains(vm.search, ignoreCase = true) ||
                it.body.contains(vm.search, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Private Notes (${vm.notes.size})") },
                actions = {
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            "สลับธีม"
                        )
                    }
                    IconButton(onClick = { vm.lockNow() }) { Icon(Icons.Default.Lock, "ล็อก") }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "เมนู")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("สำรองข้อมูล") },
                                leadingIcon = { Icon(Icons.Default.Save, null) },
                                onClick = { menu = false; onBackup() }
                            )
                            DropdownMenuItem(
                                text = { Text("กู้คืนข้อมูล") },
                                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                                onClick = { menu = false; onRestore() }
                            )
                            if (vm.biometricEnabled || BiometricAuth.available(context)) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            if (vm.biometricEnabled) "ปิดใช้ลายนิ้วมือ"
                                            else "เปิดใช้ลายนิ้วมือ"
                                        )
                                    },
                                    leadingIcon = { Icon(Icons.Default.Fingerprint, null) },
                                    onClick = { menu = false; onToggleBiometric() }
                                )
                            }
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.openEditor(null) }) {
                Icon(Icons.Default.Add, "เพิ่มบันทึก")
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedTextField(
                value = vm.search,
                onValueChange = { vm.search = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("ค้นหา") },
                leadingIcon = { Icon(Icons.Default.Search, null) }
            )
            if (vm.unreadable > 0) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        "มี ${vm.unreadable} บันทึกที่ถอดรหัสไม่ได้ (จะไม่รวมในไฟล์สำรอง) " +
                            "หากเพิ่งติดตั้งใหม่ ให้กู้คืนจากไฟล์สำรอง",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (vm.notes.isEmpty()) {
                Text("ยังไม่มีบันทึก กดปุ่ม + เพื่อเพิ่ม")
            } else if (visible.isEmpty()) {
                Text("ไม่พบบันทึกที่ตรงกับคำค้น")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { note ->
                    ElevatedCard(Modifier.fillMaxWidth().clickable { vm.openEditor(note) }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                note.title.ifBlank { "(ไม่มีชื่อ)" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (note.body.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    note.body,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

// ===================== Dialogs =====================

@Composable
fun EditorDialog(vm: NotesViewModel) {
    AlertDialog(
        onDismissRequest = vm::requestCloseEditor,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(if (vm.editingId == null) "บันทึกใหม่" else "แก้ไขบันทึก") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = vm.draftTitle,
                    onValueChange = { vm.draftTitle = it },
                    label = { Text("หัวข้อ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = vm.draftBody,
                    onValueChange = { vm.draftBody = it },
                    label = { Text("เนื้อหา") },
                    minLines = 6,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = vm::saveDraft) { Text("บันทึก") } },
        dismissButton = {
            Row {
                if (vm.editingId != null) {
                    TextButton(onClick = vm::requestDelete) {
                        Text("ลบ", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = vm::requestCloseEditor) { Text("ยกเลิก") }
            }
        }
    )
}

@Composable
fun ExportDialog(
    busy: Boolean,
    unreadable: Int,
    onStart: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("สำรองข้อมูล") },
        text = {
            Column {
                Text(
                    "ตั้งรหัสผ่านอย่างน้อย 10 ตัวอักษร ไฟล์จะถูกเข้ารหัส " +
                        "หากลืมรหัสผ่านจะกู้คืนไม่ได้",
                    style = MaterialTheme.typography.bodySmall
                )
                if (unreadable > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "คำเตือน: $unreadable บันทึกที่ถอดรหัสไม่ได้จะไม่รวมในไฟล์สำรอง",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                PasswordField("รหัสผ่านสำรอง", pw) { pw = it }
                Spacer(Modifier.height(8.dp))
                PasswordField("ยืนยันรหัสผ่าน", pw2) { pw2 = it }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = { onStart(pw, pw2) }) { Text("เลือกที่บันทึก") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } }
    )
}

@Composable
fun ImportDialog(busy: Boolean, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("กู้คืนข้อมูล") },
        text = {
            Column {
                Text("ใส่รหัสผ่านของไฟล์สำรอง", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                PasswordField("รหัสผ่านสำรอง", pw) { pw = it }
                if (busy) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && pw.isNotEmpty(), onClick = { onSubmit(pw) }) {
                Text("ถอดรหัส")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ยกเลิก") } }
    )
}

@Composable
fun ConfirmDialog(vm: NotesViewModel, kind: Confirm) {
    val (title, text, action) = when (kind) {
        Confirm.DeleteNote -> Triple("ลบบันทึกนี้?", "การลบไม่สามารถย้อนกลับได้", "ลบ")
        Confirm.DiscardDraft -> Triple("ทิ้งการแก้ไข?", "ข้อความที่ยังไม่บันทึกจะหายไป", "ทิ้ง")
        Confirm.Restore -> Triple(
            "แทนที่บันทึกทั้งหมด?",
            "บันทึกปัจจุบัน ${vm.notes.size} รายการจะถูกลบ แล้วแทนด้วย " +
                "${vm.pendingRestore?.size ?: 0} รายการจากไฟล์สำรอง",
            "กู้คืน"
        )
    }
    AlertDialog(
        onDismissRequest = { vm.resolveConfirm(false) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { vm.resolveConfirm(true) }) {
                Text(action, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = { vm.resolveConfirm(false) }) { Text("ยกเลิก") } }
    )
}
