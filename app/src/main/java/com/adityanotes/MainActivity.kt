package com.adityanotes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.adityanotes.core.database.entity.FolderEntity
import com.adityanotes.core.database.entity.NotebookEntity
import com.adityanotes.core.navigation.AdityaNotesNavHost
import com.adityanotes.core.navigation.AdityaNotesRoutes
import com.adityanotes.feature.notebook.presentation.NotebookViewModel
import com.adityanotes.feature.notebook.presentation.SidebarItem
import com.adityanotes.feature.page.presentation.PageViewModel
import com.adityanotes.ui.theme.AdityaNotesTheme
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val NOTEBOOK_COVER_COLORS = listOf(
    0xFF4F46E5L to "Indigo Royal",
    0xFF0284C7L to "Ocean Blue",
    0xFF059669L to "Emerald Green",
    0xFFD97706L to "Amber Sunset",
    0xFFDC2626L to "Ruby Red",
    0xFF7C3AEDL to "Deep Purple",
    0xFF0D9488L to "Teal Gem",
    0xFFE11D48L to "Crimson Berry",
    0xFF334155L to "Graphite Slate",
    0xFF18181BL to "Midnight Black"
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: NotebookViewModel by viewModels()
    private val pageViewModel: PageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val systemDark = isSystemInDarkTheme()
            var isDarkTheme by remember { mutableStateOf(systemDark) }

            AdityaNotesTheme(darkTheme = isDarkTheme) {
                val navController = rememberNavController()

                AdityaNotesNavHost(
                    navController = navController,
                    pageViewModel = pageViewModel,
                    notebookScreen = {
                        FreeNotesHomeScreen(
                            viewModel = viewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { isDarkTheme = !isDarkTheme },
                            onNotebookClick = { notebookId ->
                                navController.navigate(
                                    AdityaNotesRoutes.notebookEditor(notebookId)
                                )
                            }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreeNotesHomeScreen(
    viewModel: NotebookViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNotebookClick: (Long) -> Unit
) {
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle()
    val notebooks by viewModel.notebooks.collectAsStateWithLifecycle()
    val allNotebooks by viewModel.allNotebooks.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedSidebarItem.collectAsStateWithLifecycle()
    val breadcrumbs by viewModel.breadcrumbStack.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()

    var showSidebar by remember { mutableStateOf(true) }
    var showCreateNotebookDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showSearchBar by remember { mutableStateOf(false) }

    // Colors tailored for FreeNotes Dark (AMOLED) & Light aesthetics
    val bgColor = if (isDarkTheme) Color(0xFF0C0D12) else Color(0xFFF6F8FC)
    val sidebarBgColor = if (isDarkTheme) Color(0xFF13151D) else Color(0xFFFFFFFF)
    val cardBgColor = if (isDarkTheme) Color(0xFF1B1E28) else Color(0xFFFFFFFF)
    val borderColor = if (isDarkTheme) Color(0xFF262A38) else Color(0xFFE2E8F0)
    val textPrimary = if (isDarkTheme) Color(0xFFF1F5F9) else Color(0xFF0F172A)
    val textSecondary = if (isDarkTheme) Color(0xFF94A3B8) else Color(0xFF64748B)
    val accentPrimary = if (isDarkTheme) Color(0xFF6366F1) else Color(0xFF4F46E5)

    // Intercept back button when inside a subfolder
    BackHandler(enabled = breadcrumbs.size > 1) {
        viewModel.navigateUp()
    }

    val currentTitle = when (val item = selectedItem) {
        is SidebarItem.Folder -> item.folder.name
        SidebarItem.AllNotes -> "All Notes"
    }

    Scaffold(
        containerColor = bgColor
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Left Sidebar Navigation
            AnimatedVisibility(
                visible = showSidebar,
                modifier = Modifier
                    .width(270.dp)
                    .fillMaxHeight()
            ) {
                FreeNotesSidebar(
                    selectedItem = selectedItem,
                    allFolders = allFolders,
                    totalNotebooksCount = allNotebooks.size,
                    allNotebooks = allNotebooks,
                    isDarkTheme = isDarkTheme,
                    sidebarBgColor = sidebarBgColor,
                    borderColor = borderColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accentPrimary = accentPrimary,
                    onToggleTheme = onToggleTheme,
                    onSelectItem = { viewModel.selectSidebarItem(it) },
                    onCreateFolderClick = { showCreateFolderDialog = true },
                    onDeleteFolder = { viewModel.deleteFolder(it) },
                    onRenameFolder = { folder, newName -> viewModel.renameFolder(folder, newName) }
                )
            }

            // Vertical Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(borderColor)
            )

            // 2. Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(bgColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 18.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Title on Left with notebook count pill
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!showSidebar) {
                                IconButton(
                                    onClick = { showSidebar = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Text("📖", fontSize = 20.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp,
                                    color = textPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = accentPrimary.copy(alpha = if (isDarkTheme) 0.2f else 0.12f),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                val displayedCount = if (searchQuery.isNotBlank()) searchResults.size else notebooks.size
                                Text(
                                    text = "$displayedCount",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentPrimary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // Top Action Icons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Search Toggle
                            Surface(
                                shape = CircleShape,
                                color = cardBgColor,
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                modifier = Modifier.size(38.dp).clickable { showSearchBar = !showSearchBar }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🔍", fontSize = 16.sp)
                                }
                            }

                            // Theme Switcher Button (Dark 🌙 / Light ☀️)
                            Surface(
                                shape = CircleShape,
                                color = cardBgColor,
                                border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                modifier = Modifier.size(38.dp).clickable { onToggleTheme() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(if (isDarkTheme) "☀️" else "🌙", fontSize = 16.sp)
                                }
                            }

                            // New Notebook (+) Primary Button
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = accentPrimary,
                                shadowElevation = 3.dp,
                                modifier = Modifier.clickable { showCreateNotebookDialog = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("➕", fontSize = 13.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(
                                        "New Notebook",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    // Collapsible Search Bar
                    if (showSearchBar || searchQuery.isNotBlank()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChange(it) },
                            placeholder = { Text("Search notebook titles...", color = textSecondary) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = cardBgColor,
                                unfocusedContainerColor = cardBgColor,
                                focusedBorderColor = accentPrimary,
                                unfocusedBorderColor = borderColor,
                                focusedTextColor = textPrimary,
                                unfocusedTextColor = textPrimary
                            ),
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                                        Text("✕", fontSize = 14.sp, color = textSecondary)
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Notebooks Grid View
                    val displayedNotebooks = if (searchQuery.isNotBlank()) searchResults else notebooks

                    if (displayedNotebooks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = cardBgColor,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                                    modifier = Modifier.size(80.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("📓", fontSize = 38.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No notebooks found for \"$searchQuery\"" else "No notebooks in $currentTitle",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = textPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Create a notebook to start handwriting notes with smooth pages.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = textSecondary,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(18.dp))
                                Button(
                                    onClick = { showCreateNotebookDialog = true },
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = accentPrimary)
                                ) {
                                    Text("➕ Create First Notebook", fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 160.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 90.dp),
                            horizontalArrangement = Arrangement.spacedBy(22.dp),
                            verticalArrangement = Arrangement.spacedBy(22.dp)
                        ) {
                            items(displayedNotebooks, key = { it.id }) { notebook ->
                                FreeNotesNotebookCard(
                                    notebook = notebook,
                                    isDarkTheme = isDarkTheme,
                                    cardBgColor = cardBgColor,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    borderColor = borderColor,
                                    onClick = { onNotebookClick(notebook.id) },
                                    onRename = { viewModel.renameNotebook(notebook, it) },
                                    onChangeCover = { viewModel.changeNotebookCover(notebook, it) },
                                    onDelete = { viewModel.deleteNotebook(notebook) }
                                )
                            }
                        }
                    }
                }

                // Quick Note Floating Action Button on Bottom-Right
                FloatingActionButton(
                    onClick = {
                        viewModel.createNotebook(
                            name = "Quick Note",
                            coverColor = 0xFF4F46E5L,
                            onCreated = { newNotebookId ->
                                onNotebookClick(newNotebookId)
                            }
                        )
                    },
                    containerColor = accentPrimary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(24.dp)
                        .size(54.dp)
                ) {
                    Text("✏️", fontSize = 22.sp)
                }
            }
        }
    }

    if (showCreateNotebookDialog) {
        FreeNotesCreateNotebookDialog(
            isDarkTheme = isDarkTheme,
            onDismiss = { showCreateNotebookDialog = false },
            onCreate = { name, coverColor, template, isDark ->
                viewModel.createNotebook(
                    name = name,
                    coverColor = coverColor,
                    template = template,
                    isDark = isDark,
                    onCreated = { newNotebookId ->
                        onNotebookClick(newNotebookId)
                    }
                )
                showCreateNotebookDialog = false
            }
        )
    }

    if (showCreateFolderDialog) {
        FreeNotesCreateFolderDialog(
            isDarkTheme = isDarkTheme,
            onDismiss = { showCreateFolderDialog = false },
            onCreate = { name ->
                viewModel.createFolder(name)
                showCreateFolderDialog = false
            }
        )
    }
}

@Composable
private fun FreeNotesSidebar(
    selectedItem: SidebarItem,
    allFolders: List<FolderEntity>,
    totalNotebooksCount: Int,
    allNotebooks: List<NotebookEntity>,
    isDarkTheme: Boolean,
    sidebarBgColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accentPrimary: Color,
    onToggleTheme: () -> Unit,
    onSelectItem: (SidebarItem) -> Unit,
    onCreateFolderClick: () -> Unit,
    onDeleteFolder: (FolderEntity) -> Unit,
    onRenameFolder: (FolderEntity, String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(sidebarBgColor)
    ) {
        // App Brand Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentPrimary,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("📝", fontSize = 18.sp)
                }
            }
            Text(
                text = "AdityaNotes",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = textPrimary
                )
            )
        }

        HorizontalDivider(color = borderColor)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // "All Notes" Item (Default root)
            item {
                SidebarNavItem(
                    label = "All Notes",
                    icon = "📚",
                    badge = if (totalNotebooksCount > 0) "$totalNotebooksCount" else null,
                    isSelected = selectedItem is SidebarItem.AllNotes,
                    isDarkTheme = isDarkTheme,
                    accentPrimary = accentPrimary,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { onSelectItem(SidebarItem.AllNotes) }
                )
            }

            // User Folders Section Header
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FOLDERS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textSecondary,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    )
                    IconButton(
                        onClick = onCreateFolderClick,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("📁⁺", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accentPrimary)
                    }
                }
            }

            if (allFolders.isEmpty()) {
                item {
                    Text(
                        text = "No custom folders yet. Tap 📁⁺ to organize notes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            } else {
                // Dynamically Rendered User Folders Only
                items(allFolders, key = { it.id }) { folder ->
                    val count = allNotebooks.count { it.folderId == folder.id }
                    val isSelected = (selectedItem as? SidebarItem.Folder)?.folder?.id == folder.id

                    SidebarFolderItem(
                        folder = folder,
                        badge = if (count > 0) "$count" else null,
                        isSelected = isSelected,
                        isDarkTheme = isDarkTheme,
                        accentPrimary = accentPrimary,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        onClick = { onSelectItem(SidebarItem.Folder(folder)) },
                        onRename = { onRenameFolder(folder, it) },
                        onDelete = { onDeleteFolder(folder) }
                    )
                }
            }
        }

        HorizontalDivider(color = borderColor)

        // Footer with Theme Switcher & Settings Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleTheme)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(if (isDarkTheme) "🌙 Dark Mode" else "☀️ Light Mode", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = textPrimary)
            }

            Text("v2.0", fontSize = 11.sp, color = textSecondary)
        }
    }
}

