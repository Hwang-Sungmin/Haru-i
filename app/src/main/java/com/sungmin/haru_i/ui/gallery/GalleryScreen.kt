package com.sungmin.haru_i.ui.gallery

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sungmin.haru_i.model.Photo

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val analyzingMonths by viewModel.analyzingMonths.collectAsState()

    // 스크롤 상태를 탭 전환 시에도 유지하기 위해 호이스팅
    val timelineGridState = rememberLazyGridState()
    val babyPhotoGridState = rememberLazyGridState()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadPhotos()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permission)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "우리 아기 갤러리",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                )
                if (uiState is GalleryUiState.Success) {
                    val state = uiState as GalleryUiState.Success
                    TabRow(
                        selectedTabIndex = state.selectedTab,
                        containerColor = Color.Transparent,
                        divider = {}
                    ) {
                        Tab(
                            selected = state.selectedTab == 0,
                            onClick = { viewModel.selectTab(0) },
                            text = { Text("타임라인") }
                        )
                        Tab(
                            selected = state.selectedTab == 1,
                            onClick = { viewModel.selectTab(1) },
                            text = { Text("아기 사진") }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is GalleryUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is GalleryUiState.Success -> {
                    Column {
                        if (state.favoritePhotos.isNotEmpty()) {
                            HighlightSection(
                                photos = state.favoritePhotos,
                                onToggleFavorite = { viewModel.toggleFavorite(it) }
                            )
                        }
                        
                        val photos = if (state.selectedTab == 0) state.allPhotos else state.filteredPhotos
                        if (photos.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (state.selectedTab == 0) "사진이 없습니다." else "인식된 아기 사진이 없습니다.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        } else {
                            if (state.selectedTab == 0) {
                                TimelineGrid(
                                    state = timelineGridState,
                                    groupedPhotos = state.groupedPhotos,
                                    analyzingMonths = analyzingMonths,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAnalyzeMonth = { month, photos -> 
                                        viewModel.analyzeMonth(month, photos)
                                    }
                                )
                            } else {
                                PhotoGrid(
                                    state = babyPhotoGridState,
                                    photos = photos,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) }
                                )
                            }
                        }
                    }
                }
                is GalleryUiState.Error -> {
                    Text(
                        text = "에러 발생: ${state.message}",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun HighlightSection(
    photos: List<Photo>,
    onToggleFavorite: (Photo) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = "하이라이트",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(photos) { photo ->
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    PhotoItem(photo = photo, onToggleFavorite = onToggleFavorite)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
    }
}

@Composable
fun TimelineGrid(
    state: LazyGridState,
    groupedPhotos: Map<String, List<Photo>>,
    analyzingMonths: Set<String>,
    onToggleFavorite: (Photo) -> Unit,
    onAnalyzeMonth: (String, List<Photo>) -> Unit
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        groupedPhotos.forEach { (month, photos) ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = month,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                    
                    val isAnalyzing = analyzingMonths.contains(month)
                    TextButton(
                        onClick = { onAnalyzeMonth(month, photos) },
                        enabled = !isAnalyzing
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("분석 중...", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Face,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("아기 사진 찾기", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            items(photos, key = { it.id }) { photo ->
                PhotoItem(photo = photo, onToggleFavorite = onToggleFavorite)
            }
        }
    }
}

@Composable
fun PhotoGrid(
    state: LazyGridState,
    photos: List<Photo>,
    onToggleFavorite: (Photo) -> Unit
) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoItem(photo = photo, onToggleFavorite = onToggleFavorite)
        }
    }
}

@Composable
fun PhotoItem(
    photo: Photo,
    onToggleFavorite: (Photo) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(photo.uri)
                .crossfade(true)
                .build(),
            contentDescription = photo.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        IconButton(
            onClick = { onToggleFavorite(photo) },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(28.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "즐겨찾기",
                tint = if (photo.isFavorite) Color.Red else Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
