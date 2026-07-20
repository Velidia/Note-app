package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Note
import com.example.data.ChecklistItem
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import com.example.util.KeepParser
import com.example.viewmodel.NoteEditorState
import com.example.viewmodel.NoteViewModel
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Dynamic CompositionLocal to dispatch Dark Mode active flag downstream
val LocalDarkTheme = compositionLocalOf { false }



fun getAdaptiveNoteColor(colorHex: String, isDark: Boolean): Color {
    if (!isDark) {
        return try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {
            Color.White
        }
    } else {
        return when (colorHex.uppercase()) {
            "#FFFFFF" -> Color(0xFF252429) // Default Charcoal Note
            "#FFCDD2" -> Color(0xFF4D2024) // Deep Maroon Red
            "#FFE0B2" -> Color(0xFF4C3015) // Deep Bronze Amber
            "#FFF9C4" -> Color(0xFF4A4418) // Muted Brass Yellow
            "#C8E6C9" -> Color(0xFF183C21) // Forest Emerald Green
            "#B2DFDB" -> Color(0xFF143B38) // Deep Mint Teal
            "#BBDEFB" -> Color(0xFF1A324E) // Ocean Cobalt Blue
            "#B3E5FC" -> Color(0xFF12374C) // Deep Sapphire Slate
            "#D1C4E9" -> Color(0xFF33204C) // Dark Royal Purple
            "#F8BBD0" -> Color(0xFF4A1F31) // Deep Magenta Pink
            "#D7CCC8" -> Color(0xFF372723) // Roasted Coffee Brown
            "#CFD8DC" -> Color(0xFF2C3236) // Slate Steel Gray
            else -> Color(0xFF252429)
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Support devices with high screen refresh rates (e.g. 90Hz, 120Hz) programmatically
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    display
                } else {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay
                }
                val modes = display?.supportedModes
                val maxRefreshMode = modes?.maxByOrNull { it.refreshRate }
                if (maxRefreshMode != null) {
                    val lp = window.attributes
                    lp.preferredDisplayModeId = maxRefreshMode.modeId
                    window.attributes = lp
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            val viewModel: NoteViewModel = viewModel()
            val themeOption by viewModel.darkModeOption.collectAsStateWithLifecycle()
            val isSystemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeOption) {
                "dark" -> true
                "light" -> false
                else -> isSystemDark
            }

            MyApplicationTheme(darkTheme = useDarkTheme) {
                CompositionLocalProvider(LocalDarkTheme provides useDarkTheme) {
                    MainNotesApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainNotesApp(viewModel: NoteViewModel = viewModel()) {
    val notes by viewModel.notesState.collectAsStateWithLifecycle()
    val activeTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val testQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val isArchiveState by viewModel.showArchived.collectAsStateWithLifecycle()
    val importToast by viewModel.importResult.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Modal Control States
    var showImporttDialog by rememberSaveable { mutableStateOf(false) }
    var showRawPasteDialog by remember { mutableStateOf(false) }

    // File selection picker launchers
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFileFromUri(uri)
        }
    }
    val backupFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackupToUri(uri)
        }
    }

    // Display a clean Toast notification when import outcomes trigger
    LaunchedEffect(importToast) {
        importToast?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearImporttResult()
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = if (LocalDarkTheme.current) Color(0xFF1C1B1F) else Color.White,
                modifier = Modifier.width(300.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (LocalDarkTheme.current) Color(0xFF2D2A33) else LightPurple)
                        .padding(horizontal = 24.dp, vertical = 32.dp)
                ) {
                    Column {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "App Logo",
                            tint = if (LocalDarkTheme.current) Color.White else PrimaryPurple,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Keep Notes Lite",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (LocalDarkTheme.current) Color.White else TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Local Notes & ZIP Backup",
                            fontSize = 11.sp,
                            color = if (LocalDarkTheme.current) Color(0xFFCCC5D0) else TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.Default.Description, contentDescription = "Notes") },
                    label = { Text(text = "Notes", fontSize = 14.sp) },
                    selected = activeTab == "notes",
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.currentTab.value = "notes"
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = if (LocalDarkTheme.current) Color(0xFF381E72) else LightPurple,
                        selectedIconColor = if (LocalDarkTheme.current) Color.White else PrimaryPurple,
                        selectedTextColor = if (LocalDarkTheme.current) Color.White else PrimaryPurple,
                        unselectedIconColor = if (LocalDarkTheme.current) Color(0xFFCCC5D0) else TextSecondary,
                        unselectedTextColor = if (LocalDarkTheme.current) Color(0xFFCCC5D0) else TextSecondary
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                
                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.Default.List, contentDescription = "Tasks") },
                    label = { Text(text = "Tasks", fontSize = 14.sp) },
                    selected = activeTab == "tasks",
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.currentTab.value = "tasks"
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = if (LocalDarkTheme.current) Color(0xFF381E72) else LightPurple,
                        selectedIconColor = if (LocalDarkTheme.current) Color.White else PrimaryPurple,
                        selectedTextColor = if (LocalDarkTheme.current) Color.White else PrimaryPurple,
                        unselectedIconColor = if (LocalDarkTheme.current) Color(0xFFCCC5D0) else TextSecondary,
                        unselectedTextColor = if (LocalDarkTheme.current) Color(0xFFCCC5D0) else TextSecondary
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
                
                NavigationDrawerItem(
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text(text = "Settings", fontSize = 14.sp) },
                    selected = activeTab == "settings",
                    onClick = {
                        scope.launch { drawerState.close() }
                        viewModel.currentTab.value = "settings"
                    },
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = if (LocalDarkTheme.current) Color(0xFF381E72) else LightPurple,
                        selectedIconColor = if (LocalDarkTheme.current) Color.White else PrimaryPurple,
                        selectedTextColor = if (LocalDarkTheme.current) Color.White else PrimaryPurple,
                        unselectedIconColor = if (LocalDarkTheme.current) Color(0xFFCCC5D0) else TextSecondary,
                        unselectedTextColor = if (LocalDarkTheme.current) Color(0xFFCCC5D0) else TextSecondary
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            floatingActionButton = {
                if (activeTab != "settings") {
                    NotesFloatingActionButton(
                        onAddTextNote = {
                            viewModel.openEditor(Note(title = "", content = "", isChecklist = false))
                        },
                        onAddChecklistNote = {
                            viewModel.openEditor(Note(title = "", content = "", isChecklist = true))
                        }
                    )
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (LocalDarkTheme.current) Color(0xFF141218) else BackgroundPurple)
                    .padding(innerPadding)
            ) {
                // Elegant Top search App Bar representing "Clean Minimalism" header
                HeaderSearchBar(
                    query = testQuery,
                    onQueryChanged = { viewModel.searchQuery.value = it },
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    }
                )

            // Horizontal Filter bar to access normal vs archived notes
            if (activeTab != "settings") {
                FilterRowHeader(
                    isArchivedMode = isArchiveState,
                    onArchiveModeToggled = { viewModel.showArchived.value = it }
                )
            }

            // Centralized Content Screen
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    "notes", "tasks" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // contextual Keep Importt Promo banner
                            if (notes.isEmpty() && testQuery.isEmpty()) {
                                KeepImporttBanner(
                                    onClick = { showImporttDialog = true }
                                )
                            }

                            if (notes.isEmpty()) {
                                // Graceful Empty States
                                EmptyNotesPlaceholder(
                                    isSearchActive = testQuery.isNotEmpty(),
                                    tab = activeTab,
                                    onImporttClick = { showImporttDialog = true }
                                )
                            } else {
                                // Reactive Staggered-friendly Grid display for notes
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize().testTag("notes_grid")
                                ) {
                                    items(notes, key = { it.id }) { note ->
                                        NoteGridCard(
                                            note = note,
                                            onClick = { viewModel.openEditor(note) },
                                            onTogglePin = { viewModel.togglePin(note) },
                                            onArchive = {
                                                viewModel.saveNote(
                                                    note.copy(
                                                        isArchived = !note.isArchived,
                                                        userEditedTimestamp = System.currentTimeMillis()
                                                    )
                                                )
                                            },
                                            onDelete = { viewModel.deleteNote(note) },
                                            onChecklistItemToggled = { itemIndex, checkedState ->
                                                val items = note.getChecklistItems().toMutableList()
                                                if (itemIndex in items.indices) {
                                                    items[itemIndex] = items[itemIndex].copy(isChecked = checkedState)
                                                    viewModel.saveNote(note.copy(content = Note.createFromChecklist(items)))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    "settings" -> {
                        SettingsAndBackupTab(
                            viewModel = viewModel,
                            onSelectFile = { filePickerLauncher.launch("*/*") },
                            onPasteJson = { showRawPasteDialog = true },
                            onExportBackup = {
                                val timestamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
                                backupFileLauncher.launch("notes-keep-local-$timestamp.zip")
                            },
                            onResetDb = { viewModel.resetDatabase() }
                        )
                    }
                }
            }
        }
    }
}

    // Active note snapshot for smooth transition slide-out
    var activeEditState by remember { mutableStateOf<NoteEditorState?>(null) }
    val isEditOpen = editorState != null
    if (editorState != null && editorState != activeEditState) {
        activeEditState = editorState
    }

    // Modal Sheet: Note Creator and Editor with elegant transitions
    AnimatedVisibility(
        visible = isEditOpen,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        ) + fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
    ) {
        activeEditState?.let { state ->
            NoteEditDialog(
                note = state.note,
                pendingChecklistItem = state.pendingChecklistItem,
                isCopyingImages = state.isCopyingImages,
                onDraftChanged = viewModel::updateEditorNote,
                onPendingChecklistItemChanged = viewModel::updatePendingChecklistItem,
                onImagesSelected = viewModel::addImagesToEditor,
                onDismiss = viewModel::closeEditor,
                onSave = { updatedNote ->
                    viewModel.saveNote(updatedNote)
                    viewModel.closeEditor()
                },
                onDelete = { draft ->
                    viewModel.deleteNote(draft)
                    viewModel.closeEditor()
                },
                onArchiveToggle = { draft ->
                    viewModel.saveNote(
                        draft.copy(
                            isArchived = !draft.isArchived,
                            userEditedTimestamp = System.currentTimeMillis()
                        )
                    )
                    viewModel.closeEditor()
                },
                onPinToggle = { draft ->
                    val pinned = draft.copy(
                        isPinned = !draft.isPinned,
                        userEditedTimestamp = System.currentTimeMillis()
                    )
                    if (pinned.id != 0) {
                        viewModel.saveNote(pinned)
                    }
                    viewModel.updateEditorNote(pinned)
                }
            )
        }
    }

    // Modal Dialog: Importt Keep Takeout Hub
    if (showImporttDialog) {
        KeepImporttGuidelinesDialog(
            onDismiss = { showImporttDialog = false },
            onSelectFile = {
                showImporttDialog = false
                filePickerLauncher.launch("*/*")
            },
            onRawPasteClick = {
                showImporttDialog = false
                showRawPasteDialog = true
            }
        )
    }

    // Modal Dialog: Paste raw json text directement
    if (showRawPasteDialog) {
        PasteJsonRawDialog(
            onDismiss = { showRawPasteDialog = false },
            onImportt = { jsonText ->
                viewModel.importKeepJsonContent(jsonText)
                showRawPasteDialog = false
            }
        )
    }
}

