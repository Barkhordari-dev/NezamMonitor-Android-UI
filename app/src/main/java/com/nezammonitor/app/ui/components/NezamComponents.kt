package com.nezammonitor.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.FeeEntity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.ui.theme.AppTheme
import com.nezammonitor.app.ui.theme.CardStyle
import com.nezammonitor.app.ui.theme.appColorScheme
import com.nezammonitor.app.ui.theme.darker
import com.nezammonitor.app.ui.theme.nezamCardStyle
import com.nezammonitor.app.ui.theme.nezamExtras
import com.nezammonitor.app.ui.theme.nezamSemantic

// ---------------------------------------------------------------------------
// Shared building blocks. Visual only: they never change data, navigation
// arguments or callbacks — every screen keeps its own logic and handlers.
//
// Visual language (UAC-style): glassy cards with a hairline border and no
// elevation shadow, vivid gradient primary buttons, a deep gradient screen
// background, small muted secondary text + big semi-bold numbers.
// ---------------------------------------------------------------------------

/**
 * The standard card of the app: 20dp corners, a hairline border taken from
 * the theme (white ~12% on dark, dark ~10% on light) and no elevation.
 * Pass [containerColor] only when the screen needs a tonal state
 * (selection, stats, status); otherwise the Material default is used.
 */
/**
 * The standard card of the app: adapts to [CardStyle] (Glassmorphic, Elevated, or Flat)
 * and [CornerStyle] automatically.
 */
@Composable
fun NezamCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color? = null,
    content: @Composable () -> Unit
) {
    val cardStyle = MaterialTheme.nezamCardStyle
    val scheme = MaterialTheme.colorScheme
    val extras = MaterialTheme.nezamExtras

    val resolvedColor = containerColor ?: when (cardStyle) {
        CardStyle.GLASS -> scheme.surface.copy(alpha = 0.82f)
        CardStyle.ELEVATED -> scheme.surface
        CardStyle.FLAT -> scheme.surfaceVariant.copy(alpha = 0.45f)
    }

    val resolvedBorder = when (cardStyle) {
        CardStyle.GLASS -> BorderStroke(1.dp, extras.cardBorder)
        CardStyle.ELEVATED -> null
        CardStyle.FLAT -> BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.25f))
    }

    val resolvedElevation = when (cardStyle) {
        CardStyle.GLASS -> CardDefaults.cardElevation(defaultElevation = 0.dp)
        CardStyle.ELEVATED -> CardDefaults.cardElevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
        CardStyle.FLAT -> CardDefaults.cardElevation(defaultElevation = 0.dp)
    }

    val cardModifier = if (onClick != null) modifier.clickable { onClick() } else modifier

    Card(
        modifier = cardModifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = resolvedColor),
        border = resolvedBorder,
        elevation = resolvedElevation
    ) {
        content()
    }
}

/** Card with a titled header (gradient accent bar + bold title + optional subtitle). */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    NezamCard(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionTitle(title)
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

/** Section heading used inside existing cards on the detail screens. */
@Composable
fun SectionTitle(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary
                        )
                    )
                )
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

/**
 * Primary action button: 50dp minimum height, large rounded corners and a
 * vertical gradient (primary → darker primary). The gradient is drawn
 * behind the button so the ripple still works.
 */
@Composable
fun NezamPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val gradient = Brush.verticalGradient(
        listOf(scheme.primary, scheme.primary.darker())
    )
    val bgModifier = if (enabled) {
        Modifier.background(gradient, shape)
    } else {
        Modifier.background(scheme.surfaceVariant, shape)
    }
    Button(
        onClick = onClick,
        modifier = modifier.then(bgModifier).height(50.dp),
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = scheme.onPrimary,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = scheme.onSurfaceVariant
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Secondary (outlined) action button, same sizing as the primary one. */
@Composable
fun NezamSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.nezamExtras.cardBorder)
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * The standard search field: rounded, hairline border, surface background,
 * with a search icon and a clear button that appears only while there is
 * text. Clearing only empties the query — the screen's own filtering logic
 * is untouched.
 */
@Composable
fun NezamSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = if (label != null) { { Text(label) } } else null,
        placeholder = if (placeholder != null) { { Text(placeholder, fontSize = 12.sp) } } else null,
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null)
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "پاک کردن جستجو")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.nezamExtras.cardBorder
        )
    )
}

