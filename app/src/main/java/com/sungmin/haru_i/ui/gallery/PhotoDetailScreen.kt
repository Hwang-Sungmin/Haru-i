package com.sungmin.haru_i.ui.gallery

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.sungmin.haru_i.model.Photo
import com.sungmin.haru_i.util.ImageSaveHelper
import kotlinx.coroutines.launch

enum class EditMode {
    NONE, DRAW, STICKER, TEXT
}

data class StickerItem(
    val emoji: String,
    val position: Offset,
    val fontSize: Float = 40f
)

data class TextItem(
    val text: String,
    val color: Color,
    val position: Offset,
    val fontSize: Float = 24f
)

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
        val textMeasurer = rememberTextMeasurer()
        
        var editMode by remember { mutableStateOf(EditMode.NONE) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        
        var canvasSize by remember { mutableStateOf(IntSize.Zero) }
        
        // Edit states
        val paths = remember { mutableStateListOf<Pair<androidx.compose.ui.graphics.Path, Color>>() }
        var currentPath by remember { mutableStateOf<androidx.compose.ui.graphics.Path?>(null) }
        val stickers = remember { mutableStateListOf<StickerItem>() }
        val texts = remember { mutableStateListOf<TextItem>() }
        
        var selectedColor by remember { mutableStateOf(Color.Red) }
        var showTextEntryDialog by remember { mutableStateOf(false) }

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
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(photo.name, style = MaterialTheme.typography.bodySmall)
                            if (photo.aiCaption != null || photo.emotion != null) {
                                Text(
                                    text = "${photo.emotion ?: ""} ${photo.aiCaption ?: ""}".trim(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "닫기")
                        }
                    },
                    actions = {
                        IconButton(onClick = { editMode = if (editMode == EditMode.DRAW) EditMode.NONE else EditMode.DRAW }) {
                            Icon(Icons.Default.Edit, contentDescription = "그리기", tint = if (editMode == EditMode.DRAW) MaterialTheme.colorScheme.primary else Color.White)
                        }
                        IconButton(onClick = { editMode = if (editMode == EditMode.STICKER) EditMode.NONE else EditMode.STICKER }) {
                            Icon(Icons.Default.AddReaction, contentDescription = "스티커", tint = if (editMode == EditMode.STICKER) MaterialTheme.colorScheme.primary else Color.White)
                        }
                        IconButton(onClick = { 
                            editMode = EditMode.TEXT
                            showTextEntryDialog = true 
                        }) {
                            Icon(Icons.Default.TextFields, contentDescription = "텍스트", tint = if (editMode == EditMode.TEXT) MaterialTheme.colorScheme.primary else Color.White)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val loader = ImageLoader(context)
                                val request = ImageRequest.Builder(context)
                                    .data(photo.uri)
                                    .allowHardware(false)
                                    .build()
                                val result = (loader.execute(request) as? SuccessResult)?.drawable
                                result?.let { drawable ->
                                    val originalBitmap = (drawable as BitmapDrawable).bitmap
                                    val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
                                    val nativeCanvas = android.graphics.Canvas(mutableBitmap)
                                    
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
                                    
                                    // Draw Paths
                                    val paint = Paint().apply {
                                        isAntiAlias = true
                                        style = Paint.Style.STROKE
                                        strokeWidth = with(density) { 5.dp.toPx() } * scaleFactor
                                        strokeCap = Paint.Cap.ROUND
                                        strokeJoin = Paint.Join.ROUND
                                    }
                                    paths.forEach { (path, color) ->
                                        paint.color = color.toArgb()
                                        val androidPath = path.asAndroidPath()
                                        val matrix = Matrix()
                                        matrix.postTranslate(-leftOffset, -topOffset)
                                        matrix.postScale(scaleFactor, scaleFactor)
                                        val scaledPath = android.graphics.Path()
                                        androidPath.transform(matrix, scaledPath)
                                        nativeCanvas.drawPath(scaledPath, paint)
                                    }

                                    // Draw Stickers (Emojis)
                                    val emojiPaint = Paint().apply {
                                        isAntiAlias = true
                                        textAlign = Paint.Align.CENTER
                                    }
                                    stickers.forEach { sticker ->
                                        emojiPaint.textSize = sticker.fontSize * density.density * scaleFactor
                                        val x = (sticker.position.x - leftOffset) * scaleFactor
                                        val y = (sticker.position.y - topOffset) * scaleFactor + (emojiPaint.textSize / 3)
                                        nativeCanvas.drawText(sticker.emoji, x, y, emojiPaint)
                                    }

                                    // Draw Texts
                                    val textPaint = Paint().apply {
                                        isAntiAlias = true
                                        textAlign = Paint.Align.CENTER
                                        isFakeBoldText = true
                                    }
                                    texts.forEach { textItem ->
                                        textPaint.color = textItem.color.toArgb()
                                        textPaint.textSize = textItem.fontSize * density.density * scaleFactor
                                        val x = (textItem.position.x - leftOffset) * scaleFactor
                                        val y = (textItem.position.y - topOffset) * scaleFactor + (textPaint.textSize / 3)
                                        nativeCanvas.drawText(textItem.text, x, y, textPaint)
                                    }
                                    
                                    val savedUri = ImageSaveHelper.saveBitmapToGallery(context, mutableBitmap, photo.name)
                                    if (savedUri != null) {
                                        Toast.makeText(context, "편집된 사진이 저장되었습니다!", Toast.LENGTH_SHORT).show()
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
                if (editMode != EditMode.NONE) {
                    Surface(color = Color.Black.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                        Column {
                            if (editMode == EditMode.STICKER) {
                                Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                    listOf("👶", "❤️", "⭐", "🍼", "🎁", "🐾").forEach { emoji ->
                                        IconButton(onClick = {
                                            stickers.add(StickerItem(emoji, Offset(canvasSize.width / 2f, canvasSize.height / 2f)))
                                        }) {
                                            Text(emoji, fontSize = 24.sp)
                                        }
                                    }
                                }
                            }
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                                listOf(Color.Red, Color.Yellow, Color.Green, Color.Blue, Color.White).forEach { color ->
                                    IconButton(
                                        onClick = { selectedColor = color },
                                        modifier = Modifier.background(if (selectedColor == color) color.copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                                    ) {
                                        Box(modifier = Modifier.size(24.dp).background(color, CircleShape))
                                    }
                                }
                                IconButton(onClick = { 
                                    paths.clear(); stickers.clear(); texts.clear() 
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "지우기", tint = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            if (showTextEntryDialog) {
                var textValue by remember { mutableStateOf("") }
                AlertDialog(
                    onDismissRequest = { showTextEntryDialog = false },
                    title = { Text("문구 입력") },
                    text = { OutlinedTextField(value = textValue, onValueChange = { textValue = it }, modifier = Modifier.fillMaxWidth()) },
                    confirmButton = {
                        Button(onClick = {
                            if (textValue.isNotEmpty()) {
                                texts.add(TextItem(textValue, selectedColor, Offset(canvasSize.width / 2f, canvasSize.height / 2f)))
                            }
                            showTextEntryDialog = false
                        }) { Text("확인") }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(editMode) {
                        when (editMode) {
                            EditMode.DRAW -> detectDragGestures(
                                onDragStart = { currentPath = androidx.compose.ui.graphics.Path().apply { moveTo(it.x, it.y) } },
                                onDrag = { change, _ ->
                                    change.consume()
                                    currentPath?.lineTo(change.position.x, change.position.y)
                                    val p = currentPath; currentPath = null; currentPath = p
                                },
                                onDragEnd = { currentPath?.let { paths.add(it to selectedColor) }; currentPath = null }
                            )
                            EditMode.STICKER -> detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (stickers.isNotEmpty()) {
                                    val i = stickers.size - 1
                                    stickers[i] = stickers[i].copy(position = stickers[i].position + dragAmount)
                                }
                            }
                            EditMode.TEXT -> detectDragGestures { change, dragAmount ->
                                change.consume()
                                if (texts.isNotEmpty()) {
                                    val i = texts.size - 1
                                    texts[i] = texts[i].copy(position = texts[i].position + dragAmount)
                                }
                            }
                            else -> detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                offset = if (scale > 1f) offset + pan * scale else Offset.Zero
                            }
                        }
                    }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(photo.uri).crossfade(true).build(),
                        contentDescription = photo.name,
                        modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                        contentScale = ContentScale.Fit
                    )

                    Canvas(modifier = Modifier.fillMaxSize().onGloballyPositioned { canvasSize = it.size }) {
                        withTransform({
                            if (editMode == EditMode.NONE) {
                                translate(offset.x, offset.y)
                                scale(scale, scale)
                            }
                        }) {
                            paths.forEach { (path, color) ->
                                drawPath(path, color, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                            }
                            currentPath?.let {
                                drawPath(it, selectedColor, style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round))
                            }
                            stickers.forEach { sticker ->
                                val textLayoutResult = textMeasurer.measure(
                                    sticker.emoji,
                                    TextStyle(fontSize = sticker.fontSize.sp)
                                )
                                drawText(
                                    textLayoutResult,
                                    topLeft = Offset(sticker.position.x - textLayoutResult.size.width / 2, sticker.position.y - textLayoutResult.size.height / 2)
                                )
                            }
                            texts.forEach { textItem ->
                                val textLayoutResult = textMeasurer.measure(
                                    textItem.text,
                                    TextStyle(color = textItem.color, fontSize = textItem.fontSize.sp, fontWeight = FontWeight.Bold)
                                )
                                drawText(
                                    textLayoutResult,
                                    topLeft = Offset(textItem.position.x - textLayoutResult.size.width / 2, textItem.position.y - textLayoutResult.size.height / 2)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
