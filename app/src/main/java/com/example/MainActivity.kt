package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.Note
import com.example.data.ChecklistItem
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.*
import com.example.util.KeepParser
import com.example.viewmodel.NoteViewModel
import kotlinx.coroutines.launch
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
    val context = LocalContext.current

    // Modal Control States
    var showEditDialog by remember { mutableStateOf<Note?>(null) }
    var isCreatingChecklist by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showRawPasteDialog by remember { mutableStateOf(false) }

    // File selection picker launchers
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFileFromUri(uri)
        }
    }

    // Display a clean Toast notification when import outcomes trigger
    LaunchedEffect(importToast) {
        importToast?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearImportResult()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NotesBottomNavigation(
                activeTab = activeTab,
                onTabSelected = { tabName ->
                    viewModel.currentTab.value = tabName
                }
            )
        },
        floatingActionButton = {
            if (activeTab != "setelan") {
                NotesFloatingActionButton(
                    onAddTextNote = {
                        showEditDialog = Note(title = "", content = "", isChecklist = false)
                    },
                    onAddChecklistNote = {
                        showEditDialog = Note(title = "", content = "", isChecklist = true)
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
                userNameInitial = "L"
            )

            // Horizontal Filter bar to access normal vs archived notes
            if (activeTab != "setelan") {
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
                    "catatan", "tugas" -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // contextual Keep Import Promo banner
                            if (notes.isEmpty() && testQuery.isEmpty()) {
                                KeepImportBanner(
                                    onClick = { showImportDialog = true }
                                )
                            }

                            if (notes.isEmpty()) {
                                // Graceful Empty States
                                EmptyNotesPlaceholder(
                                    isSearchActive = testQuery.isNotEmpty(),
                                    tab = activeTab,
                                    onImportClick = { showImportDialog = true }
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
                                            onClick = { showEditDialog = note },
                                            onPinChanged = { viewModel.togglePin(note) },
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
                    "setelan" -> {
                        SettingsAndBackupTab(
                            viewModel = viewModel,
                            onSelectFile = { filePickerLauncher.launch("*/*") },
                            onPasteJson = { showRawPasteDialog = true },
                            onResetDb = { viewModel.resetDatabase() }
                        )
                    }
                }
            }
        }
    }

    // Modal Sheet: Note Creator and Editor
    showEditDialog?.let { note ->
        NoteEditDialog(
            note = note,
            onDismiss = { showEditDialog = null },
            onSave = { updatedNote ->
                viewModel.saveNote(updatedNote)
                showEditDialog = null
            },
            onDelete = {
                viewModel.deleteNote(note.id)
                showEditDialog = null
            },
            onArchiveToggle = {
                viewModel.toggleArchive(note)
                showEditDialog = null
            },
            onPinToggle = {
                viewModel.togglePin(note)
                showEditDialog = showEditDialog?.copy(isPinned = !note.isPinned)
            }
        )
    }

    // Modal Dialog: Import Keep Takeout Hub
    if (showImportDialog) {
        KeepImportGuidelinesDialog(
            onDismiss = { showImportDialog = false },
            onSelectFile = {
                showImportDialog = false
                filePickerLauncher.launch("*/*")
            },
            onRawPasteClick = {
                showImportDialog = false
                showRawPasteDialog = true
            }
        )
    }

    // Modal Dialog: Paste raw json text directement
    if (showRawPasteDialog) {
        PasteJsonRawDialog(
            onDismiss = { showRawPasteDialog = false },
            onImport = { jsonText ->
                val succeed = viewModel.importKeepJsonContent(jsonText)
                if (succeed) {
                    showRawPasteDialog = false
                }
            }
        )
    }
}

@Composable
fun HeaderSearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    userNameInitial: String
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
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu icon",
                tint = textSecondaryColor,
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))

            TextField(
                value = query,
                onValueChange = onQueryChanged,
                placeholder = {
                    Text(
                        text = "Cari catatan lokal...",
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

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .background(PrimaryPurple, CircleShape)
            ) {
                Text(
                    text = userNameInitial,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
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
            label = { Text("Utama") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = selectedChipBg,
                selectedLabelColor = selectedChipText,
                containerColor = unselectedChipBg,
                labelColor = unselectedChipText
            ),
            modifier = Modifier.testTag("filter_class_utama")
        )
        FilterChip(
            selected = isArchivedMode,
            onClick = { onArchiveModeToggled(true) },
            label = { Text("Arsip") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = selectedChipBg,
                selectedLabelColor = selectedChipText,
                containerColor = unselectedChipBg,
                labelColor = unselectedChipText
            ),
            modifier = Modifier.testTag("filter_class_arsip")
        )
    }
}

@Composable
fun KeepImportBanner(
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
                        text = "Hubungkan Google Keep",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textAndIconTint
                    )
                    Text(
                        text = "Impor berkas hasil export Google Takeout (.zip/.json)",
                        fontSize = 11.sp,
                        color = textSecondaryTint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = "Arahkan",
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
    onPinChanged: () -> Unit,
    onChecklistItemToggled: (Int, Boolean) -> Unit
) {
    val isDark = LocalDarkTheme.current
    val noteBg = getAdaptiveNoteColor(note.colorHex, isDark)
    val textPrimaryColor = if (isDark) Color.White else TextPrimary
    val textSecondaryColor = if (isDark) Color(0xFFCCC5D0) else TextSecondary
    val borderStrokeColor = if (isDark) Color(0xFF3E3D42) else BorderGray

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = noteBg),
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .border(
                width = 1.dp,
                color = if (note.colorHex == "#FFFFFF" && !isDark) borderStrokeColor else if (isDark) borderStrokeColor.copy(alpha = 0.4f) else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onPinChanged
            )
            .testTag("note_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
    }
}