/** Tonal pill that shows a list count / summary line. */
@Composable
fun NezamCountPill(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        border = BorderStroke(1.dp, MaterialTheme.nezamExtras.cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/** Status meaning that never changes with the theme. */
enum class StatusKind { SUCCESS, WARNING, ERROR, INFO, NEUTRAL }

/**
 * Small pill for a status word (e.g. «تراز»، «کسری»، «پرداخت شده»):
 * a vivid dot + the same colour at 14% alpha as the background, with a
 * hairline border. The text stays the tonal container colour so it is
 * readable in every theme, light or dark.
 */
@Composable
fun StatusPill(text: String, kind: StatusKind, modifier: Modifier = Modifier) {
    val semantic = MaterialTheme.nezamSemantic
    val scheme = MaterialTheme.colorScheme
    val (container, onContainer, dot) = when (kind) {
        StatusKind.SUCCESS -> Triple(
            semantic.success.copy(alpha = 0.14f),
            semantic.onSuccessContainer, semantic.success
        )
        StatusKind.WARNING -> Triple(
            semantic.warning.copy(alpha = 0.14f),
            semantic.onWarningContainer, semantic.warning
        )
        StatusKind.ERROR -> Triple(
            scheme.error.copy(alpha = 0.14f),
            scheme.onErrorContainer, scheme.error
        )
        StatusKind.INFO -> Triple(
            semantic.info.copy(alpha = 0.14f),
            semantic.onInfoContainer, semantic.info
        )
        StatusKind.NEUTRAL -> Triple(
            scheme.surfaceVariant,
            scheme.onSurfaceVariant,
            scheme.onSurfaceVariant
        )
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(container)
            .border(
                BorderStroke(1.dp, dot.copy(alpha = 0.35f)),
                RoundedCornerShape(999.dp)
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dot)
        )
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = onContainer)
    }
}

