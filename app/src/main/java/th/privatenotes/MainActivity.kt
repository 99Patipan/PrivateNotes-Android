package th.privatenotes

import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import th.privatenotes.ui.ConfirmDialog
import th.privatenotes.ui.EditorDialog
import th.privatenotes.ui.ExportDialog
import th.privatenotes.ui.ImportDialog
import th.privatenotes.ui.LockScreen
import th.privatenotes.ui.NotesScreen
import th.privatenotes.ui.appBackgroundColors
import th.privatenotes.ui.appColorScheme

// BiometricPrompt requires a FragmentActivity (ComponentActivity is not enough).
class MainActivity : FragmentActivity() {
    private val vm: NotesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Hide content in the recents screen and block screenshots / screen recording.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        setContent { PrivateNotesApp(vm) }
    }

    override fun onStart() {
        super.onStart()
        vm.onStarted()
    }

    override fun onStop() {
        vm.onStopped(isChangingConfigurations)
        super.onStop()
    }
}

private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun PrivateNotesApp(vm: NotesViewModel) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val isDark = vm.dark ?: isSystemInDarkTheme()

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> vm.finishExport(uri, context.contentResolver) }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.onShareFinished() }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> vm.onImportPicked(uri, context.contentResolver) }

    val toast = vm.toast
    LaunchedEffect(toast) {
        if (toast != null) {
            Toast.makeText(context, toast, Toast.LENGTH_LONG).show()
            vm.consumeToast()
        }
    }

    // Status/navigation bar icons: light on the dark lock screen and dark theme, dark otherwise.
    val view = LocalView.current
    val lightBars = vm.unlocked && !isDark
    SideEffect {
        activity?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
    }

    MaterialTheme(colorScheme = appColorScheme(isDark)) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(appBackgroundColors(isDark))),
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            if (!vm.unlocked) {
                LockScreen(
                    setup = vm.setup,
                    busy = vm.busy,
                    message = vm.message,
                    lockoutEndsAt = vm.lockoutEndsAt,
                    showBiometric = vm.biometricEnabled,
                    onSubmit = vm::submitPin,
                    onBiometric = { activity?.let { vm.unlockWithBiometric(it) } },
                    onClearMessage = vm::clearMessage
                )
            } else {
                NotesScreen(
                    vm = vm,
                    isDark = isDark,
                    onToggleTheme = { vm.dark = !isDark },
                    onBackup = vm::openExportDialog,
                    onRestore = {
                        vm.beginImport()
                        importLauncher.launch(arrayOf("*/*"))
                    },
                    onToggleBiometric = {
                        if (vm.biometricEnabled) vm.disableBiometric()
                        else activity?.let { vm.enableBiometric(it) }
                    }
                )
                if (vm.editorOpen) EditorDialog(vm, isDark)
                when (vm.backupDialog) {
                    BackupDialog.Export -> ExportDialog(
                        busy = vm.busy,
                        unreadable = vm.unreadable,
                        onStart = { pw, pw2 ->
                            if (vm.beginExport(pw, pw2)) {
                                exportLauncher.launch(vm.backupFileName())
                            }
                        },
                        onShare = { pw, pw2 ->
                            vm.shareBackup(pw, pw2) { uri, name ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, name)
                                    clipData = ClipData.newRawUri(name, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    shareLauncher.launch(Intent.createChooser(send, "ส่งไฟล์สำรองไปยัง..."))
                                } catch (e: Exception) {
                                    vm.onShareFinished()
                                    Toast.makeText(context, "เปิดหน้าแชร์ไม่ได้", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        onDismiss = vm::dismissBackupDialog
                    )
                    BackupDialog.ImportPassword -> ImportDialog(
                        busy = vm.busy,
                        onSubmit = vm::decryptImport,
                        onDismiss = vm::dismissBackupDialog
                    )
                    BackupDialog.None -> Unit
                }
                vm.confirm?.let { ConfirmDialog(vm, it) }
            }
        }
    }
}
