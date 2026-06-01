# Category Drag & Drop UX/UI Plan

## Executive Summary

Design für intuitive Drag & Drop Kategorieverwaltung mit hierarchischer Struktur, visuellen Drop-Zones, und klaren Feedback-Mechanismen.

---

## 1. Datenmodell & Constraints

### Aktuelles Model
```kotlin
data class Category(
    val id: Long,
    val name: String,
    val parentCategoryId: Long? = null,  // Hierarchie
    val level: Int = 1,                   // Tiefe im Baum
    val color: Int,
    val icon: String,
    val pattern: String = "",
    val isDefault: Boolean = false
)
```

### Hierarchie-Rules
- **Max Depth:** 3 Ebenen (Level 1, 2, 3)
  - Level 1: Top-Level (z.B. "Lebensmittel")
  - Level 2: Sub-Category (z.B. "Obst & Gemüse")
  - Level 3: Detail (z.B. "Bio-Äpfel")
- **Parent = null:** Top-Level Kategorie
- **Circular Reference:** NICHT erlaubt (Kategorie darf nicht ihr eigener Ancestor sein)

---

## 2. UX Flow: Drag & Drop

### 2.1 Drag Start (Long-Press)

**Trigger:** Long-Press auf Kategorie (500ms)

**Visual Feedback:**
```
┌────────────────────────────┐
│ 🍎 Lebensmittel           │  ← Normal state
├────────────────────────────┤
│                            │
│  [LONG PRESS 500ms]        │
│                            │
├────────────────────────────┤
│ 🍎 Lebensmittel           │  ← Lifting animation
│    ↑ Schatten + Scale 1.05 │
│    ↑ Haptic feedback       │
└────────────────────────────┘
```

**Feedback:**
- **Haptic:** Kurze Vibration (50ms)
- **Visual:** 
  - Item hebt sich (elevation +4dp)
  - Scale 1.05x
  - Leichte Transparenz (alpha 0.9)
  - Schatten unter Item
- **Rest der Liste:**
  - Andere Items zeigen Drop-Zones an
  - Ungültige Targets ausgegraut

### 2.2 Dragging (Active State)

**Während User zieht:**

```
┌──────────────────────────────────────┐
│  🏠 Wohnen                 [Level 1] │  ← Drop-Zone: Grün
│  ───────────────────────────────────  │  
│                                       │
│  💶 Gehalt                 [Level 1] │  ← Drop-Zone: Grün
│  ───────────────────────────────────  │
│                                       │
│    ┌─────────────────────────┐       │
│    │ 🍎 Lebensmittel   [L1] │       │  ← Dragged Item
│    │      (wird zu L2)       │       │     (semi-transparent)
│    └─────────────────────────┘       │
│  🚗 Transport              [Level 1] │  ← Drop-Zone: Grün
│  ───────────────────────────────────  │
│    ├─ Benzin              [Level 2] │  ← Drop-Zone: Gelb (würde L3)
│    │  ─────────────────────────────  │
│    └─ Reparaturen         [Level 2] │  ← Ausgegraut (keine Kinder)
│       └─ Ölwechsel        [Level 3] │  ← Rot (Max Depth!)
│         ─────────────────────────────│
└──────────────────────────────────────┘
```

**Drop-Zone Farben:**
- **Grün (✅):** Erlaubt
  - "Wird Top-Level" (wenn über L1 mit parentId=null)
  - "Wird Kind von XYZ" (wenn über L1/L2)
- **Gelb (⚠️):** Warnung
  - "Wird Level 3 (Max Depth)" 
  - User kann trotzdem droppen, aber Hinweis
- **Rot (❌):** Verboten
  - "Max Tiefe erreicht" (würde L4 werden)
  - "Zirkuläre Referenz" (Kind → Parent verschieben)
  - "Eigene Kinder" (auf sich selbst oder eigenes Kind)

**Label über Dragged Item:**
```
┌─────────────────────────────┐
│ ✅ Wird Kind von "Wohnen"   │  ← Grüner Banner
│ 🍎 Lebensmittel             │
└─────────────────────────────┘

┌─────────────────────────────┐
│ ⚠️ Wird Level 3 (Maximum)   │  ← Gelber Banner
│ 🍎 Lebensmittel             │
└─────────────────────────────┘

┌─────────────────────────────┐
│ ❌ Max Tiefe erreicht!       │  ← Roter Banner
│ 🍎 Lebensmittel             │
└─────────────────────────────┘
```

