package com.jtr.app.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.jtr.app.R
import kotlinx.coroutines.delay

// ── Écran de verrouillage (overlay MainActivity) ──────────────────────────────

/**
 * Écran de verrouillage affiché AVANT le contenu (démarrage à froid + retour
 * d'arrière-plan). Déverrouillage par schéma, par biométrie (si activée/disponible)
 * ou réinitialisation via code de secours (« Schéma oublié ? »). Temporisation
 * progressive anti-force-brute après plusieurs échecs.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricOn = remember {
        SecurityManager.isBiometricEnabled(context) && Biometrics.isAvailable(context)
    }

    var isError by remember { mutableStateOf(false) }
    var lockoutUntil by remember { mutableStateOf(0L) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    var showRecoveryEntry by remember { mutableStateOf(false) }
    var showResetSetup by remember { mutableStateOf(false) }

    LaunchedEffect(lockoutUntil) {
        while (lockoutUntil > System.currentTimeMillis()) {
            nowMs = System.currentTimeMillis()
            delay(250)
        }
        nowMs = System.currentTimeMillis()
    }
    val remainingSec = ((lockoutUntil - nowMs + 999) / 1000).coerceAtLeast(0).toInt()
    val lockedOut = remainingSec > 0

    val promptTitle = stringResource(R.string.security_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.security_biometric_prompt_subtitle)

    fun runBiometric() {
        activity?.let {
            Biometrics.authenticate(
                it, promptTitle, promptSubtitle,
                onSuccess = { SecurityManager.resetFailedAttempts(context); onUnlocked() },
                onFail = { }
            )
        }
    }

    // Invite biométrique automatique à l'ouverture (l'utilisateur peut la rejeter
    // et tracer son schéma).
    LaunchedEffect(Unit) { if (biometricOn) runBiometric() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(40.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.security_lock_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    lockedOut -> stringResource(R.string.security_locked_out, remainingSec)
                    isError -> stringResource(R.string.security_wrong_pattern)
                    else -> stringResource(R.string.security_lock_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError || lockedOut) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            PatternLockView(
                modifier = Modifier.fillMaxWidth(0.82f),
                enabled = !lockedOut,
                isError = isError,
                onComplete = { pattern ->
                    if (SecurityManager.verifyPattern(context, pattern)) {
                        SecurityManager.resetFailedAttempts(context)
                        onUnlocked()
                    } else {
                        isError = true
                        val count = SecurityManager.incrementFailedAttempts(context)
                        if (count % 5 == 0) {
                            lockoutUntil = System.currentTimeMillis() + 30_000L * (count / 5)
                        }
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
            if (biometricOn) {
                OutlinedButton(onClick = { runBiometric() }, enabled = !lockedOut) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.security_use_biometric))
                }
                Spacer(Modifier.height(8.dp))
            }
            TextButton(onClick = { showRecoveryEntry = true }) {
                Text(stringResource(R.string.security_forgot_pattern))
            }
        }
    }

    if (showRecoveryEntry) {
        RecoveryEntryDialog(
            onDismiss = { showRecoveryEntry = false },
            onValid = {
                showRecoveryEntry = false
                showResetSetup = true
            }
        )
    }

    if (showResetSetup) {
        SecuritySetupDialog(
            requireAuth = false,
            onDismiss = { showResetSetup = false },
            onComplete = { pattern, code, enableBiometric ->
                SecurityManager.setPattern(context, pattern)
                SecurityManager.storeRecoveryCode(context, code)
                SecurityManager.setBiometricEnabled(context, enableBiometric)
                showResetSetup = false
                onUnlocked()
            }
        )
    }
}

// ── Définition / changement de schéma (dialogue plein écran) ──────────────────

private enum class SetupStep { AUTH, DRAW, CONFIRM, RECOVERY, BIOMETRIC }

/**
 * Flux de (re)définition du schéma : authentification préalable optionnelle
 * ([requireAuth] pour « Changer le mot de passe »), tracer → confirmer → afficher
 * le code de secours → proposer la biométrie. [onComplete] est appelé une seule
 * fois à la fin avec le schéma, le code de secours et le choix biométrique.
 */
