package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge visivo che indica se il libro è sincronizzato con Firestore Cloud oppure memorizzato solo localmente in Room.
 */
@Composable
fun SyncStatusBadge(
    isSynced: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val bgColor = if (isSynced) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
    val contentColor = if (isSynced) Color(0xFF2E7D32) else Color(0xFFE65100)
    val borderColor = if (isSynced) Color(0xFFA5D6A7) else Color(0xFFFFCC80)
    val icon = if (isSynced) Icons.Default.CloudDone else Icons.Default.Storage
    val label = if (isSynced) "Firestore Cloud" else "Locale (Room)"

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = modifier.testTag(if (isSynced) "sync_badge_firestore" else "sync_badge_room")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(
                horizontal = if (compact) 6.dp else 8.dp,
                vertical = if (compact) 2.dp else 4.dp
            )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
                fontSize = if (compact) 10.sp else 11.sp
            )
        }
    }
}

/**
 * Indicatore circolare compatto da sovrapporre alle copertine dei libri.
 */
@Composable
fun SyncStatusIndicatorDot(
    isSynced: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSynced) Color(0xFF2E7D32) else Color(0xFFF57C00)
    val icon = if (isSynced) Icons.Default.CloudDone else Icons.Default.Storage
    val desc = if (isSynced) "Sincronizzato con Firestore" else "Presente solo in Room (Locale)"

    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(bgColor.copy(alpha = 0.92f))
            .border(1.5.dp, Color.White, CircleShape)
            .testTag(if (isSynced) "sync_dot_firestore" else "sync_dot_room"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
    }
}
