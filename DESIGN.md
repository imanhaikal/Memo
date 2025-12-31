# DESIGN.md - Memo Budget (Android / Jetpack Compose)

## 1. Design Philosophy
**Aesthetic:** "Premium Hardware" / "Clean Tech"
**Inspiration:** Sunday, Teenage Engineering, Apple (Human Interface), Braun.

The design should feel like a dedicated, physical handheld device. It eschews standard Material Design "paper" layers for a more tactile, "machined" feel. The interface is calm, high-contrast, and focused on the "One Big Number."

**Core Principles:**
1.  **Tactility:** UI elements should react to touch with satisfying physics (spring animations) and haptic feedback.
2.  **Calmness:** Use off-whites and warm grays (`Surface` vs `Background`) rather than stark white.
3.  **Focus:** The UI hierarchy is aggressive. The "Available Today" number dominates the screen.
4.  **System Integration:** The app draws behind the status bar and navigation bar (Edge-to-Edge) to feel immersive.

---

## 2. Theme System (Compose)

### 2.1 Color Palette
The palette is minimal, relying on texture and distinct functional colors.

**`Color.kt` Definitions:**

| Role | Compose Name | Hex Value | Usage |
| :--- | :--- | :--- | :--- |
| **Canvas** | `AppColors.Background` | `Color(0xFFF9F9F9)` | Main screen background. Warm Gray. |
| **Surface** | `AppColors.Surface` | `Color(0xFFFFFFFF)` | Cards, Modals, Inputs. |
| **Ink Primary** | `AppColors.TextPrimary` | `Color(0xFF111111)` | Hero numbers, primary headers. |
| **Ink Secondary** | `AppColors.TextSecondary` | `Color(0xFF888888)` | Labels, secondary data. |
| **Accent** | `AppColors.Yellow` | `Color(0xFFF2E057)` | Primary Actions, FAB. |
| **Accent Dark** | `AppColors.YellowDark` | `Color(0xFFE6D346)` | Pressed states. |
| **Success** | `AppColors.Green` | `Color(0xFF00A84D)` | "On Track" status text. |
| **Success Bg** | `AppColors.GreenSubtle` | `Color(0xFFE3FCEF)` | "On Track" pill background. |
| **Danger** | `AppColors.Red` | `Color(0xFFFF4444)` | "Over Limit" text. |
| **Danger Bg** | `AppColors.RedSubtle` | `Color(0xFFFFEBEB)` | "Over Limit" pill background. |
| **Border** | `AppColors.Border` | `Color(0xFFF0F0F0)` | Subtle strokes on cards. |

### 2.2 Typography
**Font Family:** `Inter` (Google Fonts).
*   *Fallback:* San Francisco (System Default).

**`Type.kt` Configuration:**

*   **Hero Value (`DisplayLarge`):**
    *   Size: `64.sp` (Scaled appropriately)
    *   Weight: `FontWeight.Bold` (700)
    *   Tracking: `-0.05.em` (Tight)
    *   Feature Settings: `tnum` (Tabular Numbers) — **Critical** for rolling number animations.
*   **Headings (`TitleMedium`):**
    *   Size: `18.sp`
    *   Weight: `FontWeight.SemiBold` (600)
    *   Tracking: `-0.02.em`
*   **Labels (`LabelSmall`):**
    *   Size: `12.sp`
    *   Weight: `FontWeight.SemiBold` (600)
    *   Tracking: `0.08.em` (Wide)
    *   Case: Uppercase
*   **Body (`BodyMedium`):**
    *   Size: `16.sp`
    *   Weight: `FontWeight.Medium` (500)

---

## 3. Component Architecture (Composables)

### 3.1 Layout & Scaffolding
*   **Edge-to-Edge:** Use `ComponentActivity.enableEdgeToEdge()`.
    *   Status Bar: Transparent. Icons dark/light based on theme.
    *   Nav Bar: Transparent.
*   **Padding:** Standard horizontal padding is `24.dp`.

### 3.2 Cards & Surfaces
Cards are the primary data containers. They should not look like standard Material CardViews.

