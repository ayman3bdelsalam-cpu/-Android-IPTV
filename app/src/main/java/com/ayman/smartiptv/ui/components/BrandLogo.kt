package com.ayman.smartiptv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.smartiptv.ui.theme.AccentBlue
import com.ayman.smartiptv.ui.theme.AccentBlueDark
import com.ayman.smartiptv.ui.theme.TextPrimary

/** Same visual identity used in the LG webOS version. */
@Composable
fun BrandLogo(compact: Boolean = false) {
    val markSize = if (compact) 38.dp else 44.dp
    val corner = if (compact) 10.dp else 12.dp
    val titleSize = if (compact) 16.sp else 17.sp

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(markSize)
                .background(AccentBlueDark, RoundedCornerShape(corner)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "A",
                color = TextPrimary,
                fontWeight = FontWeight.Black,
                fontSize = if (compact) 18.sp else 20.sp
            )
        }
        Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Black)) {
                    append("AYMAN ")
                }
                withStyle(SpanStyle(color = AccentBlue, fontWeight = FontWeight.Black)) {
                    append("SMART IPTV")
                }
            },
            fontSize = titleSize
        )
    }
}
