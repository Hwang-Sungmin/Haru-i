package com.sungmin.haru_i.ui.gallery

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.sungmin.haru_i.data.BabyInfo
import com.sungmin.haru_i.data.local.AlbumEntity
import com.sungmin.haru_i.model.Photo
import com.sungmin.haru_i.util.DateUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    viewModel: GalleryViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val analyzingMonths by viewModel.analyzingMonths.collectAsState()
    val isAnalyzingJournal by viewModel.isAnalyzingJournal.collectAsState()
    val selectionMode by viewModel.selectionMode.collectAsState()
    val selectedPhotos by viewModel.selectedPhotos.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAlbumSelectionDialog by remember { mutableStateOf(false) }
    var showAlbumCreateDialog by remember { mutableStateOf(false) }
    var selectedPhotoForDetail by remember { mutableStateOf<Photo?>(null) }
    var selectedAlbumForView by remember { mutableStateOf<AlbumEntity?>(null) }

    val timelineGridState = rememberLazyGridState()
    val babyPhotoGridState = rememberLazyGridState()

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) viewModel.loadPhotos()
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
                            if (selectionMode) "${selectedPhotos.size}개 선택됨" else "우리 아기 갤러리",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        if (selectionMode) {
                            IconButton(onClick = { viewModel.toggleSelectionMode(false) }) {
                                Icon(Icons.Default.Close, contentDescription = "취소")
                            }
                        }
                    },
                    actions = {
                        if (!selectionMode) {
                            IconButton(onClick = { showSettingsDialog = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "설정")
                            }
                        } else if (selectedPhotos.isNotEmpty()) {
                            IconButton(onClick = { showAlbumSelectionDialog = true }) {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = "앨범에 추가")
                            }
                        }
                    }
                )
                
                if (uiState is GalleryUiState.Success) {
                    val state = uiState as GalleryUiState.Success
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
        val context = LocalContext.current
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (showSettingsDialog) {
                val currentInfo = (uiState as? GalleryUiState.Success)?.babyInfo ?: BabyInfo()
                val allPhotos = (uiState as? GalleryUiState.Success)?.allPhotos ?: emptyList()
                BabySettingsDialog(
                    initialName = currentInfo.name,
                    initialBirthday = currentInfo.birthday,
                    initialPhotoUri = currentInfo.referencePhotoUri,
                    allPhotos = allPhotos,
                    onDismiss = { showSettingsDialog = false },
                    onSave = { name, birthday, photoUri ->
                        viewModel.updateBabyInfo(name, birthday, photoUri, context)
                        showSettingsDialog = false
                    }
                )
            }

            selectedPhotoForDetail?.let { photo ->
                PhotoDetailScreen(photo = photo, onDismiss = { selectedPhotoForDetail = null })
            }

            if (showAlbumSelectionDialog) {
                AlbumSelectionDialog(
                    albums = (uiState as? GalleryUiState.Success)?.albums ?: emptyList(),
                    onAlbumSelected = { album ->
                        viewModel.addSelectedPhotosToAlbum(album.id)
                        showAlbumSelectionDialog = false
                    },
                    onCreateNewAlbum = {
                        showAlbumSelectionDialog = false
                        showAlbumCreateDialog = true
                    },
                    onDismiss = { showAlbumSelectionDialog = false }
                )
            }

            if (showAlbumCreateDialog) {
                var albumName by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showAlbumCreateDialog = false },
                    title = { Text("새 앨범 만들기") },
                    text = {
                        OutlinedTextField(
                            value = albumName,
                            onValueChange = { albumName = it },
                            label = { Text("앨범 이름") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        Button(onClick = {
                            viewModel.createAlbum(albumName)
                            showAlbumCreateDialog = false
                        }) { Text("생성") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAlbumCreateDialog = false }) { Text("취소") }
                    }
                )
            }

            selectedAlbumForView?.let { album ->
                AlbumDetailDialog(
                    album = album,
                    photos = (uiState as? GalleryUiState.Success)?.allPhotos?.filter { it.albumId == album.id } ?: emptyList(),
                    babyBirthday = (uiState as? GalleryUiState.Success)?.babyInfo?.birthday ?: 0L,
                    onDismiss = { selectedAlbumForView = null },
                    onPhotoClick = { selectedPhotoForDetail = it },
                    onDeleteAlbum = { 
                        viewModel.deleteAlbum(album)
                        selectedAlbumForView = null
                    }
                )
            }

            when (val state = uiState) {
                is GalleryUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is GalleryUiState.Success -> {
                    Column {
                        HighlightSection(
                            photos = state.favoritePhotos,
                            albums = state.albums,
                            babyBirthday = state.babyInfo.birthday,
                            selectionMode = selectionMode,
                            selectedPhotos = selectedPhotos,
                            isAnalyzingJournal = isAnalyzingJournal,
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onUpdateMemo = { photo, memo -> viewModel.updateMemo(photo, memo) },
                            onPhotoClick = { selectedPhotoForDetail = it },
                            onPhotoSelect = { viewModel.togglePhotoSelection(it) },
                            onAlbumClick = { selectedAlbumForView = it },
                            onLongClick = { 
                                viewModel.toggleSelectionMode(true)
                                viewModel.togglePhotoSelection(it.id)
                            },
                            onGenerateAiCaption = { viewModel.generateSmartJournal(it) }
                        )
                        
                        val photos = if (state.selectedTab == 0) state.allPhotos else state.filteredPhotos
                        if (photos.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(text = if (state.selectedTab == 0) "사진이 없습니다." else "인식된 아기 사진이 없습니다.")
                            }
                        } else {
                            if (state.selectedTab == 0) {
                                TimelineGrid(
                                    state = timelineGridState,
                                    groupedPhotos = state.groupedPhotos,
                                    confirmedBabyIds = state.filteredPhotos.map { it.id }.toSet(),
                                    selectedMonth = state.selectedTimelineMonth,
                                    analyzingMonths = analyzingMonths,
                                    babyBirthday = state.babyInfo.birthday,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onAnalyzeMonth = { month, p -> viewModel.analyzeMonth(month, p, context) },
                                    onPhotoClick = { selectedPhotoForDetail = it },
                                    onMonthSelect = { viewModel.selectTimelineMonth(it) }
                                )
                            } else {
                                PhotoGrid(
                                    state = babyPhotoGridState,
                                    photos = photos,
                                    babyBirthday = state.babyInfo.birthday,
                                    onToggleFavorite = { viewModel.toggleFavorite(it) },
                                    onPhotoClick = { selectedPhotoForDetail = it }
                                )
                            }
                        }
                    }
                }
                is GalleryUiState.Error -> Text(text = "에러: ${state.message}", modifier = Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun BabyInfoBanner(babyInfo: BabyInfo) {
    val dday = DateUtils.calculateDDay(babyInfo.birthday, System.currentTimeMillis())
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                Text("👶", fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = if (babyInfo.name.isEmpty()) "우리 아기" else babyInfo.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = "태어난 지 ${DateUtils.formatDDay(dday)}째 되는 날", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabySettingsDialog(
    initialName: String,
    initialBirthday: Long,
    initialPhotoUri: String?,
    allPhotos: List<Photo>,
    onDismiss: () -> Unit,
    onSave: (String, Long, Uri?) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(initialPhotoUri?.let { Uri.parse(it) }) }
    var showPhotoPicker by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (initialBirthday > 0) initialBirthday else System.currentTimeMillis()
    )
    var showDatePicker by remember { mutableStateOf(false) }

    if (showPhotoPicker) {
        InternalPhotoPicker(
            photos = allPhotos,
            onPhotoSelected = {
                selectedPhotoUri = it.uri
                showPhotoPicker = false
            },
            onDismiss = { showPhotoPicker = false }
        )
    }

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("확인") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("아기 정보 설정", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showPhotoPicker = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedPhotoUri != null) {
                        AsyncImage(
                            model = selectedPhotoUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Default.AddAPhoto,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Text(
                    "정면 아기 사진을 등록해 주세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("아기 이름") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = datePickerState.selectedDateMillis?.let { DateUtils.formatDate(it) } ?: "",
                    onValueChange = {},
                    label = { Text("생년월일") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledBorderColor = MaterialTheme.colorScheme.outline,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { Icon(Icons.Default.CalendarToday, null) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(name, datePickerState.selectedDateMillis ?: 0L, selectedPhotoUri)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalPhotoPicker(
    photos: List<Photo>,
    onPhotoSelected: (Photo) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("사진 선택", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                        }
                    }
                )
            }
        ) { padding ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(photos, key = { it.id }) { photo ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { onPhotoSelected(photo) },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
    }
}

@Composable
fun HighlightSection(
    photos: List<Photo>,
    albums: List<AlbumEntity>,
    babyBirthday: Long,
    selectionMode: Boolean,
    selectedPhotos: Set<Long>,
    isAnalyzingJournal: Set<Long>,
    onToggleFavorite: (Photo) -> Unit,
    onUpdateMemo: (Photo, String) -> Unit,
    onPhotoClick: (Photo) -> Unit,
    onPhotoSelect: (Long) -> Unit,
    onAlbumClick: (AlbumEntity) -> Unit,
    onLongClick: (Photo) -> Unit,
    onGenerateAiCaption: (Photo) -> Unit
) {
    var isExpanded by remember { mutableStateOf(true) }
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "하이라이트 (성장 일기)", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Icon(imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, contentDescription = null)
        }
        AnimatedVisibility(visible = isExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(albums, key = { "album_${it.id}" }) { album -> AlbumFolderItem(album = album, photos = photos.filter { it.albumId == album.id }, onClick = { onAlbumClick(album) }) }
                items(photos.filter { it.albumId == null }, key = { "photo_${it.id}" }) { photo ->
                    var showMemoDialog by remember { mutableStateOf(false) }
                    if (showMemoDialog) {
                        MemoDialog(
                            photo = photo,
                            isAnalyzing = isAnalyzingJournal.contains(photo.id),
                            onDismiss = { showMemoDialog = false },
                            onSave = { onUpdateMemo(photo, it) },
                            onGenerateAiCaption = { onGenerateAiCaption(photo) }
                        )
                    }
                    Column(modifier = Modifier.width(160.dp)) {
                        Box(modifier = Modifier.size(160.dp).clip(RoundedCornerShape(20.dp))) {
                            val lines = photo.memo.split("\n"); PhotoItem(photo = photo, babyBirthday = babyBirthday, selectionMode = selectionMode, isSelected = selectedPhotos.contains(photo.id), onToggleFavorite = onToggleFavorite, onClick = { if (selectionMode) onPhotoSelect(photo.id) else onPhotoClick(photo) }, onLongClick = { onLongClick(photo) }, displayDate = if (lines.isNotEmpty() && lines[0].contains("일")) lines[0] else null)
                        }
                        if (photo.memo.isNotEmpty()) {
                            val lines = photo.memo.split("\n"); val content = if (lines.size > 1) lines.drop(1).joinToString("\n").trim() else if (lines.isNotEmpty() && !lines[0].contains("일")) lines[0] else ""
                            if (content.isNotEmpty()) Text(text = content, modifier = Modifier.padding(top = 8.dp, start = 4.dp).clickable { showMemoDialog = true }, style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.secondary), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        } else Text(text = "메모를 남겨보세요...", modifier = Modifier.padding(top = 8.dp, start = 4.dp).clickable { showMemoDialog = true }, style = MaterialTheme.typography.bodySmall.copy(color = Color.LightGray))
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 16.dp).padding(horizontal = 16.dp), thickness = 0.5.dp, color = Color.LightGray)
    }
}

@Composable
fun AlbumFolderItem(album: AlbumEntity, photos: List<Photo>, onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp).clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(160.dp).background(Color.LightGray.copy(alpha = 0.3f), RoundedCornerShape(20.dp)).padding(8.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) { AlbumPreviewImage(photos.getOrNull(0), Modifier.weight(1f)); AlbumPreviewImage(photos.getOrNull(1), Modifier.weight(1f)) }
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) { AlbumPreviewImage(photos.getOrNull(2), Modifier.weight(1f)); AlbumPreviewImage(photos.getOrNull(3), Modifier.weight(1f)) }
            }
        }
        Text(text = album.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 8.dp), maxLines = 1)
        Text(text = "${photos.size}장의 사진", style = MaterialTheme.typography.bodySmall.copy(color = Color.Gray))
    }
}