*   **Shape:** `RoundedCornerShape(32.dp)` (Super-ellipse feel).
*   **Styling (`Modifier`):**
    *   `background(AppColors.Surface)`
    *   `border(1.dp, Color.White.copy(alpha=0.5))` — A subtle inner bevel effect.
    *   `shadow(elevation = 12.dp, shape = ..., ambientColor = Color(0x10000000), spotColor = Color(0x05000000))` — Very soft, diffuse shadow. Avoid harsh standard Android elevation.

### 3.3 Buttons & FAB
*   **Primary Action (FAB):**
    *   Shape: `CircleShape` or `RoundedCornerShape(100)` (Pill).
    *   Color: `AppColors.TextPrimary` (Black).
    *   Content Color: `Color.White`.
    *   Elevation: `8.dp`.
    *   **Behavior:** Scale up on press, distinct ripple.
*   **Inputs (`OutlinedTextField` / `BasicTextField`):**
    *   Background: `Color(0xFFF5F5F5)`.
    *   Shape: `RoundedCornerShape(16.dp)`.
    *   Text Alignment: Center.
    *   Focus Indicator: Transparent (Remove underline).
    *   Active State: Animate background to `Color.White` + Border `AppColors.Yellow`.

### 3.4 Transaction List Items
*   **Container:** `Row` inside a `Surface`.
*   **Styling:**
    *   Padding: `Vertical 20.dp`, `Horizontal 24.dp`.
    *   Background: `AppColors.Surface`.
    *   Border: `1.dp` solid `AppColors.Border`.
    *   Shape: `RoundedCornerShape(16.dp)`.
    *   Margin/Spacer: `12.dp` between items.

---

## 4. Motion Design (Compose Animation Specs)

Motion is functional. It communicates weight and change.

### 4.1 Physics specs
Define these in your `AnimationConstants.kt`:

```kotlin
val SpringBouncy = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessLow
)

val SpringSnappy = spring<Float>(
    dampingRatio = 0.75f,
    stiffness = Spring.StiffnessMedium
)

val EaseOutExpo = tween<Float>(
    durationMillis = 600,
    easing = FastOutSlowInEasing
)
```

### 4.2 Choreography
1.  **Entrance (Waterfall):**
    *   Use `AnimatedVisibility` with `slideInVertically { it / 2 } + fadeIn`.
    *   Delay start times: Hero (0ms) -> Stats (100ms) -> List (200ms) -> FAB (300ms).
2.  **Rolling Numbers:**
    *   Create a custom Composable `RollingNumberText(value: Double)`.
    *   Use `animateFloatAsState` (or `Animatable`) to interpolate the number.
    *   Format string on every frame.
3.  **Haptic Feedback:**
    *   Trigger `LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.Light)` on button presses and when the rolling number counter settles.

---

## 5. Implementation Guidelines

### 5.1 Assets & Resources
*   **Icons:** Use **Phosphor Icons** or **Heroicons** (rounded variants) as SVG/VectorDrawable. Avoid standard Material Icons if possible to maintain unique brand identity.
*   **Dark Mode:**
    *   Canvas: `Color(0xFF111111)`
    *   Surface: `Color(0xFF1A1A1A)`
    *   Text: `Color(0xFFEEEEEE)`
    *   Accent: Yellow remains `Color(0xFFF2E057)`.

### 5.2 Accessibility
*   **Content Description:** Mandatory for the FAB and any icon-only buttons.
*   **Scale:** Ensure `Text` units are `sp` so they scale with system font settings.
*   **Contrast:** The Yellow accent on White text is poor contrast. Use Black text on Yellow buttons.

---

## 6. Example Modifier Stack (Card)

```kotlin
Modifier
    .fillMaxWidth()
    .shadow(
        elevation = 15.dp,
        shape = RoundedCornerShape(32.dp),
        ambientColor = Color.Black.copy(alpha = 0.1f),
        spotColor = Color.Black.copy(alpha = 0.1f)
    )
    .clip(RoundedCornerShape(32.dp))
    .background(MaterialTheme.colorScheme.surface)
    .border(
        width = 1.dp,
        color = Color.White.copy(alpha = 0.5f), // Inner bevel highlight
        shape = RoundedCornerShape(32.dp)
    )
    .padding(32.dp)
```