@Composable
fun HeaderSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    onMenuClick: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val barBgColor = if (isDark) Color(0xFF211F24) else BarColor
    val textPrimaryColor = if (isDark) Color.White else TextPrimary
    val textSecondaryColor = if (isDark) Color(0xFFCCC5D0) else TextSecondary

    PaddingBox {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(barBgColor, CircleShape)
                .padding(horizontal = 16.dp)
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu icon",
                    tint = textSecondaryColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))

            TextField(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = {
                    Text(
                        text = "Search local notes...",
                        color = textSecondaryColor,
                        fontSize = 15.sp
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = textPrimaryColor,
                    unfocusedTextColor = textPrimaryColor
                ),
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_input")
            )

            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = textSecondaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun FilterRowHeader(
    isArchivedMode: Boolean,
    onArchiveModeToggled: (Boolean) -> Unit
) {
    val isDark = LocalDarkTheme.current
    val selectedChipBg = if (isDark) Color(0xFF381E72) else LightPurple
    val selectedChipText = if (isDark) Color.White else PrimaryPurple
    val unselectedChipBg = if (isDark) Color(0xFF252429) else Color(0xFFF3EDF7)
    val unselectedChipText = if (isDark) Color(0xFFCCC5D0) else TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = !isArchivedMode,
            onClick = { onArchiveModeToggled(false) },
            label = { Text("Main") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = selectedChipBg,
                selectedLabelColor = selectedChipText,
                containerColor = unselectedChipBg,
                labelColor = unselectedChipText
            ),
            modifier = Modifier.testTag("filter_class_main")
        )
        FilterChip(
            selected = isArchivedMode,
            onClick = { onArchiveModeToggled(true) },
            label = { Text("Archive") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = selectedChipBg,
                selectedLabelColor = selectedChipText,
                containerColor = unselectedChipBg,
                labelColor = unselectedChipText
            ),
            modifier = Modifier.testTag("filter_class_archive")
        )
    }
}

