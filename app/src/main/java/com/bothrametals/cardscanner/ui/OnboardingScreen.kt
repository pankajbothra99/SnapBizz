package com.bothrametals.cardscanner.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private class WelcomePage(val title: String, val text: String)

private val Pages = listOf(
    WelcomePage(
        "Scan front and back",
        "Hold your phone over a business card. It's captured, cropped and straightened by itself. " +
            "Got a pile of card photos? Import them all from the Gallery in one go.",
    ),
    WelcomePage(
        "Details filled in for you",
        "Free AI reads the name, numbers, email, address, GSTIN and what the company does, even on stylish cards. " +
            "Anything doubtful is marked in orange for a quick check.",
    ),
    WelcomePage(
        "Saved, searchable, shared",
        "One tap saves the contact to your phone with the address and product keywords, so a search for \"aluminium\" finds them later. " +
            "Cards are backed up and shared with your team, event by event.",
    ),
)

/** Three welcome pages shown once after the app is installed. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState(pageCount = { Pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == Pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), MaterialTheme.colorScheme.background),
                )
            )
            .systemBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppLogo(Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Text("Card Scanner", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!last) TextButton(onClick = onDone) { Text("Skip") }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    val art = Modifier.aspectRatio(1.3f)
                    when (page) {
                        0 -> ScanIllustration(art)
                        1 -> AiIllustration(art)
                        else -> ContactsIllustration(art)
                    }
                }
                Text(Pages[page].title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    Pages[page].text,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(Pages.size) { i ->
                val selected = pager.currentPage == i
                val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                )
            }
        }

        Button(
            onClick = { if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
                .height(54.dp),
        ) {
            Text(if (last) "Get started" else "Next", style = MaterialTheme.typography.titleMedium)
        }
    }
}
