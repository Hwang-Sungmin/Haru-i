package com.sungmin.haru_i.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.sungmin.haru_i.data.BabyManager
import com.sungmin.haru_i.data.FaceDetectorHelper
import com.sungmin.haru_i.data.PhotoRepository

class GalleryViewModelFactory(
    private val repository: PhotoRepository,
    private val faceDetectorHelper: FaceDetectorHelper,
    private val babyManager: BabyManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GalleryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GalleryViewModel(repository, faceDetectorHelper, babyManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
