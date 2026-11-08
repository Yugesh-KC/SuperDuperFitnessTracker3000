package com.example.superduperfitnesstracker3000.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.superduperfitnesstracker3000.data.ChatMessage
import com.example.superduperfitnesstracker3000.data.ChatRole
import com.example.superduperfitnesstracker3000.ui.components.simpleMarkdown

const val SUGGEST_NEXT_MEAL = "Based on what I've eaten today, what should I eat next?"

private val suggestions = listOf(
    SUGGEST_NEXT_MEAL,
    "How am I progressing toward my goal?",
    "Work out my daily calorie, protein and water targets",
    "Review what I ate today",
    "Am I drinking enough water?",
)

@Composable
fun CoachScreen(
    messages: List<ChatMessage>,
    loading: Boolean,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    contextPreview: () -> String,
    contentPadding: PaddingValues,
) {
    var input by rememberSaveable { mutableStateOf("") }
    var showContext by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, loading) {
        val count = messages.size + if (loading) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    fun send(text: String) {
        if (text.isBlank() || loading) return
        onSend(text)
        input = ""
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .imePadding()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ask your AI coach", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Each question includes a short summary of your data — profile, weight trend, weekly averages, recent nutrition, today's meals and water — so answers are about you.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Tell it about yourself — e.g. \"I'm 178 cm, 24, and want to get to 72 kg\" or \"I'm vegetarian\" — and it saves that to your profile and remembers it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = { showContext = contextPreview() }) { Text("See exactly what is shared") }
                        suggestions.forEach { s ->
                            SuggestionChip(onClick = { send(s) }, label = { Text(s) })
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { Bubble(it) }
            if (loading) {
                item(key = "loading") {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Thinking…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (messages.lastOrNull()?.role == ChatRole.USER) {
                item {
                    TextButton(onClick = onRetry) { Text("No reply yet — retry") }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask about your progress, meals…") },
                modifier = Modifier.weight(1f).heightIn(max = 140.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(24.dp),
            )
            FilledIconButton(onClick = { send(input) }, enabled = input.isNotBlank() && !loading) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }

    showContext?.let { text ->
        AlertDialog(
            onDismissRequest = { showContext = null },
            title = { Text("Data sent to Gemini") },
            text = {
                Text(
                    text,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )
            },
            confirmButton = { TextButton(onClick = { showContext = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val isUser = message.role == ChatRole.USER
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        SelectionContainer {
            Text(
                text = if (isUser) androidx.compose.ui.text.AnnotatedString(message.text) else simpleMarkdown(message.text),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        message.savedNote?.let { note ->
            Row(
                Modifier.padding(top = 4.dp).widthIn(max = 320.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Text("Saved: $note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
