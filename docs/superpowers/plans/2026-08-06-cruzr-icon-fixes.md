# Cruzr Icon Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace emoji that render as missing/broken glyphs on the Cruzr robot (Android 5.1.1 / API 22) with Material vector icons (where the emoji is a standalone icon slot) or older, pre-2015 Unicode emoji (where it's embedded inline in a text string).

**Architecture:** No new dependencies — `androidx.compose.material.icons.extended` is already a dependency and already provides every icon needed (`AcUnit`, `BakeryDining`, `SmartToy`, `ShoppingCart`, `LocalCafe`, `EmojiFoodBeverage`, `CardGiftcard` — all verified present in the resolved jar). This is a pure UI swap across 5 files plus one JSON asset; no business logic changes.

**Tech Stack:** Jetpack Compose, `androidx.compose.material.icons.extended`.

## Global Constraints

- Do not touch any emoji confirmed safe (Unicode ≤ 7.0 / pre-2015): ☕🍵🎁🔥❄️♨️⭐✅🔔📍.
- No new Gradle dependencies — every icon used below is already resolvable from the existing `material-icons-extended` / `material-icons-core` dependencies (confirmed by inspecting the resolved jars at design time).
- Design doc: `docs/superpowers/specs/2026-08-06-payment-integration-and-icon-fixes-design.md` section 4 has the full audit table and rationale.
- These are cosmetic Compose UI changes with no meaningful JVM-unit-testable surface (rendering correctness needs a screen, not a JVM assertion) — verification per task is `./gradlew :app:compileDebugKotlin` plus a final manual visual check on-device (Task 5). This is a deliberate, stated choice, not a skipped step.

---

### Task 1: `categoryEmoji()` → `categoryIcon()` in `DetailScreen.kt` and `MenuScreen.kt`

Both files independently duplicate a `when`-mapping from category id to an emoji `String`, rendered via `Text()`. Convert both to return `ImageVector`, rendered via `Icon()`. All 5 categories are converted together (not just the 2 broken ones) because the function's return type can't cleanly mix `String` and `ImageVector`, and all 5 sit in the same visual "icon slot".

**Files:**
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/detail/DetailScreen.kt:60-64,239-241`
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/menu/MenuScreen.kt:136-139,186-193`

**Interfaces:**
- Produces: `fun categoryIcon(c: String): ImageVector` in `DetailScreen.kt` (public, unchanged visibility), `private fun categoryIcon(category: String): ImageVector` in `MenuScreen.kt`

- [ ] **Step 1: `DetailScreen.kt` — imports**

Add to the import block at the top of `app/src/main/java/com/baxailab/cadebot/ui/detail/DetailScreen.kt` (after the existing `androidx.compose.material.icons.filled.Add` line):

```kotlin
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.ui.graphics.vector.ImageVector
```

- [ ] **Step 2: `DetailScreen.kt` — replace `categoryEmoji` with `categoryIcon`**

Replace (around line 239-240):

```kotlin
fun categoryEmoji(c: String) = when (c) {
    "coffee" -> "☕"; "tea" -> "🍵"; "ice_blended" -> "🧊"; "pastry" -> "🥐"; "combo" -> "🎁"; else -> "☕"
}
```

with:

```kotlin
fun categoryIcon(c: String): ImageVector = when (c) {
    "coffee" -> Icons.Default.LocalCafe
    "tea" -> Icons.Default.EmojiFoodBeverage
    "ice_blended" -> Icons.Default.AcUnit
    "pastry" -> Icons.Default.BakeryDining
    "combo" -> Icons.Default.CardGiftcard
    else -> Icons.Default.LocalCafe
}
```

- [ ] **Step 3: `DetailScreen.kt` — fix the "iced" glyph in `tempLabel()`**

On the same area (around line 242), change:

```kotlin
fun tempLabel(t: String) = when (t) { "hot" -> "🔥 Nóng"; "iced" -> "🧊 Lạnh"; "warm" -> "♨️ Ấm"; "cold" -> "❄️ Lạnh"; else -> t }
```

to:

```kotlin
fun tempLabel(t: String) = when (t) { "hot" -> "🔥 Nóng"; "iced" -> "❄️ Lạnh"; "warm" -> "♨️ Ấm"; "cold" -> "❄️ Lạnh"; else -> t }
```

(reuses the exact glyph already proven safe one branch away for "cold" — zero new characters introduced)

- [ ] **Step 4: `DetailScreen.kt` — swap the call site from `Text` to `Icon`**

Replace (around line 60-64):

```kotlin
                    Text(
                        text = categoryEmoji(item.category),
                        style = MaterialTheme.typography.displayLarge,
                        modifier = Modifier.align(Alignment.Center)
                    )
```

with:

```kotlin
                    Icon(
                        imageVector = categoryIcon(item.category),
                        contentDescription = null,
                        tint = VivaOnDark,
                        modifier = Modifier.align(Alignment.Center).size(64.dp)
                    )
```