### 2.3 Drop Targets - Detaillierte Rules

#### Target: Top-Level (zwischen zwei L1 Kategorien)

```
┌────────────────────────────┐
│ 🏠 Wohnen          [L1]    │
├────────────────────────────┤  ← Drop-Zone (2dp Höhe)
│    INSERT HERE             │  ← Grün, "Wird Top-Level"
├────────────────────────────┤
│ 🍎 [DRAGGING]              │
│ 💶 Gehalt          [L1]    │
└────────────────────────────┘
```
**Action:** `parentCategoryId = null`, `level = 1`

#### Target: Auf L1 Kategorie droppen (wird Kind)

```
┌────────────────────────────┐
│ ┌──────────────────────┐   │  ← Grüner Rahmen (4dp thick)
│ │ 🏠 Wohnen       [L1] │   │  ← "Wird Kind von Wohnen"
│ └──────────────────────┘   │
│   ├─ Miete         [L2]    │
│   └─ Strom         [L2]    │
└────────────────────────────┘
```
**Action:** `parentCategoryId = Wohnen.id`, `level = 2`

#### Target: Auf L2 Kategorie droppen (wird L3)

```
┌────────────────────────────┐
│ 🚗 Transport       [L1]    │
│   ┌──────────────────────┐ │  ← Gelber Rahmen (Warning)
│   │ Benzin          [L2] │ │  ← "Wird L3 (Maximum)"
│   └──────────────────────┘ │
└────────────────────────────┘
```
**Action:** `parentCategoryId = Benzin.id`, `level = 3`

#### Invalid: Auf L3 droppen (verboten)

```
┌────────────────────────────┐
│ 🚗 Transport       [L1]    │
│   └─ Benzin        [L2]    │
│      ┌──────────────────┐  │  ← Roter Rahmen (Verboten)
│      │ Shell      [L3] │  │  ← "Max Tiefe erreicht!"
│      └──────────────────┘  │
│         ❌ Kein Drop       │
└────────────────────────────┘
```
**Action:** Drop wird verhindert, Item springt zurück

### 2.4 Drop (Release)

**Bei gültigem Drop:**
1. **Haptic:** Erfolgs-Vibration (doppelt: 30ms + 30ms)
2. **Animation:** 
   - Item fliegt an neue Position (200ms ease-out)
   - Andere Items weichen aus (stagger 50ms pro Item)
3. **Snackbar:** "✅ [Name] verschoben unter [Parent]"
4. **Hintergrund:** DB-Update + Transaktionen aktualisieren

**Bei ungültigem Drop:**
1. **Haptic:** Error-Vibration (lang: 100ms)
2. **Animation:** Item springt zurück zur Ausgangsposition (elastic bounce)
3. **Snackbar:** "❌ [Reason]: Max Tiefe / Zirkulär / etc."

### 2.5 Confirmation Dialog (bei kritischen Aktionen)

**Wann zeigen:**
- Kategorie hat > 10 Transaktionen
- Kategorie hat Kinder (werden mit-verschoben)
- Level-Change von L1 → L2 oder L2 → L3

**Dialog:**
```
┌─────────────────────────────────────┐
│  Kategorie verschieben?             │
│                                     │
│  📊 "Lebensmittel" → "Wohnen"      │
│                                     │
│  ⚠️ Betroffene Daten:               │
│  • 47 Transaktionen werden         │
│    aktualisiert                     │
│  • 2 Unterkategorien werden        │
│    mitverschoben:                   │
│    - Obst & Gemüse                 │
│    - Getränke                       │
│                                     │
│  [ Abbrechen ]  [ Verschieben ]    │
└─────────────────────────────────────┘
```

---

## 3. Logische Checks (Before Drop)

### Check-Matrix

| Szenario | Check | Aktion wenn Fail |
|----------|-------|------------------|
| **Circular Reference** | Target ist Descendant von Source | ❌ Verhindern, "Zirkuläre Referenz!" |
| **Self-Drop** | Target == Source | ❌ Verhindern, Item zurück |
| **Max Depth** | Target.level == 3 | ❌ Verhindern, "Max Tiefe erreicht!" |
| **Depth Overflow** | Source.children würden > L3 | ❌ Verhindern, "Kinder zu tief!" |
| **Default Category** | Source.isDefault == true | ⚠️ Warnung, "Standard-Kat verschieben?" |

### Pseudocode