@Composable
fun SecuritySetupDialog(
    requireAuth: Boolean,
    onDismiss: () -> Unit,
    onComplete: (pattern: List<Int>, recoveryCode: String, enableBiometric: Boolean) -> Unit
) {
    val context = LocalContext.current
    val biometricAvailable = remember { Biometrics.isAvailable(context) }

    var step by remember { mutableStateOf(if (requireAuth) SetupStep.AUTH else SetupStep.DRAW) }
    var first by remember { mutableStateOf<List<Int>>(emptyList()) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val recoveryCode = remember { SecurityManager.generateRecoveryCode() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                SecurityTopBar(onClose = onDismiss)
                when (step) {
                    SetupStep.AUTH -> AuthContent(
                        titleRes = R.string.security_auth_title,
                        subtitleRes = R.string.security_auth_subtitle,
                        onSuccess = { step = SetupStep.DRAW }
                    )

                    SetupStep.DRAW -> PatternPrompt(
                        titleRes = R.string.security_setup_draw_title,
                        subtitleRes = null,
                        errorRes = errorRes,
                        onPattern = { pattern ->
                            if (pattern.size < SecurityManager.MIN_PATTERN_SIZE) {
                                errorRes = R.string.security_pattern_too_short
                            } else {
                                first = pattern
                                errorRes = null
                                step = SetupStep.CONFIRM
                            }
                        }
                    )

                    SetupStep.CONFIRM -> PatternPrompt(
                        titleRes = R.string.security_setup_confirm_title,
                        subtitleRes = null,
                        errorRes = errorRes,
                        onPattern = { pattern ->
                            if (pattern == first) {
                                errorRes = null
                                step = SetupStep.RECOVERY
                            } else {
                                errorRes = R.string.security_pattern_mismatch
                                first = emptyList()
                                step = SetupStep.DRAW
                            }
                        }
                    )

                    SetupStep.RECOVERY -> RecoveryCodeContent(
                        code = recoveryCode,
                        onDone = {
                            if (biometricAvailable) step = SetupStep.BIOMETRIC
                            else onComplete(first, recoveryCode, false)
                        }
                    )

                    SetupStep.BIOMETRIC -> BiometricOfferContent(
                        onEnable = { onComplete(first, recoveryCode, true) },
                        onSkip = { onComplete(first, recoveryCode, false) }
                    )
                }
            }
        }
    }
}

// ── Authentification seule (« Changer », « Désactiver ») ───────────────────────

/**
 * Dialogue d'authentification (schéma actuel OU biométrie). [onSuccess] une fois
 * authentifié. Utilisé avant un changement ou une désactivation du verrou.
 */
@Composable
fun SecurityAuthDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                SecurityTopBar(onClose = onDismiss)
                AuthContent(
                    titleRes = R.string.security_auth_title,
                    subtitleRes = R.string.security_auth_subtitle,
                    onSuccess = onSuccess
                )
            }
        }
    }
}

// ── Briques réutilisables ──────────────────────────────────────────────────────

@Composable
private fun SecurityTopBar(onClose: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_close))
        }
    }
}

@Composable
private fun PatternPrompt(
    titleRes: Int,
    subtitleRes: Int?,
    errorRes: Int?,
    onPattern: (List<Int>) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (subtitleRes != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (errorRes != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(errorRes, SecurityManager.MIN_PATTERN_SIZE),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(24.dp))
        PatternLockView(
            modifier = Modifier.fillMaxWidth(0.82f),
            isError = errorRes != null,
            onComplete = onPattern
        )
    }
}

@Composable
private fun AuthContent(titleRes: Int, subtitleRes: Int, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricOn = remember {
        SecurityManager.isBiometricEnabled(context) && Biometrics.isAvailable(context)
    }
    var isError by remember { mutableStateOf(false) }
    val promptTitle = stringResource(R.string.security_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.security_biometric_prompt_subtitle)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isError) stringResource(R.string.security_wrong_pattern)
            else stringResource(subtitleRes),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        PatternLockView(
            modifier = Modifier.fillMaxWidth(0.82f),
            isError = isError,
            onComplete = { pattern ->
                if (SecurityManager.verifyPattern(context, pattern)) onSuccess()
                else isError = true
            }
        )
        if (biometricOn) {
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = {
                activity?.let {
                    Biometrics.authenticate(it, promptTitle, promptSubtitle, onSuccess = onSuccess, onFail = {})
                }
            }) {
                Icon(Icons.Default.Fingerprint, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.security_use_biometric))
            }
        }
    }
}

@Composable
private fun RecoveryCodeContent(code: String, onDone: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.security_recovery_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.security_recovery_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = {
            clipboard.setText(AnnotatedString(code))
            copied = true
        }) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(
                    if (copied) R.string.security_recovery_copied else R.string.security_recovery_copy
                )
            )
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.security_recovery_done))
        }
    }
}

@Composable
private fun BiometricOfferContent(onEnable: () -> Unit, onSkip: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Fingerprint,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.height(48.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.security_enable_biometric_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.security_enable_biometric_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onEnable, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.security_enable))
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip) { Text(stringResource(R.string.security_not_now)) }
    }
}

@Composable
private fun RecoveryEntryDialog(onDismiss: () -> Unit, onValid: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                SecurityTopBar(onClose = onDismiss)
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringResource(R.string.security_recovery_enter_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it; isError = false },
                        singleLine = true,
                        isError = isError,
                        label = { Text(stringResource(R.string.security_recovery_enter_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isError) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.security_recovery_invalid),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            if (SecurityManager.verifyRecoveryCode(context, input)) onValid()
                            else isError = true
                        },
                        enabled = input.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.security_recovery_reset))
                    }
                }
            }
        }
    }
}