@Composable
fun AlbumPreviewImage(photo: Photo?, modifier: Modifier) {
    if (photo != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(photo.uri).crossfade(true).build(), contentDescription = null, modifier = modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
    else Box(modifier = modifier.fillMaxSize().background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(8.dp)))
}

@Composable
fun MemoDialog(
    photo: Photo,
    isAnalyzing: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onGenerateAiCaption: () -> Unit = {}
) {
    val defaultDate = DateUtils.formatDate(if (photo.dateTaken > 0) photo.dateTaken else photo.dateAdded * 1000L)
    var memo by remember { mutableStateOf(photo.memo.ifEmpty { "$defaultDate\n" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("오늘의 기록")
                TextButton(
                    onClick = onGenerateAiCaption,
                    enabled = !isAnalyzing
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("AI 추천", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (photo.aiCaption != null || photo.emotion != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when (photo.emotion?.lowercase()) {
                                        "happy" -> "😊 행복해요"
                                        "sad" -> "😢 슬퍼요"
                                        "angry" -> "😠 화나요"
                                        "surprised" -> "😲 놀랐어요"
                                        "neutral" -> "😐 평온해요"
                                        "sleepy" -> "😴 졸려요"
                                        else -> "✨ 분석됨"
                                    },
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(onClick = {
                                    val aiText = "${photo.aiCaption ?: ""} ${photo.emotion ?: ""}".trim()
                                    if (aiText.isNotEmpty()) {
                                        memo = if (memo.endsWith("\n")) memo + aiText else memo + "\n" + aiText
                                    }
                                }) {
                                    Text("메모에 넣기", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            if (photo.aiCaption != null) {
                                Text(
                                    text = photo.aiCaption,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("당시 상황이나 느낌을 적어주세요") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(memo) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineGrid(
    state: LazyGridState,
    groupedPhotos: Map<String, List<Photo>>,
    confirmedBabyIds: Set<Long>,
    selectedMonth: String?,
    analyzingMonths: Set<String>,
    babyBirthday: Long,
    onToggleFavorite: (Photo) -> Unit,
    onAnalyzeMonth: (String, List<Photo>) -> Unit,
    onPhotoClick: (Photo) -> Unit,
    onMonthSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val months = groupedPhotos.keys.toList()

    LazyVerticalGrid(
        state = state,
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        val photosToShow = selectedMonth?.let { groupedPhotos[it] } ?: emptyList()
        
        if (selectedMonth != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 통합된 월 선택 셀렉터
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.wrapContentWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, true)
                                .clickable { expanded = true }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = selectedMonth,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }

                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            months.forEach { month ->
                                DropdownMenuItem(
                                    text = { Text(month) },
                                    onClick = {
                                        onMonthSelect(month)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                    
                    val isAnalyzing = analyzingMonths.contains(selectedMonth)
                    TextButton(
                        onClick = { onAnalyzeMonth(selectedMonth, photosToShow) },
                        enabled = true
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("분석 중 (중단)", style = MaterialTheme.typography.labelMedium)
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
            
            items(photosToShow, key = { it.id }) { photo ->
                PhotoItem(
                    photo = photo, 
                    babyBirthday = babyBirthday, 
                    isBabyConfirmed = confirmedBabyIds.contains(photo.id),
                    onToggleFavorite = onToggleFavorite,
                    onClick = { onPhotoClick(photo) }
                )
            }
        }
    }
}

@Composable
fun PhotoGrid(state: LazyGridState, photos: List<Photo>, babyBirthday: Long, onToggleFavorite: (Photo) -> Unit, onPhotoClick: (Photo) -> Unit) {
    LazyVerticalGrid(state = state, columns = GridCells.Fixed(3), contentPadding = PaddingValues(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
        items(photos, key = { it.id }) { PhotoItem(photo = it, babyBirthday = babyBirthday, onToggleFavorite = onToggleFavorite, onClick = { onPhotoClick(it) }) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoItem(
    photo: Photo,
    babyBirthday: Long,
    onToggleFavorite: (Photo) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    selectionMode: Boolean = false,
    isSelected: Boolean = false,
    isBabyConfirmed: Boolean = false,
    displayDate: String? = null
) {
    Box(modifier = Modifier.aspectRatio(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(photo.uri).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize().graphicsLayer(alpha = if (selectionMode && !isSelected) 0.5f else 1f), contentScale = ContentScale.Crop)
        
        // Baby Confirmation Badge
        if (isBabyConfirmed) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.White.copy(alpha = 0.7f), CircleShape)
                    .padding(2.dp)
            ) {
                Text("👶", fontSize = 12.sp)
            }
        }

        // Emotion Badge
        if (photo.emotion != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(2.dp)
            ) {
                Text(
                    text = when (photo.emotion.lowercase()) {
                        "happy" -> "😊"
                        "sad" -> "😢"
                        "angry" -> "😠"
                        "surprised" -> "😲"
                        "neutral" -> "😐"
                        "sleepy" -> "😴"
                        else -> "✨"
                    },
                    fontSize = 10.sp
                )
            }
        }

        if (selectionMode) {
            Box(modifier = Modifier.fillMaxSize().background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent))
            Checkbox(checked = isSelected, onCheckedChange = { onClick() }, modifier = Modifier.align(Alignment.TopStart), colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        }
        if (!selectionMode && (babyBirthday > 0 || displayDate != null)) {
            Surface(modifier = Modifier.align(Alignment.BottomStart).padding(4.dp), color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(4.dp)) {
                Row(modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (babyBirthday > 0) Text(text = DateUtils.formatDDay(DateUtils.calculateDDay(babyBirthday, photo.dateAdded * 1000L)), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    if (displayDate != null) { if (babyBirthday > 0) Spacer(modifier = Modifier.width(4.dp)); Text(text = displayDate, color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp) }
                }
            }
        }
        IconButton(onClick = { onToggleFavorite(photo) }, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape)) {
            Icon(imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (photo.isFavorite) Color.Red else Color.White, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun AlbumSelectionDialog(albums: List<AlbumEntity>, onAlbumSelected: (AlbumEntity) -> Unit, onCreateNewAlbum: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("앨범 선택") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCreateNewAlbum, modifier = Modifier.fillMaxWidth()) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("새 앨범 만들기") } }
                if (albums.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("기존 앨범에 추가", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                    Box(modifier = Modifier.heightIn(max = 300.dp)) { LazyColumn { items(albums) { album -> ListItem(headlineContent = { Text(album.name) }, leadingContent = { Icon(Icons.Default.Folder, null) }, modifier = Modifier.clickable { onAlbumSelected(album) }) } } }
                }
            }
        },
        confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailDialog(album: AlbumEntity, photos: List<Photo>, babyBirthday: Long, onDismiss: () -> Unit, onPhotoClick: (Photo) -> Unit, onDeleteAlbum: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column {
                CenterAlignedTopAppBar(title = { Text(album.name) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) } }, actions = { IconButton(onClick = onDeleteAlbum) { Icon(Icons.Default.Delete, null) } })
                LazyVerticalGrid(columns = GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos, key = { it.id }) { PhotoItem(photo = it, babyBirthday = babyBirthday, onToggleFavorite = {}, onClick = { onPhotoClick(it) }) }
                }
                if (photos.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("앨범이 비어있습니다.") }
            }
        }
    }
}
