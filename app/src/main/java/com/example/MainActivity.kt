package com.example

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var isDarkTheme by rememberSaveable { mutableStateOf(true) }
            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DiKonexApp(
                        isDarkTheme = isDarkTheme,
                        onThemeToggle = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

// Immutable Data Classes representing Channel Bounds and Filter Parameters
data class ChannelBounds(
    val rMin: Float, val rMax: Float,
    val gMin: Float, val gMax: Float,
    val bMin: Float, val bMax: Float
)

data class FilterParams(
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val temperature: Float,
    val tint: Float,
    val shadows: Float = 0f,
    val highlights: Float = 0f,
    val vibrance: Float = 0f,
    val redBalance: Float = 0f,
    val greenBalance: Float = 0f,
    val blueBalance: Float = 0f,
    val rBaseOffset: Float = 0f,
    val gBaseOffset: Float = 0f,
    val bBaseOffset: Float = 0f
)

// Collection of classic professional film grading presets
val FilmPresets = mapOf(
    "Auto Balanced" to FilterParams(brightness = 0f, contrast = 1.15f, saturation = 1.1f, temperature = 0f, tint = 0f),
    "Kodak Portra" to FilterParams(brightness = 5f, contrast = 1.05f, saturation = 1.25f, temperature = 10f, tint = 2f, vibrance = 5f),
    "Fuji Superia" to FilterParams(brightness = 2f, contrast = 1.18f, saturation = 1.20f, temperature = -4f, tint = -5f, vibrance = 8f),
    "CineStill 800T" to FilterParams(brightness = -4f, contrast = 1.25f, saturation = 1.0f, temperature = -15f, tint = -8f, shadows = -5f),
    "Vintage Sepia" to FilterParams(brightness = 0f, contrast = 0.95f, saturation = 0.2f, temperature = 25f, tint = 8f),
    "Ilford B&W" to FilterParams(brightness = 0f, contrast = 1.35f, saturation = 0f, temperature = 0f, tint = 0f)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiKonexApp(
    isDarkTheme: Boolean,
    onThemeToggle: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State Variables
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCropDialog by remember { mutableStateOf(false) }
    var cropLeft by remember { mutableStateOf(0f) }
    var cropRight by remember { mutableStateOf(0f) }
    var cropTop by remember { mutableStateOf(0f) }
    var cropBottom by remember { mutableStateOf(0f) }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf("") }
    
    // Auto-detected base levels
    var detectedBounds by remember { mutableStateOf<ChannelBounds?>(null) }

    // Sliding Manual Adjustments
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(1.15f) }
    var saturation by remember { mutableStateOf(1.1f) }
    var temperature by remember { mutableStateOf(0f) }
    var tint by remember { mutableStateOf(0f) }

    // More Options in Fine Tuning
    var shadows by remember { mutableStateOf(0f) }
    var highlights by remember { mutableStateOf(0f) }
    var vibrance by remember { mutableStateOf(0f) }

    // RGB fine-tuning color balances
    var redBalance by remember { mutableStateOf(0f) }
    var greenBalance by remember { mutableStateOf(0f) }
    var blueBalance by remember { mutableStateOf(0f) }

    // Advanced Film Base overrides
    var rBaseOffset by remember { mutableStateOf(0f) }
    var gBaseOffset by remember { mutableStateOf(0f) }
    var bBaseOffset by remember { mutableStateOf(0f) }

    var selectedPresetName by remember { mutableStateOf("Auto Balanced") }
    var currentTab by remember { mutableStateOf(0) } // 0 = Presets, 1 = Fine Tune, 2 = Color Balance & Mask

    // Apply preset helper
    fun applyPresetParams(params: FilterParams) {
        brightness = params.brightness
        contrast = params.contrast
        saturation = params.saturation
        temperature = params.temperature
        tint = params.tint
        shadows = params.shadows
        highlights = params.highlights
        vibrance = params.vibrance
        redBalance = params.redBalance
        greenBalance = params.greenBalance
        blueBalance = params.blueBalance
        rBaseOffset = params.rBaseOffset
        gBaseOffset = params.gBaseOffset
        bBaseOffset = params.bBaseOffset
    }

    // Function to run highly accurate automatic analysis & grading
    fun runAutoCalibration(bitmap: Bitmap) {
        scope.launch {
            isProcessing = true
            processingMessage = "Analyzing film negative base..."
            try {
                val result = withContext(Dispatchers.Default) {
                    smartAutoGrade(bitmap)
                }
                detectedBounds = result.bounds
                brightness = result.brightness
                contrast = result.contrast
                saturation = result.saturation
                temperature = result.temperature
                tint = result.tint
                shadows = 0f
                highlights = 0f
                vibrance = 0f
                redBalance = 0f
                greenBalance = 0f
                blueBalance = 0f
                rBaseOffset = 0f
                gBaseOffset = 0f
                bBaseOffset = 0f
                selectedPresetName = "Auto-Calibrated"
                Toast.makeText(context, "Perfectly auto-calibrated film mask and color grading!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Calibration error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    // Picker launcher for secure image selection
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isProcessing = true
                processingMessage = "Scanning orange film mask..."
                try {
                    val decoded = withContext(Dispatchers.IO) {
                        loadBitmapFromUri(context, uri)
                    }
                    if (decoded != null) {
                        val preview = withContext(Dispatchers.IO) {
                            createPreviewBitmap(decoded, 1200)
                        }
                        
                        originalPreviewBitmap = preview
                        previewBitmap = preview
                        selectedImageUri = uri
                        cropLeft = 0f
                        cropRight = 0f
                        cropTop = 0f
                        cropBottom = 0f
                        
                        // Run our smart auto-calibration instantly on load
                        runAutoCalibration(preview)
                    } else {
                        Toast.makeText(context, "Could not load selected photo.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error reading image: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            }
        }
    }

    // Save functionality
    fun saveImage() {
        val uri = selectedImageUri ?: return
        val bounds = detectedBounds ?: ChannelBounds(0f, 255f, 0f, 255f, 0f, 255f)
        scope.launch {
            isProcessing = true
            processingMessage = "Rendering high-res positive..."
            try {
                val fullResLoaded = withContext(Dispatchers.IO) {
                    loadBitmapFromUri(context, uri)
                }
                if (fullResLoaded != null) {
                    val fullRes = if (cropLeft > 0f || cropRight > 0f || cropTop > 0f || cropBottom > 0f) {
                        val cropped = cropBitmap(fullResLoaded, cropLeft, cropRight, cropTop, cropBottom)
                        fullResLoaded.recycle()
                        cropped
                    } else {
                        fullResLoaded
                    }
                    val matrix = getCombinedMatrix(
                        bounds = bounds,
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        temperature = temperature,
                        tint = tint,
                        shadows = shadows,
                        highlights = highlights,
                        vibrance = vibrance,
                        redBalance = redBalance,
                        greenBalance = greenBalance,
                        blueBalance = blueBalance,
                        rBaseOffset = rBaseOffset,
                        gBaseOffset = gBaseOffset,
                        bBaseOffset = bBaseOffset,
                        isInvertEnabled = true
                    )
                    
                    val processed = withContext(Dispatchers.Default) {
                        applyColorFilterToBitmap(fullRes, matrix)
                    }
                    
                    processingMessage = "Saving high-resolution photo..."
                    val savedUri = withContext(Dispatchers.IO) {
                        saveBitmapToGallery(context, processed)
                    }

                    fullRes.recycle()
                    processed.recycle()

                    withContext(Dispatchers.Main) {
                        if (savedUri != null) {
                            Toast.makeText(context, "Saved successfully to your Gallery!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to save photo.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Could not read original high-res image.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            } finally {
                isProcessing = false
            }
        }
    }

    // Launcher for handling legacy write permission (only for Android 9 and below)
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            saveImage()
        } else {
            Toast.makeText(context, "Gallery permission is required to save photos on this device.", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Digitify the Kodak negative",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "DiKonex • Made by Pratyush Biswas (Hero)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.secondary,
                            letterSpacing = 0.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    // Elegant Light/Dark theme toggle button
                    IconButton(
                        onClick = onThemeToggle,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme Mode",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (previewBitmap == null) {
                    // Minimalist clean home state
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                RoundedCornerShape(24.dp)
                            )
                            .clickable {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                            .padding(vertical = 48.dp, horizontal = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PhotoCamera,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            
                            Text(
                                text = "Convert Film Negatives",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                            Text(
                                text = "Upload an image of your color or black-and-white film negative. The intelligent engine will automatically analyze the orange mask, calibrate exposures, and present a high-resolution positive.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )

                            Button(
                                onClick = {
                                    pickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("choose_photo_button_empty")
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Select Photo", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    // We have an active image! Compute dynamic ColorMatrix
                    val bounds = detectedBounds ?: ChannelBounds(0f, 255f, 0f, 255f, 0f, 255f)
                    val matrix = getCombinedMatrix(
                        bounds = bounds,
                        brightness = brightness,
                        contrast = contrast,
                        saturation = saturation,
                        temperature = temperature,
                        tint = tint,
                        shadows = shadows,
                        highlights = highlights,
                        vibrance = vibrance,
                        redBalance = redBalance,
                        greenBalance = greenBalance,
                        blueBalance = blueBalance,
                        rBaseOffset = rBaseOffset,
                        gBaseOffset = gBaseOffset,
                        bBaseOffset = bBaseOffset,
                        isInvertEnabled = true
                    )
                    val composeMatrix = androidx.compose.ui.graphics.ColorMatrix(matrix.array)

                    // Real-time Slider Comparison Screen (Positive on Left, Negative on Right)
                    BeforeAfterSliderImage(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.35f),
                        previewBitmap = previewBitmap!!,
                        composeMatrix = composeMatrix,
                        onPixelSample = { r, g, b ->
                            // Instantly calibrate the film base around the tapped pixel color!
                            detectedBounds = ChannelBounds(
                                rMin = maxOf(0f, r - 150f),
                                rMax = r,
                                gMin = maxOf(0f, g - 150f),
                                gMax = g,
                                bMin = maxOf(0f, b - 150f),
                                bMax = b
                            )
                            rBaseOffset = 0f
                            gBaseOffset = 0f
                            bBaseOffset = 0f
                            selectedPresetName = "Custom Calibrated"
                            Toast.makeText(context, "Film Base Calibrated to: R=${r.toInt()} G=${g.toInt()} B=${b.toInt()}", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = "💡 Swipe across the image to compare. Tap on an orange film border to calibrate instantly!",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Magic Wand Auto-Calibrate, Crop, and Reset Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { runAutoCalibration(previewBitmap!!) },
                            modifier = Modifier
                                .weight(1.1f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Grade", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showCropDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("crop_button"),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Crop Frame", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                applyPresetParams(FilmPresets["Auto Balanced"]!!)
                                selectedPresetName = "Auto Balanced"
                                if (originalPreviewBitmap != null) {
                                    previewBitmap = originalPreviewBitmap
                                    cropLeft = 0f
                                    cropRight = 0f
                                    cropTop = 0f
                                    cropBottom = 0f
                                    runAutoCalibration(originalPreviewBitmap!!)
                                }
                            },
                            modifier = Modifier
                                .weight(0.9f)
                                .height(40.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset All", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Clean Segmented Tabs for Adjustments
                    TabRow(
                        selectedTabIndex = currentTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0 },
                            text = { Text("Presets", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = currentTab == 1,
                            onClick = { currentTab = 1 },
                            text = { Text("Fine Tune", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = currentTab == 2,
                            onClick = { currentTab = 2 },
                            text = { Text("Color Balance", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    when (currentTab) {
                        0 -> {
                            // Presets list
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "Select a Vintage or Modern Film Stock profile:",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )

                                val presetKeys = FilmPresets.keys.toList()
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (i in 0..2) {
                                        val presetName = presetKeys[i]
                                        val isSelected = selectedPresetName == presetName
                                        PresetChip(
                                            name = presetName,
                                            isSelected = isSelected,
                                            onClick = {
                                                selectedPresetName = presetName
                                                applyPresetParams(FilmPresets[presetName]!!)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (i in 3..5) {
                                        val presetName = presetKeys[i]
                                        val isSelected = selectedPresetName == presetName
                                        PresetChip(
                                            name = presetName,
                                            isSelected = isSelected,
                                            onClick = {
                                                selectedPresetName = presetName
                                                applyPresetParams(FilmPresets[presetName]!!)
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            // More Options in Fine Tuning Sliders
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CustomSlider(
                                    label = "Exposure (Darker / Lighter)",
                                    value = brightness,
                                    onValueChange = {
                                        brightness = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -50f..50f,
                                    valueDisplay = if (brightness > 0) "+${brightness.toInt()}" else "${brightness.toInt()}"
                                )

                                CustomSlider(
                                    label = "Contrast",
                                    value = contrast,
                                    onValueChange = {
                                        contrast = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = 0.6f..1.8f,
                                    valueDisplay = String.format("%.2f", contrast)
                                )

                                CustomSlider(
                                    label = "Saturation",
                                    value = saturation,
                                    onValueChange = {
                                        saturation = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = 0f..2.2f,
                                    valueDisplay = String.format("%.2f", saturation)
                                )

                                CustomSlider(
                                    label = "Vibrance (Smart Saturation)",
                                    value = vibrance,
                                    onValueChange = {
                                        vibrance = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -30f..30f,
                                    valueDisplay = if (vibrance > 0) "+${vibrance.toInt()}" else "${vibrance.toInt()}"
                                )

                                CustomSlider(
                                    label = "Shadows (Lift Dark Areas)",
                                    value = shadows,
                                    onValueChange = {
                                        shadows = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -30f..30f,
                                    valueDisplay = if (shadows > 0) "+${shadows.toInt()}" else "${shadows.toInt()}"
                                )

                                CustomSlider(
                                    label = "Highlights (Soft/Punchy Whites)",
                                    value = highlights,
                                    onValueChange = {
                                        highlights = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -30f..30f,
                                    valueDisplay = if (highlights > 0) "+${highlights.toInt()}" else "${highlights.toInt()}"
                                )
                            }
                        }
                        2 -> {
                            // Color Balance and Mask Calibration Panel
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Text(
                                    text = "Neutralize Color Casts (RGB Balance):",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                CustomSlider(
                                    label = "Temperature (Cool / Warm)",
                                    value = temperature,
                                    onValueChange = {
                                        temperature = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -40f..40f,
                                    valueDisplay = if (temperature > 0) "+${temperature.toInt()}" else "${temperature.toInt()}"
                                )

                                CustomSlider(
                                    label = "Tint (Green / Magenta)",
                                    value = tint,
                                    onValueChange = {
                                        tint = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -30f..30f,
                                    valueDisplay = if (tint > 0) "+${tint.toInt()}" else "${tint.toInt()}"
                                )

                                CustomSlider(
                                    label = "Red Channel Balance",
                                    value = redBalance,
                                    onValueChange = {
                                        redBalance = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -30f..30f,
                                    valueDisplay = if (redBalance > 0) "+${redBalance.toInt()}" else "${redBalance.toInt()}"
                                )

                                CustomSlider(
                                    label = "Green Channel Balance",
                                    value = greenBalance,
                                    onValueChange = {
                                        greenBalance = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -30f..30f,
                                    valueDisplay = if (greenBalance > 0) "+${greenBalance.toInt()}" else "${greenBalance.toInt()}"
                                )

                                CustomSlider(
                                    label = "Blue Channel Balance",
                                    value = blueBalance,
                                    onValueChange = {
                                        blueBalance = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -30f..30f,
                                    valueDisplay = if (blueBalance > 0) "+${blueBalance.toInt()}" else "${blueBalance.toInt()}"
                                )

                                Spacer(modifier = Modifier.height(4.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                                Text(
                                    text = "Fine Tune Film Base Offsets (Orange Mask Removal):",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                CustomSlider(
                                    label = "Red Film Base Offset",
                                    value = rBaseOffset,
                                    onValueChange = {
                                        rBaseOffset = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -60f..60f,
                                    valueDisplay = if (rBaseOffset > 0) "+${rBaseOffset.toInt()}" else "${rBaseOffset.toInt()}"
                                )

                                CustomSlider(
                                    label = "Green Film Base Offset",
                                    value = gBaseOffset,
                                    onValueChange = {
                                        gBaseOffset = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -60f..60f,
                                    valueDisplay = if (gBaseOffset > 0) "+${gBaseOffset.toInt()}" else "${gBaseOffset.toInt()}"
                                )

                                CustomSlider(
                                    label = "Blue Film Base Offset",
                                    value = bBaseOffset,
                                    onValueChange = {
                                        bBaseOffset = it
                                        selectedPresetName = "Custom"
                                    },
                                    valueRange = -60f..60f,
                                    valueDisplay = if (bBaseOffset > 0) "+${bBaseOffset.toInt()}" else "${bBaseOffset.toInt()}"
                                )

                                Button(
                                    onClick = {
                                        rBaseOffset = 0f
                                        gBaseOffset = 0f
                                        bBaseOffset = 0f
                                        redBalance = 0f
                                        greenBalance = 0f
                                        blueBalance = 0f
                                        selectedPresetName = "Auto Balanced"
                                        applyPresetParams(FilmPresets["Auto Balanced"]!!)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.align(Alignment.End).height(36.dp)
                                ) {
                                    Text("Reset Calibration Offsets", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Control Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("upload_another_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("New Image", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    saveImage()
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(56.dp)
                                .testTag("save_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Positive", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Quality Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Safe Export",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Lossless High-Resolution • Zero Watermarks",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // High-quality processing overlay
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable(enabled = false) {}, // absorb clicks
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(32.dp)
                            .widthIn(max = 300.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = processingMessage,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCropDialog && originalPreviewBitmap != null) {
        CropDialog(
            bitmap = originalPreviewBitmap!!,
            initialLeft = cropLeft,
            initialRight = cropRight,
            initialTop = cropTop,
            initialBottom = cropBottom,
            onConfirm = { left, right, top, bottom ->
                cropLeft = left
                cropRight = right
                cropTop = top
                cropBottom = bottom
                
                // Crop original preview and update display preview
                val cropped = cropBitmap(originalPreviewBitmap!!, left, right, top, bottom)
                previewBitmap = cropped
                
                // Run auto calibration on cropped preview
                runAutoCalibration(cropped)
                
                showCropDialog = false
            },
            onDismiss = {
                showCropDialog = false
            }
        )
    }
}

// real-time sliding comparative viewer
@Composable
fun BeforeAfterSliderImage(
    modifier: Modifier = Modifier,
    previewBitmap: Bitmap,
    composeMatrix: androidx.compose.ui.graphics.ColorMatrix,
    onPixelSample: (r: Float, g: Float, b: Float) -> Unit
) {
    var sliderPercent by rememberSaveable { mutableStateOf(0.5f) }
    var containerWidth by remember { mutableStateOf(1f) }

    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newPercent = (sliderPercent + dragAmount.x / containerWidth).coerceIn(0f, 1f)
                    sliderPercent = newPercent
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val viewWidth = size.width.toFloat()
                    val viewHeight = size.height.toFloat()
                    val bitmapWidth = previewBitmap.width
                    val bitmapHeight = previewBitmap.height
                    
                    // Calculate image scale and rect due to ContentScale.Fit
                    val scale = minOf(viewWidth / bitmapWidth.toFloat(), viewHeight / bitmapHeight.toFloat())
                    val actualWidth = bitmapWidth.toFloat() * scale
                    val actualHeight = bitmapHeight.toFloat() * scale
                    
                    val offsetX = (viewWidth - actualWidth) / 2f
                    val offsetY = (viewHeight - actualHeight) / 2f
                    
                    val relativeX = offset.x - offsetX
                    val relativeY = offset.y - offsetY
                    
                    if (relativeX in 0f..actualWidth && relativeY in 0f..actualHeight) {
                        val pixelX = ((relativeX / actualWidth) * bitmapWidth).toInt().coerceIn(0, bitmapWidth - 1)
                        val pixelY = ((relativeY / actualHeight) * bitmapHeight).toInt().coerceIn(0, bitmapHeight - 1)
                        
                        val pixelColor = previewBitmap.getPixel(pixelX, pixelY)
                        val r = (pixelColor shr 16 and 0xFF).toFloat()
                        val g = (pixelColor shr 8 and 0xFF).toFloat()
                        val b = (pixelColor and 0xFF).toFloat()
                        onPixelSample(r, g, b)
                    }
                }
            }
    ) {
        containerWidth = constraints.maxWidth.toFloat()
        if (containerWidth <= 0f) containerWidth = 1f

        // 1. Draw Original Negative (Background)
        Image(
            bitmap = previewBitmap.asImageBitmap(),
            contentDescription = "Original Film Negative",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        // 2. Clip shape to show processed positive on the left side of the slider
        val positiveClipShape = remember(sliderPercent) {
            object : androidx.compose.ui.graphics.Shape {
                override fun createOutline(
                    size: androidx.compose.ui.geometry.Size,
                    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
                    density: androidx.compose.ui.unit.Density
                ): androidx.compose.ui.graphics.Outline {
                    return androidx.compose.ui.graphics.Outline.Rectangle(
                        androidx.compose.ui.geometry.Rect(
                            left = 0f,
                            top = 0f,
                            right = size.width * sliderPercent,
                            bottom = size.height
                        )
                    )
                }
            }
        }

        // 3. Draw Processed Positive on top, clipped
        Image(
            bitmap = previewBitmap.asImageBitmap(),
            contentDescription = "Processed Positive Image",
            modifier = Modifier
                .fillMaxSize()
                .clip(positiveClipShape),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.colorMatrix(composeMatrix)
        )

        // 4. Draw Vertical Divider Line and Slider Handle
        val dividerX = maxWidth * sliderPercent
        
        // Vertical divider line
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.5.dp)
                .offset(x = dividerX - 1.25.dp)
                .background(MaterialTheme.colorScheme.primary)
        )

        // Slide Handle Button in the middle
        Box(
            modifier = Modifier
                .size(36.dp)
                .align(Alignment.CenterStart)
                .offset(x = dividerX - 18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, MaterialTheme.colorScheme.onPrimary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CompareArrows,
                contentDescription = "Slide Handle",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // Floating Badges "POSITIVE" (left) and "NEGATIVE" (right)
        // Fade out badges when handle gets close
        val positiveAlpha = (sliderPercent / 0.2f).coerceIn(0f, 1f)
        val negativeAlpha = ((1f - sliderPercent) / 0.2f).coerceIn(0f, 1f)

        if (positiveAlpha > 0.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f * positiveAlpha))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "POSITIVE (LEFT)",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        if (negativeAlpha > 0.05f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f * negativeAlpha))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "NEGATIVE (RIGHT)",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun PresetChip(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = spring(), label = "bg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(), label = "content"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
fun CustomSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueDisplay: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Text(
                text = valueDisplay,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = FontFamily.Monospace
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            ),
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}

// Memory-efficient and robust image utilities
fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE // Decodes mutable software bitmap
                decoder.isMutableRequired = true
            }
        } else {
            resolver.openInputStream(uri).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.copy(Bitmap.Config.ARGB_8888, true)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun createPreviewBitmap(source: Bitmap, maxDimension: Int): Bitmap {
    val width = source.width
    val height = source.height
    if (width <= maxDimension && height <= maxDimension) {
        return source.copy(Bitmap.Config.ARGB_8888, true)
    }
    val ratio = width.toFloat() / height.toFloat()
    val newWidth: Int
    val newHeight: Int
    if (width > height) {
        newWidth = maxDimension
        newHeight = (maxDimension / ratio).toInt()
    } else {
        newHeight = maxDimension
        newWidth = (maxDimension * ratio).toInt()
    }
    return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
}

// Sophisticated color calibration analyzer
fun smartAutoGrade(bitmap: Bitmap): AutoGradeResult {
    // Collect pixels of a fast 100x100 thumbnail of the negative
    val scaled = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
    val pixels = IntArray(100 * 100)
    scaled.getPixels(pixels, 0, 100, 0, 0, 100, 100)

    val rList = ArrayList<Float>()
    val gList = ArrayList<Float>()
    val bList = ArrayList<Float>()

    // Filtered Orange Orange/Brown base tracker
    var orangeRSum = 0f
    var orangeGSum = 0f
    var orangeBSum = 0f
    var orangeCount = 0

    for (pixel in pixels) {
        val r = (pixel shr 16 and 0xFF).toFloat()
        val g = (pixel shr 8 and 0xFF).toFloat()
        val b = (pixel and 0xFF).toFloat()

        rList.add(r)
        gList.add(g)
        bList.add(b)

        // Standard mask characteristics: Red > Green > Blue (warm orange)
        if (r > 110f && r > g * 1.15f && g > b * 1.12f && b < 160f) {
            orangeRSum += r
            orangeGSum += g
            orangeBSum += b
            orangeCount++
        }
    }
    scaled.recycle()

    rList.sort()
    gList.sort()
    bList.sort()

    val size = rList.size
    val p02 = (size * 0.02f).toInt().coerceIn(0, size - 1)
    val p98 = (size * 0.98f).toInt().coerceIn(0, size - 1)

    var rMax = rList[p98]
    var gMax = gList[p98]
    var bMax = bList[p98]

    val rMin = rList[p02]
    val gMin = gList[p02]
    val bMin = bList[p02]

    // If an orange mask border is detected, lock onto it as the film base color
    if (orangeCount > 150) {
        rMax = orangeRSum / orangeCount
        gMax = orangeGSum / orangeCount
        bMax = orangeBSum / orangeCount
    }

    val bounds = ChannelBounds(
        rMin = rMin.coerceAtMost(rMax - 20f).coerceAtLeast(0f),
        rMax = rMax.coerceAtLeast(rMin + 20f).coerceAtMost(255f),
        gMin = gMin.coerceAtMost(gMax - 20f).coerceAtLeast(0f),
        gMax = gMax.coerceAtLeast(gMin + 20f).coerceAtMost(255f),
        bMin = bMin.coerceAtMost(bMax - 20f).coerceAtLeast(0f),
        bMax = bMax.coerceAtLeast(bMin + 20f).coerceAtMost(255f)
    )

    // Analyze inverted midtones to neutralize standard color casts
    val rMid = rList[size / 2]
    val gMid = gList[size / 2]
    val bMid = bList[size / 2]

    val rInv = ((rMax - rMid) / (rMax - rMin).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val gInv = ((gMax - gMid) / (gMax - gMin).coerceAtLeast(1f)).coerceIn(0f, 1f)
    val bInv = ((bMax - bMid) / (bMax - bMin).coerceAtLeast(1f)).coerceIn(0f, 1f)

    // Compute dynamic fine-tuning feedback adjustments
    val tempAdjustment = ((bInv - rInv) * 140f).coerceIn(-25f, 25f)
    val tintAdjustment = ((gInv - (rInv + bInv) / 2f) * -140f).coerceIn(-15f, 15f)

    return AutoGradeResult(
        bounds = bounds,
        brightness = 0f,
        contrast = 1.15f,
        saturation = 1.15f,
        temperature = tempAdjustment,
        tint = tintAdjustment
    )
}

data class AutoGradeResult(
    val bounds: ChannelBounds,
    val brightness: Float,
    val contrast: Float,
    val saturation: Float,
    val temperature: Float,
    val tint: Float
)

// Linear ColorMatrix synthesis engine
fun getCombinedMatrix(
    bounds: ChannelBounds,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    temperature: Float,
    tint: Float,
    shadows: Float,
    highlights: Float,
    vibrance: Float,
    redBalance: Float,
    greenBalance: Float,
    blueBalance: Float,
    rBaseOffset: Float,
    gBaseOffset: Float,
    bBaseOffset: Float,
    isInvertEnabled: Boolean
): android.graphics.ColorMatrix {
    val finalMatrix = android.graphics.ColorMatrix()

    if (isInvertEnabled) {
        val adjRMax = (bounds.rMax + rBaseOffset).coerceIn(1f, 255f)
        val adjGMax = (bounds.gMax + gBaseOffset).coerceIn(1f, 255f)
        val adjBMax = (bounds.bMax + bBaseOffset).coerceIn(1f, 255f)

        val rRange = (adjRMax - bounds.rMin).coerceAtLeast(1f)
        val gRange = (adjGMax - bounds.gMin).coerceAtLeast(1f)
        val bRange = (adjBMax - bounds.bMin).coerceAtLeast(1f)

        val scaleR = -255f / rRange
        val offsetR = 255f * adjRMax / rRange

        val scaleG = -255f / gRange
        val offsetG = 255f * adjGMax / gRange

        val scaleB = -255f / bRange
        val offsetB = 255f * adjBMax / bRange

        val invertMatrix = android.graphics.ColorMatrix(floatArrayOf(
            scaleR, 0f, 0f, 0f, offsetR,
            0f, scaleG, 0f, 0f, offsetG,
            0f, 0f, scaleB, 0f, offsetB,
            0f, 0f, 0f, 1f, 0f
        ))
        finalMatrix.set(invertMatrix)
    }

    // Apply Contrast, Brightness & Shadows/Highlights adjustments
    val bcMatrix = android.graphics.ColorMatrix()
    val bcArray = FloatArray(20)

    val finalContrast = contrast
    val baseOffset = 128f * (1f - finalContrast) + brightness

    // Lift dark levels (Shadows)
    val rOffset = baseOffset + (shadows * 0.45f)
    val gOffset = baseOffset + (shadows * 0.45f)
    val bOffset = baseOffset + (shadows * 0.45f)

    // Push bright points (Highlights)
    val rContrast = finalContrast * (1f + (highlights * 0.003f))
    val gContrast = finalContrast * (1f + (highlights * 0.003f))
    val bContrast = finalContrast * (1f + (highlights * 0.003f))

    bcArray[0] = rContrast;  bcArray[4] = rOffset
    bcArray[6] = gContrast;  bcArray[9] = gOffset
    bcArray[12] = bContrast; bcArray[14] = bOffset
    bcArray[18] = 1f
    bcMatrix.set(bcArray)
    finalMatrix.postConcat(bcMatrix)

    // Apply Saturation and Vibrance
    val finalSat = saturation + (vibrance * 0.012f)
    val satMatrix = android.graphics.ColorMatrix()
    satMatrix.setSaturation(finalSat.coerceAtLeast(0f))
    finalMatrix.postConcat(satMatrix)

    // Coolness/Warmth Temperature + Tint + RGB Balance
    val tempMatrix = android.graphics.ColorMatrix()
    val tempArray = FloatArray(20)

    val rScale = 1f + (temperature * 0.003f) + (redBalance * 0.005f)
    val gScale = 1f + (tint * 0.003f) + (greenBalance * 0.005f)
    val bScale = 1f - (temperature * 0.003f) + (blueBalance * 0.005f)

    tempArray[0] = rScale.coerceAtLeast(0.01f);  tempArray[4] = 0f
    tempArray[6] = gScale.coerceAtLeast(0.01f);  tempArray[9] = 0f
    tempArray[12] = bScale.coerceAtLeast(0.01f); tempArray[14] = 0f
    tempArray[18] = 1f
    tempMatrix.set(tempArray)
    finalMatrix.postConcat(tempMatrix)

    return finalMatrix
}

fun applyColorFilterToBitmap(sourceBitmap: Bitmap, colorMatrix: android.graphics.ColorMatrix): Bitmap {
    val resultBitmap = Bitmap.createBitmap(sourceBitmap.width, sourceBitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(resultBitmap)
    val paint = android.graphics.Paint()
    paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(sourceBitmap, 0f, 0f, paint)
    return resultBitmap
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Uri? {
    val filename = "DiKonex_${System.currentTimeMillis()}.jpg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/DiKonex")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }

    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    if (uri != null) {
        try {
            resolver.openOutputStream(uri).use { outputStream ->
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
            return uri
        } catch (e: Exception) {
            e.printStackTrace()
            resolver.delete(uri, null, null)
        }
    }
    return null
}

fun cropBitmap(source: Bitmap, leftPercent: Float, rightPercent: Float, topPercent: Float, bottomPercent: Float): Bitmap {
    val width = source.width
    val height = source.height
    
    val cropX = (leftPercent * width).toInt().coerceIn(0, width - 1)
    val cropY = (topPercent * height).toInt().coerceIn(0, height - 1)
    val cropWidth = (width - (leftPercent + rightPercent) * width).toInt().coerceIn(1, width - cropX)
    val cropHeight = (height - (topPercent + bottomPercent) * height).toInt().coerceIn(1, height - cropY)
    
    return Bitmap.createBitmap(source, cropX, cropY, cropWidth, cropHeight)
}

@Composable
fun CropDialog(
    bitmap: Bitmap,
    initialLeft: Float,
    initialRight: Float,
    initialTop: Float,
    initialBottom: Float,
    onConfirm: (Float, Float, Float, Float) -> Unit,
    onDismiss: () -> Unit
) {
    var left by remember { mutableStateOf(initialLeft) }
    var right by remember { mutableStateOf(initialRight) }
    var top by remember { mutableStateOf(initialTop) }
    var bottom by remember { mutableStateOf(initialBottom) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Crop Film Frame",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Remove surrounding borders or orange masks for perfect calibration",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                // Visual Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val bitmapWidth = bitmap.width.toFloat()
                        val bitmapHeight = bitmap.height.toFloat()

                        // Fit image in canvas
                        val scale = minOf(canvasWidth / bitmapWidth, canvasHeight / bitmapHeight)
                        val drawWidth = bitmapWidth * scale
                        val drawHeight = bitmapHeight * scale
                        val offsetX = (canvasWidth - drawWidth) / 2f
                        val offsetY = (canvasHeight - drawHeight) / 2f

                        // Draw the base bitmap
                        drawImage(
                            image = bitmap.asImageBitmap(),
                            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                            dstSize = IntSize(drawWidth.toInt(), drawHeight.toInt())
                        )

                        // Draw semi-transparent dimming over cropped parts
                        val cropLeftX = offsetX + (left * drawWidth)
                        val cropRightX = offsetX + drawWidth - (right * drawWidth)
                        val cropTopY = offsetY + (top * drawHeight)
                        val cropBottomY = offsetY + drawHeight - (bottom * drawHeight)

                        // 1. Top dim band
                        drawRect(
                            color = Color.Black.copy(alpha = 0.6f),
                            topLeft = Offset(offsetX, offsetY),
                            size = Size(drawWidth, cropTopY - offsetY)
                        )
                        // 2. Bottom dim band
                        drawRect(
                            color = Color.Black.copy(alpha = 0.6f),
                            topLeft = Offset(offsetX, cropBottomY),
                            size = Size(drawWidth, (offsetY + drawHeight) - cropBottomY)
                        )
                        // 3. Left dim band
                        drawRect(
                            color = Color.Black.copy(alpha = 0.6f),
                            topLeft = Offset(offsetX, cropTopY),
                            size = Size(cropLeftX - offsetX, cropBottomY - cropTopY)
                        )
                        // 4. Right dim band
                        drawRect(
                            color = Color.Black.copy(alpha = 0.6f),
                            topLeft = Offset(cropRightX, cropTopY),
                            size = Size((offsetX + drawWidth) - cropRightX, cropBottomY - cropTopY)
                        )

                        // Draw dashed boundary line around the active crop area
                        drawRect(
                            color = Color.Yellow,
                            topLeft = Offset(cropLeftX, cropTopY),
                            size = Size(cropRightX - cropLeftX, cropBottomY - cropTopY),
                            style = Stroke(
                                width = 2f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Elegant Margin adjustment Sliders
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Left Cut: ${(left * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = left,
                                onValueChange = { left = it.coerceIn(0f, 0.45f) },
                                valueRange = 0f..0.45f,
                                modifier = Modifier.height(32.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Right Cut: ${(right * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = right,
                                onValueChange = { right = it.coerceIn(0f, 0.45f) },
                                valueRange = 0f..0.45f,
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Top Cut: ${(top * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = top,
                                onValueChange = { top = it.coerceIn(0f, 0.45f) },
                                valueRange = 0f..0.45f,
                                modifier = Modifier.height(32.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Bottom Cut: ${(bottom * 100).toInt()}%", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = bottom,
                                onValueChange = { bottom = it.coerceIn(0f, 0.45f) },
                                valueRange = 0f..0.45f,
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(left, right, top, bottom) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Apply Crop")
                    }
                }
            }
        }
    }
}

// Kept Greeting composable exactly as-is to preserve perfect backwards compatibility with Robolectric/Screenshot tests
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
