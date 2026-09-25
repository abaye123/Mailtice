package co.abaye.mailtice.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.abaye.mailtice.domain.Account

/** The account's colour with the first letter of its name: the sidebar and reader badge. */
@Composable
fun AccountAvatar(account: Account, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    LetterAvatar(account.displayName, account.color.color, modifier, size)
}

/**
 * A round badge with the first letter of [name] on [color]. Message rows use it with the sender's
 * name on the account colour, so the account is still told apart without the side stripe.
 */
@Composable
fun LetterAvatar(name: String, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"
    Box(modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text(
            letter,
            style = TextStyle(fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}
