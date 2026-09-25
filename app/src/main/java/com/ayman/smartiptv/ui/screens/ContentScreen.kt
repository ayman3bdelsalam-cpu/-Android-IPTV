package com.ayman.smartiptv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.ayman.smartiptv.R
import com.ayman.smartiptv.data.model.Category
import com.ayman.smartiptv.data.model.ContentType
import com.ayman.smartiptv.data.model.StreamItem
import com.ayman.smartiptv.ui.theme.*
import com.ayman.smartiptv.viewmodel.AppViewModel
import com.ayman.smartiptv.viewmodel.UiState

private const val SPECIAL_ALL = "__ALL__"
private const val SPECIAL_FAV = "__FAV__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentScreen(
    viewModel: AppViewModel,
    type: ContentType,
    onBack: () -> Unit,
    onPlayItem: (StreamItem) -> Unit,
    onOpenSeries: (StreamItem) -> Unit
) {
    val categoriesState by viewModel.categoriesState.collectAsState()
    val streamsState by viewModel.streamsState.collectAsState()
    val favoritesFlow = when (type) {
        ContentType.LIVE -> viewModel.favoritesLive
        ContentType.VOD -> viewModel.favoritesVod
        ContentType.SERIES -> viewModel.favoritesSeries
    }
    val favoriteIds by favoritesFlow.collectAsState()

    var selectedCategory by remember(type) { mutableStateOf(SPECIAL_ALL) }
    var searchQuery by remember(type) { mutableStateOf("") }

    // Load categories and the default "All" list together. The ViewModel cache
    // makes returning to this screen effectively instant during the same session.
    LaunchedEffect(type) {
        selectedCategory = SPECIAL_ALL
        searchQuery = ""
        viewModel.loadCategories(type)
        viewModel.loadStreams(null)
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (type) {
                            ContentType.LIVE -> "البث المباشر"
                            ContentType.VOD -> "الأفلام"
                            ContentType.SERIES -> "المسلسلات"
                        },
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCard)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    searchQuery = it
                    viewModel.searchStreams(it) // internally debounced by 250 ms
                },
                placeholder = { Text("🔍 بحث في القائمة...") },
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                singleLine = true
            )

            when (categoriesState) {
                is UiState.Loading -> LoadingBox()
                is UiState.Error -> ErrorBox((categoriesState as UiState.Error).message) {
                    viewModel.loadCategories(type, forceRefresh = true)
                }
                is UiState.Success -> {
                    val cats = (categoriesState as UiState.Success<List<Category>>).data

                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            modifier = Modifier
                                .width(150.dp)
                                .fillMaxHeight()
                                .background(BgCardAlt)
                                .padding(8.dp)
                        ) {
                            item {
                                CategoryRow("⭐ الكل", selectedCategory == SPECIAL_ALL) {
                                    selectedCategory = SPECIAL_ALL
                                    searchQuery = ""
                                    viewModel.loadStreams(null)
                                }
                                CategoryRow("❤️ المفضلة", selectedCategory == SPECIAL_FAV) {
                                    selectedCategory = SPECIAL_FAV
                                    searchQuery = ""
                                    viewModel.loadStreams(null, onlyFavorites = true)
                                }
                            }
                            items(cats, key = { it.categoryId }) { c ->
                                CategoryRow(c.categoryName, selectedCategory == c.categoryId) {
                                    selectedCategory = c.categoryId
                                    searchQuery = ""
                                    viewModel.loadStreams(c.categoryId)
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            when (streamsState) {
                                is UiState.Loading -> LoadingBox()
                                is UiState.Error -> ErrorBox((streamsState as UiState.Error).message) {
                                    viewModel.loadStreams(
                                        if (selectedCategory == SPECIAL_ALL || selectedCategory == SPECIAL_FAV) null else selectedCategory,
                                        onlyFavorites = selectedCategory == SPECIAL_FAV,
                                        forceRefresh = true
                                    )
                                }
                                is UiState.Success -> {
                                    val streamList = (streamsState as UiState.Success<List<StreamItem>>).data
                                    if (streamList.isEmpty()) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text("لا توجد عناصر", color = TextMuted)
                                        }
                                    } else {
                                        val cols = if (type == ContentType.LIVE) 1 else 3
                                        LazyVerticalGrid(
                                            columns = GridCells.Fixed(cols),
                                            contentPadding = PaddingValues(12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            items(streamList, key = { "${type.name}-${it.id}-${it.displayName}" }) { item ->
                                                if (type == ContentType.LIVE) {
                                                    LiveItemCard(
                                                        item,
                                                        favoriteIds.contains(item.id),
                                                        onClick = { onPlayItem(item) },
                                                        onFavClick = { viewModel.toggleFavorite(type, item.id) }
                                                    )
                                                } else {
                                                    VodItemCard(
                                                        item,
                                                        favoriteIds.contains(item.id),
                                                        onClick = {
                                                            if (type == ContentType.SERIES) onOpenSeries(item)
                                                            else onPlayItem(item)
                                                        },
                                                        onFavClick = { viewModel.toggleFavorite(type, item.id) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun CategoryRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentBlueDark else BgCard)
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Text(
            name,
            color = if (selected) TextPrimary else TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LiveItemCard(item: StreamItem, isFav: Boolean, onClick: () -> Unit, onFavClick: () -> Unit) {
    val context = LocalContext.current
    val imageRequest = remember(item.posterUrl) {
        ImageRequest.Builder(context)
            .data(item.posterUrl)
            .size(160, 160)
            .precision(Precision.INEXACT)
            .crossfade(100)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .build()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCardAlt)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = null,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit
        )
        Spacer(Modifier.width(12.dp))
        Text(
            item.displayName,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onFavClick) {
            Icon(
                if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (isFav) AccentRed else TextMuted
            )
        }
    }
}

@Composable
private fun VodItemCard(item: StreamItem, isFav: Boolean, onClick: () -> Unit, onFavClick: () -> Unit) {
    val context = LocalContext.current
    val imageRequest = remember(item.posterUrl) {
        ImageRequest.Builder(context)
            .data(item.posterUrl)
            // Decode near the actual card size instead of keeping multi-megapixel posters in RAM.
            .size(480, 720)
            .precision(Precision.INEXACT)
            .crossfade(120)
            .placeholder(R.mipmap.ic_launcher)
            .error(R.mipmap.ic_launcher)
            .build()
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgCardAlt)
            .clickable { onClick() }
    ) {
        Box {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(150.dp),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onFavClick,
                modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
            ) {
                Icon(
                    if (isFav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isFav) AccentRed else Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Text(
            item.displayName,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = AccentBlue)
    }
}

@Composable
private fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = AccentRed, fontSize = 14.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = AccentBlueDark)) {
                Text("إعادة المحاولة")
            }
        }
    }
}
