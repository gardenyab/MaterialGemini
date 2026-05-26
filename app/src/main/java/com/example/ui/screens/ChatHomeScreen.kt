package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.db.Conversation
import com.example.data.db.MessageEntity
import com.example.ui.components.MarkdownText
import com.example.ui.viewmodel.ChatViewModel
import com.example.ui.viewmodel.ThemeMode
import kotlinx.coroutines.launch
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import android.widget.Toast

@OptIn(ExperimentalAnimationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatHomeScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val currentConv by viewModel.currentConversation.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamicColors by viewModel.dynamicColorEnabled.collectAsStateWithLifecycle()

    val apiKeyVal by viewModel.apiKey.collectAsStateWithLifecycle()
    val selectedModelVal by viewModel.selectedModel.collectAsStateWithLifecycle()

    val isAssistantMode by viewModel.isAssistantMode.collectAsStateWithLifecycle()
    val assistantResponse by viewModel.assistantResponse.collectAsStateWithLifecycle()
    val isAssistantGenerating by viewModel.isAssistantGenerating.collectAsStateWithLifecycle()
    val assistantMessages by viewModel.assistantMessages.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var mobileDraweOpen by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        if (!isAssistantMode) {
            if (isTablet) {
            // Dual Pane Layout for Tablet Support
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Chats List (Fixed width 280.dp)
                Surface(
                    modifier = Modifier.width(280.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = Modifier.weight(1f)) {
                            ChatSidebarContent(
                                conversations = conversations,
                                currentConv = currentConv,
                                onSelectConv = { viewModel.selectConversation(it) },
                                onNewConv = { viewModel.startNewConversation() },
                                onDeleteConv = { viewModel.deleteConversation(it) },
                                onOpenSettings = { showSettings = true }
                            )
                        }
                    }
                }

                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
					color = MaterialTheme.colorScheme.outlineVariant
                )

                // Right Panel: Active Chat Room
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    AnimatedContent(
                        targetState = currentConv,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                                animationSpec = tween(300),
                                initialOffsetX = { 40 }
                            ) with fadeOut(animationSpec = tween(150))
                        },
                        label = "dialogue_animation"
                    ) { activeConv ->
                        if (activeConv != null) {
                            ChatRoomArea(
                                conversation = activeConv,
                                messages = messages,
                                isGenerating = isGenerating,
                                onSendMessage = { text, img, mime -> viewModel.sendMessage(text, img, mime) },
                                isTablet = true,
                                onToggleSidebar = {}
                            )
                        } else {
                            EmptyWelcomeState(
                                onStartFirstChat = { viewModel.startNewConversation() }
                            )
                        }
                    }
                }
            }
        } else {
            // Mobile Responsive Screen Layout (Single Pane with Animated Sidebar Drawer)
            Box(modifier = Modifier.fillMaxSize()) {
                // Active Chat Content or Welcome
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = currentConv?.title ?: "Gemini Chat",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { mobileDraweOpen = true }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Меню чатов")
                                }
                            },
                            actions = {
                                if (currentConv != null) {
                                    IconButton(onClick = { viewModel.selectConversation(null) }) {
                                        Icon(Icons.Default.Close, contentDescription = "Закрыть чат")
                                    }
                                }
                                IconButton(onClick = { showSettings = true }) {
                                    Icon(Icons.Default.Settings, contentDescription = "Настройки")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AnimatedContent(
                            targetState = currentConv,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                                    animationSpec = tween(300),
                                    initialOffsetX = { 50 }
                                ) with fadeOut(animationSpec = tween(150))
                            },
                            label = "mobile_dialogue_transition"
                        ) { activeConv ->
                            if (activeConv != null) {
                                ChatRoomArea(
                                    conversation = activeConv,
                                    messages = messages,
                                    isGenerating = isGenerating,
                                    onSendMessage = { text, img, mime -> viewModel.sendMessage(text, img, mime) },
                                    isTablet = false,
                                    onToggleSidebar = { mobileDraweOpen = true }
                                )
                            } else {
                                EmptyWelcomeState(
                                    onStartFirstChat = { viewModel.startNewConversation() }
                                )
                            }
                        }
                    }
                }

                // Custom Smooth Sliding Sidebar Drawer for Mobile
                AnimatedVisibility(
                    visible = mobileDraweOpen,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { mobileDraweOpen = false }
                    )
                }

                AnimatedVisibility(
                    visible = mobileDraweOpen,
                    enter = slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = tween(250)
                    )
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(280.dp)
                            .align(Alignment.CenterStart),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 6.dp
                    ) {
                        ChatSidebarContent(
                            conversations = conversations,
                            currentConv = currentConv,
                            onSelectConv = {
                                viewModel.selectConversation(it)
                                mobileDraweOpen = false
                            },
                            onNewConv = {
                                viewModel.startNewConversation()
                                mobileDraweOpen = false
                            },
                            onDeleteConv = { viewModel.deleteConversation(it) },
                            onOpenSettings = {
                                showSettings = true
                                mobileDraweOpen = false
                            }
                        )
                    }
                }
            }
        }

        } // Конец блока !isAssistantMode

        // Beautiful Settings Overlay Screen with right-to-left sliding animation
        androidx.compose.animation.AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(tween(300)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(250)
            ) + fadeOut(tween(250)),
            modifier = Modifier.fillMaxSize()
        ) {
            SettingsScreenContent(
                currentMode = themeMode,
                dynamicColors = dynamicColors,
                onModeChange = { viewModel.changeThemeMode(it) },
                onDynamicChange = { viewModel.setDynamicColor(it) },
                apiKey = apiKeyVal,
                onApiKeyChange = { viewModel.setApiKey(it) },
                selectedModel = selectedModelVal,
                onModelChange = { viewModel.setSelectedModel(it) },
                onDismiss = { showSettings = false }
            )
        }

        // DIGITAL ASSISTANT FLOATING SIMULATED OVERLAY SHEET
        if (isAssistantMode) {
            var assistantInput by remember { mutableStateOf("") }

            // Using MutableTransitionState to orchestrate beautiful entrance AND exit transitions
            val dialogTransitionState = remember {
                androidx.compose.animation.core.MutableTransitionState(false).apply {
                    targetState = true
                }
            }

            // Monitor state changes to dismiss completely only after exit animation finishes
            LaunchedEffect(dialogTransitionState.currentState, dialogTransitionState.targetState) {
                if (!dialogTransitionState.currentState && !dialogTransitionState.targetState) {
                    viewModel.setAssistantMode(false)
                }
            }
            
            // Soft and elegant ambient flowing animated gradient representing intelligence
            val infiniteTransition = rememberInfiniteTransition(label = "assistant_bg")
            val gradientShift by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1500f,
                animationSpec = infiniteRepeatable(
                    animation = tween(12000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "assistant_bg_shift"
            )

            val backgroundAlpha by animateFloatAsState(
                targetValue = if (dialogTransitionState.targetState) 1f else 0f,
                animationSpec = tween(400, easing = LinearOutSlowInEasing),
                label = "background_alpha"
            )

            val baseDimColor = Color.Black.copy(alpha = 0.60f * backgroundAlpha)
            val primaryGradientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f * backgroundAlpha)
            val secondaryGradientColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.50f * backgroundAlpha)
            val ambientGradientColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.40f * backgroundAlpha)

            var attachedAssistantImageB64 by remember { mutableStateOf<String?>(null) }
            var attachedAssistantMimeType by remember { mutableStateOf<String?>(null) }
            var attachedAssistantBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

            val assistantContext = LocalContext.current
            val assistantFilePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    try {
                        val contentResolver = assistantContext.contentResolver
                        val mime = contentResolver.getType(uri) ?: "image/png"
                        
                        // Use BitmapFactory.Options to decode the image with sample size to prevent OOM
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        
                        // Limit size to max 1024x1024 (e.g.)
                        var sampleSize = 1
                        while (options.outWidth / sampleSize > 1024 || options.outHeight / sampleSize > 1024) {
                            sampleSize *= 2
                        }
                        
                        options.inJustDecodeBounds = false
                        options.inSampleSize = sampleSize
                        
                        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        
                        if (bmp != null) {
                            val baos = java.io.ByteArrayOutputStream()
                            bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
                            val b64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                            
                            attachedAssistantImageB64 = b64
                            attachedAssistantMimeType = "image/jpeg"
                            attachedAssistantBitmap = bmp
                        }
                    } catch (e: Exception) {
                        Toast.makeText(assistantContext, "Ошибка загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(baseDimColor)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(primaryGradientColor, secondaryGradientColor, ambientGradientColor, Color.Transparent),
                            start = androidx.compose.ui.geometry.Offset(0f + gradientShift, 0f),
                            end = androidx.compose.ui.geometry.Offset(1000f + gradientShift, 1000f)
                        )
                    )
                    .clickable { dialogTransitionState.targetState = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visibleState = dialogTransitionState,
                    enter = slideInVertically(
                        initialOffsetY = { it },
                        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                    ) + fadeIn(tween(400)),
                    exit = slideOutVertically(
                        targetOffsetY = { it },
                        animationSpec = tween(350, easing = FastOutLinearInEasing)
                    ) + fadeOut(tween(300)),
                    modifier = Modifier
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .heightIn(max = 520.dp)
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                            .clickable(enabled = false, onClick = {}),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.98f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Цифровой Ассистент",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                IconButton(
                                    onClick = { 
                                        dialogTransitionState.targetState = false
                                    },
                                    modifier = Modifier.background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                        CircleShape
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Закрыть ассистента",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Dynamic Conversations Bubble Area
                            val assistantScrollState = rememberLazyListState()
                            LaunchedEffect(assistantMessages.size, isAssistantGenerating) {
                                if (assistantMessages.isNotEmpty()) {
                                    assistantScrollState.animateScrollToItem(assistantMessages.size - 1)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .heightIn(max = 360.dp)
                            ) {
                                if (assistantMessages.isEmpty() && !isAssistantGenerating) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.Center)
                                            .padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                            modifier = Modifier.size(44.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Вы вызвали ассистента Gemini!",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Введите любой вопрос ниже или отправьте фото для создания чата с историей.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        state = assistantScrollState,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(bottom = 8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(
                                            items = assistantMessages,
                                            key = { it.id }
                                        ) { message ->
                                            val isMsgUser = message.sender == "user"
                                            val visibleState = remember(message.id) {
                                                androidx.compose.animation.core.MutableTransitionState(false).apply {
                                                    targetState = true
                                                }
                                            }
                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                androidx.compose.animation.AnimatedVisibility(
                                                    visibleState = visibleState,
                                                    enter = fadeIn(animationSpec = tween(400)) + slideInHorizontally(
                                                        initialOffsetX = { if (isMsgUser) 120 else -120 },
                                                        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                                                    ),
                                                    exit = fadeOut(animationSpec = tween(150))
                                                ) {
                                                    MessageBubble(message = message)
                                                }
                                            }
                                        }

                                        if (isAssistantGenerating) {
                                            item {
                                                Row(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 1.5.dp)
                                                    Text(
                                                        text = "Формирование ответа...",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Image Attachment Thumbnail Preview Line
                            if (attachedAssistantBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                ) {
                                    Image(
                                        bitmap = attachedAssistantBitmap!!.asImageBitmap(),
                                        contentDescription = "Превью",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                    IconButton(
                                        onClick = {
                                            attachedAssistantImageB64 = null
                                            attachedAssistantMimeType = null
                                            attachedAssistantBitmap = null
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(20.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                            .padding(2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Удалить фото",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }

                            // Dynamic Input Control Bar
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Microphone for Voice Input
                                    val context = LocalContext.current
                                    val activity = context as? android.app.Activity
                                    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
                                    var isListening by remember { mutableStateOf(false) }
                                    var rms by remember { mutableStateOf(0f) }
                                    
                                    val permissionLauncher = rememberLauncherForActivityResult(
                                        ActivityResultContracts.RequestPermission()
                                    ) { isGranted ->
                                        if (isGranted) {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                // Support Russian, Ukrainian, and English
                                                val languages = arrayListOf("ru-RU", "uk-UA", "en-US")
                                                putExtra(RecognizerIntent.EXTRA_SUPPORTED_LANGUAGES, languages)
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU") 
                                            }
                                            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                                                override fun onReadyForSpeech(params: android.os.Bundle?) { isListening = true }
                                                override fun onBeginningOfSpeech() {}
                                                override fun onRmsChanged(rmsDb: Float) { rms = rmsDb }
                                                override fun onBufferReceived(buffer: ByteArray?) {}
                                                override fun onEndOfSpeech() { isListening = false }
                                                override fun onError(error: Int) { isListening = false }
                                                override fun onResults(results: android.os.Bundle?) {
                                                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                                    matches?.firstOrNull()?.let { assistantInput = it }
                                                    isListening = false
                                                }
                                                override fun onPartialResults(partialResults: android.os.Bundle?) {}
                                                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
                                            })
                                            speechRecognizer.startListening(intent)
                                        } else {
                                            Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
                                        }
                                    }

                                    if (!isListening) {
                                        IconButton(
                                            onClick = { assistantFilePicker.launch("image/*") }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Прикрепить фото",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        TextField(
                                            value = assistantInput,
                                            onValueChange = { assistantInput = it },
                                            placeholder = { Text("Спросите ассистента...", style = MaterialTheme.typography.bodyMedium) },
                                            modifier = Modifier.weight(1f),
                                            maxLines = 3,
                                            colors = TextFieldDefaults.colors(
                                                focusedContainerColor = Color.Transparent,
                                                unfocusedContainerColor = Color.Transparent,
                                                disabledContainerColor = Color.Transparent,
                                                focusedIndicatorColor = Color.Transparent,
                                                unfocusedIndicatorColor = Color.Transparent
                                            ),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                            keyboardActions = KeyboardActions(onSend = {
                                                if (assistantInput.isNotBlank()) {
                                                    val txt = assistantInput
                                                    val b64 = attachedAssistantImageB64
                                                    val mime = attachedAssistantMimeType
                                                    assistantInput = ""
                                                    attachedAssistantImageB64 = null
                                                    attachedAssistantMimeType = null
                                                    attachedAssistantBitmap = null
                                                    viewModel.sendAssistantMessage(txt, b64, mime)
                                                }
                                            })
                                        )
                                        
                                        IconButton(
                                            onClick = {
                                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Mic,
                                                contentDescription = "Голосовой ввод",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    } else {
                                        // Active visualizer
                                        val errorColor = MaterialTheme.colorScheme.error
                                        Canvas(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .padding(8.dp)
                                        ) {
                                            val centerY = size.height / 2
                                            val waveCount = 15
                                            for (i in 0 until waveCount) {
                                                val offset = i * (size.width / waveCount)
                                                val amplitude = (rms / 20f) * (size.height / 2) * (1f - Math.abs(i - waveCount / 2f) / (waveCount / 2f))
                                                drawLine(
                                                   color = errorColor,
                                                   start = androidx.compose.ui.geometry.Offset(offset, centerY - amplitude),
                                                   end = androidx.compose.ui.geometry.Offset(offset, centerY + amplitude),
                                                   strokeWidth = 6f
                                                )
                                            }
                                        }
                                    }

                                    val isSendEnabled = assistantInput.isNotBlank() || attachedAssistantBitmap != null
                                    IconButton(
                                        onClick = {
                                            if (isSendEnabled) {
                                                val txt = assistantInput.ifBlank { "Прикрепленное фото" }
                                                val b64 = attachedAssistantImageB64
                                                val mime = attachedAssistantMimeType
                                                assistantInput = ""
                                                attachedAssistantImageB64 = null
                                                attachedAssistantMimeType = null
                                                attachedAssistantBitmap = null
                                                viewModel.sendAssistantMessage(txt, b64, mime)
                                            }
                                        },
                                        enabled = isSendEnabled,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                if (isSendEnabled) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                CircleShape
                                            )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "Отправить",
                                            tint = if (isSendEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared layout component for Chat Sidebar listing history
 */
@Composable
fun ChatSidebarContent(
    conversations: List<Conversation>,
    currentConv: Conversation?,
    onSelectConv: (Conversation) -> Unit,
    onNewConv: () -> Unit,
    onDeleteConv: (Conversation) -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // App Expressive Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Gemini AI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Интеллектуальный чат",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Настройки",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // "Start Chat" Main Trigger Button
        Button(
            onClick = onNewConv,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Создать", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Новый диалог", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Conversation history list
        Text(
            text = "ИСТОРИЯ ЧАТОВ",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "История пока пуста",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(conversations, key = { it.id }) { item ->
                    val isSelected = currentConv?.id == item.id
                    val cardBgColor = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    }
                    val textColor = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBgColor)
                            .clickable { onSelectConv(item) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 4.dp).size(18.dp),
                                tint = textColor
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }

                        IconButton(
                            onClick = { onDeleteConv(item) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Удалить чат",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty welcome state with glowing gradient accents representing Gemini logo UI
 */
@Composable
fun EmptyWelcomeState(
    onStartFirstChat: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Expressive Gemini Logo Orb with animating color gradients
        Box(
            modifier = Modifier
                .size(125.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f + gradientShift, 0f),
                        end = androidx.compose.ui.geometry.Offset(1000f - gradientShift, 1000f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(54.dp)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Добро пожаловать в Gemini Chat!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
		)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Умный искусственный интеллект от Google у вас на ладони. Начните диалог, чтобы спросить о чём угодно, написать код, перевести текст или составить план.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 400.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onStartFirstChat,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Начать общение", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Message space inside a dialogue
 */
@Composable
fun ChatRoomArea(
    conversation: Conversation,
    messages: List<MessageEntity>,
    isGenerating: Boolean,
    onSendMessage: (String, String?, String?) -> Unit,
    isTablet: Boolean,
    onToggleSidebar: () -> Unit
) {
    var rawInputText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

    val context = LocalContext.current
    var attachedImageB64 by remember { mutableStateOf<String?>(null) }
    var attachedMimeType by remember { mutableStateOf<String?>(null) }
    var attachedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val mime = contentResolver.getType(uri) ?: "image/png"
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                if (bytes != null) {
                    val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    attachedImageB64 = b64
                    attachedMimeType = mime
                    
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    attachedBitmap = bmp
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось прочитать файл: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Key to scroll down whenever messages change size or generation finishes/starts
    LaunchedEffect(messages.size, isGenerating) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Desktop Top Bar inside Chat Pane (Mobile utilizes parent Scaffold top bar)
        if (isTablet) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = conversation.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    Text(
                        text = "Чат работает через Gemini API",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                    )
                }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        // Messages list
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !isGenerating) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Введите ваш первый запрос ниже...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = messages,
                        key = { it.id }
                    ) { message ->
                        val isUser = message.sender == "user"
                        val visibleState = remember(message.id) {
                            MutableTransitionState(false).apply {
                                targetState = true
                            }
                        }
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.animation.AnimatedVisibility(
                                visibleState = visibleState,
                                enter = fadeIn(animationSpec = tween(400)) + slideInHorizontally(
                                    initialOffsetX = { if (isUser) 120 else -120 },
                                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                                ),
                                exit = fadeOut(animationSpec = tween(150))
                            ) {
                                MessageBubble(message = message)
                            }
                        }
                    }

                    if (isGenerating) {
                        item {
                            ThinkingDotAnimation()
                        }
                    }
                }
            }
        }

        // Selected media attachment card preview
        if (attachedBitmap != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = attachedBitmap!!.asImageBitmap(),
                        contentDescription = "Выбранный файл",
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Выбранное изображение",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = attachedMimeType ?: "image/png",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(onClick = {
                        attachedBitmap = null
                        attachedImageB64 = null
                        attachedMimeType = null
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Удалить файл",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Message input area
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            tonalElevation = 1.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    placeholder = { Text("Спросите что-нибудь у Gemini...") },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    maxLines = 4,
                    leadingIcon = {
                        IconButton(onClick = { filePicker.launch("image/*") }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить фото",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = {
                        IconButton(onClick = { /* Decorational Action or Info */ }) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Инфо",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (rawInputText.isNotBlank() || attachedImageB64 != null) {
                                val textToSend = rawInputText
                                val imgB64 = attachedImageB64
                                val imgMime = attachedMimeType
                                rawInputText = ""
                                attachedImageB64 = null
                                attachedMimeType = null
                                attachedBitmap = null
                                onSendMessage(textToSend, imgB64, imgMime)
                                keyboardController?.hide()
                            }
                        }
                    )
                )

                val hasInput = rawInputText.isNotBlank() || attachedImageB64 != null
                val sendButtonBg = if (hasInput) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                val sendButtonContentColor = if (hasInput) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(sendButtonBg)
                        .clickable(enabled = hasInput) {
                            val textToSend = rawInputText
                            val imgB64 = attachedImageB64
                            val imgMime = attachedMimeType
                            rawInputText = ""
                            attachedImageB64 = null
                            attachedMimeType = null
                            attachedBitmap = null
                            onSendMessage(textToSend, imgB64, imgMime)
                            keyboardController?.hide()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = sendButtonContentColor
                    )
                }
            }
        }
    }
}

/**
 * Message Bubble visual styling
 */
@Composable
fun MessageBubble(message: MessageEntity) {
    val isUser = message.sender == "user"

    val alignment = if (isUser) Alignment.End else Alignment.Start
    val containerColor = if (isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    // Modern M3 asymmetry: rounded bubble corners matching "Sleek Interface" (28dp)
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 4.dp, bottomEnd = 28.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            tonalElevation = if (isUser) 1.dp else 2.dp,
            modifier = Modifier.widthIn(max = 340.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (!message.imageB64.isNullOrEmpty()) {
                    Base64Image(
                        b64 = message.imageB64,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .padding(bottom = 8.dp)
                    )
                }
                if (isUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        ),
                        color = contentColor
                    )
                } else {
                    MarkdownText(
                        text = message.text,
                        color = contentColor
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Text(
            text = if (isUser) "Вы" else "Gemini",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

/**
 * Animated Gemini bouncing gradient thinking dots indicating api call activity
 */
@Composable
fun ThinkingDotAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    
    // Smooth pulsing alpha animation for looking dynamic
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 2.dp, bottomEnd = 18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 140.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = scale))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E0FF).copy(alpha = scale))
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = scale))
                )
                
                Spacer(modifier = Modifier.width(4.dp))
                
                Text(
                    text = "Думает...",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Settings Screen Content showing beautifully categorized cards and large Material 3 typography
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreenContent(
    currentMode: ThemeMode,
    dynamicColors: Boolean,
    onModeChange: (ThemeMode) -> Unit,
    onDynamicChange: (Boolean) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    selectedModel: String,
    onModelChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = "Настройки",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Назад",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // General Info Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Персонализация ИИ",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Настройте тему оформления, выберите модель нейросети Gemini или установите собственный API-ключ для общения.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Theme section
            Text(
                text = "ВНЕШНИЙ ВИД",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    SettingsSelectableRow(
                        title = "Системная тема",
                        description = "Автоматическая подстройка под параметры Android",
                        isSelected = currentMode == ThemeMode.SYSTEM,
                        icon = Icons.Default.Settings,
                        onClick = { onModeChange(ThemeMode.SYSTEM) }
                    )
                    
                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SettingsSelectableRow(
                        title = "Светлая тема",
                        description = "Светлое и минималистичное оформление",
                        isSelected = currentMode == ThemeMode.LIGHT,
                        icon = Icons.Default.Home,
                        onClick = { onModeChange(ThemeMode.LIGHT) }
                    )

                    Divider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    SettingsSelectableRow(
                        title = "Тёмная тема",
                        description = "Глубокий чёрный цвет для экономии энергии",
                        isSelected = currentMode == ThemeMode.DARK,
                        icon = Icons.Default.Star,
                        onClick = { onModeChange(ThemeMode.DARK) }
                    )
                }
            }

            // Material You Switch Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Цвета Material You",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Динамическая палитра на основе Ваших обоев",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = dynamicColors,
                        onCheckedChange = onDynamicChange
                    )
                }
            }

            // AI Model selection section
            Text(
                text = "МОДЕЛЬ НЕЙРОСЕТИ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    listOf(
                        "gemini-3.5-flash" to Pair("Gemini 3.5 Flash", "Новое поколение моделей ИИ с невероятной скоростью"),
                        "gemini-3.1-flash-lite" to Pair("Gemini 3.1 Flash Lite", "Облегченная сверхскоростная модель"),
                        "gemini-2.5-flash" to Pair("Gemini 2.5 Flash", "Сбалансированная и проверенная генерация информации"),
                        "gemini-2.0-flash" to Pair("Gemini 2.0 Flash", "Универсальный и умный базовый интеллект")
                    ).forEachIndexed { index, (modelId, info) ->
                        val (name, desc) = info
                        SettingsSelectableRow(
                            title = name,
                            description = desc,
                            isSelected = selectedModel == modelId,
                            icon = Icons.Default.Star,
                            onClick = { onModelChange(modelId) }
                        )

                        if (index < 3) {
                            Divider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            }

            // API Key Details
            Text(
                text = "БЕЗОПАСНОСТЬ И ДОСТУП",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp)
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Персональный API-Ключ Gemini",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Используйте собственный ключ, чтобы избежать лимитов запросов",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = onApiKeyChange,
                        label = { Text("Ключ API Gemini") },
                        placeholder = { Text("Используется встроенный ключ по умолчанию") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Text(
                        text = "Кусок вашего ключа шифруется и безопасно сохраняется в локальной песочнице устройства. Оставьте поле пустым, чтобы вернуться к базовому ключу.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun SettingsSelectableRow(
    title: String,
    description: String,
    isSelected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}

@Composable
fun Base64Image(b64: String, modifier: Modifier = Modifier) {
    val bitmap = remember(b64) {
        try {
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Прикрепленное изображение",
            modifier = modifier,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}
