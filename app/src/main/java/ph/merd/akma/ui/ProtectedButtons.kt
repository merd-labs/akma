package ph.merd.akma.ui

import android.content.Context
import android.widget.Button
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ph.merd.akma.ui.components.ButtonVariant

/**
 * Hosts an obscured-touch-safe native button (confirmationButton / copyButton from
 * ReplyPresentation) in Compose, styled like the Figma Button. The factory's touch filtering is kept;
 * only appearance, label and enabled state change here.
 */
@Composable
internal fun ProtectedAkmaButton(
    factory: (Context) -> Button,
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Primary,
    @DrawableRes icon: Int? = null,
) {
    AndroidView(
        factory = { context -> factory(context) },
        update = { button ->
            button.text = text
            button.contentDescription = text
            button.isEnabled = enabled
            button.akmaButton(variant, icon)
            button.setOnClickListener { onClick() }
        },
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
    )
}