@Composable
fun EmptyNotesPlaceholder(
    isSearchActive: Boolean,
    tab: String,
    onImportClick: () -> Unit
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
            "Catatan tidak ditemukan"
        } else if (tab == "tugas") {
            "Tidak ada tugas checklists"
        } else {
            "Catatan Anda kosong"
        }
        val helpText = if (isSearchActive) {
            "Coba cari kata kunci lainnya"
        } else {
            "Buat catatan lokal baru untuk mulai merekam ide Anda hari ini."
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

        if (!isSearchActive && tab == "catatan") {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onImportClick,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Import icon",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Impor dari Google Keep", fontSize = 13.sp)
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
            selected = activeTab == "catatan",
            onClick = { onTabSelected("catatan") },
            icon = {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Catatan"
                )
            },
            label = { Text("Catatan", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = selectedItemColor,
                selectedTextColor = selectedItemColor,
                indicatorColor = indicatorColorHex,
                unselectedIconColor = unselectedColorVal,
                unselectedTextColor = unselectedColorVal
            )
        )
        NavigationBarItem(
            selected = activeTab == "tugas",
            onClick = { onTabSelected("tugas") },
            icon = {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Tugas"
                )
            },
            label = { Text("Tugas", fontSize = 11.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = selectedItemColor,
                selectedTextColor = selectedItemColor,
                indicatorColor = indicatorColorHex,
                unselectedIconColor = unselectedColorVal,
                unselectedTextColor = unselectedColorVal
            )
        )
        NavigationBarItem(
            selected = activeTab == "setelan",
            onClick = { onTabSelected("setelan") },
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Setelan"
                )
            },
            label = { Text("Setelan", fontSize = 11.sp) },
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
                    Text("Checklist Baru", fontSize = 12.sp, color = TextPrimary)
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
                    Text("Catatan Baru", fontSize = 12.sp, color = TextPrimary)
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
    onResetDb: () -> Unit
) {
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val themeOption by viewModel.darkModeOption.collectAsStateWithLifecycle()
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
                text = "Pengaturan & Sinkronisasi",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = textPrimaryColor
            )
            Text(
                text = "Konfigurasi integrasi Google Keep dan manajemen penyimpanan lokal.",
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
                        text = "Cara Impor dari Google Keep",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val steps = listOf(
                        "1. Buka laman Google Takeout (takeout.google.com).",
                        "2. Batalkan pilihan semua data, lalu pilih Google Keep saja.",
                        "3. Unduh hasil ekspor dalam bentuk berkas ZIP.",
                        "4. Klik tombol di bawah untuk memilih ZIP tersebut,",
                        "   atau salin-tempel teks JSON dari hasil ekstrak."
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
                            Text("Pilih ZIP / JSON", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onPasteJson,
                            modifier = Modifier.weight(1f).testTag("paste_json_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isDark) Color.White else PrimaryPurple
                            )
                        ) {
                            Text("Tempel JSON", fontSize = 12.sp)
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
                        text = "Manajemen Database",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = textPrimaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Semua catatan Anda disimpan di perangkat lokal secara mandiri.",
                        fontSize = 11.sp,
                        color = textSecondaryColor
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { confirmDeleteAll = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NoteRed.copy(alpha = 0.9f)),
                        modifier = Modifier.fillMaxWidth().testTag("reset_db_btn")
                    ) {
                        Text("Kosongkan Semua Catatan Lokal", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Kosongkan Semua?") },
            text = { Text("Tindakan ini akan menghapus semua catatan dan checklist lokal secara permanen. Ekspor Keep tidak akan terpengaruh.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetDb()
                        confirmDeleteAll = false
                    }
                ) {
                    Text("Ya, Hapus Semua", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteAll = false }) {
                    Text("Batal")
                }
            }
        )
    }
}

