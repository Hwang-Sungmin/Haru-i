package com.sungmin.haru_i.ui.gallery

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        val filteredPhotos: List<Photo>,
        val groupedPhotos: Map<String, List<Photo>>,
        val favoritePhotos: List<Photo>,
        val albums: List<AlbumEntity> = emptyList(),
        val selectedTab: Int = 0,
        val babyInfo: BabyInfo = BabyInfo(),
        val selectedTimelineMonth: String? = null
    ) : GalleryUiState()
    data class Error(val message: String) : GalleryUiState()
}

class GalleryViewModel(
    private val repository: PhotoRepository,
    private val faceDetectorHelper: FaceDetectorHelper,
    private val babyManager: BabyManager
) : ViewModel() {

    private val _allPhotos = MutableStateFlow<List<Photo>>(emptyList())
    private val _filteredPhotos = MutableStateFlow<List<Photo>>(emptyList())
    private val _favoritePhotos = MutableStateFlow<List<Photo>>(emptyList())
    private val _groupedPhotos = MutableStateFlow<Map<String, List<Photo>>>(emptyMap())
    private val _selectedTab = MutableStateFlow(0)
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _analyzingMonths = MutableStateFlow<Set<String>>(emptySet())
    val analyzingMonths = _analyzingMonths.asStateFlow()
    
    private val _selectedTimelineMonth = MutableStateFlow<String?>(null)
    val selectedTimelineMonth = _selectedTimelineMonth.asStateFlow()
    
    private val _selectionMode = MutableStateFlow(false)
    val selectionMode = _selectionMode.asStateFlow()
    
    private val _selectedPhotos = MutableStateFlow<Set<Long>>(emptySet())
    val selectedPhotos = _selectedPhotos.asStateFlow()
    
    val babyInfo = babyManager.babyInfo
    val albums = repository.getAllAlbums()

    val uiState: StateFlow<GalleryUiState> = combine(
        _allPhotos, _filteredPhotos, _groupedPhotos, _favoritePhotos, _selectedTab, _isLoading, _errorMessage, babyInfo, _selectedTimelineMonth, albums
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val all = args[0] as List<Photo>
        @Suppress("UNCHECKED_CAST")
        val filtered = args[1] as List<Photo>
        @Suppress("UNCHECKED_CAST")
        val grouped = args[2] as Map<String, List<Photo>>
        @Suppress("UNCHECKED_CAST")
        val favorites = args[3] as List<Photo>
        val tab = args[4] as Int
        val loading = args[5] as Boolean
        val error = args[6] as String?
        val baby = args[7] as BabyInfo
        val selectedMonth = args[8] as String?
        @Suppress("UNCHECKED_CAST")
        val albumList = args[9] as List<AlbumEntity>

        when {
            error != null -> GalleryUiState.Error(error)
            loading && all.isEmpty() -> GalleryUiState.Loading
            else -> GalleryUiState.Success(
                allPhotos = all,
                filteredPhotos = filtered,
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

    fun updateBabyInfo(name: String, birthday: Long, photoUri: Uri? = null, context: Context? = null) {
        babyManager.updateBabyInfo(name, birthday, photoUri?.toString())
        
        // 서버에 아기 사진 등록
        if (photoUri != null && context != null) {
            viewModelScope.launch {
                try {
                    val file = getFileFromUri(context, photoUri)
                    if (file != null) {
                        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                        val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                        RetrofitClient.apiService.registerBaby(body)
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
        if (_analyzingMonths.value.contains(month)) return

        viewModelScope.launch {
            _analyzingMonths.value = _analyzingMonths.value + month
            
            val currentFiltered = _filteredPhotos.value.toMutableList()
            
            photos.forEach { photo ->
                if (currentFiltered.none { it.id == photo.id }) {
                    // 1단계: 온디바이스 ML Kit 분석 (얼굴 유무 및 아기 여부)
                    if (faceDetectorHelper.isBabyPhoto(photo.uri)) {
                        
                        // 2단계: 서버 정밀 분석 (등록된 우리 아기가 맞는지)
                        try {
                            val file = getFileFromUri(context, photo.uri)
                            if (file != null) {
                                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                                val response = RetrofitClient.apiService.analyzePhoto(body)
                                
                                if (response.is_target_baby) {
                                    currentFiltered.add(photo.copy())
                                    _filteredPhotos.value = currentFiltered.toList()
                                }
                            }
                        } catch (e: Exception) {
                            // 서버 통신 실패 시에는 1단계 결과만 믿고 추가하거나, 보수적으로 제외 가능
                            // 여기서는 정밀 분석이 목표이므로 서버 응답 실패 시 일단 제외 처리
                            e.printStackTrace()
                        }
                    }
                }
            }
            
            _analyzingMonths.value = _analyzingMonths.value - month
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
                    
                    // Set default month if not selected
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
