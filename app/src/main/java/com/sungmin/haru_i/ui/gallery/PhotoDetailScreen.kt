package com.sungmin.haru_i.ui.gallery

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sungmin.haru_i.model.Photo
import com.sungmin.haru_i.util.ImageSaveHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailScreen(
    photo: Photo,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        
        var isDrawingMode by remember { mutableStateOf(false) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        
        // Layout state to map coordinates
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        
        // Drawing state
        val paths = remember { mutableStateListOf<Pair<androidx.compose.ui.graphics.Path, Color>>() }
        var currentPath by remember { mutableStateOf<androidx.compose.ui.graphics.Path?>(null) }
        var selectedColor by remember { mutableStateOf(Color.Red) }

        Scaffold(
            containerColor = Color.Black,
            topBar = {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.5f),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    title = { Text(photo.name, style = MaterialTheme.typography.bodySmall) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "닫기")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isDrawingMode = !isDrawingMode }) {
                            Icon(
                                imageVector = if (isDrawingMode) Icons.Default.Check else Icons.Default.Edit,
                                contentDescription = "그리기 모드",
                                tint = if (isDrawingMode) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val loader = coil.ImageLoader(context)
                                val request = ImageRequest.Builder(context)
                                    .data(photo.uri)
                                    .allowHardware(false)
                                    .build()
                                val result = (loader.execute(request) as? SuccessResult)?.drawable
                                result?.let { drawable ->
                                    val originalBitmap = (drawable as android.graphics.drawable.BitmapDrawable).bitmap
                                    val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                                    val nativeCanvas = android.graphics.Canvas(mutableBitmap)
                                    
                                    // Calculate scale between screen canvas and original bitmap
                                    // ContentScale.Fit logic
                                    val viewAspectRatio = canvasSize.width.toFloat() / canvasSize.height.toFloat()
                                    val bitmapAspectRatio = originalBitmap.width.toFloat() / originalBitmap.height.toFloat()
                                    
                                    val (drawWidth, drawHeight) = if (viewAspectRatio > bitmapAspectRatio) {
                                        (canvasSize.height * bitmapAspectRatio) to canvasSize.height.toFloat()
                                    } else {
                                        canvasSize.width.toFloat() to (canvasSize.width / bitmapAspectRatio)
                                    }
                                    
                                    val leftOffset = (canvasSize.width - drawWidth) / 2
                                    val topOffset = (canvasSize.height - drawHeight) / 2
                                    
                                    val scaleFactor = originalBitmap.width.toFloat() / drawWidth
                                    
                                    val paint = android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        style = android.graphics.Paint.Style.STROKE
                                        strokeWidth = with(density) { 5.dp.toPx() } * scaleFactor
                                        strokeCap = android.graphics.Paint.Cap.ROUND
                                        strokeJoin = android.graphics.Paint.Join.ROUND
                                    }

                                    paths.forEach { (path, color) ->
                                        paint.color = color.toArgb()
                                        val androidPath = path.asAndroidPath()
                                        val matrix = android.graphics.Matrix()
                                        // Translate to align with image start in the view, then scale to bitmap size
                                        matrix.postTranslate(-leftOffset, -topOffset)
                                        matrix.postScale(scaleFactor, scaleFactor)
                                        
                                        val scaledPath = android.graphics.Path()
                                        androidPath.transform(matrix, scaledPath)
                                        nativeCanvas.drawPath(scaledPath, paint)
                                    }
                                    
                                    val savedUri = ImageSaveHelper.saveBitmapToGallery(context, mutableBitmap, photo.name)
                                    if (savedUri != null) {
                                        android.widget.Toast.makeText(context, "편집된 사진이 갤러리에 저장되었습니다!", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "저장")
                        }
                    }
                )
            },
            bottomBar = {
                if (isDrawingMode) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.White).forEach { color ->
                                IconButton(
                                    onClick = { selectedColor = color },
                                    modifier = Modifier.background(if (selectedColor == color) color.copy(alpha = 0.3f) else Color.Transparent, shape = CircleShape)
                                ) {
                                    Box(modifier = Modifier.size(24.dp).background(color, CircleShape))
                                }
                            }
                            IconButton(onClick = { paths.clear() }) {
                                Icon(Icons.Default.Delete, contentDescription = "지우기", tint = Color.White)
                            }
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isDrawingMode) {
                            if (isDrawingMode) {
                                detectDragGestures(
                                    onDragStart = { pointerOffset ->
                                        currentPath = androidx.compose.ui.graphics.Path().apply {
                                            moveTo(pointerOffset.x, pointerOffset.y)
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        currentPath?.lineTo(change.position.x, change.position.y)
                                        val p = currentPath
                                        currentPath = null
                                        currentPath = p
                                    },
                                    onDragEnd = {
                                        currentPath?.let { paths.add(it to selectedColor) }
                                        currentPath = null
                                    }
                                )
                            } else {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offset = Offset(offset.x + pan.x * scale, offset.y + pan.y * scale)
                                    } else {
                                        offset = Offset.Zero
                                    }
                                }
                            }
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = photo.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            ),
                        contentScale = ContentScale.Fit
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onGloballyPositioned { canvasSize = it.size }
                    ) {
                        if (!isDrawingMode) {
                            withTransform({
                                translate(offset.x, offset.y)
                                scale(scale, scale)
                            }) {
                                paths.forEach { (path, color) ->
                                    drawPath(path, color, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                                }
                                currentPath?.let {
                                    drawPath(it, selectedColor, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                                }
                            }
                        } else {
                            paths.forEach { (path, color) ->
                                drawPath(path, color, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                            }
                            currentPath?.let {
                                drawPath(it, selectedColor, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                    }
                }
            }
        }
    }
}
