// direction B "Field" login (DESIGN.md): clean centered template, navy/orange brand, white card.
package bd.sicip.qavisit.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.R
import bd.sicip.qavisit.data.auth.Session
import bd.sicip.qavisit.data.auth.SessionStore
import bd.sicip.qavisit.data.db.Officer
import bd.sicip.qavisit.data.db.OfficerDao
import bd.sicip.qavisit.data.remote.SupabaseClient
import bd.sicip.qavisit.data.remote.SupabaseException
import bd.sicip.qavisit.ui.theme.LocalStatusColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException

@Composable
fun LoginScreen(
    sessionStore: SessionStore,
    officerDao: OfficerDao,
    client: SupabaseClient = SupabaseClient(),
    onLoggedIn: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showForgotDialog by remember { mutableStateOf(false) }
    var recoverEmail by remember { mutableStateOf("") }
    var recoverLoading by remember { mutableStateOf(false) }
    var recoverMessage by remember { mutableStateOf<String?>(null) }
    var recoverIsError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .widthIn(max = 400.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_fg),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "SICIP QA Visit",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Sign in to continue",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !loading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            capitalization = KeyboardCapitalization.None,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Password") },
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
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(
                        onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                try {
                                    val auth = client.signIn(email.trim(), password.trim())
                                    sessionStore.save(
                                        Session(auth.accessToken, auth.refreshToken, auth.expiresAt, auth.userId, email.trim()),
                                    )
                                    syncOwnOfficer(client, auth.accessToken, auth.userId, officerDao)
                                    onLoggedIn()
                                } catch (e: Exception) {
                                    error = loginErrorMessage(e)
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                        ),
                        shape = RoundedCornerShape(99),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onTertiary,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Log in")
                        }
                    }
                    TextButton(
                        onClick = {
                            recoverEmail = email.trim()
                            recoverMessage = null
                            recoverIsError = false
                            showForgotDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Forgot password?", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("Reset password") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("We'll email a reset link to this address.")
                    OutlinedTextField(
                        value = recoverEmail,
                        onValueChange = { recoverEmail = it; recoverMessage = null },
                        label = { Text("Email") },
                        singleLine = true,
                        enabled = !recoverLoading,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            capitalization = KeyboardCapitalization.None,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    recoverMessage?.let {
                        Text(
                            it,
                            color = if (recoverIsError) MaterialTheme.colorScheme.error else LocalStatusColors.current.success.ink,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        recoverLoading = true
                        recoverMessage = null
                        scope.launch {
                            try {
                                client.recover(recoverEmail.trim())
                                recoverIsError = false
                                recoverMessage = "Reset link sent. Open the email and set a new password."
                            } catch (e: Exception) {
                                recoverIsError = true
                                recoverMessage = recoverErrorMessage(e)
                            } finally {
                                recoverLoading = false
                            }
                        }
                    },
                    enabled = !recoverLoading && recoverEmail.isNotBlank(),
                ) {
                    if (recoverLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Send")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("Close") }
            },
        )
    }
}

// pulls the officer row matching the just-logged-in user id and mirrors it into room.
// best-effort: an empty result (row not provisioned yet) is not an error.
private suspend fun syncOwnOfficer(client: SupabaseClient, accessToken: String, userId: String, officerDao: OfficerDao) {
    val rows = client.select("officers", mapOf("id" to "eq.$userId", "select" to "*"), accessToken)
    val row = rows.firstOrNull()?.jsonObject ?: return
    officerDao.upsert(
        Officer(
            id = row.getValue("id").jsonPrimitive.content,
            name = row.getValue("name").jsonPrimitive.content,
            email = row.getValue("email").jsonPrimitive.content,
            role = row.getValue("role").jsonPrimitive.content,
            active = row.getValue("active").jsonPrimitive.boolean,
            updatedAt = row.getValue("updated_at").jsonPrimitive.content,
        ),
    )
}

// pure so it's unit-testable without touching compose/android.
fun loginErrorMessage(t: Throwable): String = when {
    t is SupabaseException && t.code == 400 -> "Wrong email or password"
    t is IOException -> "No connection — try again"
    else -> "Something went wrong — try again"
}

fun recoverErrorMessage(t: Throwable): String = when {
    t is SupabaseException && t.code == 429 -> "Too many requests, try later"
    t is IOException -> "No connection — try again"
    else -> "Something went wrong — try again"
}
