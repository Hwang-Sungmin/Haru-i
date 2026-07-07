package com.sungmin.haru_i.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sungmin.haru_i.data.BabyInfo
import com.sungmin.haru_i.data.BabyManager
import com.sungmin.haru_i.data.FaceDetectorHelper
import com.sungmin.haru_i.data.PhotoRepository
import com.sungmin.haru_i.model.Photo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
        val selectedTab: Int = 0,
        val babyInfo: BabyInfo = BabyInfo()
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
    
    val babyInfo = babyManager.babyInfo

    val uiState: StateFlow<GalleryUiState> = combine(
        _allPhotos, _filteredPhotos, _groupedPhotos, _favoritePhotos, _selectedTab, _isLoading, _errorMessage, babyInfo
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

        when {
            error != null -> GalleryUiState.Error(error)
            loading && all.isEmpty() -> GalleryUiState.Loading
            else -> GalleryUiState.Success(all, filtered, grouped, favorites, tab, baby)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GalleryUiState.Loading)

    fun selectTab(index: Int) {
        _selectedTab.value = index
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

    fun updateBabyInfo(name: String, birthday: Long) {
        babyManager.updateBabyInfo(name, birthday)
    }

    fun analyzeMonth(month: String, photos: List<Photo>) {
        if (_analyzingMonths.value.contains(month)) return

        viewModelScope.launch {
            _analyzingMonths.value = _analyzingMonths.value + month
            
            val currentFiltered = _filteredPhotos.value.toMutableList()
            
            photos.forEach { photo ->
                if (currentFiltered.none { it.id == photo.id }) {
                    if (faceDetectorHelper.isBabyPhoto(photo.uri)) {
                        currentFiltered.add(photo.copy())
                        _filteredPhotos.value = currentFiltered.toList()
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
                    _groupedPhotos.value = groupPhotosByMonth(photos)
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