```kotlin
fun validateDrop(source: Category, target: Category?): DropResult {
    // 1. Self-drop
    if (source.id == target?.id) {
        return DropResult.Invalid("Kategorie kann nicht auf sich selbst verschoben werden")
    }
    
    // 2. Circular reference (target ist Kind/Enkel von source)
    if (target != null && isDescendantOf(target, source)) {
        return DropResult.Invalid("Zirkuläre Referenz: $target ist Kind von $source")
    }
    
    // 3. Max depth check
    val newLevel = if (target == null) 1 else target.level + 1
    if (newLevel > 3) {
        return DropResult.Invalid("Maximale Tiefe (Level 3) erreicht")
    }
    
    // 4. Children depth overflow (Kinder von source würden zu tief)
    val maxChildDepth = getMaxDescendantDepth(source)
    if (newLevel + maxChildDepth > 3) {
        return DropResult.Invalid("Unterkategorien würden zu tief (> Level 3)")
    }
    
    // 5. Default category warning
    if (source.isDefault) {
        return DropResult.Warning("Standard-Kategorie verschieben?")
    }
    
    // 6. Check transaction count for confirmation
    val txCount = getTransactionCount(source)
    if (txCount > 10) {
        return DropResult.Warning("$txCount Transaktionen betroffen")
    }
    
    return DropResult.Valid(newLevel)
}

fun isDescendantOf(child: Category, ancestor: Category): Boolean {
    var current = child
    while (current.parentCategoryId != null) {
        current = getCategory(current.parentCategoryId) ?: break
        if (current.id == ancestor.id) return true
    }
    return false
}
```

---

## 4. Database Operations (After Drop)

### Update-Cascade

```kotlin
suspend fun moveCategory(
    source: Category, 
    newParentId: Long?, 
    insertPosition: Int? = null
) {
    // 1. Update source category
    val newLevel = if (newParentId == null) 1 else getCategory(newParentId).level + 1
    categoryDao.update(source.copy(
        parentCategoryId = newParentId,
        level = newLevel
    ))
    
    // 2. Update ALL descendants recursively (level cascade)
    val descendants = getAllDescendants(source.id)
    descendants.forEach { child ->
        val levelDelta = newLevel - source.level
        categoryDao.update(child.copy(
            level = child.level + levelDelta
        ))
    }
    
    // 3. Update transaction references (optional, depends on business logic)
    // Transactions bleiben bei ihrer Kategorie, auch wenn diese verschoben wird
    // KEINE Änderung nötig - categoryId bleibt gleich!
    
    // 4. Insert position (Sortierung für UI)
    if (insertPosition != null) {
        updateCategorySortOrder(source.id, insertPosition)
    }
}
```

**Wichtig:** Transaktionen zeigen weiterhin auf die gleiche `categoryId`, auch wenn die Kategorie in der Hierarchie verschoben wird. Die Kategorie-Hierarchie ist rein organisatorisch.

---

## 5. UI Components & Implementation

### 5.1 RecyclerView mit ItemTouchHelper

```kotlin
class CategoryDragDropHelper(
    private val adapter: CategoryAdapter,
    private val onMove: (from: Int, to: Int) -> Boolean,
    private val onDropValidation: (source: Category, target: Category?) -> DropResult
) : ItemTouchHelper.Callback() {
    
    private var draggedItem: Category? = null
    private var targetItem: Category? = null
    
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.adapterPosition
        val to = target.adapterPosition
        
        draggedItem = adapter.getItem(from)
        targetItem = adapter.getItem(to)
        
        // Validate drop
        val result = onDropValidation(draggedItem!!, targetItem)
        
        // Update visual feedback
        updateDropZoneFeedback(target.itemView, result)
        
        return result is DropResult.Valid || result is DropResult.Warning
    }
    
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        when (actionState) {
            ItemTouchHelper.ACTION_STATE_DRAG -> {
                viewHolder?.itemView?.let { view ->
                    // Lift animation
                    view.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .alpha(0.9f)
                        .elevation(8.dpToPx())
                        .setDuration(200)
                        .start()
                    
                    // Haptic feedback
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    
                    // Show drop zones
                    adapter.showDropZones(true)
                }
            }
            ItemTouchHelper.ACTION_STATE_IDLE -> {
                // Reset
                adapter.showDropZones(false)
            }
        }
    }
}
```

### 5.2 Drop-Zone Visual Feedback

