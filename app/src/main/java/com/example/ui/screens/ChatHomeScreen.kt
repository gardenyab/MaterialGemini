package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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

    var showSettings by remember { mutableStateOf(false) }
    var mobileDraweOpen by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        if (isTablet) {
            // Dual Pane Layout for Tablet Support
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Chats List (Fixed width 280.dp)
                Surface(
                    modifier = Modifier.width(280.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp
                ) {
                    ChatSidebarContent(
                        conversations = conversations,
                        currentConv = currentConv,
                        onSelectConv = { viewModel.selectConversation(it) },
                        onNewConv = { viewModel.startNewConversation() },
                        onDeleteConv = { viewModel.deleteConversation(it) },
                        onOpenSettings = { showSettings = true }
                    )
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
                                onSendMessage = { text -> viewModel.sendMessage(text) },
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
                                    onSendMessage = { text -> viewModel.sendMessage(text) },
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

        // Beautiful Settings Overlay Dialog
        if (showSettings) {
            SettingsDialog(
                currentMode = themeMode,
                dynamicColors = dynamicColors,
                onModeChange = { viewModel.changeThemeMode(it) },
                onDynamicChange = { viewModel.setDynamicColor(it) },
                onDismiss = { showSettings = false }
            )
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
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                            Text(
                                text = "💬",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 4.dp)
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
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF9E00FF),
                            Color(0xFF00E0FF),
                            Color(0xFFFF007A)
                        ),
                        start = androidx.compose.ui.geometry.Offset(0f + gradientShift, 0f),
                        end = androidx.compose.ui.geometry.Offset(1000f - gradientShift, 1000f)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✨",
                fontSize = 52.sp
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
    onSendMessage: (String) -> Unit,
    isTablet: Boolean,
    onToggleSidebar: () -> Unit
) {
    var rawInputText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()

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
                        Text(
                            text = "💬",
                            fontSize = 20.sp
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
                        text = "Чат работает через Gemini 3.5 Flash",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
                    Text(
                        text = "👇",
                        fontSize = 48.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Введите ваш первый запрос ниже...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(message = message)
                    }

                    if (isGenerating) {
                        item {
                            ThinkingDotAnimation()
                        }
                    }
                }
            }
        }

        // Message input area
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 2.dp,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .imePadding(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextField(
                    value = rawInputText,
                    onValueChange = { rawInputText = it },
                    placeholder = { Text("Спросите что-нибудь у Gemini...") },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    maxLines = 4,
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
                            if (rawInputText.isNotBlank()) {
                                val textToSend = rawInputText
                                rawInputText = ""
                                onSendMessage(textToSend)
                                keyboardController?.hide()
                            }
                        }
                    )
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (rawInputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable(enabled = rawInputText.isNotBlank()) {
                            val textToSend = rawInputText
                            rawInputText = ""
                            onSendMessage(textToSend)
                            keyboardController?.hide()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Отправить",
                        tint = if (rawInputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Modern M3 asymmetry: rounded bubble corners
    val shape = if (isUser) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 2.dp, bottomEnd = 18.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = shape,
            color = containerColor,
            tonalElevation = if (isUser) 0.dp else 1.dp,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Box(modifier = Modifier.padding(14.dp)) {
                if (isUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyLarge,
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
 * Settings Overlay Dialog
 */
@Composable
fun SettingsDialog(
    currentMode: ThemeMode,
    dynamicColors: Boolean,
    onModeChange: (ThemeMode) -> Unit,
    onDynamicChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Настройки интерфейса",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Выберите предпочтительную тему оформления и включите динамические цвета Material You.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Theme selection
                Text(
                    text = "Тема оформления",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ThemeModeRadioButton(
                        text = "Системная тема",
                        selected = currentMode == ThemeMode.SYSTEM,
                        onClick = { onModeChange(ThemeMode.SYSTEM) }
                    )
                    ThemeModeRadioButton(
                        text = "Светлая тема",
                        selected = currentMode == ThemeMode.LIGHT,
                        onClick = { onModeChange(ThemeMode.LIGHT) }
                    )
                    ThemeModeRadioButton(
                        text = "Тёмная тема",
                        selected = currentMode == ThemeMode.DARK,
                        onClick = { onModeChange(ThemeMode.DARK) }
                    )
                }

                Divider(color = MaterialTheme.colorScheme.outlineVariant)

                // Material You settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Цвета Material You",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Динамические цвета на основе обоев устройства (Android 12+)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = dynamicColors,
                        onCheckedChange = onDynamicChange
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Готово", fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun ThemeModeRadioButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}