@Composable
fun KeepImporttBanner(
    onClick: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val bannerBgColor = if (isDark) Color(0xFF252429) else LightPurple
    val textAndIconTint = if (isDark) Color.White else Color(0xFF21005D)
    val circleBgColor = if (isDark) Color(0xFF35333A) else Color(0xFFE0D4F7)
    val textSecondaryTint = if (isDark) Color(0xFFCCC5D0) else Color(0xFF21005D).copy(alpha = 0.8f)

    PaddingBox(top = 8, bottom = 8) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(bannerBgColor, RoundedCornerShape(20.dp))
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .background(circleBgColor, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Cloud integration",
                        tint = PrimaryPurple
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Import Google Keep",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textAndIconTint
                    )
                    Text(
                        text = "Import Google Takeout export (.zip/.json)",
                        fontSize = 11.sp,
                        color = textSecondaryTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Pin",
                tint = textAndIconTint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteGridCard(
    note: Note,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onChecklistItemToggled: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = LocalDarkTheme.current
    val noteBg = getAdaptiveNoteColor(note.colorHex, isDark)
    val textPrimaryColor = if (isDark) Color.White else TextPrimary
    val textSecondaryColor = if (isDark) Color(0xFFCCC5D0) else TextSecondary
    val borderStrokeColor = if (isDark) Color(0xFF3E3D42) else BorderGray

    var showMenu by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    val images = remember(note.imagePath) {
        if (note.imagePath.isNullOrBlank()) emptyList<String>()
        else note.imagePath.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = noteBg),
        modifier = modifier
            .fillMaxWidth()
            .height(if (images.isNotEmpty()) 180.dp else 160.dp)
            .border(
                width = 1.dp,
                color = if (note.colorHex == "#FFFFFF" && !isDark) borderStrokeColor else if (isDark) borderStrokeColor.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .testTag("note_card_${note.id}")
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (images.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(65.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    when (images.size) {
                        1 -> {
                            AsyncImage(
                                model = images[0],
                                contentDescription = "Note image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        2 -> {
                            AsyncImage(
                                model = images[0],
                                contentDescription = "Note image 1",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            AsyncImage(
                                model = images[1],
                                contentDescription = "Note image 2",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        else -> {
                            AsyncImage(
                                model = images[0],
                                contentDescription = "Note image 1",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            AsyncImage(
                                model = images[1],
                                contentDescription = "Note image 2",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = images[2],
                                    contentDescription = "Note image 3",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (images.size > 3) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+${images.size - 2}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = note.title.ifEmpty { "Tanpa Judul" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Pinned note",
                        tint = PrimaryPurple,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (note.isChecklist) {
                // Checklist Render Snippet
                val items = note.getChecklistItems().take(3)
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items.forEachIndexed { index, checklistItem ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChecklistItemToggled(index, !checklistItem.isChecked)
                                }
                        ) {
                            Icon(
                                imageVector = if (checklistItem.isChecked) Icons.Default.CheckCircle else Icons.Default.AddCircle,
                                contentDescription = "Checkbox icon",
                                tint = if (checklistItem.isChecked) PrimaryPurple else textSecondaryColor.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = checklistItem.text,
                                fontSize = 11.sp,
                                color = if (checklistItem.isChecked) textSecondaryColor.copy(alpha = 0.5f) else textPrimaryColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textDecoration = if (checklistItem.isChecked) TextDecoration.LineThrough else null
                            )
                        }
                    }
                    if (note.getChecklistItems().size > 3) {
                        Text(
                            text = "+ ${note.getChecklistItems().size - 3} item lagi",
                            fontSize = 9.sp,
                            color = textSecondaryColor,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                // Text note Render Snippet
                Text(
                    text = note.content.ifEmpty { "Kosong" },
                    fontSize = 11.sp,
                    color = textSecondaryColor,
                    lineHeight = 16.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(if (note.isPinned) "Unpin" else "Pin") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (note.isPinned) PrimaryPurple else textSecondaryColor
                    )
                },
                onClick = {
                    showMenu = false
                    onTogglePin()
                }
            )
            DropdownMenuItem(
                text = { Text(if (note.isArchived) "Unarchive" else "Archive") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = textSecondaryColor
                    )
                },
                onClick = {
                    showMenu = false
                    onArchive()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = NoteRed
                    )
                },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}
}

@Composable
fun EmptyNotesPlaceholder(
    isSearchActive: Boolean,
    tab: String,
    onImporttClick: () -> Unit
) {
    val isDark = LocalDarkTheme.current
    val textPrimaryColor = if (isDark) Color.White else TextPrimary
    val textSecondaryColor = if (isDark) Color(0xFFCCC5D0) else TextSecondary
    val illustrationBgColor = if (isDark) Color(0xFF252429) else LightPurple.copy(alpha = 0.6f)
    val borderStrokeColor = if (isDark) Color(0xFF3E3D42) else BorderGray

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val illustrationIcon = if (isSearchActive) Icons.Default.Search else Icons.Default.Edit
        val mainText = if (isSearchActive) {
            "Notes tidak ditemukan"
        } else if (tab == "tasks") {
            "No checklist tasks"
        } else {
            "Notes Anda kosong"
        }
        val helpText = if (isSearchActive) {
            "Coba cari kata kunci lainnya"
        } else {
            "Create a new local note to start capturing today’s ideas."
        }

        Box(
            modifier = Modifier
                .size(72.dp)
                .background(illustrationBgColor, CircleShape)
                .border(2.dp, borderStrokeColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = illustrationIcon,
                contentDescription = "Empty state icon",
                tint = PrimaryPurple,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = mainText,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = textPrimaryColor
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = helpText,
            fontSize = 12.sp,
            color = textSecondaryColor,
            modifier = Modifier.fillMaxWidth(0.8f),
            lineHeight = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (!isSearchActive && tab == "notes") {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onImporttClick,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Import icon",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import from Google Keep", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun NotesBottomNavigation(
    activeTab: String,
    onTabSelected: (String) -> Unit
) {
    val isDark = LocalDarkTheme.current
    val navContainerColor = if (isDark) Color(0xFF1C1B1F) else BarColor
    val selectedItemColor = if (isDark) Color.White else Color(0xFF1D192B)
    val indicatorColorHex = if (isDark) Color(0xFF49454F) else LightPurple
    val unselectedColorVal = if (isDark) Color(0xFF938F99) else TextSecondary

    NavigationBar(
        containerColor = navContainerColor,
        tonalElevation = 8.dp,
        modifier = Modifier.height(80.dp)
    ) {
        NavigationBarItem(
            selected = activeTab == "notes",
            onClick = { onTabSelected("notes") },
            icon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Notes"
                )
            },
            label = { Text("Notes", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = selectedItemColor,
                selectedTextColor = selectedItemColor,
                indicatorColor = indicatorColorHex,
                unselectedIconColor = unselectedColorVal,
                unselectedTextColor = unselectedColorVal
            )
        )
        NavigationBarItem(
            selected = activeTab == "tasks",
            onClick = { onTabSelected("tasks") },
            icon = {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Tasks"
                )
            },
            label = { Text("Tasks", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = selectedItemColor,
                selectedTextColor = selectedItemColor,
                indicatorColor = indicatorColorHex,
                unselectedIconColor = unselectedColorVal,
                unselectedTextColor = unselectedColorVal
            )
        )
        NavigationBarItem(
            selected = activeTab == "settings",
            onClick = { onTabSelected("settings") },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings"
                )
            },
            label = { Text("Settings", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = selectedItemColor,
                selectedTextColor = selectedItemColor,
                indicatorColor = indicatorColorHex,
                unselectedIconColor = unselectedColorVal,
                unselectedTextColor = unselectedColorVal
            )
        )
    }
}

@Composable
fun NotesFloatingActionButton(
    onAddTextNote: () -> Unit,
    onAddChecklistNote: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(contentAlignment = Alignment.BottomEnd) {
        // Overlay selection elements when active
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(bottom = 74.dp, end = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .clickable {
                            expanded = false
                            onAddChecklistNote()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                ) {
                    Text("New Checklist", fontSize = 12.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "New List",
                        tint = PrimaryPurple,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .clickable {
                            expanded = false
                            onAddTextNote()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .border(1.dp, BorderGray, RoundedCornerShape(12.dp))
                ) {
                    Text("Notes Baru", fontSize = 12.sp, color = TextPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New Text",
                        tint = PrimaryPurple,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MediumPurple,
            contentColor = Color(0xFF381E72),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("add_fab")
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Clear else Icons.Default.Add,
                contentDescription = "Add options",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// Full Notes Backup configuration view
@Composable
fun SettingsAndBackupTab(
    viewModel: NoteViewModel,
    onSelectFile: () -> Unit,
    onPasteJson: () -> Unit,
    onExportBackup: () -> Unit,
    onResetDb: () -> Unit
) {
    var confirmDeleteAll by rememberSaveable { mutableStateOf(false) }
    val themeOption by viewModel.darkModeOption.collectAsStateWithLifecycle()
    val backupInProgress by viewModel.backupInProgress.collectAsStateWithLifecycle()
    val isDark = LocalDarkTheme.current

    val textPrimaryColor = if (isDark) Color.White else TextPrimary
    val textSecondaryColor = if (isDark) Color(0xFFCCC5D0) else TextSecondary
    val cardColor = if (isDark) Color(0xFF1C1B1F) else Color.White
    val cardBorderColor = if (isDark) Color(0xFF3E3D42) else BorderGray

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings & Backup",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textPrimaryColor
            )
            Text(
                text = "Manage Google Keep import, ZIP backup, and local storage.",
                fontSize = 12.sp,
                color = textSecondaryColor
            )
        }

        // Beautiful Interactive Dark Mode Preference Option
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tema Aplikasi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Aktifkan mode gelap agar mata Anda rileks saat mencatat di malam hari.",
                        fontSize = 11.sp,
                        color = textSecondaryColor
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    val themeSelectionMapping = listOf(
                        "system" to "Ikuti Sistem",
                        "light" to "Terang",
                        "dark" to "Gelap"
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        themeSelectionMapping.forEach { (key, label) ->
                            val isSelected = themeOption == key
                            val optionBg = if (isSelected) PrimaryPurple else if (isDark) Color(0xFF252429) else Color(0xFFF3EDF7)
                            val optionTextColor = if (isSelected) Color.White else if (isDark) Color.White else TextPrimary

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(21.dp))
                                    .background(optionBg)
                                    .clickable { viewModel.setDarkModeOption(key) }
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) Color.Transparent else if (isDark) Color(0xFF3E3D42) else BorderGray,
                                        shape = RoundedCornerShape(21.dp)
                                    )
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = optionTextColor
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "How to Import from Google Keep",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val steps = listOf(
                        "1. Open Google Takeout (takeout.google.com).",
                        "2. Deselect all data, then select only Google Keep.",
                        "3. Download the export as a ZIP file.",
                        "4. Klik tombol di bawah untuk memilih ZIP tersebut,",
                        "   or paste the JSON text from the extracted files."
                    )
                    steps.forEach { step ->
                        Text(
                            text = step,
                            fontSize = 11.sp,
                            color = textSecondaryColor,
                            lineHeight = 16.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onSelectFile,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            modifier = Modifier.weight(1f).testTag("import_file_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick ZIP / JSON", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onPasteJson,
                            modifier = Modifier.weight(1f).testTag("paste_json_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isDark) Color.White else PrimaryPurple
                            )
                        ) {
                            Text("Paste JSON", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Local Backup",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Save all notes and images into a single re-importable ZIP file.",
                        fontSize = 11.sp,
                        color = textSecondaryColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onExportBackup,
                        enabled = !backupInProgress,
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                        modifier = Modifier.fillMaxWidth().testTag("export_backup_btn")
                    ) {
                        if (backupInProgress) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (backupInProgress) "Creating Backup..." else "Export ZIP Backup",
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, cardBorderColor, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Manajemen Database",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All your notes are stored privately on your local device.",
                        fontSize = 11.sp,
                        color = textSecondaryColor
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { confirmDeleteAll = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NoteRed.copy(alpha = 0.9f)),
                        modifier = Modifier.fillMaxWidth().testTag("reset_db_btn")
                    ) {
                        Text("Clear All Local Notes", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Clear Everything?") },
            text = { Text("This will permanently delete all local notes and checklists. Keep exports are unaffected.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetDb()
                        confirmDeleteAll = false
                    }
                ) {
                    Text("Yes, Delete All", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// High-fidelity fully featured Edit/Compose Note Dialog
@Composable
fun NoteEditDialog(
    note: Note,
    pendingChecklistItem: String,
    isCopyingImages: Boolean,
    onDraftChanged: (Note) -> Unit,
    onPendingChecklistItemChanged: (String) -> Unit,
    onImagesSelected: (List<Uri>) -> Unit,
    onDismiss: () -> Unit,
    onSave: (Note) -> Unit,
    onDelete: (Note) -> Unit,
    onArchiveToggle: (Note) -> Unit,
    onPinToggle: (Note) -> Unit
) {
    var lightboxImage by rememberSaveable(note.id) { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable(note.id) { mutableStateOf(false) }
    val selectedImagePaths = remember(note.imagePath) {
        note.imagePath.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
    val checklistItems = remember(note.content, note.isChecklist) {
        note.getChecklistItems()
    }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents(),
        onResult = onImagesSelected
    )

    val isDark = LocalDarkTheme.current
    val noteBg = getAdaptiveNoteColor(note.colorHex, isDark)
    val textPrimaryColor = if (isDark) Color.White else TextPrimary
    val textSecondaryColor = if (isDark) Color(0xFFCCC5D0) else TextSecondary
    val borderStrokeColor = if (isDark) Color(0xFF3E3D42) else BorderGray

    val committedDraft = {
        val content = if (note.isChecklist && pendingChecklistItem.isNotBlank()) {
            Note.createFromChecklist(
                checklistItems + ChecklistItem(pendingChecklistItem.trim(), false)
            )
        } else {
            note.content
        }
        if (pendingChecklistItem.isNotBlank()) {
            onPendingChecklistItemChanged("")
        }
        val draft = note.copy(content = content)
        // Only bump the edited timestamp when the note actually changed.
        // Opening and closing a note without edits must NOT move it to the top.
        val changed = draft.title != note.title ||
            draft.content != note.content ||
            draft.colorHex != note.colorHex ||
            draft.imagePath != note.imagePath ||
            draft.isChecklist != note.isChecklist
        if (changed) {
            draft.copy(userEditedTimestamp = System.currentTimeMillis())
        } else {
            draft
        }
    }
    val saveAndDismiss = {
        if (isCopyingImages) {
            Toast.makeText(context, "Wait until images finish copying", Toast.LENGTH_SHORT).show()
        } else {
            val draft = committedDraft()
            val changed = draft.title != note.title ||
                draft.content != note.content ||
                draft.colorHex != note.colorHex ||
                draft.imagePath != note.imagePath ||
                draft.isChecklist != note.isChecklist
            if (changed || note.id == 0) {
                onSave(draft)
            }
            onDismiss()
        }
    }

    BackHandler {
        saveAndDismiss()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = noteBg
    ) {
            Scaffold(
                containerColor = noteBg,
                topBar = {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { saveAndDismiss() }) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Save & Back",
                                tint = textPrimaryColor
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { onPinToggle(committedDraft()) },
                                enabled = !isCopyingImages
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Pin Note",
                                    tint = if (note.isPinned) PrimaryPurple else textSecondaryColor.copy(alpha = 0.4f)
                                )
                            }

                            IconButton(
                                onClick = { onArchiveToggle(committedDraft()) },
                                enabled = !isCopyingImages
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Archive note",
                                    tint = if (note.isArchived) PrimaryPurple else textSecondaryColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            if (note.id != 0) {
                                IconButton(
                                    onClick = { confirmDelete = true },
                                    enabled = !isCopyingImages
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Note",
                                        tint = NoteRed.copy(alpha = 0.9f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(4.dp))
                            
                            TextButton(
                                onClick = { saveAndDismiss() },
                                enabled = !isCopyingImages
                            ) {
                                Text(
                                    text = "Selesai",
                                    color = PrimaryPurple,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(noteBg)
                            .navigationBarsPadding()
                            .border(1.dp, borderStrokeColor.copy(alpha = 0.12f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Pick Color",
                                tint = textSecondaryColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                            
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                KeepParser.KEEP_COLORS_MAP.forEach { (hex, _) ->
                                    val color = getAdaptiveNoteColor(hex, isDark)
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (note.colorHex == hex) 2.5.dp else 1.dp,
                                                color = if (note.colorHex == hex) PrimaryPurple else borderStrokeColor.copy(alpha = 0.3f),
                                                shape = CircleShape
                                            )
                                            .clickable(enabled = !isCopyingImages) {
                                                onDraftChanged(note.copy(colorHex = hex))
                                            }
                                    )
                                }
                            }
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = borderStrokeColor.copy(alpha = 0.1f))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = {
                                    try {
                                        imagePickerLauncher.launch("image/*")
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No app available to pick images", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isCopyingImages
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isCopyingImages) {
                                        CircularProgressIndicator(
                                            color = PrimaryPurple,
                                            strokeWidth = 2.dp,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = "Tambah Foto",
                                            tint = PrimaryPurple,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isCopyingImages) {
                                            "Copying images..."
                                        } else {
                                            "Images (${selectedImagePaths.size})"
                                        },
                                        color = textSecondaryColor,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                            
                            val editingTime = remember(note.userEditedTimestamp) {
                                val date = java.util.Date(note.userEditedTimestamp)
                                val format = java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault())
                                format.format(date)
                            }
                            Text(
                                text = "Diedit $editingTime",
                                color = textSecondaryColor.copy(alpha = 0.5f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedImagePaths.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            selectedImagePaths.forEachIndexed { index, path ->
                                Box(
                                    modifier = Modifier
                                        .width(240.dp)
                                        .height(200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.Black.copy(alpha = 0.05f))
                                        .clickable { lightboxImage = path }
                                ) {
                                    AsyncImage(
                                        model = path,
                                        contentDescription = "Note image",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    
                                    IconButton(
                                        onClick = { 
                                            val remainingPaths = selectedImagePaths
                                                .toMutableList()
                                                .apply { removeAt(index) }
                                            onDraftChanged(
                                                note.copy(
                                                    imagePath = remainingPaths
                                                        .takeIf { it.isNotEmpty() }
                                                        ?.joinToString(",")
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            .size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove Image",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Full Screen",
                                            color = Color.White,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    TextField(
                        value = note.title,
                        onValueChange = { onDraftChanged(note.copy(title = it)) },
                        placeholder = { Text("Judul", color = textSecondaryColor.copy(alpha = 0.5f), fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_title_input")
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = borderStrokeColor.copy(alpha = 0.12f))
                    Spacer(modifier = Modifier.height(12.dp))

                    if (note.isChecklist) {
                        checklistItems.forEachIndexed { index, item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                            ) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = { isChecked ->
                                        val updatedItems = checklistItems.toMutableList()
                                        updatedItems[index] = item.copy(isChecked = isChecked)
                                        onDraftChanged(
                                            note.copy(content = Note.createFromChecklist(updatedItems))
                                        )
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryPurple)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                TextField(
                                    value = item.text,
                                    onValueChange = { text ->
                                        val updatedItems = checklistItems.toMutableList()
                                        updatedItems[index] = item.copy(text = text)
                                        onDraftChanged(
                                            note.copy(content = Note.createFromChecklist(updatedItems))
                                        )
                                    },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        focusedTextColor = textPrimaryColor,
                                        unfocusedTextColor = textPrimaryColor
                                    ),
                                    textStyle = LocalTextStyle.current.copy(
                                        fontSize = 15.sp,
                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                        color = if (item.isChecked) textSecondaryColor.copy(alpha = 0.5f) else textPrimaryColor
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val updatedItems = checklistItems.toMutableList().apply { removeAt(index) }
                                        onDraftChanged(
                                            note.copy(content = Note.createFromChecklist(updatedItems))
                                        )
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete Item",
                                        tint = textSecondaryColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add new item",
                                tint = textSecondaryColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextField(
                                value = pendingChecklistItem,
                                onValueChange = onPendingChecklistItemChanged,
                                placeholder = { Text("Add list item...", color = textSecondaryColor.copy(alpha = 0.5f), fontSize = 15.sp) },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = textPrimaryColor,
                                    unfocusedTextColor = textPrimaryColor
                                ),
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (pendingChecklistItem.isNotBlank()) {
                                        onDraftChanged(
                                            note.copy(
                                                content = Note.createFromChecklist(
                                                    checklistItems + ChecklistItem(
                                                        pendingChecklistItem.trim(),
                                                        false
                                                    )
                                                )
                                            )
                                        )
                                        onPendingChecklistItemChanged("")
                                    }
                                }),
                                modifier = Modifier.weight(1f).testTag("add_item_input")
                            )
                            if (pendingChecklistItem.isNotBlank()) {
                                IconButton(onClick = {
                                    onDraftChanged(
                                        note.copy(
                                            content = Note.createFromChecklist(
                                                checklistItems + ChecklistItem(
                                                    pendingChecklistItem.trim(),
                                                    false
                                                )
                                            )
                                        )
                                    )
                                    onPendingChecklistItemChanged("")
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Confirm",
                                        tint = PrimaryPurple
                                    )
                                }
                            }
                        }
                    } else {
                        TextField(
                            value = note.content,
                            onValueChange = { onDraftChanged(note.copy(content = it)) },
                            placeholder = { Text("Write your idea or note here...", color = textSecondaryColor.copy(alpha = 0.5f), fontSize = 15.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = textPrimaryColor,
                                unfocusedTextColor = textPrimaryColor
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 22.sp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 350.dp)
                                .testTag("edit_content_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(40.dp))
                }
            }

            if (lightboxImage != null) {
                val initialPage = selectedImagePaths.indexOf(lightboxImage).coerceAtLeast(0)
                val pagerState = rememberPagerState(initialPage = initialPage) { selectedImagePaths.size }
                Dialog(
                    onDismissRequest = { lightboxImage = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically
                        ) { page ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = selectedImagePaths[page],
                                    contentDescription = "Full size image ${page + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                        if (selectedImagePaths.size > 1) {
                            Text(
                                text = "${pagerState.currentPage + 1} / ${selectedImagePaths.size}",
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 24.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                        }
                        IconButton(
                            onClick = { lightboxImage = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            if (confirmDelete) {
                AlertDialog(
                    onDismissRequest = { confirmDelete = false },
                    title = { Text("Delete note?") },
                    text = { Text("The note and its unused images will be permanently deleted.") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                confirmDelete = false
                                onDelete(committedDraft())
                                onDismiss()
                            }
                        ) {
                            Text("Delete", color = NoteRed)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDelete = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
}

// Dialog explaining Keep takeout steps and linking options
@Composable
fun KeepImporttGuidelinesDialog(
    onDismiss: () -> Unit,
    onSelectFile: () -> Unit,
    onRawPasteClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = PrimaryPurple,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import from Google Keep")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "This app processes your Google Keep Takeout export to keep your data fully local.",
                    fontSize = 13.sp,
                    color = TextPrimary
                )
                
                Text(
                    text = "Dua metode praktis tersedia:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Text(
                    text = "• File Method (ZIP/JSON): Pick the whole Takeout ZIP or a single JSON file.\n" +
                           "• Quick Paste: Copy the text inside a Keep JSON file and paste it straight into the app.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSelectFile,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Text("Pick ZIP/JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onRawPasteClick) {
                Text("Quick Paste")
            }
        }
    )
}

// Dialog allowing direct paste of raw single keep json data
@Composable
fun PasteJsonRawDialog(
    onDismiss: () -> Unit,
    onImportt: (String) -> Unit
) {
    var rawJson by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste JSON Google Keep") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste the JSON text of your Google Keep note below:", fontSize = 12.sp)
                OutlinedTextField(
                    value = rawJson,
                    onValueChange = { rawJson = it },
                    placeholder = { Text("{\n  \"title\": \"...\",\n  \"textContent\": \"...\"\n}", fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .testTag("raw_json_input"),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    maxLines = 15
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onImportt(rawJson) },
                enabled = rawJson.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                modifier = Modifier.testTag("raw_json_submit_btn")
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Mini padding wrapper helping M3 spacing grid layout
@Composable
fun PaddingBox(
    top: Int = 12,
    bottom: Int = 12,
    horizontal: Int = 16,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.padding(
            top = top.dp,
            bottom = bottom.dp,
            start = horizontal.dp,
            end = horizontal.dp
        )
    ) {
        content()
    }
}