```xml
<!-- item_category.xml -->
<androidx.constraintlayout.widget.ConstraintLayout
    android:id="@+id/category_item_root"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    
    <!-- Drop Zone Indicator (hidden by default) -->
    <View
        android:id="@+id/drop_zone_indicator"
        android:layout_width="match_parent"
        android:layout_height="4dp"
        android:background="@drawable/drop_zone_gradient"
        android:visibility="gone"
        android:alpha="0"
        app:layout_constraintTop_toTopOf="parent" />
    
    <!-- Category Content -->
    <LinearLayout
        android:id="@+id/category_content"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="16dp"
        android:orientation="horizontal"
        android:background="?attr/selectableItemBackground">
        
        <!-- Drag Handle -->
        <ImageView
            android:id="@+id/drag_handle"
            android:layout_width="24dp"
            android:layout_height="24dp"
            android:src="@drawable/ic_drag_handle"
            android:contentDescription="Drag to reorder"
            android:alpha="0.5" />
        
        <!-- Level Indicator (Indentation) -->
        <Space
            android:id="@+id/level_indent"
            android:layout_width="0dp"
            android:layout_height="match_parent" />
        
        <!-- Icon + Name -->
        <TextView
            android:id="@+id/tv_category_name"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:textSize="16sp" />
        
        <!-- Drop Target Frame (shown during drag) -->
        <FrameLayout
            android:id="@+id/drop_target_frame"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@drawable/drop_target_border"
            android:visibility="gone" />
    </LinearLayout>
    
    <!-- Feedback Banner (shown above dragged item) -->
    <TextView
        android:id="@+id/drop_feedback_banner"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="8dp"
        android:gravity="center"
        android:textColor="@android:color/white"
        android:textSize="12sp"
        android:visibility="gone"
        app:layout_constraintTop_toTopOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>
```

### 5.3 Drop-Zone Drawables

```xml
<!-- res/drawable/drop_zone_valid.xml (Grün) -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#4CAF50" />
    <corners android:radius="2dp" />
</shape>

<!-- res/drawable/drop_zone_warning.xml (Gelb) -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#FFC107" />
    <corners android:radius="2dp" />
</shape>

<!-- res/drawable/drop_zone_invalid.xml (Rot) -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <solid android:color="#F44336" />
    <corners android:radius="2dp" />
</shape>

<!-- res/drawable/drop_target_border.xml -->
<shape xmlns:android="http://schemas.android.com/apk/res/android">
    <stroke 
        android:width="3dp"
        android:color="#4CAF50" />
    <corners android:radius="8dp" />
</shape>
```

---

## 6. Edge Cases & Error Handling

### Edge Case Matrix

| Case | Behavior | User Feedback |
|------|----------|---------------|
| **Drop außerhalb Liste** | Item springt zurück | "Ungültige Position" |
| **Schnelles Multi-Drag** | Nur ein Drag zur Zeit erlaubt | Zweiter ignored |
| **Kategorie gelöscht während Drag** | Drag abbrechen | "Kategorie nicht mehr vorhanden" |
| **Netzwerk-Timeout beim Save** | Rollback, Item zurück | "Fehler beim Speichern, versuche erneut" |
| **Parent gelöscht während Drag** | Parent=null setzen (Top-Level) | "Parent nicht mehr vorhanden, wird Top-Level" |
| **Kinder verschieben mit** | Alle Descendants updaten | "X Unterkategorien mitverschoben" |

### Undo-Funktion

**Snackbar mit Undo:**
```kotlin
fun showMoveConfirmation(source: Category, newParent: Category?) {
    val message = if (newParent == null) {
        "${source.name} → Top-Level"
    } else {
        "${source.name} → ${newParent.name}"
    }
    
    Snackbar.make(view, "✅ $message", Snackbar.LENGTH_LONG)
        .setAction("UNDO") {
            viewModel.undoLastMove()
        }
        .show()
}
```

**Undo-Stack:**
```kotlin
data class MoveOperation(
    val categoryId: Long,
    val oldParentId: Long?,
    val newParentId: Long?,
    val oldLevel: Int,
    val timestamp: Long
)

class CategoryViewModel {
    private val undoStack = mutableListOf<MoveOperation>()
    
    suspend fun undoLastMove() {
        val lastOp = undoStack.removeLastOrNull() ?: return
        moveCategory(
            categoryId = lastOp.categoryId,
            newParentId = lastOp.oldParentId,
            silent = true // kein Snackbar
        )
    }
}
```

---

## 7. Performance Optimizations

### Render-Optimierung

**Problem:** Bei 100+ Kategorien Drag-Performance schlecht

