package app.birdeye.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.birdeye.core.Alert
import app.birdeye.core.AlertKind
import app.birdeye.core.Copy
import app.birdeye.core.LightRhythm
import app.birdeye.core.Listening
import app.birdeye.ui.theme.LocalColors
import app.birdeye.ui.theme.Tokens
import app.birdeye.ui.theme.fdp

/**
 * Cards size to their content and sit at the top of the window: system UI can cover the lower
 * part, so key information never depends on being vertically centered in a tall window.
 */
@Composable
private fun SoundCard(
    modifier: Modifier,
    borderColor: Color?,
    icon: @Composable () -> Unit,
    words: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(32.fdp)
    val stackedBelow = 680.fdp
    // Below the system's camera window the space is short, and the caption must still fit.
    val verticalPadding = if (LocalSystemCameraHeight.current > 0.dp) 24.fdp else 40.fdp
    BoxWithConstraints(
        modifier
            .background(LocalColors.current.surface, shape)
            .let { if (borderColor != null) it.border(3.dp, borderColor, shape) else it },
    ) {
        val padded = Modifier.fillMaxWidth().padding(vertical = verticalPadding, horizontal = 44.fdp)
        if (maxWidth < stackedBelow) {
            Column(verticalArrangement = Arrangement.spacedBy(20.fdp), modifier = padded) {
                icon()
                words()
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(44.fdp),
                modifier = padded,
            ) {
                icon()
                words()
            }
        }
    }
}

@Composable
private fun SoundIcon(paths: List<String>, tint: Color, background: Color, description: String?) {
    val compact = LocalSystemCameraHeight.current > 0.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(if (compact) 96.fdp else 152.fdp).background(background, CircleShape),
    ) {
        LineIcon(paths, tint, if (compact) 52.fdp else 80.fdp, strokeWidth = 1.8f, contentDescription = description)
    }
}

@Composable
fun AlertCard(alert: Alert, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    val tint = colors.accent(alert.kind.accent())
    val isVehicle = alert.kind == AlertKind.VEHICLE
    SoundCard(
        modifier = modifier,
        borderColor = if (isVehicle) null else tint,
        icon = {
            SoundIcon(
                paths = when (alert.kind) {
                    AlertKind.SIREN -> LineIcons.siren
                    AlertKind.HORN -> LineIcons.horn
                    AlertKind.VEHICLE -> LineIcons.warning
                    AlertKind.TRAFFIC -> LineIcons.car
                },
                tint = tint,
                background = if (isVehicle) colors.surfaceHigh else colors.sirenIconBg,
                description = alert.eyebrow,
            )
        },
        words = {
            Column(verticalArrangement = Arrangement.spacedBy(6.fdp)) {
                Label(alert.eyebrow, Tokens.bodySmall.withWeight(FontWeight.Bold), tint)
                Label(alert.headline, Tokens.headlineLarge, maxLines = 2)
                if (alert.guidance != null) {
                    Label(alert.guidance, Tokens.alertGuidance, colors.textSecondary)
                }
            }
        },
    )
}

/**
 * Shown when no sound needs attention. It says plainly whether sounds are really being watched:
 * a deaf driver can't tell a quiet road from a microphone that stopped working.
 */
@Composable
fun IdleCard(listening: Listening, scriptStarted: Boolean, muffled: Boolean, modifier: Modifier = Modifier) {
    val colors = LocalColors.current
    val failed = listening == Listening.NO_PERMISSION || listening == Listening.MIC_ERROR
    val (headline, detail) = when (listening) {
        Listening.ACTIVE ->
            if (muffled) Copy.MUFFLED_IDLE to Copy.MUFFLED_IDLE_DETAIL else Copy.IDLE_HEADLINE to Copy.IDLE_DETAIL
        Listening.NOT_STARTED -> "잠시만요" to "소리를 들을 준비를 하고 있어요"
        Listening.NO_PERMISSION -> "소리를 듣지 못하고 있어요" to "마이크 권한을 허용해 주세요"
        Listening.MIC_ERROR -> "소리를 듣지 못하고 있어요" to "마이크를 사용할 수 없어요"
        Listening.DEMO ->
            if (scriptStarted && muffled) {
                Copy.MUFFLED_IDLE to Copy.MUFFLED_IDLE_DETAIL
            } else if (scriptStarted) {
                Copy.IDLE_HEADLINE to Copy.IDLE_DETAIL
            } else {
                "시연을 기다리고 있어요" to "설정에서 시연을 시작할 수 있어요"
            }
    }
    SoundCard(
        modifier = modifier,
        borderColor = if (failed) colors.siren else null,
        icon = {
            SoundIcon(
                paths = if (failed) LineIcons.warning else LineIcons.wave,
                tint = if (failed) colors.siren else colors.textSecondary,
                background = if (failed) colors.sirenIconBg else colors.surfaceHigh,
                description = null,
            )
        },
        words = {
            Column(verticalArrangement = Arrangement.spacedBy(6.fdp)) {
                Label(headline, if (failed) Tokens.headlineLarge else Tokens.titleLarge)
                Label(detail, Tokens.titleMedium, colors.textSecondary)
            }
        },
    )
}

/** What the current light rhythm means: the dots repeat it, and the words explain it. */
@Composable
fun RhythmLegend(rhythm: LightRhythm?) {
    val colors = LocalColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(12.fdp),
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(24.fdp))
            .padding(vertical = 20.fdp, horizontal = 24.fdp),
    ) {
        if (rhythm != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.fdp)) {
                repeat(Copy.rhythmDots(rhythm)) {
                    Spacer(Modifier.size(16.fdp).background(colors.accent(rhythm.accent()), CircleShape))
                }
            }
            Column {
                Label("실내 조명", Tokens.bodySmall, colors.textTertiary)
                Label(Copy.rhythmLegend(rhythm), Tokens.bodySmall, colors.textPrimary)
            }
        } else {
            Column {
                Label("실내 조명", Tokens.bodySmall, colors.textTertiary)
                Label("소리가 나면 함께 깜빡여요", Tokens.bodySmall, colors.textSecondary)
            }
        }
    }
}
