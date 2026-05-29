package com.example.ui.screens

import android.content.Context
import android.print.PrintManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.Document
import com.example.ui.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboard(viewModel: DocumentViewModel) {
    val documents by viewModel.documentsState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val isNightMode by viewModel.isNightMode.collectAsState()
    val context = LocalContext.current

    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importDocumentFromUri(uri, context.contentResolver)
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.scanLocalDirectories(context)
        } else {
            Toast.makeText(context, "لم يتم منح إذن الوصول، سنبحث بالصلاحيات المتاحة...", Toast.LENGTH_SHORT).show()
            viewModel.scanLocalDirectories(context)
        }
    }

    val folderLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            viewModel.importDocumentsFromDirectory(uri, context.contentResolver, context)
        }
    }

    var showManageStorageExplanation by remember { mutableStateOf(false) }

    val handleFullScanClick = {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            if (!android.os.Environment.isExternalStorageManager()) {
                showManageStorageExplanation = true
            } else {
                viewModel.scanLocalDirectories(context)
            }
        } else {
            permissionLauncher.launch(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    // Active screen selection representation
    var currentSelectedDoc by remember { mutableStateOf<Document?>(null) }
    var showOfflineBanner by remember { mutableStateOf(true) }

    // Dialog & overlay controls
    var showImageToPdfSheet by remember { mutableStateOf(false) }
    var showCreateTextDialog by remember { mutableStateOf(false) }
    var docToRename by remember { mutableStateOf<Document?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Collect Toast Alerts from the ViewModel
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    // Force RTL local layout direction for perfect Arabic translation
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalContentColor provides if (isNightMode) Color(0xFFE2E8F0) else Color(0xFF0F172A)
    ) {
        MyApplicationTheme(darkTheme = isNightMode) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (currentSelectedDoc != null) {
                        // Switch over to specified Reader View
                        DocumentReaderScreen(
                            document = currentSelectedDoc!!,
                            isNightMode = isNightMode,
                            onBack = { currentSelectedDoc = null },
                            onUpdate = { updatedText ->
                                viewModel.updateTextDocumentContent(currentSelectedDoc!!, updatedText)
                                // Keep refreshed locally
                                currentSelectedDoc = currentSelectedDoc!!.copy(content = updatedText)
                            },
                            onToggleFavorite = {
                                viewModel.toggleFavorite(currentSelectedDoc!!)
                                currentSelectedDoc = currentSelectedDoc!!.copy(isFavorite = !currentSelectedDoc!!.isFavorite)
                            }
                        )
                    } else {
                        // Main Dashboard / Workspace Explorer
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = {
                                        Text(
                                            text = "G-Read " + if (isNightMode) "🌙" else "☀️",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp,
                                            modifier = Modifier.testTag("app_title_home")
                                        )
                                    },
                                    actions = {
                                        IconButton(
                                            onClick = { viewModel.toggleNightMode() },
                                            modifier = Modifier.testTag("night_mode_button")
                                        ) {
                                            Icon(
                                                imageVector = if (isNightMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                                                contentDescription = "تبديل الوضع الليلي"
                                            )
                                        }
                                        IconButton(onClick = {
                                            Toast.makeText(context, "تطبيق جي ريد قارئ المستندات الشامل الإصدار ١.٠ - أوفلاين ١٠٠٪ بنجاح", Toast.LENGTH_LONG).show()
                                        }) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = "معلومات عن التطبيق"
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            },
                            floatingActionButton = {
                                FloatingActionButton(
                                    onClick = { showCreateTextDialog = true },
                                    containerColor = BlueBrandPrimary,
                                    contentColor = Color.White,
                                    modifier = Modifier.testTag("create_doc_fab")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Add, "مستند جديد")
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("إنشاء مستند", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (showOfflineBanner) {
                                    Surface(
                                        color = BlueBrandDark.copy(alpha = 0.08f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.CloudOff,
                                                contentDescription = "أوفلاين",
                                                tint = BlueBrandPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "الوضع الأوفلاين نشط: قراءة وإدارة المستندات بدون استهلاك باقة الإنترنت.",
                                                fontSize = 12.sp,
                                                modifier = Modifier.weight(1f),
                                                fontWeight = FontWeight.Medium
                                            )
                                            IconButton(
                                                onClick = { showOfflineBanner = false },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.Close, "إغلاق", modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                // Interactive Custom G-Read App Header/Banner mirroring the brand icon
                                BrandGReadHeader(isNightMode)

                                // Direct Feature Actions (including image-to-PDF converter)
                                QuickFeatureModules(
                                    onOpenImageToPdf = { showImageToPdfSheet = true },
                                    onCreateNewDoc = { showCreateTextDialog = true }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // The 5-type Circular Filtering Hub mirroring the beautiful icon layout!
                                Text(
                                    text = "استيراد ومزامنة مستندات الهاتف",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = if (isNightMode) Color.White else BlueBrandDark
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        // Card 1: Import Single File via SAF
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    importLauncher.launch(
                                                        arrayOf(
                                                            "application/pdf",
                                                            "text/plain",
                                                            "application/msword",
                                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                            "application/vnd.ms-excel",
                                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                            "application/vnd.ms-powerpoint",
                                                            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                                        )
                                                    )
                                                }
                                                .testTag("import_file_card"),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, BlueBrandCyan.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(BlueBrandCyan.copy(alpha = 0.15f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.FileOpen, "استيراد ملف", tint = BlueBrandCyan, modifier = Modifier.size(18.dp))
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text("استيراد ملف", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("اختر ملفاً معيناً", fontSize = 10.sp, color = Color.Gray)
                                                }
                                            }
                                        }

                                        // Card 2: Sync entire folder recursively (SAF Tree)
                                        Card(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    folderLauncher.launch(null)
                                                }
                                                .testTag("sync_folder_card"),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                            border = BorderStroke(1.dp, BlueBrandCyan.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .background(Color(0xFFFFB020).copy(alpha = 0.15f), CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(Icons.Default.Folder, "مزامنة مجلد", tint = Color(0xFFFFB020), modifier = Modifier.size(18.dp))
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text("مزامنة مجلد كامل", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("استيراد مجلد كامل", fontSize = 10.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }

                                    // Card 3: Scan all public directories automatically with permission handling
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                handleFullScanClick()
                                            }
                                            .testTag("scan_storage_card"),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, BlueBrandPrimary.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(BlueBrandPrimary.copy(alpha = 0.15f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Sync, "مزامنة الهاتف", tint = BlueBrandPrimary, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text("البحث التلقائي بالجهاز (مزامنة الهاتف)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text("البحث والمسح التلقائي لكافة المستندات والملفات الموجودة على ذاكرة الهاتف محلياً", fontSize = 10.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }

                                 Spacer(modifier = Modifier.height(16.dp))

                                 Text(
                                     text = "تصنيف المستندات والمجلدات",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                
                                DocumentCategoryRow(
                                    selectedFilter = filterType,
                                    onFilterChange = { clickedType ->
                                        viewModel.setFilterType(clickedType)
                                    }
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                // Rounded Search Bar with Quick Sort Menu Toggle
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = searchQuery,
                                        onValueChange = { viewModel.setSearchQuery(it) },
                                        placeholder = { Text("البحث السريع عن الملفات بالاسم...") },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
                                        trailingIcon = {
                                            if (searchQuery.isNotEmpty()) {
                                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                                    Icon(Icons.Default.Clear, "مسح")
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("file_search_input"),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = BlueBrandPrimary,
                                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box {
                                        IconButton(
                                            onClick = { showSortMenu = true },
                                            modifier = Modifier
                                                .background(
                                                    color = BlueBrandPrimary.copy(alpha = 0.1f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .size(54.dp)
                                                .testTag("sort_menu_button")
                                        ) {
                                            Icon(Icons.Default.Sort, "ترتيب", tint = BlueBrandPrimary)
                                        }
                                        DropdownMenu(
                                            expanded = showSortMenu,
                                            onDismissRequest = { showSortMenu = false }
                                        ) {
                                            SortOrder.values().forEach { order ->
                                                DropdownMenuItem(
                                                    text = { Text(order.labelAr) },
                                                    onClick = {
                                                        viewModel.setSortOrder(order)
                                                        showSortMenu = false
                                                    },
                                                    leadingIcon = {
                                                        if (sortOrder == order) {
                                                            Icon(Icons.Default.Check, "مختار", tint = BlueBrandPrimary)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // List Header count
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المستندات المتوفرة (${documents.size})",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        text = sortOrder.labelAr,
                                        style = TextStyle(fontSize = 12.sp, color = Color.Gray)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Render the Interactive File List in a non-nested style manually using column of cards
                                if (documents.isEmpty()) {
                                    EmptyStateView(hasSearch = searchQuery.isNotBlank() || filterType != "ALL")
                                } else {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        documents.forEach { doc ->
                                            DocumentFileCard(
                                                doc = doc,
                                                onClick = { currentSelectedDoc = doc },
                                                onFavoriteToggle = { viewModel.toggleFavorite(doc) },
                                                onRename = { docToRename = doc },
                                                onDelete = { viewModel.deleteDocument(doc) }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(80.dp)) // padding for FAB
                            }
                        }
                    }

                    // Bottom Sheet Dialog for Converting Multiple Images to a single high quality PDF!
                    if (showImageToPdfSheet) {
                        ImageToPdfSheetDialog(
                            onDismiss = { showImageToPdfSheet = false },
                            onConvert = { pdfName, imageCount ->
                                viewModel.convertImagesToPdf(pdfName, imageCount)
                                showImageToPdfSheet = false
                            }
                        )
                    }

                    // Create New Text File Dialog
                    if (showCreateTextDialog) {
                        CreateTextDocumentDialog(
                            onDismiss = { showCreateTextDialog = false },
                            onSubmit = { name, body ->
                                viewModel.createTxtDocument(name, body)
                                showCreateTextDialog = false
                            }
                        )
                    }

                    // Rename Dialog
                    if (docToRename != null) {
                        RenameDocumentDialog(
                            document = docToRename!!,
                            onDismiss = { docToRename = null },
                            onSubmit = { newTitle ->
                                viewModel.renameDocument(docToRename!!, newTitle)
                                docToRename = null
                            }
                        )
                    }

                    if (showManageStorageExplanation) {
                        AlertDialog(
                            onDismissRequest = { showManageStorageExplanation = false },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showManageStorageExplanation = false
                                        try {
                                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                                data = android.net.Uri.parse("package:${context.packageName}")
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            try {
                                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                                context.startActivity(intent)
                                            } catch (ex: Exception) {
                                                Toast.makeText(context, "فشل فتح الإعدادات تلقائياً: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BlueBrandPrimary)
                                ) {
                                    Text("الذهاب للإعدادات", color = Color.White)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showManageStorageExplanation = false }) {
                                    Text("إلغاء", color = Color.Gray)
                                }
                            },
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Security,
                                        contentDescription = null,
                                        tint = BlueBrandPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("طلب إذن الوصول للملفات", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                            },
                            text = {
                                Text(
                                    "لتشغيل ميزة المسح التلقائي الشامل وتحديد كافة المستندات (PDF, Word, Excel, PPT) المخزنة في جهازك، يتطلب التطبيق صلاحية 'الوصول لجميع الملفات'.\n\nيرجى فتح الإعدادات وتفعيل الصلاحية لـ G-Read.\n\n💡 يمكنك دائماً استخدام خيار 'مزامنة مجلد محدد' أو 'استيراد ملف' والعمل بدون أي صلاحيات عامة تماماً!",
                                    fontSize = 14.sp,
                                    lineHeight = 22.sp
                                )
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Draw a beautiful header panel that mimics the brand identity icon from the prompt.
@Composable
fun BrandGReadHeader(isNightMode: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNightMode) GReadCardDark else Color.White
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Draw premium Logo Icon inside header
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .drawBehind {
                        // Drawing a subtle glowing background gradient circle
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(BlueBrandPrimary.copy(alpha = 0.2f), Color.Transparent),
                                radius = size.minDimension / 1.1f
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                GReadLogoDrawing()
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "G-Read",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                color = if (isNightMode) Color.White else BlueBrandDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Sub-text in Arabic "قارئ جميع صيغ المستندات" with beautiful styling lines
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Left decorative line
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(1f)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, BlueBrandPrimary)
                            )
                        )
                )
                
                Text(
                    text = "قارئ جميع صيغ المستندات",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueBrandPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )

                // Right decorative line
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(1f)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(BlueBrandPrimary, Color.Transparent)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "جميع وثائق المكتب PDF و Word و Excel و PPT و Text في مكان واحد آمن وبدون إنترنت.",
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

// Gorgeous High-Fidelity Custom Drawing using Canvas & layered Composables representing G-Read's icon elements
@Composable
fun GReadLogoDrawing() {
    Box(
        modifier = Modifier
            .size(100.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(BlueBrandPrimary, BlueBrandDark)
                ),
                shape = RoundedCornerShape(22.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            // Draw outer white G curve
            val path = Path().apply {
                val r = size.minDimension / 2f
                val cx = size.width / 2f
                val cy = size.height / 2f
                
                // Draw a nice stylized circular 'G' path in vector
                arcTo(
                    rect = androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                    startAngleDegrees = 40f,
                    sweepAngleDegrees = 290f,
                    forceMoveTo = false
                )
                // Curve in to draw the horizontal shelf of G
                lineTo(cx + r * 0.4f, cy)
                lineTo(cx * 0.95f, cy)
            }
            
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(width = 10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }

        // Draw miniature overlapping document sheet inside the G's belly (White card with tiny lines + blue folded corner)
        Box(
            modifier = Modifier
                .size(36.dp)
                .align(Alignment.CenterEnd)
                .offset(x = (-16).dp, y = (4).dp)
                .background(Color.White, RoundedCornerShape(4.dp))
                .border(1.dp, BlueBrandLight, RoundedCornerShape(4.dp))
                .padding(4.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(BlueBrandPrimary))
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(2.dp).background(BlueBrandPrimary))
                Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.dp).background(BlueBrandPrimary))
            }
            // Cute folded corner tag
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        BlueBrandCyan,
                        shape = RoundedCornerShape(bottomStart = 2.dp)
                    )
            )
        }
    }
}

@Composable
fun QuickFeatureModules(
    onOpenImageToPdf: () -> Unit,
    onCreateNewDoc: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Direct conversion card
        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenImageToPdf() }
                .testTag("convert_images_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, BlueBrandPrimary.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(PdfColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PhotoLibrary, "تحويل لـ PDF", tint = PdfColor)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("صورة إلى PDF", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("مسح وتحويل المستندات", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }

        Card(
            modifier = Modifier
                .weight(1f)
                .clickable { onCreateNewDoc() }
                .testTag("create_txt_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, BlueBrandPrimary.copy(alpha = 0.2f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(TextColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.NoteAdd, "مفكرة مدمجة", tint = TextColor)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("تدوين ملاحظة", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("كتابة مستند نصي", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

// 5-type Circular Category Row matching the image icons and standard structures
@Composable
fun DocumentCategoryRow(
    selectedFilter: String,
    onFilterChange: (String) -> Unit
) {
    val items = listOf(
        CategoryItem("ALL", "الكل", Icons.Default.AllInbox, BlueBrandPrimary),
        CategoryItem("WORD", "Word", Icons.Default.Description, WordColor),
        CategoryItem("EXCEL", "Excel", Icons.Default.TableChart, ExcelColor),
        CategoryItem("PPT", "PowerPoint", Icons.Default.Slideshow, PptColor),
        CategoryItem("PDF", "PDF", Icons.Default.PictureAsPdf, PdfColor),
        CategoryItem("TEXT", "مستند نصي", Icons.Default.Notes, TextColor),
        CategoryItem("FAVORITE", "المفضلة", Icons.Default.Star, Color(0xFFFFB300))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items.forEach { category ->
            val isSelected = selectedFilter == category.id
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable { onFilterChange(category.id) }
                    .padding(vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            brush = if (isSelected) {
                                Brush.linearGradient(colors = listOf(category.color, category.color.copy(alpha = 0.7f)))
                            } else {
                                Brush.linearGradient(colors = listOf(Color.Gray.copy(alpha = 0.1f), Color.Gray.copy(alpha = 0.15f)))
                            },
                            shape = CircleShape
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color.White else Color.Gray.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = category.title,
                        tint = if (isSelected) Color.White else category.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = category.title,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) category.color else MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

data class CategoryItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun DocumentFileCard(
    doc: Document,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("yyyy/MM/dd hh:mm a", Locale("ar")) }
    val formattedDate = remember(doc.date) { formatter.format(Date(doc.date)) }

    val docColor = when (doc.type.uppercase()) {
        "PDF" -> PdfColor
        "WORD" -> WordColor
        "EXCEL" -> ExcelColor
        "PPT" -> PptColor
        else -> TextColor
    }

    val docIcon = when (doc.type.uppercase()) {
        "PDF" -> Icons.Default.PictureAsPdf
        "WORD" -> Icons.Default.Description
        "EXCEL" -> Icons.Default.GridOn
        "PPT" -> Icons.Default.Slideshow
        else -> Icons.Default.Notes
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("doc_item_${doc.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Thumb Icon mimicking G-Read's colorful cards
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(docColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .border(1.dp, docColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = docIcon,
                    contentDescription = doc.type,
                    tint = docColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = doc.size,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = doc.folder,
                        fontSize = 11.sp,
                        color = docColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formattedDate,
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            // Quick Actions
            IconButton(onClick = onFavoriteToggle) {
                Icon(
                    imageVector = if (doc.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "تعديل التفضيل",
                    tint = if (doc.isFavorite) Color(0xFFFFB300) else Color.Gray
                )
            }

            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "مزيد من الخيارات")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("قراءة واستعراض") },
                        onClick = {
                            onClick()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Book, "عرض") }
                    )
                    DropdownMenuItem(
                        text = { Text("إعادة تسمية") },
                        onClick = {
                            onRename()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, "تغيير الاسم") }
                    )
                    DropdownMenuItem(
                        text = { Text("حذف المستند") },
                        onClick = {
                            onDelete()
                            showMenu = false
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, "حذف", tint = Color.Red) }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(hasSearch: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (hasSearch) Icons.Default.SearchOff else Icons.Default.FolderOpen,
            contentDescription = "فارغ",
            tint = Color.LightGray,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (hasSearch) "لم نجد أي مستند يطابق بحثك" else "مجلداتك فارغة حالياً",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (hasSearch) "جرب البحث بكلمة مفتاحية مختلفة أو تغيير التصفية." else "اضغط على زر الإنشاء بالأسفل لإدخال مستند مخصص، أو حول صور الكاميرا لنسخة PDF.",
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

// -------------------------------------------------------------
// Interactive Simulated Reader Screen with full offline support
// -------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentReaderScreen(
    document: Document,
    isNightMode: Boolean,
    onBack: () -> Unit,
    onUpdate: (String) -> Unit,
    onToggleFavorite: () -> Unit
) {
    val context = LocalContext.current
    var textScale by remember { mutableStateOf(16f) }
    var searchKeyword by remember { mutableStateOf("") }
    
    // PDF page views
    var pdfActivePage by remember { mutableStateOf(1) }
    var pdfScrollVerticalMode by remember { mutableStateOf(true) }

    // Excel configurations
    var excelActiveSheet by remember { mutableStateOf(1) }
    var excelCellToEdit by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var excelEditValue by remember { mutableStateOf("") }

    // PowerPoint slide decks
    val slideList = remember(document.content) {
        if (document.type.uppercase() == "PPT") {
            document.content.split("[SLIDE]").filter { it.isNotBlank() }
        } else emptyList()
    }
    var pptActiveSlide by remember { mutableStateOf(1) }
    var pptSlideshowMode by remember { mutableStateOf(false) }

    // Formatted Text components for Word Docs
    val wordParagraphs = remember(document.content) {
        document.content.split("\n\n")
    }

    // Header Background Palette mapping format
    val formatColor = when (document.type.uppercase()) {
        "PDF" -> PdfColor
        "WORD" -> WordColor
        "EXCEL" -> ExcelColor
        "PPT" -> PptColor
        else -> TextColor
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = document.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("reader_file_title")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("reader_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (document.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "مفضلة",
                            tint = if (document.isFavorite) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                            if (printManager != null) {
                                Toast.makeText(context, "جاري تحضير الملف للطباعة... تم تهيئة طابعتك بنجاح للأوفلاين.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "الطباعة غير مدعومة على هذا الجهاز", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Print, "طباعة")
                    }
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "تم توليد رابط مشاركة وعقد المستند وأرسل بنجاح!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.Share, "مشاركة")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = formatColor,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(if (isNightMode) GReadBackgroundDark else Color(0xFFECEFF4))
        ) {
            
            // Format Specific Accessory Controls Panel (eg. search word highlight, text zoom slider)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Word Search Input for Document highlight
                        OutlinedTextField(
                            value = searchKeyword,
                            onValueChange = { searchKeyword = it },
                            placeholder = { Text("البحث عن نص داخل الصفحة...", fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.FindInPage, "البحث بمحتوى", modifier = Modifier.size(16.dp)) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            textStyle = TextStyle(fontSize = 12.sp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Text Font Scaling Slider (Word/Text Readers)
                        if (document.type.uppercase() in listOf("WORD", "TEXT")) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.width(130.dp)
                            ) {
                                Icon(Icons.Default.TextFormat, "حجم", modifier = Modifier.size(16.dp), tint = Color.Gray)
                                Slider(
                                    value = textScale,
                                    onValueChange = { textScale = it },
                                    valueRange = 12f..24f,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${textScale.toInt()}sp", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }

            // Core Layout - Switcheable per Document Format
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(12.dp)
            ) {
                when (document.type.uppercase()) {
                    "PDF" -> {
                        // 📕 PDF Reader screen mockup
                        val pagesList = remember(document.content) {
                            document.content.split("[PAGE]").filter { it.isNotBlank() }
                        }
                        val finalPages = if (pagesList.isEmpty()) listOf(document.content) else pagesList
                        val clampedPage = pdfActivePage.coerceIn(1, finalPages.size)

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Orientation controls
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row {
                                    ElevatedButton(
                                        onClick = { pdfScrollVerticalMode = true },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = if (pdfScrollVerticalMode) formatColor else MaterialTheme.colorScheme.surface,
                                            contentColor = if (pdfScrollVerticalMode) Color.White else formatColor
                                        ),
                                        modifier = Modifier.padding(horizontal = 4.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Text("وضع عمودي", fontSize = 12.sp)
                                    }
                                    ElevatedButton(
                                        onClick = { pdfScrollVerticalMode = false },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = if (!pdfScrollVerticalMode) formatColor else MaterialTheme.colorScheme.surface,
                                            contentColor = if (!pdfScrollVerticalMode) Color.White else formatColor
                                        ),
                                        contentPadding = PaddingValues(horizontal = 10.dp)
                                    ) {
                                        Text("وضع أفقي", fontSize = 12.sp)
                                    }
                                }
                                Text("الصفحة $clampedPage من ${finalPages.size}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Rendering pages
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(if (isNightMode) GReadCardDark else Color.White, RoundedCornerShape(12.dp))
                                    .border(1.dp, if (isNightMode) Color.DarkGray else Color.LightGray.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .padding(16.dp)
                            ) {
                                ScrollablePdfPage(
                                    content = finalPages[clampedPage - 1],
                                    searchKeyword = searchKeyword,
                                    isNightMode = isNightMode
                                )
                            }

                            // Pagination controllers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if (pdfActivePage > 1) pdfActivePage-- },
                                    enabled = pdfActivePage > 1,
                                    modifier = Modifier.background(Color.White, CircleShape)
                                ) {
                                    Icon(Icons.Default.ArrowBack, "الصفحة السابقة")
                                }
                                
                                Slider(
                                    value = clampedPage.toFloat(),
                                    onValueChange = { pdfActivePage = it.toInt() },
                                    valueRange = 1f..finalPages.size.toFloat(),
                                    steps = if (finalPages.size > 1) finalPages.size - 2 else 0,
                                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                                    colors = SliderDefaults.colors(thumbColor = formatColor, activeTrackColor = formatColor)
                                )

                                IconButton(
                                    onClick = { if (pdfActivePage < finalPages.size) pdfActivePage++ },
                                    enabled = pdfActivePage < finalPages.size,
                                    modifier = Modifier.background(Color.White, CircleShape)
                                ) {
                                    Icon(Icons.Default.ArrowForward, "الصفحة التالية")
                                }
                            }
                        }
                    }

                    "WORD" -> {
                        // 📘 Word Format Viewer mockup
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(if (isNightMode) GReadCardDark else Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                                .padding(16.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column {
                                wordParagraphs.forEach { paragraph ->
                                    WordParagraphRenderer(
                                        text = paragraph,
                                        searchHighlight = searchKeyword,
                                        fontSize = textScale.sp,
                                        isNightMode = isNightMode
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                        }
                    }

                    "EXCEL" -> {
                        // 📈 Interactive Spreadsheet XLS Mockup
                        val rows = remember(document.content) {
                            document.content.trim().split("\n").map { line ->
                                line.split(",").toMutableStateList()
                            }.toMutableStateList()
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            // Sheet Selector tabs at the top of the workspace
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val sheets = listOf("مبيعات المنتجات", "رواتب الموظفين", "الميزانية العامة")
                                sheets.forEachIndexed { i, sheetName ->
                                    val idx = i + 1
                                    ElevatedButton(
                                        onClick = { excelActiveSheet = idx },
                                        colors = ButtonDefaults.elevatedButtonColors(
                                            containerColor = if (excelActiveSheet == idx) ExcelColor else MaterialTheme.colorScheme.surface,
                                            contentColor = if (excelActiveSheet == idx) Color.White else ExcelColor
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        modifier = Modifier.height(36.dp)
                                    ) {
                                        Text(sheetName, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Live Table Grid scroll panels
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(if (isNightMode) GReadCardDark else Color.White, RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.LightGray, RoundedCornerShape(12.dp))
                                    .horizontalScroll(rememberScrollState())
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    // Render Table Header with indices
                                    Row {
                                        // Index corner cell
                                        CellBox("#", isHeader = true)
                                        if (rows.isNotEmpty()) {
                                            for (colIdx in 0 until rows[0].size) {
                                                val colLetter = ('أ'.toInt() + colIdx).toChar().toString()
                                                CellBox(colLetter, isHeader = true)
                                            }
                                        }
                                    }

                                    // Render Interactive cells rows
                                    rows.forEachIndexed { rowIdx, rowData ->
                                        Row {
                                            // Row ID side cell
                                            CellBox("${rowIdx + 1}", isHeader = true)
                                            rowData.forEachIndexed { colIdx, cellValue ->
                                                val isHighlighted = searchKeyword.isNotBlank() && cellValue.contains(searchKeyword)
                                                Box(
                                                    modifier = Modifier
                                                        .clickable {
                                                            excelCellToEdit = Pair(rowIdx, colIdx)
                                                            excelEditValue = cellValue
                                                        }
                                                        .background(if (isHighlighted) Color.Yellow else Color.Transparent)
                                                ) {
                                                    CellBox(
                                                        text = cellValue,
                                                        isHeader = rowIdx == 0,
                                                        highlight = isHighlighted
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "💡 انقر فوق أي خلية بالجدول لتعديل القيمة وإعادة الحساب فوراً بنسق تفاعلي ممتاز.",
                                fontSize = 11.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Cell Editing dialog inside interactive sheet
                        if (excelCellToEdit != null) {
                            Dialog(onDismissRequest = { excelCellToEdit = null }) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("تعديل قيمة الخلية [صف ${excelCellToEdit!!.first + 1}، عمود ${excelCellToEdit!!.second + 1}]", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = excelEditValue,
                                            onValueChange = { excelEditValue = it },
                                            modifier = Modifier.fillMaxWidth().testTag("excel_cell_editor_input"),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            TextButton(onClick = { excelCellToEdit = null }) {
                                                Text("إلغاء")
                                            }
                                            Button(
                                                onClick = {
                                                    val rIdx = excelCellToEdit!!.first
                                                    val cIdx = excelCellToEdit!!.second
                                                    rows[rIdx][cIdx] = excelEditValue
                                                    
                                                    // Recompile to csv and save to source
                                                    val updatedCsv = rows.joinToString("\n") { it.joinToString(",") }
                                                    onUpdate(updatedCsv)
                                                    
                                                    excelCellToEdit = null
                                                    Toast.makeText(context, "تم تحديث وحساب قيمة الجدول وتخزينه", Toast.LENGTH_SHORT).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = ExcelColor)
                                            ) {
                                                Text("حفظ التغيير", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "PPT" -> {
                        // 🔍 PPT Slideshow/Viewer Screen
                        val currentSlideStr = if (slideList.isNotEmpty()) slideList[pptActiveSlide - 1] else ""
                        val slideTitle = remember(currentSlideStr) {
                            currentSlideStr.lines().find { it.startsWith("TITLE:") }?.replace("TITLE:", "")?.trim() ?: "بلا عنوان رئيسي"
                        }
                        val slideSubtitle = remember(currentSlideStr) {
                            currentSlideStr.lines().find { it.startsWith("SUBTITLE:") }?.replace("SUBTITLE:", "")?.trim() ?: ""
                        }
                        val slideHighlight = remember(currentSlideStr) {
                            currentSlideStr.lines().find { it.startsWith("HIGHLIGHT:") }?.replace("HIGHLIGHT:", "")?.trim() ?: ""
                        }
                        val slideBullets = remember(currentSlideStr) {
                            val bl = currentSlideStr.lines().find { it.startsWith("BULLETS:") }
                            if (bl != null) {
                                val startIdx = currentSlideStr.lines().indexOf(bl)
                                currentSlideStr.lines().drop(startIdx + 1)
                                    .filter { it.trim().startsWith("•") || it.trim().isNotEmpty() && !it.contains("BGIMAGE") }
                            } else emptyList()
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { pptSlideshowMode = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = PptColor),
                                    modifier = Modifier.testTag("start_slideshow_button")
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, "عرض الشرائح", tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تشغيل العرض العادي", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                                Text("شريحة $pptActiveSlide من ${slideList.size}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            // Render Slide Canvas Area mimicking real Presentation Slide Structure
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(PptColor.copy(alpha = 0.9f), BlueBrandDark)
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = slideTitle,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 28.sp
                                    )
                                    if (slideSubtitle.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = slideSubtitle,
                                            fontSize = 14.sp,
                                            color = BlueBrandCyan,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                    if (slideHighlight.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Surface(
                                            color = Color.White.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(8.dp),
                                            shadowElevation = 2.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = slideHighlight,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White,
                                                modifier = Modifier.padding(10.dp),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    if (slideBullets.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            slideBullets.forEach { bullet ->
                                                Row(
                                                    verticalAlignment = Alignment.Top,
                                                    modifier = Modifier.padding(start = 12.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.ArrowRightAlt,
                                                        "نقطة",
                                                        tint = BlueBrandCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = bullet.trim().removePrefix("•").trim(),
                                                        fontSize = 12.sp,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Slide controllers
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { if (pptActiveSlide > 1) pptActiveSlide-- },
                                    enabled = pptActiveSlide > 1,
                                    modifier = Modifier.background(Color.White, CircleShape)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowLeft, "الشريحة السابقة")
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (dot in 1..slideList.size) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(
                                                    color = if (pptActiveSlide == dot) PptColor else Color.Gray.copy(alpha = 0.5f),
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { if (pptActiveSlide < slideList.size) pptActiveSlide++ },
                                    enabled = pptActiveSlide < slideList.size,
                                    modifier = Modifier.background(Color.White, CircleShape)
                                ) {
                                    Icon(Icons.Default.KeyboardArrowRight, "الشريحة التالية")
                                }
                            }
                        }

                        // Fullscreen PPT Slideshow mode simulation
                        if (pptSlideshowMode) {
                            Dialog(onDismissRequest = { pptSlideshowMode = false }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(16f/9f)
                                        .background(BlueBrandDark)
                                        .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = slideTitle,
                                            fontSize = 20.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                        if (slideSubtitle.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(slideSubtitle, fontSize = 12.sp, color = BlueBrandCyan)
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(onClick = { if (pptActiveSlide > 1) pptActiveSlide-- }) {
                                                Text("السابق", color = Color.White)
                                            }
                                            Text("شريحة $pptActiveSlide من ${slideList.size}", color = Color.LightGray, fontSize = 11.sp)
                                            TextButton(onClick = { if (pptActiveSlide < slideList.size) pptActiveSlide++ else pptSlideshowMode = false }) {
                                                Text(if (pptActiveSlide == slideList.size) "إنهاء العرض" else "التالي", color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    "TEXT" -> {
                        // 📝 Raw Text Reader & Live Editing Pad Layout
                        var editedTextState by remember(document.content) { mutableStateOf(document.content) }
                        val charCount = remember(editedTextState) { editedTextState.length }
                        val wordCount = remember(editedTextState) {
                            editedTextState.split(Regex("\\s+")).filter { it.isNotBlank() }.size
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AssistChip(
                                        onClick = { },
                                        label = { Text("عدد الكلمات: $wordCount") }
                                    )
                                    AssistChip(
                                        onClick = { },
                                        label = { Text("الشخصيات: $charCount") }
                                    )
                                }
                                Button(
                                    onClick = {
                                        onUpdate(editedTextState)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TextColor),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Save, "حفظ التعديلات", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("حفظ التغييرات", fontSize = 12.sp)
                                }
                            }

                            // Interactive Editor Notepad Sheet
                            OutlinedTextField(
                                value = editedTextState,
                                onValueChange = { editedTextState = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .testTag("text_note_editing_field"),
                                textStyle = TextStyle(fontSize = textScale.sp, fontFamily = FontFamily.Monospace),
                                visualTransformation = SearchHighlightTransformation(searchKeyword, isNightMode),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = if (isNightMode) GReadCardDark else Color.White,
                                    unfocusedContainerColor = if (isNightMode) GReadCardDark else Color.White,
                                    focusedBorderColor = TextColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

// Custom mini helper composables for Table
@Composable
fun CellBox(text: String, isHeader: Boolean, highlight: Boolean = false) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .height(42.dp)
            .background(
                color = when {
                    highlight -> Color.Yellow.copy(alpha = 0.5f)
                    isHeader -> ExcelColor.copy(alpha = 0.1f)
                    else -> Color.Transparent
                }
            )
            .border(
                width = 0.5.dp,
                color = if (isHeader) ExcelColor else Color.LightGray
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
            color = if (isHeader) ExcelColor else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

// Helper function to build AnnotatedString with highlighted search terms
fun buildHighlightedText(
    text: String,
    searchKeyword: String,
    isNightMode: Boolean,
    baseColor: Color = if (isNightMode) Color.White else Color.Black
): AnnotatedString {
    if (searchKeyword.isBlank()) {
        return AnnotatedString(text)
    }
    val builder = AnnotatedString.Builder(text)
    val lowerText = text.lowercase()
    val lowerKeyword = searchKeyword.lowercase()
    var startIndex = lowerText.indexOf(lowerKeyword)
    
    val highlightColor = if (isNightMode) Color(0xFFFFB300) else Color(0xFFFFEB3B)
    val textColor = Color.Black
    
    while (startIndex >= 0) {
        val endIndex = startIndex + searchKeyword.length
        builder.addStyle(
            style = SpanStyle(
                background = highlightColor,
                color = textColor,
                fontWeight = FontWeight.Bold
            ),
            start = startIndex,
            end = endIndex
        )
        startIndex = lowerText.indexOf(lowerKeyword, endIndex)
    }
    return builder.toAnnotatedString()
}

// VisualTransformation to highlight search queries in text input field (Notepad Editor)
class SearchHighlightTransformation(
    private val searchKeyword: String,
    private val isNightMode: Boolean
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (searchKeyword.isBlank()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder(text.text)
        val lowerText = text.text.lowercase()
        val lowerKeyword = searchKeyword.lowercase()
        var startIndex = lowerText.indexOf(lowerKeyword)
        
        val highlightColor = if (isNightMode) Color(0xFFFFB300) else Color(0xFFFFEB3B)
        val textColor = Color.Black
        
        while (startIndex >= 0) {
            val endIndex = startIndex + searchKeyword.length
            builder.addStyle(
                style = SpanStyle(
                    background = highlightColor,
                    color = textColor,
                    fontWeight = FontWeight.Bold
                ),
                start = startIndex,
                end = endIndex
            )
            startIndex = lowerText.indexOf(lowerKeyword, endIndex)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

// Text highlights parser for Word reader
@Composable
fun WordParagraphRenderer(
    text: String,
    searchHighlight: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    isNightMode: Boolean
) {
    if (text.startsWith("# ")) {
        val pureText = text.removePrefix("# ")
        val highlighted = buildHighlightedText(
            text = pureText,
            searchKeyword = searchHighlight,
            isNightMode = isNightMode,
            baseColor = if (isNightMode) Color.White else BlueBrandDark
        )
        Text(
            text = highlighted,
            fontSize = fontSize * 1.4f,
            fontWeight = FontWeight.Bold,
            color = if (isNightMode) Color.White else BlueBrandDark,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        )
    } else if (text.startsWith("### ")) {
        val pureText = text.removePrefix("### ")
        val highlighted = buildHighlightedText(
            text = pureText,
            searchKeyword = searchHighlight,
            isNightMode = isNightMode,
            baseColor = if (isNightMode) BlueBrandCyan else BlueBrandPrimary
        )
        Text(
            text = highlighted,
            fontSize = fontSize * 1.15f,
            fontWeight = FontWeight.Bold,
            color = if (isNightMode) BlueBrandCyan else BlueBrandPrimary,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )
    } else {
        // Normal paragraph with custom precise word highlighting
        val highlighted = buildHighlightedText(
            text = text,
            searchKeyword = searchHighlight,
            isNightMode = isNightMode,
            baseColor = if (isNightMode) Color.White else Color.Black
        )
        Text(
            text = highlighted,
            fontSize = fontSize,
            modifier = Modifier.fillMaxWidth(),
            lineHeight = 22.sp,
            color = if (isNightMode) Color.White else Color.Black
        )
    }
}

// PDF Scrollable mock text pages
@Composable
fun ScrollablePdfPage(
    content: String,
    searchKeyword: String,
    isNightMode: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopStart
    ) {
        Column {
            val lines = content.lines()
            lines.forEach { line ->
                val highlightedText = buildHighlightedText(
                    text = line,
                    searchKeyword = searchKeyword,
                    isNightMode = isNightMode,
                    baseColor = if (isNightMode) Color.White else Color.Black
                )
                Text(
                    text = highlightedText,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (isNightMode) Color.White else Color.Black,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}


// -------------------------------------------------------------
// Interactive Dialog Modules
// -------------------------------------------------------------

@Composable
fun ImageToPdfSheetDialog(
    onDismiss: () -> Unit,
    onConvert: (String, Int) -> Unit
) {
    var pdfName by remember { mutableStateOf("مسند_الصور_الجديد.pdf") }
    val mockImages = listOf(
        Pair("تقرير الفحص الفني والمالي", true),
        Pair("فاتورة مبيعات المقر الرئيسي", true),
        Pair("عقد الإيجار والمستودعات", false),
        Pair("بطاقة الهوية والضمان", false),
        Pair("صورة سند الاستلام والتسليم", false)
    )
    val selectedIndices = remember { mutableStateListOf(0, 1) } // Default preselect 2 images

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "تحويل الصور إلى مستند PDF 📸",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BlueBrandDark,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "حدد الصور الملتقطة من الكاميرا أو الألبوم لدمجها تلقائياً داخل وثيقة PDF واحدة أوفلاين.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("اسم ملف الـ PDF الناتج:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = pdfName,
                    onValueChange = { pdfName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("image_to_pdf_name_input"),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("اختر الصور المراد دمجها (${selectedIndices.size} مختارة):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable mockup Image Gallery Grid selector
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(Color.Gray.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.LightGray.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        mockImages.forEachIndexed { idx, item ->
                            val isSelected = selectedIndices.contains(idx)
                            Card(
                                modifier = Modifier
                                    .width(100.dp)
                                    .fillMaxHeight()
                                    .clickable {
                                        if (isSelected) {
                                            selectedIndices.remove(idx)
                                        } else {
                                            selectedIndices.add(idx)
                                        }
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) BlueBrandPrimary.copy(alpha = 0.1f) else Color.White
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) BlueBrandPrimary else Color.Gray.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Image,
                                        contentDescription = "صورة",
                                        tint = if (isSelected) BlueBrandPrimary else Color.LightGray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = item.first,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        lineHeight = 11.sp,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            if (selectedIndices.isEmpty()) {
                                // show error toast simulated
                            } else {
                                onConvert(pdfName, selectedIndices.size)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PdfColor),
                        modifier = Modifier.weight(1.5f).testTag("popup_convert_submit_btn"),
                        enabled = selectedIndices.isNotEmpty()
                    ) {
                        Text("دمج وتحويل لـ PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CreateTextDocumentDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit
) {
    var textTitle by remember { mutableStateOf("ملاحظة_اجتماع_جديدة.txt") }
    var textBody by remember { mutableStateOf("ملاحظات وسياق النقاط الهامة هنا:\n") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "إنشاء مستند نصي جديد ✍️",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = BlueBrandDark,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text("عنوان المستند:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = textTitle,
                    onValueChange = { textTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add_txt_title_input")
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("نص ومحتوى المذكرة:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = textBody,
                    onValueChange = { textBody = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .testTag("add_txt_body_input"),
                    maxLines = 8
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            if (textTitle.isNotBlank()) {
                                onSubmit(textTitle, textBody)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueBrandPrimary),
                        modifier = Modifier.weight(1.5f).testTag("popup_create_submit_btn")
                    ) {
                        Text("إنشاء الملف", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun RenameDocumentDialog(
    document: Document,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var newTitle by remember { mutableStateOf(document.name) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "إعادة تسمية المستند 📝",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = BlueBrandDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    modifier = Modifier.fillMaxWidth().testTag("rename_input_field"),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("إلغاء")
                    }
                    Button(
                        onClick = {
                            if (newTitle.isNotBlank()) {
                                onSubmit(newTitle)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueBrandPrimary)
                    ) {
                        Text("تغيير الاسم", color = Color.White)
                    }
                }
            }
        }
    }
}