**Lösungen:**
1. **ViewPool:** RecyclerView ViewPool size erhöhen
2. **DiffUtil:** Nur geänderte Items re-rendern
3. **Debounce:** Drop-Zone Updates max 60fps (16ms debounce)
4. **Async Validation:** Heavy checks (DB-Queries) in Background

```kotlin
class CategoryAdapter : ListAdapter<Category, CategoryViewHolder>(CategoryDiffCallback()) {
    
    private val dropZoneUpdateJob = Job()
    private val scope = CoroutineScope(Dispatchers.Main + dropZoneUpdateJob)
    
    fun updateDropZone(position: Int, result: DropResult) {
        scope.launch {
            delay(16) // 60fps debounce
            notifyItemChanged(position, result) // Partial update
        }
    }
    
    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads[0] is DropResult) {
            // Partial bind: Only update drop zone visual
            holder.updateDropZone(payloads[0] as DropResult)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
}
```

---

## 8. Accessibility (A11y)

### Screen Reader Support

**TalkBack-Announcements:**
- **Start Drag:** "Kategorie [Name] ausgewählt. Ziehen um zu verschieben."
- **Over Valid Target:** "Wird Kind von [Target]. Loslassen um zu verschieben."
- **Over Invalid Target:** "Ungültige Position. [Reason]."
- **Drop Success:** "Kategorie [Name] verschoben unter [Parent]."

**Alternative Drag-Methode für A11y:**
- **Accessible Drag:** Menü-Button zeigt "Verschieben nach..." Dialog
- Dialog zeigt flache Kategorie-Liste mit Radio-Buttons
- User wählt neues Parent, Bestätigt mit "OK"

```kotlin
fun showAccessibleMoveDialog(category: Category) {
    val categories = viewModel.categories.value
    val items = categories.map { 
        "${it.name} (Level ${it.level})"
    }.toTypedArray()
    
    MaterialAlertDialogBuilder(requireContext())
        .setTitle("${category.name} verschieben nach...")
        .setSingleChoiceItems(items, -1) { dialog, which ->
            val target = categories[which]
            val result = validateDrop(category, target)
            
            if (result is DropResult.Valid) {
                viewModel.moveCategory(category, target.id)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), result.message, Toast.LENGTH_SHORT).show()
            }
        }
        .setNegativeButton("Abbrechen", null)
        .show()
}
```

---

## 9. Testing Strategy

### Unit Tests

```kotlin
@Test
fun `validateDrop - circular reference detected`() {
    val parent = Category(id = 1, name = "Parent", level = 1)
    val child = Category(id = 2, name = "Child", parentCategoryId = 1, level = 2)
    
    val result = validateDrop(source = parent, target = child)
    
    assertTrue(result is DropResult.Invalid)
    assertEquals("Zirkuläre Referenz", result.message)
}

@Test
fun `moveCategory - updates descendants level`() = runTest {
    val parent = Category(id = 1, name = "Parent", level = 1)
    val child = Category(id = 2, name = "Child", parentCategoryId = 1, level = 2)
    val grandchild = Category(id = 3, name = "Grandchild", parentCategoryId = 2, level = 3)
    
    categoryRepo.moveCategory(parent, newParentId = null) // Move to top-level
    
    val updatedChild = categoryRepo.getById(2)
    val updatedGrandchild = categoryRepo.getById(3)
    
    assertEquals(2, updatedChild.level) // Unchanged
    assertEquals(3, updatedGrandchild.level) // Unchanged (still relative)
}
```

### UI Tests (Espresso)

```kotlin
@Test
fun dragDropCategory_success() {
    // Given: Category list with 3 items
    launchFragmentInContainer<CategoriesFragment>()
    
    // When: Drag "Lebensmittel" onto "Wohnen"
    onView(withText("Lebensmittel"))
        .perform(longClick())
        .perform(dragTo(hasDescendant(withText("Wohnen"))))
    
    // Then: Snackbar shows success
    onView(withText("✅ Lebensmittel verschoben unter Wohnen"))
        .check(matches(isDisplayed()))
    
    // And: Category is now child of Wohnen
    onView(withText("Lebensmittel"))
        .check(matches(hasIndentation(1))) // Level 2 indentation
}

@Test
fun dragDropCategory_maxDepthReached_showsError() {
    // Given: Category at Level 3
    launchFragmentInContainer<CategoriesFragment>()
    
    // When: Try to drop on another L3 category
    onView(withText("Level3Cat"))
        .perform(longClick())
        .perform(dragTo(hasDescendant(withText("AnotherL3Cat"))))
    
    // Then: Item snaps back
    onView(withText("Level3Cat"))
        .check(matches(hasOriginalPosition()))
    
    // And: Error snackbar shown
    onView(withText("❌ Maximale Tiefe erreicht"))
        .check(matches(isDisplayed()))
}
```