---

## 7. Implementation Specification (Prototype Mapping)

### 7.1 CSS to Compose Mapping

**Colors:**
| CSS Variable | Compose Color | Hex | Role |
| :--- | :--- | :--- | :--- |
| `--bg-color` | `AppColors.Background` | `0xFFF9F9F9` | Main screen background |
| `--surface-color` | `AppColors.Surface` | `0xFFFFFFFF` | Card backgrounds |
| `--text-primary` | `AppColors.TextPrimary` | `0xFF111111` | Primary text |
| `--text-secondary` | `AppColors.TextSecondary` | `0xFF888888` | Labels |
| `--text-tertiary` | `AppColors.TextTertiary` | `0xFFC0C0C0` | Dates/Meta |
| `--accent-color` | `AppColors.Yellow` | `0xFFF2E057` | Primary buttons, active states |
| `--danger-color` | `AppColors.Red` | `0xFFFF4444` | Over limit text |
| `--border-color` | `AppColors.Border` | `0xFFF0F0F0` | Subtle card borders |

**Shapes:**
| CSS Variable | Compose Shape | Size | Usage |
| :--- | :--- | :--- | :--- |
| `--radius-xl` (32px) | `AppShapes.Large` | `32.dp` | Main Hero Card, Modal |
| `--radius-lg` (24px) | `AppShapes.Medium` | `24.dp` | Stat Cards |
| `--radius-md` (16px) | `AppShapes.Small` | `16.dp` | Transaction Items, Inputs |
| `99px` (Pill) | `CircleShape` | `50` | FAB, Buttons, Status Pill |

**Animations:**
| CSS Transition | Compose Equivalent | Spec |
| :--- | :--- | :--- |
| `--ease-spring` (Bouncy) | `spring()` | `dampingRatio = 0.6f`, `stiffness = Low` |
| `--ease-out` (Smooth) | `tween()` | `durationMillis = 400`, `easing = FastOutSlowIn` |
| `itemSlideIn` (Y + Fade) | `AnimatedVisibility` | `slideInVertically { it / 2 } + fadeIn` |
| Rolling Numbers | `Animatable` | Custom `RollingNumberText` composable |

### 7.2 Architecture Components

**Data Layer:**
*   **Transactions:** `Room` Database.
    *   Entity: `Transaction(id, amount, note, dateString)`
    *   *Note:* Prototype uses ISO strings. We will use `java.time.Instant` or `LocalDateTime` stored as `Long` (Epoch) or `String`.
*   **Budget State:** `DataStore` (Preferences).
    *   Keys: `TOTAL_BUDGET` (Double), `CYCLE_START_DATE` (String/Long), `TOTAL_DAYS` (Int).

**UI Structure (Composable Tree):**
*   `MemoApp` (Theme Wrapper)
    *   `NavHost`
        *   `SetupScreen` (Form)
        *   `DashboardScreen` (Scaffold)
            *   `TopBar` (Brand + Reset)
            *   `HeroCard` (Available Today + StatusPill)
            *   `StatsGrid` (Row of `StatCard`)
            *   `TransactionHistory` (LazyColumn of `TransactionItem`)
            *   `AddExpenseFab` (Box/Positioned)
        *   `AddTransactionSheet` (ModalBottomSheet)

### 7.3 Logic Replication (`calculate()`)

The Kotlin `ViewModel` must replicate the JS `calculate()` function exactly:

1.  **Days Passed:** `(CurrentDate - StartDate)`.
2.  **Days Remaining:** `max(1, TotalDays - DaysPassed)`.
3.  **Spent Before Today:** Sum of transactions where `date < Today`.
4.  **Pool Start of Day:** `TotalBudget - SpentBeforeToday`.
5.  **Baseline Daily Limit:** `PoolStartOfDay / DaysRemaining`.
6.  **Available Today:** `BaselineDailyLimit - SpentToday`.

*Constraint:* If `Available Today < 0`, Status = "Over Limit". If `Available Today` < 20% of `Daily Limit`, Status = "Careful".