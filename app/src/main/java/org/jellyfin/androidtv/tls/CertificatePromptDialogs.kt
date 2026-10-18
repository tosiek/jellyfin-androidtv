package org.jellyfin.androidtv.tls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.JellyfinTheme
import org.jellyfin.androidtv.ui.base.LocalShapes
import org.jellyfin.androidtv.ui.base.LocalTextStyle
import org.jellyfin.androidtv.ui.base.ProvideTextStyle
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.dialog.DialogBase
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.design.Tokens

/**
 * Shows the current question of [state] as a dialog.
 */
@Composable
fun CertificatePromptDialogs(state: PromptState) {
	when (val prompt = state.current) {
		is Prompt.Url -> TextInputDialog(
			title = stringResource(R.string.tls_import_from_url),
			hint = stringResource(R.string.tls_import_url_hint),
			keyboardType = KeyboardType.Uri,
			onDone = { prompt.answer(it.trim().ifEmpty { null }) },
			onDismiss = { prompt.answer(null) },
		)
		is Prompt.Password -> TextInputDialog(
			title = prompt.title,
			hint = stringResource(R.string.tls_password),
			keyboardType = KeyboardType.Password,
			onDone = { prompt.answer(it.toCharArray()) },
			onDismiss = { prompt.answer(null) },
		)
		is Prompt.Choice -> PromptDialog(title = prompt.title, onDismiss = { prompt.answer(null) }) {
			prompt.items.forEachIndexed { index, item ->
				ListButton(onClick = { prompt.answer(index) }, headingContent = { Text(item) })
			}
			ListButton(onClick = { prompt.answer(null) }, headingContent = { Text(stringResource(android.R.string.cancel)) })
		}
		is Prompt.Confirm -> ConfirmDialog(prompt)
		is Prompt.Message -> PromptDialog(title = null, message = prompt.text, onDismiss = { prompt.answer(Unit) }) {
			ListButton(onClick = { prompt.answer(Unit) }, headingContent = { Text(stringResource(android.R.string.ok)) })
		}
		null -> Unit
	}
}

@Composable
private fun ConfirmDialog(prompt: Prompt.Confirm) {
	val confirmation = prompt.confirmation
	val dismissFocus = remember { FocusRequester() }
	PromptDialog(title = confirmation.title, message = confirmation.message, onDismiss = { prompt.answer(false) }) {
		ListButton(onClick = { prompt.answer(true) }, headingContent = { Text(confirmation.confirm) })
		ListButton(
			onClick = { prompt.answer(false) },
			headingContent = { Text(confirmation.dismiss) },
			modifier = Modifier.focusRequester(dismissFocus),
		)
	}
	if (confirmation.destructive) LaunchedEffect(prompt) { dismissFocus.requestFocus() }
}

@Composable
private fun TextInputDialog(
	title: String,
	hint: String,
	keyboardType: KeyboardType,
	onDone: (String) -> Unit,
	onDismiss: () -> Unit,
) {
	var text by remember { mutableStateOf("") }
	val focusRequester = remember { FocusRequester() }
	PromptDialog(title = title, onDismiss = onDismiss) {
		TextInput(
			value = text,
			onValueChange = { text = it },
			hint = hint,
			keyboardType = keyboardType,
			onSubmit = { onDone(text) },
			modifier = Modifier.focusRequester(focusRequester),
		)
		ListButton(onClick = { onDone(text) }, headingContent = { Text(stringResource(android.R.string.ok)) })
		ListButton(onClick = onDismiss, headingContent = { Text(stringResource(android.R.string.cancel)) })
	}
	LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
}

@Composable
private fun PromptDialog(
	title: String?,
	onDismiss: () -> Unit,
	message: String? = null,
	content: @Composable () -> Unit,
) {
	DialogBase(visible = true, onDismissRequest = onDismiss) {
		Column(
			modifier = Modifier
				.padding(Tokens.Space.spaceMd)
				.clip(LocalShapes.current.large)
				.background(JellyfinTheme.colorScheme.surface)
				.width(480.dp)
				.padding(Tokens.Space.spaceSm)
				.verticalScroll(rememberScrollState()),
			verticalArrangement = Arrangement.spacedBy(Tokens.Space.spaceXs),
		) {
			ListSection(
				headingContent = { Text(title.orEmpty()) },
				captionContent = message?.let { { Text(it) } },
			)
			content()
		}
	}
}

@Suppress("LongParameterList")
@Composable
private fun TextInput(
	value: String,
	onValueChange: (String) -> Unit,
	hint: String,
	keyboardType: KeyboardType,
	onSubmit: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionSource = remember { MutableInteractionSource() }
	val focused by interactionSource.collectIsFocusedAsState()
	val focusManager = LocalFocusManager.current
	val color = when {
		focused -> JellyfinTheme.colorScheme.inputFocused to JellyfinTheme.colorScheme.onInputFocused
		else -> JellyfinTheme.colorScheme.input to JellyfinTheme.colorScheme.onInput
	}

	ProvideTextStyle(LocalTextStyle.current.copy(color = color.second, fontSize = 16.sp)) {
		BasicTextField(
			value = value,
			onValueChange = onValueChange,
			// The text field keeps the D-pad keys, up and down leave it for the buttons of the dialog
			modifier = modifier
				.fillMaxWidth()
				.onPreviewKeyEvent { event ->
					when {
						event.type != KeyEventType.KeyDown -> false
						event.key == Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
						event.key == Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
						else -> false
					}
				},
			singleLine = true,
			interactionSource = interactionSource,
			keyboardActions = KeyboardActions { onSubmit() },
			keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
			visualTransformation = if (keyboardType == KeyboardType.Password) PasswordVisualTransformation() else VisualTransformation.None,
			textStyle = LocalTextStyle.current,
			cursorBrush = SolidColor(color.first),
			decorationBox = { innerTextField ->
				Box(
					modifier = Modifier
						.border(2.dp, color.first, RoundedCornerShape(percent = 30))
						.padding(12.dp),
				) {
					if (value.isEmpty()) Text(hint, color = color.second.copy(alpha = HINT_ALPHA))
					innerTextField()
				}
			},
		)
	}
}

private const val HINT_ALPHA = 0.6f