---

## 10. Implementation Roadmap

### Phase 1: Core Drag & Drop (Week 1)
- [ ] ItemTouchHelper Integration
- [ ] Basic drag gestures (long-press, lift, move)
- [ ] Drop-Zone visual feedback (grün/rot)
- [ ] validateDrop() Logic mit allen Checks
- [ ] moveCategory() DB Operation mit Cascade

### Phase 2: UX Polish (Week 2)
- [ ] Drop-Feedback Banner ("Wird Kind von...")
- [ ] Smooth animations (lift, drop, reorder)
- [ ] Haptic feedback (start, success, error)
- [ ] Confirmation dialog (bei > 10 TXs oder Kindern)
- [ ] Undo via Snackbar

### Phase 3: Edge Cases (Week 3)
- [ ] Circular reference prevention
- [ ] Max depth handling
- [ ] Default category warnings
- [ ] Error handling & rollback
- [ ] Network timeout handling

### Phase 4: Accessibility & Testing (Week 4)
- [ ] Screen reader support (TalkBack)
- [ ] Alternative move dialog (A11y)
- [ ] Unit tests (validation, DB operations)
- [ ] UI tests (Espresso drag & drop)
- [ ] Performance profiling (100+ items)

---

## 11. Open Questions / Decisions Needed

1. **Sort Order:** 
   - Sollen Kategorien eine explizite `sortOrder: Int` haben?
   - Oder alphabetisch + manuell per Drag sortierbar?
   
2. **Undo History:**
   - Wie viele Moves im Undo-Stack? (Default: 10)
   - Persistent über App-Restart?
   
3. **Batch Move:**
   - Multi-Select + Move mehrerer Kategorien auf einmal?
   - Oder nur Einzeln?
   
4. **Visual Hierarchy:**
   - Tree-View mit Expand/Collapse?
   - Oder flache Liste mit Einrückung (aktuell)?
   
5. **Transaction Migration:**
   - Wenn Kategorie verschoben: Transaktionen automatisch folgen?
   - Oder manuelle Bestätigung?

---

## Appendix: UI Mockups (ASCII)

### Mockup 1: Drag Start

```
┌──────────────────────────────────────┐
│  KATEGORIEN                    [+]   │
├──────────────────────────────────────┤
│  🏠 Wohnen                           │
│    ├─ Miete                          │
│    └─ Strom                          │
│                                      │
│  ╔══════════════════════════════╗   │  ← Lifting
│  ║ 🍎 Lebensmittel              ║   │
│  ║    ↑ DRAGGING                ║   │
│  ╚══════════════════════════════╝   │
│                                      │
│  🚗 Transport                        │
│  ─────────────────────── (Drop Zone) │  ← Grün
│    ├─ Benzin                         │
│    └─ Reparaturen                    │
└──────────────────────────────────────┘
```

### Mockup 2: Hovering over Valid Target

```
┌──────────────────────────────────────┐
│  ┌────────────────────────────────┐  │
│  │ ✅ Wird Kind von "Wohnen"      │  │  ← Grüner Banner
│  │ 🍎 Lebensmittel                │  │
│  └────────────────────────────────┘  │
│                                      │
│  ╔══════════════════════════════╗   │  ← Grüner Rahmen
│  ║ 🏠 Wohnen                    ║   │
│  ╚══════════════════════════════╝   │
│    ├─ Miete                          │
│    └─ Strom                          │
└──────────────────────────────────────┘
```

### Mockup 3: Max Depth Warning

```
┌──────────────────────────────────────┐
│  ┌────────────────────────────────┐  │
│  │ ❌ Max Tiefe erreicht!         │  │  ← Roter Banner
│  │ 🍎 Lebensmittel                │  │
│  └────────────────────────────────┘  │
│                                      │
│  🚗 Transport                        │
│    └─ Benzin                         │
│       ╔════════════════════════╗    │  ← Roter Rahmen (Disabled)
│       ║ Shell            [L3] ║    │
│       ╚════════════════════════╝    │
└──────────────────────────────────────┘
```

---

**Ende des UX/UI Plans**

**Version:** 1.0  
**Datum:** 2026-05-27  
**Autor:** MyBudgets Dev Team
