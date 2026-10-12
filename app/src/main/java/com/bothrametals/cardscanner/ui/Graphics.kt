package com.bothrametals.cardscanner.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/*
 * Simple drawn graphics for the app: logo, the three welcome pictures, coloured initials,
 * section cards and small tags. Everything is drawn in code, so the app stays small and the
 * pictures stay sharp on every screen size.
 */

// ------------------------------------------------------------------ logo

/** Same light copper as the launcher icon. */
private val LogoCopper = Color(0xFFE8A56B)

/** App logo: a business card inside a scanner frame with a copper scan line. */
@Composable
fun AppLogo(modifier: Modifier = Modifier.size(72.dp)) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            brush = Brush.linearGradient(listOf(Color(0xFF2E6DA8), SteelDeep), start = Offset.Zero, end = Offset(w, h)),
            cornerRadius = CornerRadius(w * 0.24f),
        )
        val cl = w * 0.19f
        val ct = h * 0.31f
        val cw = w * 0.62f
        val ch = h * 0.38f
        drawRoundRect(Color.White, topLeft = Offset(cl, ct), size = Size(cw, ch), cornerRadius = CornerRadius(w * 0.05f))
        drawCircle(SteelLight, radius = ch * 0.19f, center = Offset(cl + cw * 0.21f, ct + ch * 0.40f))
        bar(Offset(cl + cw * 0.40f, ct + ch * 0.30f), cw * 0.46f, ch * 0.10f, Color(0xFF8E9DAD))
        bar(Offset(cl + cw * 0.40f, ct + ch * 0.53f), cw * 0.32f, ch * 0.08f, Color(0xFFC5CED8))
        bar(Offset(cl + cw * 0.12f, ct + ch * 0.80f), cw * 0.50f, ch * 0.07f, Color(0xFFC5CED8))
        drawLine(LogoCopper, Offset(w * 0.11f, h * 0.5f), Offset(w * 0.89f, h * 0.5f), strokeWidth = w * 0.035f, cap = StrokeCap.Round)
        corners(Offset(w * 0.11f, h * 0.21f), Size(w * 0.78f, h * 0.58f), len = w * 0.12f, color = LogoCopper, stroke = w * 0.035f)
    }
}

// ------------------------------------------------------------------ welcome pictures

/** Phone scanning a business card (welcome page 1 and the empty home screen). */
@Composable
fun ScanIllustration(modifier: Modifier = Modifier) {
    val blob = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(blob, radius = h * 0.46f, center = Offset(w / 2, h / 2))

        // a second card waiting on the table
        rotate(-14f, pivot = Offset(w * 0.27f, h * 0.62f)) {
            miniCard(Offset(w * 0.08f, h * 0.50f), Size(h * 0.42f, h * 0.25f), Copper)
        }

        // phone
        val pw = h * 0.48f
        val ph = h * 0.86f
        val pl = w / 2 - pw / 2 + w * 0.06f
        val pt = h * 0.07f
        drawRoundRect(SteelDeep, topLeft = Offset(pl, pt), size = Size(pw, ph), cornerRadius = CornerRadius(pw * 0.16f))
        val inset = pw * 0.06f
        val sl = pl + inset
        val st = pt + inset * 1.8f
        val sw = pw - inset * 2
        val sh = ph - inset * 3.6f
        drawRoundRect(Color(0xFFE9F1F9), topLeft = Offset(sl, st), size = Size(sw, sh), cornerRadius = CornerRadius(pw * 0.09f))

        // card on the screen, with the scanner frame and scan line
        val cw = sw * 0.82f
        val ch = cw * 0.60f
        val cl = sl + (sw - cw) / 2
        val ct = st + sh * 0.30f
        miniCard(Offset(cl, ct), Size(cw, ch), Steel)
        corners(Offset(cl - pw * 0.03f, ct - pw * 0.03f), Size(cw + pw * 0.06f, ch + pw * 0.06f), len = cw * 0.16f, color = Copper, stroke = pw * 0.025f)
        val y = ct + ch * 0.46f
        drawRect(
            brush = Brush.verticalGradient(listOf(Color.Transparent, Copper.copy(alpha = 0.30f)), startY = y - ch * 0.35f, endY = y),
            topLeft = Offset(sl, y - ch * 0.35f),
            size = Size(sw, ch * 0.35f),
        )
        drawLine(Copper, Offset(sl, y), Offset(sl + sw, y), strokeWidth = pw * 0.022f, cap = StrokeCap.Round)

        // shutter button
        val shutter = Offset(pl + pw / 2, st + sh * 0.86f)
        drawCircle(Color.White, radius = pw * 0.085f, center = shutter)
        drawCircle(Steel, radius = pw * 0.085f, center = shutter, style = Stroke(pw * 0.022f))

        sparkle(Offset(pl + pw + w * 0.07f, h * 0.20f), h * 0.07f, Copper)
        sparkle(Offset(pl + pw + w * 0.13f, h * 0.34f), h * 0.035f, Steel)
    }
}

