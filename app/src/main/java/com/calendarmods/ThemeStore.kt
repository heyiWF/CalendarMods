package com.calendarmods

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay

/** Optional frame observer for device acceptance; absent in production. */
internal val LocalStoreTabMotionObserver = staticCompositionLocalOf<((Float, Int, Int) -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeStore(inventory: ThemeInventory, data: CalendarData?, busy: Boolean, modifier: Modifier,
    onBack: () -> Unit, onAdd: suspend (String, (Float?) -> Unit) -> Unit, onFavorite: (String) -> Unit,
    onSaveFavorites: suspend (Map<String, Boolean>) -> Unit, onMessage: (String) -> Unit,
    onDelete: (String) -> Unit, onReorder: (List<String>) -> Unit, onUse: (String) -> Unit,
    previewsEnabled: Boolean = true, session: Int = 0) {
    var tab by rememberSaveable(session) { mutableIntStateOf(0) }
    var pendingUnfavorites by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var savingFavorites by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var applyingId by remember { mutableStateOf<String?>(null) }
    var applicationProgress by remember { mutableStateOf<Float?>(null) }
    val interactionBusy = busy || applyingId != null
    fun applyTheme(id: String) {
        if (interactionBusy || savingFavorites) return
        applyingId = id
        applicationProgress = null
        scope.launch {
            val started = android.os.SystemClock.uptimeMillis()
            try {
                withFrameNanos { } // Present acknowledgement before local IO or future acquisition.
                onAdd(id) { progress -> applicationProgress = progress?.coerceIn(0f, 1f) }
                // Keep fast local installs legible without inventing a download percentage.
                delay((400L - (android.os.SystemClock.uptimeMillis() - started)).coerceAtLeast(0L))
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                onMessage("应用失败，请重试")
            } finally { applyingId = null; applicationProgress = null }
        }
    }
    fun leavePage(action: () -> Unit) {
        if (savingFavorites || interactionBusy) return
        if (pendingUnfavorites.isEmpty()) { action(); return }
        savingFavorites = true
        scope.launch {
            try {
                onSaveFavorites(pendingUnfavorites.associateWith { false })
                pendingUnfavorites = emptyList()
                action()
            } catch (failure: Exception) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                pendingUnfavorites = emptyList()
                onMessage("收藏保存失败，请重试")
            } finally { savingFavorites = false }
        }
    }
    var editing by rememberSaveable(session) { mutableStateOf(false) }
    var restoredSession by rememberSaveable { mutableIntStateOf(session) }
    // SaveableStateHolder restores values before rememberSaveable can compare new inputs.
    // Reset only the selected tab/edit mode on a new visit; preserve per-tab scroll offsets.
    if (restoredSession != session) { restoredSession = session; tab = 0; editing = false }
    val states = listOf(rememberLazyGridState(), rememberLazyGridState(), rememberLazyGridState())
    var draft by remember(inventory.library.order) { mutableStateOf(inventory.library.order) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grab by remember { mutableStateOf(Offset.Zero) }
    val grid = states[2]
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    val latestReorder by rememberUpdatedState(onReorder)
    fun move() {
        val id = draggedId ?: return
        val target = grid.layoutInfo.visibleItemsInfo.firstOrNull {
            pointer.x >= it.offset.x && pointer.x < it.offset.x + it.size.width &&
                pointer.y >= it.offset.y && pointer.y < it.offset.y + it.size.height
        }?.key as? String ?: return
        if (target != id && target in draft) {
            val targetIndex = draft.indexOf(target)
            draft = draft.toMutableList().apply { remove(id); add(targetIndex, id) }
        }
    }
    LaunchedEffect(draggedId) {
        while (draggedId != null) {
            val layout = grid.layoutInfo
            val delta = when {
                pointer.y < layout.viewportStartOffset + edge -> -12f
                pointer.y > layout.viewportEndOffset - edge -> 12f
                else -> 0f
            }
            if (delta != 0f) { grid.scrollBy(delta); move() }
            delay(16)
        }
    }
    fun finishDrag(commit: Boolean) {
        if (draggedId == null) return
        draggedId = null
        if (commit && draft != inventory.library.order) latestReorder(draft)
        else if (!commit) draft = inventory.library.order
    }
    BackHandler { if (editing) { finishDrag(false); editing = false } else leavePage(onBack) }
    val window = LocalWindowInfo.current.containerSize
    LaunchedEffect(window) { finishDrag(false) }
    LaunchedEffect(busy, inventory.library.order) {
        if (!busy && draggedId == null) draft = inventory.library.order
    }
    var displayedTab by remember { mutableIntStateOf(tab) }
    val tabOpacity = remember { Animatable(1f) }
    var tabMoving by remember { mutableStateOf(false) }
    var tabExiting by remember { mutableStateOf(false) }
    var tabDirection by remember { mutableIntStateOf(1) }
    LaunchedEffect(tab) {
        if (displayedTab == tab && tabOpacity.value == 1f) return@LaunchedEffect
        tabMoving = true
        val durationScale = systemAnimationScale(context.contentResolver)
        if (durationScale == 0f) { displayedTab = tab; tabOpacity.snapTo(1f); tabMoving = false }
        else withAnimationScale(durationScale) {
            if (displayedTab != tab) {
                tabDirection = if (tab > displayedTab) 1 else -1
                tabExiting = true
                tabOpacity.animateTo(0f, tween(130))
                displayedTab = tab
                tabExiting = false
                // Reuse the single keyed grid. Let its new content lay out while invisible,
                // then begin the incoming animation instead of losing frames to composition.
                withFrameNanos { }; withFrameNanos { }
            }
            tabExiting = false
            tabOpacity.animateTo(1f, tween(220))
            tabMoving = false
        }
    }
    val motionObserver = LocalStoreTabMotionObserver.current
    LaunchedEffect(motionObserver) {
        if (motionObserver != null) snapshotFlow { Triple(tabOpacity.value, displayedTab, tab) }
            .collect { (alpha, displayed, target) -> motionObserver(alpha, displayed, target) }
    }
    val stillCycle = remember { AlmanacCycle() }
    Surface(modifier.fillMaxSize().testTag("theme-store").semantics { testTagsAsResourceId = true },
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopAppBar(title = { Text("主题商店") }, navigationIcon = {
                IconButton(onClick = { if (editing) { finishDrag(false); editing = false } else leavePage(onBack) },
                    modifier = Modifier.testTag("store-back")) { Icon(BackIcon, "返回") }
            }, actions = {
                if (tab == 2) TextButton(enabled = !interactionBusy, onClick = {
                    finishDrag(true); editing = !editing
                }, modifier = Modifier.testTag("store-edit")) { Text(if (editing) "完成" else "编辑") }
            }, windowInsets = WindowInsets(0, 0, 0, 0))
            PrimaryTabRow(selectedTabIndex = tab) {
                listOf("推荐", "收藏", "已安装").forEachIndexed { index, label ->
                    Tab(selected = index == tab, enabled = !interactionBusy && !savingFavorites,
                        onClick = { if (index != tab) leavePage { finishDrag(false); editing = false; tab = index } },
                        text = { Text(label) }, modifier = Modifier.testTag("store-tab-$index"))
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().graphicsLayer {
                alpha = tabOpacity.value
                translationX = (if (tabExiting) -1 else 1) * tabDirection * 16.dp.toPx() * (1f - tabOpacity.value)
            }) {
                val page = displayedTab
                val entries = when (page) {
                    0 -> CalendarTheme.entries
                    1 -> inventory.catalog.filter { it.id in inventory.library.favorites }
                    else -> draft.mapNotNull(inventory.byId::get)
                }
                if (entries.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(FavoriteIcon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.outline)
                        Text("收藏喜欢的主题，在这里轻松找到", style = MaterialTheme.typography.bodyMedium)
                    }
                } else CompositionLocalProvider(LocalAlmanacCycle provides stillCycle, LocalGoodDayCycle provides stillCycle,
                    LocalThumbnailGeneration provides (previewsEnabled && !tabMoving && !states[page].isScrollInProgress && draggedId == null)) {
                    LazyVerticalGrid(columns = GridCells.Fixed(3), state = states[page],
                        modifier = Modifier.fillMaxSize().testTag("store-grid-$page"),
                        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp), userScrollEnabled = draggedId == null) {
                        items(entries, key = { it.id }) { theme ->
                            val isDragged = draggedId == theme.id && page == 2
                            val installed = theme.id in inventory.library.order
                            val favorite = theme.id in inventory.library.favorites && theme.id !in pendingUnfavorites
                            val scale by animateFloatAsState(if (isDragged) 1.03f else 1f, spring(), label = "drag scale")
                            val elevation by animateFloatAsState(if (isDragged) 8f else 0f, spring(), label = "preview shadow")
                            Column(Modifier.animateItem(placementSpec = if (isDragged) null else tween(220))
                                .zIndex(if (isDragged) 1f else 0f).graphicsLayer {
                                    val position = if (isDragged) grid.layoutInfo.visibleItemsInfo.find { it.key == theme.id }?.offset else null
                                    if (isDragged && position != null) {
                                        translationX = pointer.x - grab.x - position.x
                                        translationY = pointer.y - grab.y - position.y

                                    }
                                    scaleX = scale; scaleY = scale
                                }.testTag("store-card-${theme.name}")) {
                                Box(Modifier.fillMaxWidth().aspectRatio(window.width.toFloat() / window.height.coerceAtLeast(1))
                                    .graphicsLayer {
                                        shadowElevation = elevation.dp.toPx()
                                        shape = RoundedCornerShape(14.dp); clip = true
                                    }.background(theme.background)) {
                                    data?.let { ThemeThumbnail(theme, it) }
                                    if (editing && page == 2) {
                                        IconButton(onClick = {}, enabled = !interactionBusy && !tabMoving,
                                            modifier = Modifier.align(Alignment.TopStart).size(48.dp)
                                                .testTag("drag-${theme.name}")
                                                .semantics {
                                                    contentDescription = "拖动排序${theme.title}"
                                                    customActions = listOf(
                                                        CustomAccessibilityAction("向前移动") {
                                                            val index = draft.indexOf(theme.id)
                                                            if (index > 0) onReorder(draft.toMutableList().apply { add(index - 1, removeAt(index)) })
                                                            index > 0
                                                        }, CustomAccessibilityAction("向后移动") {
                                                            val index = draft.indexOf(theme.id)
                                                            if (index < draft.lastIndex) onReorder(draft.toMutableList().apply { add(index + 1, removeAt(index)) })
                                                            index < draft.lastIndex
                                                        })
                                                }.pointerInput(theme.id, interactionBusy) {
                                                    if (!interactionBusy) detectDragGestures(onDragStart = { offset ->
                                                        val item = grid.layoutInfo.visibleItemsInfo.find { it.key == theme.id }
                                                        if (item != null) {
                                                            grab = offset
                                                            pointer = Offset(item.offset.x.toFloat(), item.offset.y.toFloat()) + offset
                                                            draggedId = theme.id
                                                        }
                                                    }, onDrag = { change, delta -> change.consume(); pointer += delta; move() },
                                                        onDragEnd = { finishDrag(true) }, onDragCancel = { finishDrag(false) })
                                                }) { StoreIcon(DragIcon, null) }
                                        IconButton(onClick = { onDelete(theme.id) }, enabled = !interactionBusy && !tabMoving,
                                            modifier = Modifier.align(Alignment.TopEnd).size(48.dp).testTag("delete-${theme.name}")) {
                                            StoreIcon(DeleteIcon, "删除${theme.title}")
                                        }
                                    } else IconToggleButton(checked = favorite, onCheckedChange = {
                                        if (page == 1) {
                                            if (theme.id in pendingUnfavorites) pendingUnfavorites = pendingUnfavorites - theme.id
                                            else {
                                                pendingUnfavorites = pendingUnfavorites + theme.id
                                                onMessage("已取消收藏")
                                            }
                                        } else onFavorite(theme.id)
                                    },
                                        enabled = !interactionBusy && !tabMoving, modifier = Modifier.align(Alignment.TopEnd).size(48.dp).testTag("favorite-${theme.name}")) {
                                        StoreIcon(if (favorite) FavoriteFilledIcon else FavoriteIcon, if (favorite) "取消收藏${theme.title}" else "收藏${theme.title}", favorite)
                                    }
                                }
                                Column(Modifier.fillMaxWidth().padding(top = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(theme.title.substringBefore(" · "),
                                        Modifier.fillMaxWidth().padding(horizontal = 4.dp).testTag("store-title-${theme.name}"),
                                        style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!editing || page != 2) StoreInstallButton(theme, installed, page == 2,
                                        applying = applyingId == theme.id, progress = applicationProgress,
                                        enabled = !interactionBusy && !savingFavorites && !tabMoving,
                                        onClick = {
                                            if (page == 2) leavePage { onUse(theme.id) }
                                            else if (installed) onDelete(theme.id) else applyTheme(theme.id)
                                        })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String?, selected: Boolean = false) {
    Surface(shape = RoundedCornerShape(24.dp), color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface.copy(alpha = .94f)) {
        Icon(icon, label, Modifier.padding(6.dp).size(22.dp), tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StoreInstallButton(theme: CalendarTheme, installed: Boolean, installedPage: Boolean,
    enabled: Boolean, applying: Boolean, progress: Float?, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add-use-${theme.name}")
    if (applying) Box(modifier.semantics(mergeDescendants = true) {
        contentDescription = "正在应用${theme.title}"
        stateDescription = progress?.let { "${(it * 100).toInt()}%" } ?: "正在安装"
    }, contentAlignment = Alignment.Center) {
        if (progress == null) CircularWavyProgressIndicator(Modifier.size(32.dp))
        else CircularWavyProgressIndicator(progress = { progress }, modifier = Modifier.size(32.dp))
    }
    else if (installed || installedPage) OutlinedButton(onClick, modifier, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp)) { Text(if (installedPage) "使用" else "已安装", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
    else Button(onClick, modifier, enabled = enabled,
        contentPadding = PaddingValues(horizontal = 6.dp)) { Text("应用", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
}