- [ ] **Step 5: `MenuScreen.kt` — imports**

Add to the import block at the top of `app/src/main/java/com/baxailab/cadebot/ui/menu/MenuScreen.kt` (after the existing `androidx.compose.material.icons.filled.ShoppingCart` line):

```kotlin
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.EmojiFoodBeverage
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.ui.graphics.vector.ImageVector
```

- [ ] **Step 6: `MenuScreen.kt` — replace `categoryEmoji` with `categoryIcon`**

Replace (around line 186-193):

```kotlin
private fun categoryEmoji(category: String) = when (category) {
    "coffee" -> "☕"
    "tea" -> "🍵"
    "ice_blended" -> "🧊"
    "pastry" -> "🥐"
    "combo" -> "🎁"
    else -> "☕"
}
```

with:

```kotlin
private fun categoryIcon(category: String): ImageVector = when (category) {
    "coffee" -> Icons.Default.LocalCafe
    "tea" -> Icons.Default.EmojiFoodBeverage
    "ice_blended" -> Icons.Default.AcUnit
    "pastry" -> Icons.Default.BakeryDining
    "combo" -> Icons.Default.CardGiftcard
    else -> Icons.Default.LocalCafe
}
```

- [ ] **Step 7: `MenuScreen.kt` — swap the call site from `Text` to `Icon`**

Replace (around line 136-139):

```kotlin
                Text(
                    text = categoryEmoji(item.category),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 36.sp)
                )
```

with:

```kotlin
                Icon(
                    imageVector = categoryIcon(item.category),
                    contentDescription = null,
                    tint = VivaOnDark,
                    modifier = Modifier.size(40.dp)
                )
```

- [ ] **Step 8: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/baxailab/cadebot/ui/detail/DetailScreen.kt \
        app/src/main/java/com/baxailab/cadebot/ui/menu/MenuScreen.kt
git commit -m "fix: replace category emoji with Material icons for Cruzr/API22 compat"
```

---

### Task 2: `menu.json` category filter chips

The chip row at the top of `MenuScreen` reads `category.iconEmoji` straight from JSON and renders it inline with the category name in one `Text` (`"${category.iconEmoji} ${category.name}"`). Restructuring this into icon+text pairs is disproportionate for a small filter chip; swap only the 2 risky characters for safe pre-2015 equivalents.

**Files:**
- Modify: `app/src/main/assets/config/menu.json`

- [ ] **Step 1: Edit the JSON**

In `app/src/main/assets/config/menu.json`, change:

```json
    { "id": "ice_blended", "name": "Đá Xay", "iconEmoji": "🧊" },
    { "id": "pastry", "name": "Bánh Ngọt", "iconEmoji": "🥐" },
```

to:

```json
    { "id": "ice_blended", "name": "Đá Xay", "iconEmoji": "🍧" },
    { "id": "pastry", "name": "Bánh Ngọt", "iconEmoji": "🍰" },
```

(🍧 shaved ice, 🍰 shortcake — both Unicode 6.0 / 2010, safe on Android 5.1)

- [ ] **Step 2: Verify the asset is valid JSON**

Run: `python3 -c "import json; json.load(open('app/src/main/assets/config/menu.json'))" && echo "valid JSON"`
Expected: prints `valid JSON`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/assets/config/menu.json
git commit -m "fix: replace ice_blended/pastry category chip emoji with pre-2015-safe glyphs"
```

---

### Task 3: `CartScreen.kt` empty-cart icon