/** A card turning into neat fields, with AI sparkles (welcome page 2). */
@Composable
fun AiIllustration(modifier: Modifier = Modifier) {
    val blob = MaterialTheme.colorScheme.secondaryContainer
    val pill = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(blob, radius = h * 0.46f, center = Offset(w / 2, h / 2))

        val cs = Size(w * 0.40f, w * 0.40f * 0.6f)
        val cardTop = Offset(w * 0.05f, h * 0.5f - cs.height / 2)
        rotate(-6f, pivot = cardTop + Offset(cs.width / 2, cs.height / 2)) {
            miniCard(cardTop, cs, Steel)
        }

        // dots flowing from the card to the fields
        for (i in 0..2) drawCircle(Copper.copy(alpha = 0.5f + i * 0.2f), radius = h * 0.018f, center = Offset(w * (0.50f + i * 0.04f), h * 0.5f))

        sparkle(Offset(w * 0.50f, h * 0.22f), h * 0.10f, Copper)
        sparkle(Offset(w * 0.59f, h * 0.10f), h * 0.045f, Copper)
        sparkle(Offset(w * 0.42f, h * 0.80f), h * 0.04f, Steel)

        // fields: name, phone, email, address
        val accents = listOf(Steel, Copper, Success, Steel)
        val lengths = listOf(0.62f, 0.50f, 0.70f, 0.56f)
        val pl = w * 0.62f
        val pw = w * 0.34f
        val ph = h * 0.115f
        for (i in 0..3) {
            val top = h * 0.24f + i * h * 0.145f
            drawRoundRect(Color.Black.copy(alpha = 0.06f), topLeft = Offset(pl, top + ph * 0.08f), size = Size(pw, ph), cornerRadius = CornerRadius(ph / 2))
            drawRoundRect(pill, topLeft = Offset(pl, top), size = Size(pw, ph), cornerRadius = CornerRadius(ph / 2))
            drawCircle(accents[i], radius = ph * 0.26f, center = Offset(pl + ph * 0.5f, top + ph / 2))
            bar(Offset(pl + ph * 0.95f, top + ph / 2), pw * lengths[i] - ph * 0.5f, ph * 0.16f, Color(0xFFB3BFCB))
        }
    }
}