@Composable
private fun SidebarNavItem(
    label: String,
    icon: String,
    badge: String?,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    accentPrimary: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            accentPrimary.copy(alpha = if (isDarkTheme) 0.22f else 0.12f)
        } else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(icon, fontSize = 16.sp)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) accentPrimary else textPrimary
                    )
                )
            }
            if (badge != null) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) accentPrimary else (if (isDarkTheme) Color(0xFF262A38) else Color(0xFFE2E8F0))
                ) {
                    Text(
                        text = badge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else textSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarFolderItem(
    folder: FolderEntity,
    badge: String?,
    isSelected: Boolean,
    isDarkTheme: Boolean,
    accentPrimary: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            accentPrimary.copy(alpha = if (isDarkTheme) 0.22f else 0.12f)
        } else Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text("📁", fontSize = 16.sp)
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) accentPrimary else textPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (badge != null) {
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) accentPrimary else (if (isDarkTheme) Color(0xFF262A38) else Color(0xFFE2E8F0)),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else textSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("⋮", fontSize = 14.sp, color = textSecondary)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        FreeNotesRenameDialog(
            title = "Rename Folder",
            currentName = folder.name,
            onDismiss = { showRenameDialog = false },
            onRename = {
                onRename(it)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Folder \"${folder.name}\"?") },
            text = { Text("This will delete the folder and all its notebooks.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FreeNotesNotebookCard(
    notebook: NotebookEntity,
    isDarkTheme: Boolean,
    cardBgColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onChangeCover: (Long) -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCoverPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val formattedDate = remember(notebook.updatedAt) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(notebook.updatedAt))
    }

    val coverColor = remember(notebook.coverColor) {
        Color(notebook.coverColor)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Card(
            shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp),
            colors = CardDefaults.cardColors(containerColor = coverColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.74f)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(10.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.35f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFAF9F6),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(0.86f)
                        .padding(horizontal = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = notebook.name,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                fontSize = 13.sp
                            ),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("⋮", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Change Cover Color") },
                            onClick = {
                                showMenu = false
                                showCoverPicker = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = notebook.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = formattedDate,
            style = MaterialTheme.typography.labelSmall.copy(
                color = textSecondary,
                fontSize = 11.sp
            )
        )
    }

    if (showRenameDialog) {
        FreeNotesRenameDialog(
            title = "Rename Notebook",
            currentName = notebook.name,
            onDismiss = { showRenameDialog = false },
            onRename = {
                onRename(it)
                showRenameDialog = false
            }
        )
    }

    if (showCoverPicker) {
        FreeNotesCoverColorPickerDialog(
            currentColor = notebook.coverColor,
            onDismiss = { showCoverPicker = false },
            onColorSelected = {
                onChangeCover(it)
                showCoverPicker = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Notebook?") },
            text = { Text("Are you sure you want to delete \"${notebook.name}\" and all its pages?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FreeNotesCreateNotebookDialog(
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, Long, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(NOTEBOOK_COVER_COLORS.first().first) }
    var selectedTemplate by remember { mutableStateOf("RULED") }
    var isDarkPaper by remember { mutableStateOf(false) }

    val templates = listOf(
        "RULED" to "Ruled",
        "GRID" to "Grid",
        "BLANK" to "Blank"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Notebook", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Notebook Title") },
                    placeholder = { Text("e.g. Work Notes, Biology, Ideas") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Cover Color:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NOTEBOOK_COVER_COLORS.forEach { (colorLong, _) ->
                        val isSelected = selectedColor == colorLong
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(colorLong))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedColor = colorLong }
                        )
                    }
                }

                Text("Paper Template:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    templates.forEach { (key, label) ->
                        val isSelected = selectedTemplate == key
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTemplate = key }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(vertical = 10.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (name.isNotBlank()) name.trim() else "Untitled Notebook"
                    onCreate(finalName, selectedColor, selectedTemplate, isDarkPaper)
                }
            ) {
                Text("Create Notebook")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FreeNotesCreateFolderDialog(
    isDarkTheme: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder Name") },
                placeholder = { Text("e.g. Projects, Personal, Study") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = name.trim()
                    if (trimmed.isNotEmpty()) {
                        onCreate(trimmed)
                    }
                }
            ) {
                Text("Create Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FreeNotesRenameDialog(
    title: String,
    currentName: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        onRename(trimmed)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FreeNotesCoverColorPickerDialog(
    currentColor: Long,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    var selectedColor by remember { mutableStateOf(currentColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Cover Color", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(NOTEBOOK_COVER_COLORS) { (colorLong, label) ->
                    val isSelected = selectedColor == colorLong
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { selectedColor = colorLong }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(colorLong))
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(label, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onColorSelected(selectedColor) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
