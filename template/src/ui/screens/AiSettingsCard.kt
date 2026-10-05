@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package __PKG__.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Reusable BYOK settings block: provider toggle, key, model (with suggestions, or the live list from the
 * provider via [AiClient.listModels]), base URL, test + save.
 */
@Composable
fun AiSettingsCard(current: AiSettings, client: AiClient, onSave: (AiSettings) -> Unit, onMessage: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var provider by remember { mutableStateOf(current.provider) }
    var key by remember { mutableStateOf(current.apiKey) }
    var model by remember { mutableStateOf(current.model) }
    var baseUrl by remember { mutableStateOf(current.baseUrl) }
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var loadingModels by remember { mutableStateOf(false) }
    // Ids fetched from the provider; null until "load" is tapped. Cleared when the provider changes.
    var fetched by remember { mutableStateOf<List<String>?>(null) }
    val scope = rememberCoroutineScope()
    fun build() = AiSettings(provider, key.trim(), model.trim(), baseUrl.trim())
    fun pick(p: AiProvider) {
        if (p == provider) return
        // A model id that only makes sense for the other provider goes back to "default".
        if (model.trim() in provider.suggestedModels) model = ""
        provider = p; fetched = null
    }
    val choices = fetched ?: provider.suggestedModels

    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShapeIcon(Icons.Default.Key, cs.primaryContainer, cs.onPrimaryContainer, MaterialShapes.Cookie7Sided)
            Column { Text("AI provider", style = MaterialTheme.typography.titleMedium); Label("Bring your own key") }
        }
        Text("Your key is stored only on this device and sent only to the provider below.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            ToggleButton(checked = provider == AiProvider.ANTHROPIC, onCheckedChange = { pick(AiProvider.ANTHROPIC) },
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(), modifier = Modifier.weight(1f)) { Text("Claude") }
            ToggleButton(checked = provider == AiProvider.OPENAI_COMPAT, onCheckedChange = { pick(AiProvider.OPENAI_COMPAT) },
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(), modifier = Modifier.weight(1f)) { Text("OpenAI‑compatible") }
        }
        OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility, if (showKey) "Hide key" else "Show key") } })
        ExposedDropdownMenuBox(expanded = modelMenu, onExpandedChange = { modelMenu = it }) {
            OutlinedTextField(model, { model = it }, label = { Text("Model") }, placeholder = { Text(provider.defaultModel) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable), shape = MaterialTheme.shapes.large,
                supportingText = { Text(if (fetched != null) "${choices.size} models available to this key" else "Blank uses ${provider.defaultModel}. ⟳ lists the models your key can use.") },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (loadingModels) LoadingIndicator(Modifier.size(24.dp)) else IconButton(
                            onClick = {
                                loadingModels = true
                                scope.launch {
                                    try {
                                        val ids = client.listModels(build())
                                        if (ids.isEmpty()) onMessage("The provider didn't list any models — type one in.")
                                        else { fetched = ids; modelMenu = true }
                                    } catch (e: CancellationException) { throw e // left the screen: say nothing
                                    } catch (e: Exception) { onMessage(e.message ?: "Couldn't load models")
                                    } finally { loadingModels = false }
                                }
                            },
                            enabled = key.isNotBlank(),
                        ) { Icon(Icons.Default.Refresh, "Load models from provider") }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenu,
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable))
                    }
                })
            ExposedDropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                val selected = model.trim().ifBlank { provider.defaultModel }
                choices.forEach { id ->
                    DropdownMenuItem(
                        text = { Text(if (id == provider.defaultModel) "$id (default)" else id) },
                        onClick = { model = if (id == provider.defaultModel) "" else id; modelMenu = false },
                        leadingIcon = { if (id == selected) Icon(Icons.Default.Check, null) },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                    )
                }
            }
        }
        OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, placeholder = { Text(provider.defaultBaseUrl) }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large,
            supportingText = {
                when {
                    baseUrl.trim().startsWith("http://", ignoreCase = true) -> Text("Plain http:// is unencrypted — only use it for a server on your own device or network (Ollama, LM Studio).")
                    provider == AiProvider.OPENAI_COMPAT -> Text("Works with OpenAI, Groq, OpenRouter, Ollama, LM Studio…")
                }
            })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    testing = true
                    scope.launch {
                        try {
                            // 1024: current Claude models always think, so a tiny limit can come back empty.
                            val r = client.chatFull(build(), "Reply with the single word OK.", listOf(ChatMsg("user", "ping")), maxTokens = 1024)
                            onMessage("Connected to ${r.model.ifBlank { build().effectiveModel }} — replied “${r.text.trim().take(40)}”")
                        } catch (e: CancellationException) { throw e
                        } catch (e: Exception) { onMessage(e.message ?: "Failed")
                        } finally { testing = false }
                    }
                }, enabled = key.isNotBlank() && !testing, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f),
            ) { if (testing) LoadingIndicator(Modifier.size(20.dp)) else Text("Test") }
            Button(onClick = { onSave(build()); onMessage("AI settings saved") }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Save") }
        }
    }
}