// High-fidelity fully featured Edit/Compose Note Dialog
@Composable
fun NoteEditDialog(
    note: Note,
    onDismiss: () -> Unit,
    onSave: (Note) -> Unit,
    onDelete: () -> Unit,
    onArchiveToggle: () -> Unit,
    onPinToggle: () -> Unit
) {
    var title by remember { mutableStateOf(note.title) }
    var rawTextContent by remember { mutableStateOf(if (note.isChecklist) "" else note.content) }
    var selectedColorHex by remember { mutableStateOf(note.colorHex) }
    
    // Checklist processing elements
    val checklistItems = remember { 
        mutableStateListOf<ChecklistItem>().apply {
            addAll(note.getChecklistItems())
        }
    }
    var newItemText by remember { mutableStateOf("") }

    val isDark = LocalDarkTheme.current
    val noteBg = getAdaptiveNoteColor(selectedColorHex, isDark)
    val textPrimaryColor = if (isDark) Color.White else TextPrimary
    val textSecondaryColor = if (isDark) Color(0xFFCCC5D0) else TextSecondary
    val borderStrokeColor = if (isDark) Color(0xFF3E3D42) else BorderGray

    Dialog(onDismissRequest = {
        // Compose note object and save automatically when dismissing dialog
        val finalContent = if (note.isChecklist) {
            Note.createFromChecklist(checklistItems)
        } else {
            rawTextContent
        }
        onSave(note.copy(
            title = title,
            content = finalContent,
            colorHex = selectedColorHex,
            userEditedTimestamp = System.currentTimeMillis()
        ))
    }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = noteBg),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .border(1.dp, borderStrokeColor, RoundedCornerShape(24.dp))
                .testTag("edit_note_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Toolbar Header
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onPinToggle) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Default.Star else Icons.Default.Star,
                            contentDescription = "Pin Note",
                            tint = if (note.isPinned) PrimaryPurple else textSecondaryColor.copy(alpha = 0.5f)
                        )
                    }

                    Row {
                        IconButton(onClick = onArchiveToggle) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Archive note",
                                tint = if (note.isArchived) PrimaryPurple else textSecondaryColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        if (note.id != 0) {
                            IconButton(onClick = onDelete) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Note",
                                    tint = NoteRed.copy(alpha = 0.9f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Title Input
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Judul", color = textSecondaryColor.copy(alpha = 0.7f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = textPrimaryColor,
                        unfocusedTextColor = textPrimaryColor
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_title_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Content Area: Either Text Input or Active Checklist Composer list
                if (note.isChecklist) {
                    Divider(color = borderStrokeColor.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Display Current Checklist Items
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(checklistItems.size) { index ->
                            val item = checklistItems[index]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = { isChecked ->
                                        checklistItems[index] = item.copy(isChecked = isChecked)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaryPurple)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.text,
                                    fontSize = 13.sp,
                                    color = if (item.isChecked) textSecondaryColor.copy(alpha = 0.5f) else textPrimaryColor,
                                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { checklistItems.removeAt(index) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete Item",
                                        tint = textSecondaryColor.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Add checklist item Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New item placeholder",
                            tint = textSecondaryColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = newItemText,
                            onValueChange = { newItemText = it },
                            placeholder = { Text("Tambahkan item checklist...", color = textSecondaryColor.copy(alpha = 0.7f), fontSize = 13.sp) },
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
                                if (newItemText.isNotEmpty()) {
                                    checklistItems.add(ChecklistItem(newItemText, false))
                                    newItemText = ""
                                }
                            }),
                            modifier = Modifier.weight(1f).testTag("add_item_input")
                        )
                        if (newItemText.isNotEmpty()) {
                            IconButton(onClick = {
                                checklistItems.add(ChecklistItem(newItemText, false))
                                newItemText = ""
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Done,
                                    contentDescription = "Confirm add item",
                                    tint = PrimaryPurple
                                )
                            }
                        }
                    }
                } else {
                    // Raw string Text area input
                    TextField(
                        value = rawTextContent,
                        onValueChange = { rawTextContent = it },
                        placeholder = { Text("Tulis ide atau catatan disini...", color = textSecondaryColor.copy(alpha = 0.7f), fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = textPrimaryColor,
                            unfocusedTextColor = textPrimaryColor
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, lineHeight = 20.sp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(min = 150.dp, max = 320.dp)
                            .testTag("edit_content_input")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Color picker bubble drawer
                Text(
                    text = "Ganti Warna Catatan",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = textSecondaryColor
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    items(KeepParser.KEEP_COLORS_MAP) { pair ->
                        val (hex, _) = pair
                        val color = getAdaptiveNoteColor(hex, isDark)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (selectedColorHex == hex) 2.dp else 1.dp,
                                    color = if (selectedColorHex == hex) PrimaryPurple else borderStrokeColor,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorHex = hex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Confirm/Dismiss Save button
                Button(
                    onClick = {
                        val finalContent = if (note.isChecklist) {
                            Note.createFromChecklist(checklistItems)
                        } else {
                            rawTextContent
                        }
                        onSave(note.copy(
                            title = title,
                            content = finalContent,
                            colorHex = selectedColorHex,
                            userEditedTimestamp = System.currentTimeMillis()
                        ))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    modifier = Modifier.fillMaxWidth().testTag("save_note_btn")
                ) {
                    Text("Simpan & Tutup", color = Color.White)
                }
            }
        }
    }
}

// Dialog explaining Keep takeout steps and linking options
@Composable
fun KeepImportGuidelinesDialog(
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
                Text("Impor dari Google Keep")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Aplikasi ini memproses data hasil ekspor Google Keep dari Google Takeout untuk melestarikan data Anda sepenuhnya secara lokal.",
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
                    text = "• Metode Berkas (ZIP/JSON): Pilih file ZIP dari Google Takeout secara utuh atau pilih berkas JSON tunggal.\n" +
                           "• Tempel Cepat: Salin teks dalam file JSON Keep dan tempelkan ke aplikasi langsung.",
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
                Text("Pilih ZIP/JSON")
            }
        },
        dismissButton = {
            TextButton(onClick = onRawPasteClick) {
                Text("Cepat Tempel")
            }
        }
    )
}

// Dialog allowing direct paste of raw single keep json data
@Composable
fun PasteJsonRawDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var rawJson by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tempel JSON Google Keep") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tempel isi teks berkas JSON catatan Google Keep Anda di bawah ini:", fontSize = 12.sp)
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
                onClick = { onImport(rawJson) },
                enabled = rawJson.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                modifier = Modifier.testTag("raw_json_submit_btn")
            ) {
                Text("Impor")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
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
