package co.abaye.mailtice.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import co.abaye.mailtice.domain.PaneStyle
import co.abaye.mailtice.ui.LocalPaneStyle

/** `true` when the host draws its own title bar, so the app must not repeat the brand strip. */
val LocalHostHasTitleBar = staticCompositionLocalOf { false }

/**
 * A surface the window can be dragged by. Empty everywhere except the desktop host, which supplies
 * Nucleus's drag modifier without the shared UI having to depend on Nucleus.
 */
val LocalWindowDrag = staticCompositionLocalOf<Modifier> { Modifier }

/** Narrow window (phones, < 720dp): bottom bar instead of the rail, reader as its own page. */
val LocalCompactLayout = staticCompositionLocalOf { false }

/** Card style in effect: the user picked it and the window is wide enough for floating panes. */
@Composable
@ReadOnlyComposable
fun cardPanes(): Boolean = cardStyle() && !LocalCompactLayout.current

/** Card style picked: rounded rows and filled fields, no hairlines. Applies on phones too. */
@Composable
@ReadOnlyComposable
fun cardStyle(): Boolean = LocalPaneStyle.current == PaneStyle.Cards
