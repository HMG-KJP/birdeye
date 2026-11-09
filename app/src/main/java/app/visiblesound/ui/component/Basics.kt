package app.visiblesound.ui.component

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.visiblesound.ui.theme.ButtonToken
import app.visiblesound.ui.theme.LocalColors
import app.visiblesound.ui.theme.TextToken
import app.visiblesound.ui.theme.Tokens
import app.visiblesound.ui.theme.dp as tokenDp
import app.visiblesound.ui.theme.sp as tokenSp

@Composable
private fun TextToken.asStyle(color: Color) = TextStyle(
    fontFamily = Tokens.font,
    fontSize = size.tokenSp,
    fontWeight = weight,
    color = color,
    lineHeight = lineHeight.tokenSp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** Text in the app typeface. Style names follow the guide: headline, title, body, label. */
@Composable
fun Label(
    text: String,
    style: TextToken,
    color: Color = LocalColors.current.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    BasicText(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        style = style.asStyle(color),
    )
}

/** Outlined single-color icons, kept as path data on the guide's 24-unit grid. */
object LineIcons {
    val wave = listOf("M4 10v4M8 7v10M12 4v16M16 7v10M20 10v4")
    val siren = listOf("M7 18v-6a5 5 0 0 1 10 0v6", "M5 21h14v-3H5z", "M12 3v2M4.2 6.2l1.4 1.4M19.8 6.2l-1.4 1.4")
    val horn = listOf("M4 10v4h3l6 4V6L7 10z", "M16 9.5a3.5 3.5 0 0 1 0 5M18.5 7a7 7 0 0 1 0 10")
    val warning = listOf("M12 4l9 16H3z", "M12 10v4M12 17v0.5")
    val car = listOf("M5 17v-4l2-6h10l2 6v4z", "M5 13h14", "M7 17v2M17 17v2")
    val weather = listOf("M7 15a4 4 0 0 1-.5-8A5.5 5.5 0 0 1 17 7.5a3.8 3.8 0 0 1 0 7.5z", "M9 18l-1 3M13 18l-1 3M17 18l-1 3")
    val chat = listOf("M4 5h16v11H9l-5 4z", "M8 10h8M8 13h5")
    val window = listOf("M4 20V8l6-4h10v16z", "M4 12h16")
    val outside = listOf("M3 12h4M17 12h4M12 3v4M12 17v4", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6")
    val clock = listOf("M12 3a9 9 0 1 0 0 18a9 9 0 1 0 0-18", "M12 7v5l3 2")
    val plus = listOf("M12 5v14M5 12h14")
    val close = listOf("M6 6l12 12M18 6L6 18")
    val settings = listOf("M4 8h9M17 8h3M4 16h3M11 16h9", "M15 6a2 2 0 1 0 0 4a2 2 0 1 0 0-4", "M9 14a2 2 0 1 0 0 4a2 2 0 1 0 0-4")
}

@Composable
fun LineIcon(
    paths: List<String>,
    color: Color,
    size: Dp,
    strokeWidth: Float = Tokens.ICON_STROKE,
    contentDescription: String? = null,
) {
    val vector = remember(paths, color, strokeWidth) {
        ImageVector.Builder(
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { path ->
                addPath(
                    pathData = PathParser().parsePathString(path).toNodes(),
                    stroke = SolidColor(color),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }
    Image(rememberVectorPainter(vector), contentDescription, Modifier.size(size))
}

/** BASIC and FILLED are the guide's Primary and Secondary; the accents are the app's main actions. */
enum class ButtonKind { BASIC, FILLED, ACCENT_TALK, ACCENT_OUTSIDE }

/** The guide's Basic button, with its Pressed, Disabled and Loading states. */
@Composable
fun BasicButton(
    text: String,
    onClick: () -> Unit,
    kind: ButtonKind = ButtonKind.BASIC,
    size: ButtonToken = Tokens.buttonMedium,
    icon: List<String>? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
    loadingDescription: String = "처리 중",
    modifier: Modifier = Modifier,
) {
    val colors = LocalColors.current
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val disabled = !enabled && !loading
    val accent = kind == ButtonKind.ACCENT_TALK || kind == ButtonKind.ACCENT_OUTSIDE
    val shape = RoundedCornerShape(size.radius.tokenDp)
    val container = when {
        disabled -> colors.buttonBasicDisabled
        kind == ButtonKind.BASIC -> if (pressed) colors.buttonBasicPressed else colors.buttonBasic
        kind == ButtonKind.FILLED -> if (pressed) colors.buttonFilledPressed else colors.buttonFilled
        kind == ButtonKind.ACCENT_TALK -> colors.talk
        else -> colors.outside
    }
    val content = when {
        disabled -> colors.textDisabled
        kind == ButtonKind.BASIC -> colors.textPrimary
        kind == ButtonKind.FILLED -> colors.onFilled
        kind == ButtonKind.ACCENT_TALK -> colors.onTalk
        else -> colors.onOutside
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(size.gap.tokenDp, Alignment.CenterHorizontally),
        modifier = modifier
            .defaultMinSize(minWidth = size.minWidth.tokenDp, minHeight = size.height.tokenDp)
            .clip(shape)
            .background(container)
            .let { if (pressed && !disabled && accent) it.background(colors.buttonBasicPressed) else it }
            .let { if (disabled) it.border(Tokens.buttonBorderDisabled.tokenDp, colors.dividerStrong, shape) else it }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                role = Role.Button,
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
            .padding(horizontal = size.paddingH.tokenDp),
    ) {
        when {
            loading -> Loader(content, size.icon.tokenDp, loadingDescription)
            icon != null -> LineIcon(icon, content, size.icon.tokenDp)
        }
        Label(text, size.label, content, maxLines = 1)
    }
}

/** The guide's Text field, Small size. */
@Composable
fun TextFieldBox(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalColors.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(Tokens.fieldRadius.tokenDp)
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(Tokens.fieldHeight.tokenDp)
            .background(colors.fieldBackground, shape)
            .border(
                if (focused) Tokens.fieldBorderFocused.tokenDp else Tokens.fieldBorder.tokenDp,
                if (focused) colors.fieldFocused else colors.dividerStrong,
                shape,
            )
            .padding(horizontal = Tokens.fieldPaddingH.tokenDp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            interactionSource = interaction,
            cursorBrush = SolidColor(colors.textPrimary),
            textStyle = Tokens.bodyLarge.asStyle(colors.textPrimary),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
        )
    }
}
