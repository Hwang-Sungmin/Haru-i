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

import com.sungmin.haru_i.data.BabyInfo
import com.sungmin.haru_i.util.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.window.Dialog
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val analyzingMonths by viewModel.analyzingMonths.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }

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
                    },
                    actions = {
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "설정")
                        }
                    }
                )
                
                if (uiState is GalleryUiState.Success) {
                    val state = uiState as GalleryUiState.Success
                    
                    // Baby Info Banner
                    if (state.babyInfo.birthday > 0) {
                        BabyInfoBanner(state.babyInfo)
                    }

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
            if (showSettingsDialog) {
                val currentInfo = (uiState as? GalleryUiState.Success)?.babyInfo ?: BabyInfo()
                BabySettingsDialog(
                    initialName = currentInfo.name,
                    initialBirthday = currentInfo.birthday,
                    onDismiss = { showSettingsDialog = false },
                    onSave = { name, birthday ->
                        viewModel.updateBabyInfo(name, birthday)
                        showSettingsDialog = false
                    }
                )
            }

            when (val state = uiState) {
                is GalleryUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is GalleryUiState.Success -> {
                    Column {
                        if (state.favoritePhotos.isNotEmpty()) {
                            HighlightSection(
                                photos = state.favoritePhotos,
                                babyBirthday = state.babyInfo.birthday,
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onUpdateMemo = { photo, memo -> viewModel.updateMemo(photo, memo) }
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
                                    babyBirthday = state.babyInfo.birthday,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAnalyzeMonth = { month, photos -> 
                                        viewModel.analyzeMonth(month, photos)
                                    }
                                )
                            } else {
                                PhotoGrid(
                                    state = babyPhotoGridState,
                                    photos = photos,
                                    babyBirthday = state.babyInfo.birthday,
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
fun BabyInfoBanner(babyInfo: BabyInfo) {
    val dday = DateUtils.calculateDDay(babyInfo.birthday, System.currentTimeMillis())
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("👶", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (babyInfo.name.isEmpty()) "우리 아기" else babyInfo.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "태어난 지 ${DateUtils.formatDDay(dday)}째 되는 날",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun BabySettingsDialog(
    initialName: String,
    initialBirthday: Long,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var year by remember { 
        val cal = Calendar.getInstance()
        if (initialBirthday > 0) cal.timeInMillis = initialBirthday
        mutableStateOf(cal.get(Calendar.YEAR).toString()) 
    }
    var month by remember { 
        val cal = Calendar.getInstance()
        if (initialBirthday > 0) cal.timeInMillis = initialBirthday
        mutableStateOf((cal.get(Calendar.MONTH) + 1).toString()) 
    }
    var day by remember { 
        val cal = Calendar.getInstance()
        if (initialBirthday > 0) cal.timeInMillis = initialBirthday
        mutableStateOf(cal.get(Calendar.DAY_OF_MONTH).toString()) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("아기 정보 설정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("아기 이름") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("생년월일", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = year, onValueChange = { year = it }, label = { Text("년") }, modifier = Modifier.weight(1.5f))
                    OutlinedTextField(value = month, onValueChange = { month = it }, label = { Text("월") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = day, onValueChange = { day = it }, label = { Text("일") }, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cal = Calendar.getInstance().apply {
                    set(year.toIntOrNull() ?: 2024, (month.toIntOrNull() ?: 1) - 1, day.toIntOrNull() ?: 1)
                }
                onSave(name, cal.timeInMillis)
            }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
fun HighlightSection(
    photos: List<Photo>,
    babyBirthday: Long,
    onToggleFavorite: (Photo) -> Unit,
    onUpdateMemo: (Photo, String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }

    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "하이라이트 (성장 일기)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "접기" else "펼치기"
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(photos) { photo ->
                        var showMemoDialog by remember { mutableStateOf(false) }
                        
                        if (showMemoDialog) {
                            MemoDialog(
                                photo = photo,
                                onDismiss = { showMemoDialog = false },
                                onSave = { memo ->
                                    onUpdateMemo(photo, memo)
                                    showMemoDialog = false
                                }
                            )
                        }

                        Column(
                            modifier = Modifier
                                .width(160.dp)
                                .clickable { showMemoDialog = true }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(RoundedCornerShape(20.dp))
                            ) {
                                PhotoItem(
                                    photo = photo, 
                                    babyBirthday = babyBirthday, 
                                    onToggleFavorite = onToggleFavorite
                                )
                            }
                            if (photo.memo.isNotEmpty()) {
                                Text(
                                    text = photo.memo,
                                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.secondary
                                    ),
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    text = "메모를 남겨보세요...",
                                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = Color.LightGray
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
    }
}

@Composable
fun MemoDialog(
    photo: Photo,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var memo by remember { 
        val defaultDate = DateUtils.formatDate(if (photo.dateTaken > 0) photo.dateTaken else photo.dateAdded * 1000L)
        mutableStateOf(photo.memo.ifEmpty { defaultDate }) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오늘의 기록") },
        text = {
            OutlinedTextField(
                value = memo,
                onValueChange = { memo = it },
                label = { Text("당시 상황이나 느낌을 적어주세요") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        },
        confirmButton = {
            Button(onClick = { onSave(memo) }) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
fun TimelineGrid(
    state: LazyGridState,
    groupedPhotos: Map<String, List<Photo>>,
    analyzingMonths: Set<String>,
    babyBirthday: Long,
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
                PhotoItem(photo = photo, babyBirthday = babyBirthday, onToggleFavorite = onToggleFavorite)
            }
        }
    }
}

@Composable
fun PhotoGrid(
    state: LazyGridState,
    photos: List<Photo>,
    babyBirthday: Long,
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
            PhotoItem(photo = photo, babyBirthday = babyBirthday, onToggleFavorite = onToggleFavorite)
        }
    }
}

@Composable
fun PhotoItem(
    photo: Photo,
    babyBirthday: Long,
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

        // D-Day Tag
        if (babyBirthday > 0) {
            val photoDDay = DateUtils.calculateDDay(babyBirthday, photo.dateAdded * 1000L)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
                color = Color.Black.copy(alpha = 0.5f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = DateUtils.formatDDay(photoDDay),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

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
