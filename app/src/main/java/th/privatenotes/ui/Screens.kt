package th.privatenotes.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import th.privatenotes.Confirm
import th.privatenotes.NotesViewModel
import th.privatenotes.SortMode
import th.privatenotes.data.PlainNote
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

@Composable
private fun ColorDot(color: Color, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** 2x2 colour squares, like the filter icon in ColorNote. */
@Composable
private fun PaletteIcon() {
    Canvas(Modifier.size(24.dp)) {
        val gap = 2.dp.toPx()
        val s = (size.width - gap) / 2f
        val box = Size(s, s)
        drawRect(Color(0xFF41B3D9), Offset(0f, 0f), box)
        drawRect(Color(0xFFDCDC22), Offset(s + gap, 0f), box)
        drawRect(Color(0xFF9A8FBF), Offset(0f, s + gap), box)
        drawRect(Color(0xFFF0612B), Offset(s + gap, s + gap), box)
    }
}

private fun formatDate(ts: Long): String {
    val thisYear = GregorianCalendar().get(java.util.Calendar.YEAR)
    val noteYear = GregorianCalendar().apply { timeInMillis = ts }.get(java.util.Calendar.YEAR)
    val pattern = if (thisYear == noteYear) "d MMM" else "d MMM\nyyyy"
    val fmt = SimpleDateFormat(pattern, Locale("th"))
    fmt.calendar = GregorianCalendar() // Gregorian year (2025), not Buddhist era
    return fmt.format(Date(ts))
}

@Composable
private fun NoteCard(note: PlainNote, isDark: Boolean, onClick: () -> Unit) {
    val c = noteColor(note.color)
    val shape = RoundedCornerShape(10.dp)
    val date = remember(note.updatedAt) { formatDate(note.updatedAt) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.background(isDark))
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min)
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(c.stripe))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 16.dp)) {
            Text(
                note.title.ifBlank { "(ไม่มีชื่อ)" },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (note.body.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    note.body,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
        Text(
            date,
            Modifier.padding(end = 14.dp).align(Alignment.CenterVertically),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

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
    var colorMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }

    val visible = remember(vm.notes, vm.search, vm.sort, vm.colorFilter) {
        val q = vm.search.trim()
        val filtered = vm.notes.filter { n ->
            (vm.colorFilter == null || n.color == vm.colorFilter) &&
                (q.isEmpty() || n.title.contains(q, ignoreCase = true) ||
                    n.body.contains(q, ignoreCase = true))
        }
        when (vm.sort) {
            SortMode.Modified -> filtered.sortedByDescending { it.updatedAt }
            SortMode.Color ->
                filtered.sortedWith(compareBy<PlainNote>({ it.color }, { -it.updatedAt }))
            SortMode.Title -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Private Notes",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Light
                    )
                },
                actions = {
                    // Colour filter
                    Box {
                        IconButton(onClick = { colorMenu = true }) { PaletteIcon() }
                        DropdownMenu(expanded = colorMenu, onDismissRequest = { colorMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("ทุกสี") },
                                trailingIcon = {
                                    if (vm.colorFilter == null) Icon(Icons.Default.Check, null)
                                },
                                onClick = { vm.colorFilter = null; colorMenu = false }
                            )
                            NoteColors.forEachIndexed { i, nc ->
                                DropdownMenuItem(
                                    text = { Text(nc.name) },
                                    leadingIcon = { ColorDot(nc.stripe, 20.dp) },
                                    trailingIcon = {
                                        if (vm.colorFilter == i) Icon(Icons.Default.Check, null)
                                    },
                                    onClick = { vm.colorFilter = i; colorMenu = false }
                                )
                            }
                        }
                    }
                    IconButton(onClick = {
                        searchOpen = !searchOpen
                        if (!searchOpen) vm.search = ""
                    }) {
                        Icon(
                            if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            "ค้นหา"
                        )
                    }
                    IconButton(onClick = { vm.lockNow() }) { Icon(Icons.Default.Lock, "ล็อก") }
                    Box {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, "เมนู")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (isDark) "โหมดสว่าง" else "โหมดมืด") },
                                leadingIcon = {
                                    Icon(
                                        if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                                        null
                                    )
                                },
                                onClick = { menu = false; onToggleTheme() }
                            )
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
            FloatingActionButton(
                onClick = { vm.openEditor(null) },
                shape = RoundedCornerShape(20.dp),
                containerColor = Color(0xFF009688),
                contentColor = Color.White
            ) { Icon(Icons.Default.Add, "เพิ่มบันทึก") }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            if (vm.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (searchOpen) {
                OutlinedTextField(
                    value = vm.search,
                    onValueChange = { vm.search = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    placeholder = { Text("ค้นหา") },
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(Modifier.height(8.dp))
            }

            // Sort bar
            Box(Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp).clickable { sortMenu = true }
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(vm.sort.label, style = MaterialTheme.typography.titleSmall)
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    SortMode.values().forEach { m ->
                        DropdownMenuItem(
                            text = { Text(m.label) },
                            trailingIcon = { if (m == vm.sort) Icon(Icons.Default.Check, null) },
                            onClick = { vm.sort = m; sortMenu = false }
                        )
                    }
                }
            }

            vm.colorFilter?.let { idx ->
                Spacer(Modifier.height(6.dp))
                FilterChip(
                    selected = true,
                    onClick = { vm.colorFilter = null },
                    label = { Text("สี${noteColor(idx).name}") },
                    leadingIcon = { ColorDot(noteColor(idx).stripe, 14.dp) },
                    trailingIcon = {
                        Icon(Icons.Default.Close, "ล้างตัวกรอง", Modifier.size(16.dp))
                    }
                )
            }

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
                Text("ยังไม่มีบันทึก กดปุ่ม + เพื่อเพิ่ม", Modifier.padding(8.dp))
            } else if (visible.isEmpty()) {
                Text("ไม่พบบันทึกที่ตรงกับตัวกรอง", Modifier.padding(8.dp))
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(visible, key = { it.id }) { note ->
                    NoteCard(note, isDark) { vm.openEditor(note) }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

// ===================== Dialogs =====================

@Composable
private fun ColorSwatch(c: NoteColor, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(c.stripe)
            .then(
                if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Default.Check, c.name, tint = Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent
)

/** Full-screen editor tinted with the note's colour; the colour can be changed at any time. */
@Composable
fun EditorDialog(vm: NotesViewModel, isDark: Boolean) {
    val c = noteColor(vm.draftColor)
    Dialog(
        onDismissRequest = vm::requestCloseEditor,
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            securePolicy = SecureFlagPolicy.SecureOn // no screenshots of note text
        )
    ) {
        Surface(Modifier.fillMaxSize(), color = c.background(isDark)) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = vm::requestCloseEditor) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "กลับ")
                    }
                    Spacer(Modifier.weight(1f))
                    if (vm.editingId != null) {
                        IconButton(onClick = vm::requestDelete) {
                            Icon(
                                Icons.Default.Delete, "ลบ",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = vm::saveDraft) { Icon(Icons.Default.Check, "บันทึก") }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NoteColors.forEachIndexed { i, nc ->
                        ColorSwatch(nc, selected = i == vm.draftColor) { vm.draftColor = i }
                    }
                }
                TextField(
                    value = vm.draftTitle,
                    onValueChange = { vm.draftTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.titleLarge,
                    placeholder = { Text("หัวข้อ", style = MaterialTheme.typography.titleLarge) },
                    colors = transparentFieldColors()
                )
                TextField(
                    value = vm.draftBody,
                    onValueChange = { vm.draftBody = it },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    placeholder = { Text("เนื้อหา") },
                    colors = transparentFieldColors()
                )
            }
        }
    }
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
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
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
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
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
