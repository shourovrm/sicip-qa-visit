// Profile card: log in to / out of the officer's TMS account (data/tms/TmsAuth.kt).
package bd.sicip.qavisit.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.tms.TmsAuth
import bd.sicip.qavisit.data.tms.TmsAuthState
import bd.sicip.qavisit.data.tms.TmsLoginResult
import bd.sicip.qavisit.data.tms.TmsServices
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TmsAccountCard() {
    val auth = TmsServices.get(LocalContext.current).auth
    val state by auth.state.collectAsState()
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("TMS account", style = MaterialTheme.typography.titleMedium)
            when (val current = state) {
                is TmsAuthState.LoggedIn -> SignedIn(auth, current.displayName, current.expiresAt, failure = null)
                // background re-login failed but the login is still stored: stay signed in, offer a retry
                is TmsAuthState.Failed -> if (current.credentialsStored) {
                    SignedIn(auth, displayName = null, expiresAt = 0, failure = current.message)
                } else {
                    Text(current.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    LoginForm(auth)
                }
                TmsAuthState.LoggedOut -> LoginForm(auth)
            }
        }
    }
}

@Composable
private fun SignedIn(auth: TmsAuth, displayName: String?, expiresAt: Long, failure: String?) {
    val scope = rememberCoroutineScope()
    // restored from the vault (expiresAt 0): fetch a token once so name + expiry are real
    LaunchedEffect(displayName, expiresAt) {
        if (displayName != null && expiresAt == 0L) runCatching { auth.bearer() }
    }
    if (displayName != null) Text("Signed in as $displayName", style = MaterialTheme.typography.bodyMedium)
    if (expiresAt > 0) {
        Text(
            "Signed in until ${signedInUntilText(expiresAt)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (failure != null) {
        Text("Last TMS sign-in failed: $failure", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = { scope.launch { auth.retry() } },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Try again") }
    }
    OutlinedButton(
        onClick = { scope.launch { auth.logout() } },
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text("Sign out") }
}

// "2 Oct, 1:23 PM" in the phone's zone
private fun signedInUntilText(expiresAtSeconds: Long): String =
    DateTimeFormatter.ofPattern("d MMM, h:mm a", Locale.ENGLISH)
        .format(Instant.ofEpochSecond(expiresAtSeconds).atZone(ZoneId.systemDefault()))

@Composable
private fun LoginForm(auth: TmsAuth) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = username,
        onValueChange = { username = it; error = null },
        label = { Text("TMS username") },
        singleLine = true,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it; error = null },
        label = { Text("TMS password") },
        singleLine = true,
        enabled = !loading,
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (passwordVisible) "Hide password" else "Show password",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    Button(
        onClick = {
            loading = true
            error = null
            scope.launch {
                when (val result = auth.login(username.trim(), password)) {
                    TmsLoginResult.Success -> password = ""
                    is TmsLoginResult.Failure -> error = result.message
                }
                loading = false
            }
        },
        enabled = !loading && username.isNotBlank() && password.isNotEmpty(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiary,
            contentColor = MaterialTheme.colorScheme.onTertiary,
        ),
        shape = RoundedCornerShape(99),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onTertiary, strokeWidth = 2.dp)
        } else {
            Text("Log in")
        }
    }
}