/** Centred empty state for lists with no rows (no data / no search match). */
@Composable
fun NezamEmptyState(
    emoji: String,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(emoji, fontSize = 40.sp)
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Centred loading state (used while a detail is being loaded). */
@Composable
fun NezamLoadingState(text: String = "در حال بارگذاری…", modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Hero action card (Home): a full diagonal gradient (primary → secondary →
 * tertiary) with the emoji in a translucent white chip. Text is always the
 * theme's onPrimary, so it stays readable on the vivid gradient in every
 * theme, light or dark.
 */
@Composable
fun HeroActionCard(
    emoji: String,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier.clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(colors.primary, colors.secondary, colors.tertiary)
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emoji, fontSize = 24.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = colors.onPrimary.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

/**
 * Full-width menu row (Home): emoji in a translucent accent chip + bold
 * title, on a glassy card.
 */
@Composable
fun MenuLinkCard(
    emoji: String,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    NezamCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(
                        BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(emoji, fontSize = 22.sp)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        subtitle,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                "‹",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * One theme row in Settings: live colour dots of that theme (light
 * variant), its Persian name + description, and a radio button.
 * Tapping anywhere selects it; the change applies instantly.
 */
@Composable
fun ThemeOptionRow(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lightScheme = remember(theme) { appColorScheme(theme, darkTheme = false) }
    val darkScheme = remember(theme) { appColorScheme(theme, darkTheme = true) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            }
        ),
        border = if (selected) {
            BorderStroke(1.5.dp, primaryColor)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (selected) 2.dp else 0.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live 4-dot preview (Light Primary, Secondary, Tertiary, Dark Primary)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(lightScheme.primary, lightScheme.secondary, lightScheme.tertiary, darkScheme.primary).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.4f)), CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(theme.persianName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    if (selected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "فعال",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    theme.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

// ---------------------------------------------------------------------------
// New in the UAC-style redesign (v3.5.0).
// ---------------------------------------------------------------------------

/**
 * The screen background: the theme's vertical gradient (top → middle →
 * bottom) filling the whole screen. Static — no blur, no animation, no
 * extra cost. Wrap the Scaffold content in it once (NezamMonitorApp) and
 * every screen inherits it.
 */
@Composable
fun NezamGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val gradient = MaterialTheme.nezamExtras.gradient
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(gradient.top, gradient.middle, gradient.bottom)
                )
            )
    ) {
        content()
    }
}

/**
 * Segmented control with an animated selection (like the reference app's
 * animated engine selector): the selected segment's gradient background
 * fades in and its label animates to the onPrimary colour. Fully
 * RTL-safe — no manual offsets, only per-segment alpha / colour animation.
 */
@Composable
fun NezamSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val extras = MaterialTheme.nezamExtras
    val gradient = Brush.verticalGradient(
        listOf(scheme.primary, scheme.primary.darker())
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surface)
            .border(BorderStroke(1.dp, extras.cardBorder), RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val pillAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0f,
                    label = "segmentBackground"
                )
                val textColor by animateColorAsState(
                    targetValue = if (selected) scheme.onPrimary else scheme.onSurfaceVariant,
                    label = "segmentText"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = pillAlpha }
                            .clip(RoundedCornerShape(12.dp))
                            .background(gradient)
                    )
                    Text(
                        label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}

/** Stage title as stored by the API / import may already contain the word «مرحله»
 *  (e.g. "مرحله اول" from brt_marhale_title). Returns the bare stage name
 *  ("اول", "دوم", ...) so callers can safely prefix "مرحله " without doubling it. */
fun stageNameOnly(stage: String): String {
    var s = stage.trim()
    if (s.startsWith("مرحله")) {
        // strip the word «مرحله» plus any space / ZWNJ (U+200C) after it
        s = s.removePrefix("مرحله").trimStart(' ', '\u200C').trim()
    }
    return s
}

/** Stage number from a stored stage title: "مرحله اول" / "اول" / "1" -> 1,
 *  ... "مرحله چهارم" / "چهارم" / "4" -> 4. 0 when unknown. */
fun stageNumberFromTitle(stage: String): Int {
    return when (stageNameOnly(stage).trim()) {
        "اول", "1", "۱" -> 1
        "دوم", "2", "۲" -> 2
        "سوم", "3", "۳" -> 3
        "چهارم", "4", "۴" -> 4
        else -> 0
    }
}

/** Amount ("23158293 ریال") -> 23158293, for sorting and totals. */
fun parseAmount(amount: String): Long {
    if (amount.isBlank()) return 0L
    var c = amount.replace("ریال", "").replace(",", "").replace("٬", "").replace("،", "").replace(" ", "").trim()
    c = c.replace("۰", "0").replace("۱", "1").replace("۲", "2").replace("۳", "3").replace("۴", "4")
        .replace("۵", "5").replace("۶", "6").replace("۷", "7").replace("۸", "8").replace("۹", "9")
        .replace("٠", "0").replace("١", "1").replace("٢", "2").replace("٣", "3").replace("٤", "4")
        .replace("٥", "5").replace("٦", "6").replace("٧", "7").replace("٨", "8").replace("٩", "9")
    return c.toLongOrNull() ?: 0L
}

/** Service type of a fee for display. Stage fees are always "نظارت"; stage-0 fees
 *  (بیمه، حسن انجام کار، سهم سازمان، مالیات) are labeled by amount rank within
 *  the case, largest first — the same rule Windows uses. */
fun mappedServiceType(fee: FeeEntity, allOfCase: List<FeeEntity>): String {
    val st = fee.stage.trim()
    if (st != "0") return "نظارت"
    val stage0 = allOfCase.filter { it.stage.trim() == "0" }
    val order = arrayOf("حسن انجام کار", "نظارت سهم سازمان", "مالیات نظارت سهم سازمان", "بیمه")
    val sorted = stage0.sortedByDescending { parseAmount(it.amount) }
    val idx = sorted.indexOfFirst { it.id == fee.id }
    if (idx == -1) return "بیمه"
    return if (idx < order.size) order[idx] else order.last()
}

/** Persian display name for a fee's amount type. The stored value may be the raw
 *  API/import code ("n"/"b"/"ho"/"t"/"pn") or an already-mapped name ("نظارت").
 *  Blank falls back to the amount-ranked service type. */
fun feeTypeName(fee: FeeEntity, allOfCase: List<FeeEntity>): String {
    when (fee.amountType.trim().lowercase()) {
        "n" -> return "نظارت"
        "b" -> return "بیمه"
        "ho" -> return "حسن انجام کار"
        "t" -> return "مالیات نظارت سهم سازمان"
        "pn" -> return "نظارت سهم سازمان"
    }
    val raw = fee.amountType.trim()
    if (raw.isNotBlank()) return toPersianChars(raw)
    return mappedServiceType(fee, allOfCase)
}

/** Display title of a fee card. The discipline always comes from the fee's own
 *  data (its discipline field, falling back to a sibling fee of the same case) —
 *  never a hardcoded value. Stage fees: "گزارش مرحله دوم <رشته>".
 *  Stage-0 fees: "<type> <رشته>", e.g. "بیمه <رشته>", and never "مرحله: 0". */
fun feeTitle(fee: FeeEntity, allOfCase: List<FeeEntity>): String {
    val disc = toPersianChars(
        fee.discipline.ifBlank {
            allOfCase.firstOrNull { it.discipline.isNotBlank() }?.discipline ?: ""
        }.trim()
    )
    val stage = fee.stage.trim()
    if (stage != "0" && stage.isNotBlank()) {
        val n = stage.toIntOrNull() ?: 0
        val stageName = if (n in 1..4) stageNameForNumber(n) else stage
        return "گزارش مرحله $stageName" + if (disc.isNotBlank()) " $disc" else ""
    }
    val type = feeTypeName(fee, allOfCase)
    return listOf(type, disc).filter { it.isNotBlank() }.joinToString(" ")
}

/** Confirmation status for display: only "تایید شده" is a real status. Stored
 *  values from older imports may be bare numbers (e.g. "85") — those are not
 *  a confirmation and must not be shown as "تأیید: 85". */
fun confirmStatusDisplay(fee: FeeEntity): String {
    val s = fee.confirmStatus.trim()
    return if (s == "تایید شده" || s == "1") "تایید شده" else ""
}

/** True when a fee is confirmed — by its payment status, or by its confirmation
 *  field. The two fields can disagree: the API/sync stores the payment state in
 *  payStatus (from `payed`: "yes"/"ok"/other) and the confirmation in
 *  confirmStatus (from `selected`=="1"), so a confirmed-but-unpaid fee has
 *  payStatus "پرداخت نشده" AND confirmStatus "تایید شده". The Windows export
 *  also stores a bare number ("78".."85") in confirmStatus — only ever on paid
 *  or confirmed fees — which counts as confirmed too. */
fun isFeeConfirmed(fee: FeeEntity): Boolean {
    if (fee.payStatus.trim() == "تایید شده") return true
    val c = fee.confirmStatus.trim()
    if (c == "تایید شده" || c == "1") return true
    if (c.isNotEmpty() && c.all { it.isDigit() }) return true
    return false
}

/** Payment status line for a fee card ("پرداخت: ..."). A confirmed fee must read
 *  "تایید شده" here even when its payStatus says "پرداخت نشده" — the
 *  confirmation lives in confirmStatus, and showing "پرداخت نشده" for it is
 *  wrong. Paid stays "پرداخت شده". */
fun payStatusDisplay(fee: FeeEntity): String {
    if (fee.payStatus.trim() == "پرداخت شده") return "پرداخت شده"
    if (isFeeConfirmed(fee)) return "تایید شده"
    val s = fee.payStatus.trim()
    return if (s.isNotBlank()) s else "پرداخت نشده"
}

/** Fee amount for display, with thousand separators ("23158293 ریال" ->
 *  "23,158,293 ریال"). The sync stores amounts already formatted, but imported
 *  (.nzmdata) amounts are raw digits — formatting at display keeps both
 *  sources identical, like the fees list already does. */
fun formatFeeAmount(raw: String): String {
    if (raw.isBlank()) return "۰ ریال"
    val v = parseAmount(raw)
    return String.format(java.util.Locale.US, "%,d", v) + " ریال"
}

/** "مرحله X" label for a report card, or "" when the stage should not be shown:
 *  the stage is blank, or the report's title already states that stage
 *  (e.g. title "گزارش مرحله دوم" + stage "مرحله دوم" would otherwise print
 *  the word «مرحله» twice on the same card). */
fun reportStageLabel(reportType: String, stage: String): String {
    val name = stageNameOnly(stage)
    if (name.isBlank()) return ""
    if (reportType.contains(name)) return ""
    return "مرحله $name"
}

/** Persian ordinal name for a stage number (1 -> "اول", ..., 4 -> "چهارم"). */
fun stageNameForNumber(n: Int): String = when (n) {
    1 -> "اول"
    2 -> "دوم"
    3 -> "سوم"
    4 -> "چهارم"
    else -> "?"
}