**Files:**
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/cart/CartScreen.kt:69-80`

- [ ] **Step 1: Add the import**

Add to the import block at the top of `app/src/main/java/com/baxailab/cadebot/ui/cart/CartScreen.kt` (after the existing `androidx.compose.material.icons.filled.QrCode` line):

```kotlin
import androidx.compose.material.icons.filled.ShoppingCart
```

- [ ] **Step 2: Replace the emoji Text with an Icon**

Replace (around line 69-80):

```kotlin
        if (uiState.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🛒", style = MaterialTheme.typography.displayMedium)
                    Spacer(Modifier.height(12.dp))
                    Text("Giỏ hàng đang trống", style = MaterialTheme.typography.titleLarge, color = VivaEspresso)
                    Spacer(Modifier.height(8.dp))
                    Text("Hãy chọn món từ thực đơn nhé!", style = MaterialTheme.typography.bodyMedium, color = VivaGray)
                    Spacer(Modifier.height(24.dp))
                    VivaPrimaryButton("Xem thực đơn", onClick = onContinueShopping, modifier = Modifier.width(200.dp))
                }
            }
        } else {
```

with:

```kotlin
        if (uiState.isEmpty) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        tint = VivaEspresso,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Giỏ hàng đang trống", style = MaterialTheme.typography.titleLarge, color = VivaEspresso)
                    Spacer(Modifier.height(8.dp))
                    Text("Hãy chọn món từ thực đơn nhé!", style = MaterialTheme.typography.bodyMedium, color = VivaGray)
                    Spacer(Modifier.height(24.dp))
                    VivaPrimaryButton("Xem thực đơn", onClick = onContinueShopping, modifier = Modifier.width(200.dp))
                }
            }
        } else {
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/baxailab/cadebot/ui/cart/CartScreen.kt
git commit -m "fix: replace empty-cart emoji with Material ShoppingCart icon"
```

---

### Task 4: Robot avatar icon — `AiScreen.kt` and `OrderSuccessScreen.kt`

**Files:**
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/ai/AiScreen.kt:126-129,289,326`
- Modify: `app/src/main/java/com/baxailab/cadebot/ui/ordersuccess/OrderSuccessScreen.kt:118`

- [ ] **Step 1: `AiScreen.kt` — add the import**

Add to the import block at the top of `app/src/main/java/com/baxailab/cadebot/ui/ai/AiScreen.kt` (after the existing `androidx.compose.material.icons.filled.MicNone` line):

```kotlin
import androidx.compose.material.icons.filled.SmartToy
```

- [ ] **Step 2: `AiScreen.kt` — fix the header (inline text → icon+text row)**

Replace (around line 126-129):

```kotlin
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🤖 Hỏi Cadebot", style = MaterialTheme.typography.headlineSmall, color = VivaOnDark)
                Text("Trợ lý AI Viva Reserve", style = MaterialTheme.typography.labelSmall, color = VivaLatte)
            }
```

with:

```kotlin
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = VivaOnDark, modifier = Modifier.size(20.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Hỏi Cadebot", style = MaterialTheme.typography.headlineSmall, color = VivaOnDark)
                    Text("Trợ lý AI Viva Reserve", style = MaterialTheme.typography.labelSmall, color = VivaLatte)
                }
            }
```

- [ ] **Step 3: `AiScreen.kt` — fix the two avatar-circle usages**

Replace both occurrences (around line 289 in `MessageBubble` and line 326 in `TypingIndicator`) of:

```kotlin
                Text("🤖", style = MaterialTheme.typography.bodyMedium)
```

with:

```kotlin
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = VivaOnDark,
                    modifier = Modifier.size(20.dp)
                )
```

(both sit inside a 36.dp circular `Box` with `contentAlignment = Alignment.Center` against a `VivaCoffee`/`VivaCaramel` gradient background, so `VivaOnDark` tint is the right contrast choice, matching what the header icon uses against the same gradient elsewhere in the app)

- [ ] **Step 4: `OrderSuccessScreen.kt` — add the import**

Add to the import block at the top of `app/src/main/java/com/baxailab/cadebot/ui/ordersuccess/OrderSuccessScreen.kt` (after the existing `androidx.compose.material.icons.filled.CheckCircle` line):

```kotlin
import androidx.compose.material.icons.filled.SmartToy
```

- [ ] **Step 5: `OrderSuccessScreen.kt` — fix the robot-delivery-notice icon**

Replace (around line 118):

```kotlin
                        Text("🤖", style = MaterialTheme.typography.headlineMedium)
```

with:

```kotlin
                        Icon(
                            imageVector = Icons.Default.SmartToy,
                            contentDescription = null,
                            tint = VivaCoffee,
                            modifier = Modifier.size(28.dp)
                        )
```

- [ ] **Step 6: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/baxailab/cadebot/ui/ai/AiScreen.kt \
        app/src/main/java/com/baxailab/cadebot/ui/ordersuccess/OrderSuccessScreen.kt
git commit -m "fix: replace robot emoji with Material SmartToy icon"
```

---

### Task 5: Full-app verification

**Files:** none (verification only)

- [ ] **Step 1: Full clean build**

Run: `./gradlew clean assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Visual check on a normal device/emulator first**

Install the debug APK on any handy Android device (not necessarily Cruzr — a phone or emulator is fine for this first pass) and visually confirm: Menu screen category chips and item-card icons all render (no tofu boxes, no missing text), cart empty-state icon renders, AI chat header + message avatars render, order success screen renders. This isolates "did I break the layout" from "does Cruzr's old font specifically lack a glyph" — the latter can only be confirmed on Cruzr itself.

- [ ] **Step 3: Visual check on Cruzr**

Install on the actual Cruzr robot (`adb install -r`) and re-check the same 5 spots. This is the only way to confirm the original missing-glyph bug is actually gone — per the standing note in this project's history that Compose rendering on API 22 is under-exercised and surprises can only be found on the robot.

- [ ] **Step 4: Report results to the user**

Confirm all 4 previously-broken spots (Đá Xay category, Bánh Ngọt category, empty-cart icon, robot avatar) now render correctly on Cruzr, and that nothing else visually regressed.
