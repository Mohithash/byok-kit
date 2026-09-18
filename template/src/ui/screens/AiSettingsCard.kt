@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package __PKG__.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import __PKG__.ai.AiClient
import __PKG__.ai.AiProvider
import __PKG__.ai.AiSettings
import __PKG__.ai.ChatMsg
import __PKG__.ui.Label
import __PKG__.ui.ShapeIcon
import __PKG__.ui.StatCard
import kotlinx.coroutines.launch

/** Reusable BYOK settings block: provider toggle, key, model, base URL, test + save. */
@Composable
fun AiSettingsCard(current: AiSettings, client: AiClient, onSave: (AiSettings) -> Unit, onMessage: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var provider by remember { mutableStateOf(current.provider) }
    var key by remember { mutableStateOf(current.apiKey) }
    var model by remember { mutableStateOf(current.model) }
    var baseUrl by remember { mutableStateOf(current.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun build() = AiSettings(provider, key.trim(), model.trim(), baseUrl.trim())

    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShapeIcon(Icons.Default.Key, cs.primaryContainer, cs.onPrimaryContainer, MaterialShapes.Cookie7Sided)
            Column { Text("AI provider", style = MaterialTheme.typography.titleMedium); Label("Bring your own key") }
        }
        Text("Your key is stored only on this device and sent only to the provider below.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            ToggleButton(checked = provider == AiProvider.ANTHROPIC, onCheckedChange = { provider = AiProvider.ANTHROPIC },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(), modifier = Modifier.weight(1f)) { Text("Claude") }
            ToggleButton(checked = provider == AiProvider.OPENAI_COMPAT, onCheckedChange = { provider = AiProvider.OPENAI_COMPAT },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(), modifier = Modifier.weight(1f)) { Text("OpenAI‑compatible") }
        }
        OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } })
        OutlinedTextField(model, { model = it }, label = { Text("Model") }, placeholder = { Text(provider.defaultModel) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large)
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, placeholder = { Text(provider.defaultBaseUrl) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            supportingText = { if (provider == AiProvider.OPENAI_COMPAT) Text("Works with OpenAI, Groq, OpenRouter, Ollama, LM Studio…") })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    testing = true
                    scope.launch {
                        val r = runCatching { client.chat(build(), "Reply with the single word OK.", listOf(ChatMsg("user", "ping")), maxTokens = 64) }
                        testing = false
                        onMessage(r.fold({ "Connected — model replied “${it.trim().take(40)}”" }, { it.message ?: "Failed" }))
                    }
                }, enabled = key.isNotBlank() && !testing, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f),
            ) { if (testing) LoadingIndicator(Modifier.size(20.dp)) else Text("Test") }
            Button(onClick = { onSave(build()); onMessage("AI settings saved") }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Save") }
        }
    }
}
