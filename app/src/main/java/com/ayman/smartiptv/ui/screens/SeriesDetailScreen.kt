package com.ayman.smartiptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.smartiptv.data.model.Episode
import com.ayman.smartiptv.data.model.SeriesInfoResponse
import com.ayman.smartiptv.data.model.StreamItem
import com.ayman.smartiptv.ui.theme.*
import com.ayman.smartiptv.viewmodel.AppViewModel
import com.ayman.smartiptv.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailScreen(
    viewModel: AppViewModel,
    series: StreamItem,
    onBack: () -> Unit,
    onPlayEpisode: (Episode, Int) -> Unit
) {
    val seriesInfoState by viewModel.seriesInfoState.collectAsState()
    var selectedSeason by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(series.id) {
        viewModel.loadSeriesInfo(series.id)
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = { Text(series.displayName, color = TextPrimary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedSeason != null) selectedSeason = null else onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCard)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (seriesInfoState) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AccentBlue)
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text((seriesInfoState as UiState.Error).message, color = AccentRed)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadSeriesInfo(series.id) }) { Text("إعادة المحاولة") }
                    }
                }
                is UiState.Success -> {
                    val info = (seriesInfoState as UiState.Success<SeriesInfoResponse>).data
                    val episodesMap = info.episodes ?: emptyMap()

                    if (selectedSeason == null) {
                        LazyColumn(contentPadding = PaddingValues(14.dp)) {
                            items(episodesMap.keys.toList()) { seasonKey ->
                                val count = episodesMap[seasonKey]?.size ?: 0
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BgCardAlt)
                                        .clickable { selectedSeason = seasonKey }
                                        .padding(16.dp)
                                ) {
                                    Text("الموسم $seasonKey  ($count حلقة)", color = TextPrimary, fontSize = 15.sp)
                                }
                            }
                        }
                    } else {
                        val episodes = episodesMap[selectedSeason] ?: emptyList()
                        LazyColumn(contentPadding = PaddingValues(14.dp)) {
                            itemsIndexed(episodes) { idx, ep ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(BgCardAlt)
                                        .clickable { onPlayEpisode(ep, idx) }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AccentBlue)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "الحلقة ${ep.episodeNum ?: (idx + 1)}",
                                        color = TextPrimary, fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