/** A saved contact, a green tick and the cloud backup (welcome page 3). */
@Composable
fun ContactsIllustration(modifier: Modifier = Modifier) {
    val blob = MaterialTheme.colorScheme.tertiaryContainer
    val cardColor = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(blob, radius = h * 0.46f, center = Offset(w / 2, h / 2))

        // contact card
        val cw = h * 0.50f
        val chh = h * 0.70f
        val cl = w / 2 - cw / 2
        val ct = h * 0.14f
        drawRoundRect(Color.Black.copy(alpha = 0.07f), topLeft = Offset(cl, ct + chh * 0.03f), size = Size(cw, chh), cornerRadius = CornerRadius(cw * 0.12f))
        drawRoundRect(cardColor, topLeft = Offset(cl, ct), size = Size(cw, chh), cornerRadius = CornerRadius(cw * 0.12f))
        person(Offset(cl + cw / 2, ct + chh * 0.25f), cw * 0.20f, Color(0xFFD6E6F6), Steel)
        bar(Offset(cl + cw * 0.22f, ct + chh * 0.52f), cw * 0.56f, chh * 0.045f, Color(0xFF8E9DAD))
        for (i in 0..1) {
            val y = ct + chh * (0.66f + i * 0.13f)
            drawCircle(if (i == 0) Success else Copper, radius = chh * 0.032f, center = Offset(cl + cw * 0.20f, y))
            bar(Offset(cl + cw * 0.32f, y), cw * (0.48f - i * 0.1f), chh * 0.035f, Color(0xFFC5CED8))
        }

        // green tick
        val tc = Offset(cl + cw * 0.92f, ct + chh * 0.90f)
        val tr = h * 0.085f
        drawCircle(Success, radius = tr, center = tc)
        val tick = Path().apply {
            moveTo(tc.x - tr * 0.42f, tc.y + tr * 0.02f)
            lineTo(tc.x - tr * 0.10f, tc.y + tr * 0.32f)
            lineTo(tc.x + tr * 0.45f, tc.y - tr * 0.30f)
        }
        drawPath(tick, Color.White, style = Stroke(tr * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // cloud backup
        val cloud = Color(0xFFBFD8EF)
        val cc = Offset(w * 0.84f, h * 0.19f)
        val u = h * 0.06f
        drawCircle(cloud, radius = u * 1.15f, center = cc)
        drawCircle(cloud, radius = u * 0.85f, center = cc + Offset(-u * 1.1f, u * 0.35f))
        drawCircle(cloud, radius = u * 0.80f, center = cc + Offset(u * 1.1f, u * 0.4f))
        drawRoundRect(cloud, topLeft = Offset(cc.x - u * 1.9f, cc.y + u * 0.2f), size = Size(u * 3.8f, u * 1.0f), cornerRadius = CornerRadius(u * 0.5f))
        drawLine(Steel, cc + Offset(0f, u * 0.75f), cc + Offset(0f, -u * 0.45f), strokeWidth = u * 0.22f, cap = StrokeCap.Round)
        val arrow = Path().apply {
            moveTo(cc.x - u * 0.4f, cc.y - u * 0.05f)
            lineTo(cc.x, cc.y - u * 0.45f)
            lineTo(cc.x + u * 0.4f, cc.y - u * 0.05f)
        }
        drawPath(arrow, Steel, style = Stroke(u * 0.22f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // team: two colleagues who see the same cards
        person(Offset(w * 0.15f, h * 0.68f), h * 0.075f, Copper, Color.White)
        drawCircle(blob, radius = h * 0.075f * 1.18f, center = Offset(w * 0.24f, h * 0.75f))
        person(Offset(w * 0.24f, h * 0.75f), h * 0.075f, Steel, Color.White)
    }
}

// ------------------------------------------------------------------ drawing helpers

/** A small business card: avatar dot and three text lines. */
private fun DrawScope.miniCard(topLeft: Offset, s: Size, accent: Color) {
    val r = CornerRadius(s.height * 0.10f)
    drawRoundRect(Color.Black.copy(alpha = 0.08f), topLeft = topLeft + Offset(0f, s.height * 0.05f), size = s, cornerRadius = r)
    drawRoundRect(Color.White, topLeft = topLeft, size = s, cornerRadius = r)
    drawCircle(accent, radius = s.height * 0.15f, center = topLeft + Offset(s.width * 0.17f, s.height * 0.36f))
    bar(topLeft + Offset(s.width * 0.34f, s.height * 0.28f), s.width * 0.50f, s.height * 0.09f, Color(0xFF7F8FA0))
    bar(topLeft + Offset(s.width * 0.34f, s.height * 0.46f), s.width * 0.34f, s.height * 0.07f, Color(0xFFC5CED8))
    bar(topLeft + Offset(s.width * 0.10f, s.height * 0.72f), s.width * 0.62f, s.height * 0.07f, Color(0xFFC5CED8))
}

/** A rounded line that stands for a line of text. [start] is the left end, centred vertically. */
private fun DrawScope.bar(start: Offset, length: Float, thickness: Float, color: Color) {
    drawLine(color, start, start + Offset(length, 0f), strokeWidth = thickness, cap = StrokeCap.Round)
}

/** Round avatar with a simple head-and-shoulders figure. */
private fun DrawScope.person(c: Offset, r: Float, bg: Color, fg: Color) {
    drawCircle(bg, radius = r, center = c)
    drawCircle(fg, radius = r * 0.32f, center = c - Offset(0f, r * 0.22f))
    drawArc(
        fg, startAngle = 180f, sweepAngle = 180f, useCenter = true,
        topLeft = Offset(c.x - r * 0.55f, c.y + r * 0.18f), size = Size(r * 1.1f, r * 0.9f),
    )
}

/** Scanner-style corner brackets around a box. */
private fun DrawScope.corners(topLeft: Offset, box: Size, len: Float, color: Color, stroke: Float) {
    val l = topLeft.x
    val t = topLeft.y
    val r = l + box.width
    val b = t + box.height
    val p = Path().apply {
        moveTo(l, t + len); lineTo(l, t); lineTo(l + len, t)
        moveTo(r - len, t); lineTo(r, t); lineTo(r, t + len)
        moveTo(r, b - len); lineTo(r, b); lineTo(r - len, b)
        moveTo(l + len, b); lineTo(l, b); lineTo(l, b - len)
    }
    drawPath(p, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Four-pointed sparkle. */
private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r)
        cubicTo(c.x, c.y, c.x, c.y, c.x + r, c.y)
        cubicTo(c.x, c.y, c.x, c.y, c.x, c.y + r)
        cubicTo(c.x, c.y, c.x, c.y, c.x - r, c.y)
        cubicTo(c.x, c.y, c.x, c.y, c.x, c.y - r)
        close()
    }
    drawPath(p, color)
    drawCircle(color, radius = r * 0.16f, center = c)
}

// ------------------------------------------------------------------ small building blocks

private val AvatarColors = listOf(
    Color(0xFF1F4E79), Color(0xFFC0773A), Color(0xFF2E7D4F), Color(0xFF6A4C93),
    Color(0xFF00838F), Color(0xFFAD1457), Color(0xFF5D6D7E), Color(0xFF8D6E63),
)

/** Colours for sub-groups inside an event (1st, 2nd, 3rd…), used on chips and card labels. */
private val SubGroupColors = listOf(
    Color(0xFFC0773A), Color(0xFF2E7D4F), Color(0xFF6A4C93), Color(0xFF00838F),
    Color(0xFFAD1457), Color(0xFF1F4E79), Color(0xFF8D6E63), Color(0xFF5D6D7E),
)

fun subGroupColor(index: Int): Color = if (index < 0) Color(0xFF5D6D7E) else SubGroupColors[index % SubGroupColors.size]

/** "Pankaj Bothra" → "PB", "Ramesh" → "R". */
fun initialsOf(name: String): String {
    val words = name.split(' ', '.', '-', ',').filter { it.isNotBlank() && it[0].isLetterOrDigit() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words.first().take(1) + words.last().take(1)).uppercase()
    }
}

/** Coloured circle with the person's initials; the same name always gets the same colour. */
@Composable
fun InitialsAvatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val color = AvatarColors[(name.trim().lowercase().hashCode() and 0x7fffffff) % AvatarColors.size]
    Box(modifier.size(size).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(initialsOf(name), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp)
    }
}

/** Small icon in a tinted circle. */
@Composable
fun IconBadge(
    icon: ImageVector,
    size: Dp = 30.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Box(Modifier.size(size).clip(CircleShape).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.56f), tint = tint)
    }
}

/** White rounded card with an icon and a heading, used to group the fields on the details screen. */
@Composable
fun SectionCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(icon)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

/** Number with a label, e.g. "12 / This week". Tappable when [onClick] is given. */
@Composable
fun StatTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = accent)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Small coloured label such as "AI", an event name or "Duplicate?". */
@Composable
fun Tag(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.13f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(11.dp), tint = color)
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Light status-bar icons over the blue headers, dark icons over light screens. */
@Composable
fun StatusBarIcons(lightIcons: Boolean) {
    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !lightIcons
    }
}
