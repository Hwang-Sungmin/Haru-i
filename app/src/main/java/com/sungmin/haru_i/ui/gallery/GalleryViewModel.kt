package com.sungmin.haru_i.ui.gallery

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sungmin.haru_i.data.BabyInfo
import com.sungmin.haru_i.data.BabyManager
import com.sungmin.haru_i.data.FaceDetectorHelper
import com.sungmin.haru_i.data.PhotoRepository
import com.sungmin.haru_i.data.local.AlbumEntity
import com.sungmin.haru_i.data.remote.RetrofitClient
import com.sungmin.haru_i.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class GalleryUiState {
    object Loading : GalleryUiState()
    data class Success(
        val allPhotos: List<Photo>,
        val groupedPhotos: Map<String, List<Photo>>,
        val favoritePhotos: List<Photo>,
        val albums: List<AlbumEntity> = emptyList(),
        val selectedTab: Int = 0,
        val babyInfo: BabyInfo = BabyInfo(),
        val selectedTimelineMonth: String? = null
    ) : GalleryUiState() {
        val filteredPhotos: List<Photo> = allPhotos.filter { it.isBaby }
    }
    data class Error(val message: String) : GalleryUiState()
}

class GalleryViewModel(
    private val repository: PhotoRepository,
    private val faceDetectorHelper: FaceDetectorHelper,
    private val babyManager: BabyManager
) : ViewModel() {

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    private val _favoritePhotos = MutableStateFlow<List<Photo>>(emptyList())
    private val _groupedPhotos = MutableStateFlow<Map<String, List<Photo>>>(emptyMap())
    private val _selectedTab = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    
    // 강제 유지 상태: WorkManager가 종료를 보고할 때까지 무조건 "분석 중" 유지
    private val _stickyAnalyzingMonths = MutableStateFlow<Set<String>>(emptySet())
    private val _stickyAnalyzingJournals = MutableStateFlow<Set<Long>>(emptySet())

    // WorkManager 상태 추적
    private val activeWorkManagerMonths: StateFlow<Set<String>> = repository.getWorkManager()
        .getWorkInfosByTagLiveData("analysis_task")
        .asFlow()
        .map { workInfos ->
            // 현재 활성 상태인 작업의 월 태그 추출
            val activeMonths = workInfos.filter { !it.state.isFinished }
                .mapNotNull { info -> 
                    info.tags.find { it.startsWith("analysis_") && it != "analysis_task" }?.removePrefix("analysis_")
                }.toSet()
            
            // 종료된 작업의 월 태그 추출하여 sticky 상태 해제
            val finishedMonths = workInfos.filter { it.state.isFinished }
                .mapNotNull { info -> 
                    info.tags.find { it.startsWith("analysis_") && it != "analysis_task" }?.removePrefix("analysis_")
                }
            if (finishedMonths.isNotEmpty()) {
                _stickyAnalyzingMonths.value = _stickyAnalyzingMonths.value - finishedMonths.toSet()
            }
            
            activeMonths
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val activeWorkManagerJournals: StateFlow<Set<Long>> = repository.getWorkManager()
        .getWorkInfosByTagLiveData("journal_task")
        .asFlow()
        .map { workInfos ->
            val activeIds = workInfos.filter { !it.state.isFinished }
                .mapNotNull { info ->
                    info.tags.find { it.startsWith("journal_") && it != "journal_task" }?.removePrefix("journal_")?.toLongOrNull()
                }.toSet()
                
            val finishedIds = workInfos.filter { it.state.isFinished }
                .mapNotNull { info ->
                    info.tags.find { it.startsWith("journal_") && it != "journal_task" }?.removePrefix("journal_")?.toLongOrNull()
                }
            if (finishedIds.isNotEmpty()) {
                _stickyAnalyzingJournals.value = _stickyAnalyzingJournals.value - finishedIds.toSet()
            }
            
            activeIds
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // 최종 UI 상태: Sticky 상태거나 실제 활성 작업이 있는 경우 "분석 중"
    val analyzingMonths: StateFlow<Set<String>> = combine(
        _stickyAnalyzingMonths, activeWorkManagerMonths
    ) { sticky, active ->
        sticky + active
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isAnalyzingJournal: StateFlow<Set<Long>> = combine(
        _stickyAnalyzingJournals, activeWorkManagerJournals
    ) { sticky, active ->
        sticky + active
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    
    private val _selectedTimelineMonth = MutableStateFlow<String?>(null)
    val selectedTimelineMonth = _selectedTimelineMonth.asStateFlow()
    
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode = _selectionMode.asStateFlow()
    
    private val _selectedPhotos = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPhotos = _selectedPhotos.asStateFlow()
    
    val babyInfo = babyManager.babyInfo
    val albums = repository.getAllAlbums()

    val uiState: StateFlow<GalleryUiState> = combine(
        _allPhotos, _groupedPhotos, _favoritePhotos, _selectedTab, _isLoading, _errorMessage, babyInfo, _selectedTimelineMonth, albums
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val all = args[0] as List<Photo>
        @Suppress("UNCHECKED_CAST")
        val grouped = args[1] as Map<String, List<Photo>>
        @Suppress("UNCHECKED_CAST")
        val favorites = args[2] as List<Photo>
        val tab = args[3] as Int
        val loading = args[4] as Boolean
        val error = args[5] as String?
        val baby = args[6] as BabyInfo
        val selectedMonth = args[7] as String?
        @Suppress("UNCHECKED_CAST")
        val albumList = args[8] as List<AlbumEntity>

        when {
            error != null -> GalleryUiState.Error(error)
            loading && all.isEmpty() -> GalleryUiState.Loading
            else -> GalleryUiState.Success(
                allPhotos = all,
                groupedPhotos = grouped,
                favoritePhotos = favorites,
                albums = albumList,
                selectedTab = tab,
                babyInfo = baby,
                selectedTimelineMonth = selectedMonth
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GalleryUiState.Loading)

    fun toggleSelectionMode(enabled: Boolean) {
        _selectionMode.value = enabled
        if (!enabled) _selectedPhotos.value = emptySet()
    }

    fun togglePhotoSelection(photoId: Long) {
        val current = _selectedPhotos.value
        _selectedPhotos.value = if (current.contains(photoId)) current - photoId else current + photoId
    }

    fun createAlbum(name: String) {
        viewModelScope.launch {
            val selected = _allPhotos.value.filter { _selectedPhotos.value.contains(it.id) }
            repository.createAlbum(name, selected)
            toggleSelectionMode(false)
        }
    }

    fun addSelectedPhotosToAlbum(albumId: Long) {
        viewModelScope.launch {
            val selected = _allPhotos.value.filter { _selectedPhotos.value.contains(it.id) }
            repository.addPhotosToAlbum(albumId, selected)
            toggleSelectionMode(false)
        }
    }
    
    fun deleteAlbum(album: AlbumEntity) {
        viewModelScope.launch {
            repository.deleteAlbum(album)
        }
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun selectTimelineMonth(month: String) {
        _selectedTimelineMonth.value = month
    }

    fun toggleFavorite(photo: Photo) {
        viewModelScope.launch {
            repository.toggleFavorite(photo)
        }
    }

    fun updateMemo(photo: Photo, memo: String) {
        viewModelScope.launch {
            repository.updateMemo(photo, memo)
        }
    }

    fun generateSmartJournal(photo: Photo) {
        _stickyAnalyzingJournals.value = _stickyAnalyzingJournals.value + photo.id
        repository.generateSmartJournalInBackground(photo)
    }

    fun updateBabyInfo(name: String, birthday: Long, photoUri: Uri? = null, context: Context? = null) {
        babyManager.updateBabyInfo(name, birthday, photoUri?.toString())
        
        if (photoUri != null && context != null) {
            viewModelScope.launch {
                try {
                    val file = getFileFromUri(context, photoUri)
                    if (file != null) {
                        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                        RetrofitClient.apiService.registerBaby(body, babyManager.getUserId())
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "baby_reg_temp.jpg")
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    fun analyzeMonth(month: String, photos: List<Photo>, context: Context) {
        if (analyzingMonths.value.contains(month)) {
            // 중단 로직
            _stickyAnalyzingMonths.value = _stickyAnalyzingMonths.value - month
            repository.cancelAnalysis(month)
            viewModelScope.launch {
                repository.stopServer()
                repository.finishBatch()
            }
        } else {
            // 시작 로직: Sticky 상태로 전환 (WorkManager의 응답이 올 때까지 + 작업이 끝날 때까지 유지)
            _stickyAnalyzingMonths.value = _stickyAnalyzingMonths.value + month
            repository.analyzeMonthInBackground(month)
        }
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                repository.getPhotos().collect { photos ->
                    _allPhotos.value = photos
                    _favoritePhotos.value = photos.filter { it.isFavorite }
                    val grouped = groupPhotosByMonth(photos)
                    _groupedPhotos.value = grouped
                    
                    if (_selectedTimelineMonth.value == null && grouped.isNotEmpty()) {
                        _selectedTimelineMonth.value = grouped.keys.first()
                    }

                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
                _errorMessage.value = e.message ?: "Unknown Error"
            }
        }
    }

    private fun groupPhotosByMonth(photos: List<Photo>): Map<String, List<Photo>> {
        val dateFormat = SimpleDateFormat("yyyy년 MM월", Locale.KOREAN)
        return photos.groupBy { photo ->
            dateFormat.format(Date(photo.dateAdded * 1000L))
        }
    }
}
