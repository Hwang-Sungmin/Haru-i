package com.sungmin.haru_i

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sungmin.haru_i.data.FaceDetectorHelper
import com.sungmin.haru_i.data.PhotoRepository
import com.sungmin.haru_i.data.local.AppDatabase
import com.sungmin.haru_i.ui.gallery.GalleryScreen
import com.sungmin.haru_i.ui.gallery.GalleryViewModel
import com.sungmin.haru_i.ui.gallery.GalleryViewModelFactory
import com.sungmin.haru_i.ui.theme.HaruiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = AppDatabase.getDatabase(this)
        val repository = PhotoRepository(this, database.photoDao())
        val faceDetectorHelper = FaceDetectorHelper(this)
        
        setContent {
            HaruiTheme {
                val viewModel: GalleryViewModel = viewModel(
                    factory = GalleryViewModelFactory(repository, faceDetectorHelper)
                )
                GalleryScreen(viewModel = viewModel)
            }
        }
    }
}
