package com.chrispoole.intervaltimer.ui

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.chrispoole.intervaltimer.Billing
import com.chrispoole.intervaltimer.FREE_PRESET_SLOTS

/**
 * The paywall. It appears only when the user has just reached for something behind it — a locked
 * theme, or a fourth saved sequence — never on launch, and never over a running workout.
 *
 * Deliberately says what stays free before it says what costs money: the timer is the app, and
 * nobody should have to read to the bottom to find out the thing they came for still works.
 */
@Composable
fun UnlockSheet(onDismiss: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val shape = RoundedCornerShape(28.dp)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                // Solid black under the glass, not the translucent fill the in-page cards use: a
                // Dialog draws on its own window with the app behind it, so GlassFill alone would
                // read as a smear of the home screen rather than as a surface.
                .background(Color.Black)
                .background(GlassFill)
                .border(1.dp, glassBorder(), shape)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Unlock everything", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(
                "All eight themes.\nUnlimited saved sequences.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "The timer, the cues, background running, all $FREE_PRESET_SLOTS free slots and every " +
                    "language stay free forever. One payment, no subscription.",
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(22.dp))
            // Play's formatted price, in the user's own currency. Until it arrives the button says
            // what it does and nothing about money — a hardcoded "$2.99" would be a lie in most of
            // the 12 languages this app ships.
            GlassPill(
                Billing.price?.let { "Unlock — $it" } ?: "Unlock",
                { activity?.let { Billing.purchase(it) } },
                Modifier.fillMaxWidth(),
                big = true,
            )
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Not now",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                    modifier = Modifier.noRippleClickable(onDismiss).padding(12.dp),
                )
            }
        }
    }
}
