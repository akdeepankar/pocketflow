package app.ak25.pocketflow.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

sealed class MessageToken {
    data class Plain(val text: String) : MessageToken()
    data class Mention(val username: String, val trailingPunctuation: String = "") : MessageToken()
}

/**
 * Tokenizes message text into plain text chunks and @mentions.
 */
fun parseMessageTokens(text: String): List<MessageToken> {
    if (text.isEmpty()) return emptyList()
    val regex = Regex("""@([a-zA-Z0-9_\.\-]+)([,\.!\?:;]*)""")
    val tokens = mutableListOf<MessageToken>()
    var lastIndex = 0

    for (match in regex.findAll(text)) {
        val range = match.range
        if (range.first > lastIndex) {
            val plain = text.substring(lastIndex, range.first)
            if (plain.isNotEmpty()) tokens.add(MessageToken.Plain(plain))
        }
        val username = match.groupValues[1]
        val punct = match.groupValues[2]
        tokens.add(MessageToken.Mention(username, punct))
        lastIndex = range.last + 1
    }

    if (lastIndex < text.length) {
        val plain = text.substring(lastIndex)
        if (plain.isNotEmpty()) tokens.add(MessageToken.Plain(plain))
    }
    return tokens
}

/**
 * Renders an inline pill badge for a mentioned user.
 */
@Composable
fun MentionBadge(
    username: String,
    isMine: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val containerColor = if (isMine) Color.White.copy(alpha = 0.24f) else Color(0xFFE0F2FE)
    val borderColor = if (isMine) Color.White.copy(alpha = 0.45f) else Color(0xFFBAE6FD)
    val atColor = if (isMine) Color.White else Color(0xFF0284C7)
    val textColor = if (isMine) Color.White else Color(0xFF0369A1)

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = containerColor,
        border = BorderStroke(0.5.dp, borderColor),
        modifier = modifier.then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(1.5.dp)
        ) {
            Text(
                text = "@",
                color = atColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = username,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Renders message text with embedded @mentions rendered as stylish badges.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MentionMessageContent(
    text: String,
    isMine: Boolean,
    modifier: Modifier = Modifier,
    textColor: Color = if (isMine) Color.White else Color(0xFF1E293B),
    fontSize: TextUnit = 14.sp
) {
    val tokens = remember(text) { parseMessageTokens(text) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        tokens.forEach { token ->
            when (token) {
                is MessageToken.Plain -> {
                    val words = token.text.split(" ")
                    words.forEach { word ->
                        if (word.isNotEmpty()) {
                            Text(
                                text = word,
                                color = textColor,
                                fontSize = fontSize,
                                lineHeight = (fontSize.value + 4).sp
                            )
                        }
                    }
                }
                is MessageToken.Mention -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        MentionBadge(username = token.username, isMine = isMine)
                        if (token.trailingPunctuation.isNotEmpty()) {
                            Text(
                                text = token.trailingPunctuation,
                                color = textColor,
                                fontSize = fontSize
                            )
                        }
                    }
                }
            }
        }
    }
}
