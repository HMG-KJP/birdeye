package app.visiblesound.settings

import android.content.Context
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import app.visiblesound.core.Copy
import app.visiblesound.source.demo.DemoScene
import app.visiblesound.ui.component.BasicButton
import app.visiblesound.ui.component.ButtonKind
import app.visiblesound.ui.component.Label
import app.visiblesound.ui.component.LineIcons
import app.visiblesound.ui.component.PageNavigation
import app.visiblesound.ui.component.TextFieldBox
import app.visiblesound.ui.theme.LocalColors
import app.visiblesound.ui.theme.Tokens
import app.visiblesound.ui.theme.fdp

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var demoMode: Boolean
        get() = prefs.getBoolean(KEY_DEMO, false)
        set(value) = prefs.edit().putBoolean(KEY_DEMO, value).apply()

    var replies: List<String>
        get() = prefs.getString(KEY_REPLIES, null)?.split(SEPARATOR) ?: Copy.DEFAULT_REPLIES
        set(value) = prefs.edit().putString(KEY_REPLIES, value.joinToString(SEPARATOR)).apply()

    private companion object {
        const val KEY_DEMO = "demo_mode"
        const val KEY_REPLIES = "replies"
        const val SEPARATOR = "\n"
    }
}

/** A Depth page in the guide's terms. It opens only while parked, since it has free text inputs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    demoMode: Boolean,
    replies: List<String>,
    onDemoMode: (Boolean) -> Unit = {},
    onPlayDemo: (DemoScene) -> Unit = {},
    onSaveReplies: (List<String>) -> Unit = {},
    onClose: () -> Unit = {},
) {
    val colors = LocalColors.current
    val view = LocalView.current
    val edits = remember(replies) { mutableStateListOf(*replies.toTypedArray()) }
    Column(
        verticalArrangement = Arrangement.spacedBy(24.fdp),
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.fdp, horizontal = 32.fdp),
    ) {
        PageNavigation(
            icon = LineIcons.settings,
            iconColor = colors.textPrimary,
            title = "설정",
            subtitle = null,
            suffix = { BasicButton("닫기", onClose, size = Tokens.buttonSmall, icon = LineIcons.close) },
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(20.fdp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(24.fdp))
                .padding(vertical = 20.fdp, horizontal = 28.fdp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.fdp),
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = demoMode, role = Role.Switch) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onDemoMode(it)
                    },
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Label("데모 모드", Tokens.bodyLarge.withWeight(FontWeight.Bold))
                    Label("차량·음성 입력 대신 시연 장면으로 움직입니다", Tokens.bodySmall, colors.textTertiary)
                }
                Switch(demoMode)
            }
            if (demoMode) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(14.fdp),
                    verticalArrangement = Arrangement.spacedBy(14.fdp),
                ) {
                    DemoScene.entries.forEach { scene ->
                        BasicButton("${scene.label} 재생", { onPlayDemo(scene) }, kind = ButtonKind.ACCENT_TALK)
                    }
                }
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(14.fdp),
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(24.fdp))
                .padding(vertical = 20.fdp, horizontal = 28.fdp),
        ) {
            Label("대신 말하기 답장", Tokens.bodyLarge.withWeight(FontWeight.Bold))
            edits.forEachIndexed { index, value ->
                TextFieldBox(
                    value,
                    { edits[index] = it.replace("\n", " ") },
                    "답장 ${index + 1}",
                    Modifier.fillMaxWidth(),
                )
            }
            BasicButton(
                "답장 저장",
                onClick = { onSaveReplies(edits.mapIndexed { i, text -> text.trim().ifEmpty { replies[i] } }) },
                kind = ButtonKind.FILLED,
            )
        }
    }
}

/** The guide's Switch at Medium size. */
@Composable
private fun Switch(on: Boolean) {
    val colors = LocalColors.current
    Box(
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .width(80.fdp)
            .height(48.fdp)
            .background(if (on) colors.switchOn else colors.switchOff, RoundedCornerShape(24.fdp))
            .padding(4.fdp),
    ) {
        Spacer(Modifier.size(40.fdp).background(if (on) colors.knobOn else colors.knobOff, CircleShape))
    }
}
