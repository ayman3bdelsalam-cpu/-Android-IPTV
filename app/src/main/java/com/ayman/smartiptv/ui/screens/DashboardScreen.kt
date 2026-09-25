package com.ayman.smartiptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.smartiptv.data.model.ContentType
import com.ayman.smartiptv.ui.components.BrandLogo
import com.ayman.smartiptv.ui.theme.*

@Composable
fun DashboardScreen(
    onSelect: (ContentType) -> Unit,
    onLogout: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {

        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandLogo(compact = true)
            IconButton(onClick = onLogout) {
                Icon(Icons.Filled.ExitToApp, contentDescription = "خروج", tint = TextMuted)
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            HubCard("📺", "البث المباشر", "القنوات والبرامج", AccentBlue) { onSelect(ContentType.LIVE) }
            Spacer(Modifier.height(16.dp))
            HubCard("🎬", "الأفلام", "مكتبة VOD الكاملة", AccentRed) { onSelect(ContentType.VOD) }
            Spacer(Modifier.height(16.dp))
            HubCard("🍿", "المسلسلات", "المواسم والحلقات", AccentOrange) { onSelect(ContentType.SERIES) }
        }
    }
}

@Composable
private fun HubCard(icon: String, title: String, subtitle: String, accent: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .clickable { onClick() }
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).background(accent, RoundedCornerShape(2.dp)))
        Spacer(Modifier.height(16.dp))
        Text(icon, fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(subtitle, color = TextMuted, fontSize = 13.sp)
    }
}
