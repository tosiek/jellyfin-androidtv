package org.jellyfin.androidtv.tls

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import org.jellyfin.androidtv.R

private const val ROW_TOP_PADDING_DP = 12
private const val ROW_TITLE_SIZE_SP = 16f

/**
 * Content of the "Advanced" section of the connect screen: CA and client certificate rows with buttons to choose or
 * remove a certificate (the client one also from the system store) and a checkbox to load it from a URL instead of a
 * file. Plain views for D-pad navigation.
 */
class CertificateRowsView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
	/** Choose a certificate, from a URL when [loadFromUrl] is checked, otherwise with the file picker. */
	var onChoose: (kind: CertificateKind, fromUrl: Boolean) -> Unit = { _, _ -> }
	var onRemove: (CertificateKind) -> Unit = {}

	/** Choose a client key from the system store, the button is only shown when set. */
	var onChooseSystem: (() -> Unit)? = null

	private val httpsOnly = text(context.getString(R.string.tls_https_only))
	private val loadFromUrlCheckBox = CheckBox(context).apply { text = context.getString(R.string.tls_load_from_url) }
	private val rows = CertificateKind.entries.associateWith { kind -> Row(kind) }

	var loadFromUrl: Boolean
		get() = loadFromUrlCheckBox.isChecked
		set(value) {
			loadFromUrlCheckBox.isChecked = value
		}

	init {
		orientation = VERTICAL
		addView(httpsOnly)
		addView(loadFromUrlCheckBox)
		// CA and client certificate side by side, the system store button below them across the width
		addView(LinearLayout(context).apply {
			orientation = HORIZONTAL
			rows.values.forEach { row -> addView(row.container, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)) }
		})
		rows.values.forEach { row -> row.chooseSystem?.let { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) } }

		// D-pad: the full-width checkbox and system store button are closer to the client row, down and up would skip
		// the CA row. Lead them to the CA row, the client row is to its right.
		val caChoose = rows.getValue(CertificateKind.CA).choose
		caChoose.id = generateViewId()
		loadFromUrlCheckBox.nextFocusDownId = caChoose.id
		rows.getValue(CertificateKind.CLIENT).chooseSystem?.nextFocusUpId = caChoose.id
	}

	fun update(httpsAvailable: Boolean, ca: CertificateLabel?, client: CertificateLabel?) {
		httpsOnly.visibility = if (httpsAvailable) View.GONE else View.VISIBLE
		loadFromUrlCheckBox.isEnabled = httpsAvailable
		rows.getValue(CertificateKind.CA).update(httpsAvailable, ca)
		rows.getValue(CertificateKind.CLIENT).update(httpsAvailable, client)
	}

	private inner class Row(kind: CertificateKind) {
		val value = text("")
		val choose = button(context.getString(R.string.tls_choose)) { onChoose(kind, loadFromUrl) }
		val remove = button(context.getString(R.string.tls_remove)) { onRemove(kind) }
		val buttons = listOf(choose, remove)

		// Own line below both rows, does not fit next to the other buttons
		val chooseSystem = if (kind == CertificateKind.CLIENT) {
			button(context.getString(R.string.tls_client_choose_system)) { onChooseSystem?.invoke() }
				.apply { visibility = View.GONE }
		} else {
			null
		}
		val container = LinearLayout(context).apply {
			orientation = VERTICAL
			setPadding(0, dp(ROW_TOP_PADDING_DP), 0, 0)
			val title = if (kind == CertificateKind.CA) R.string.tls_ca_certificate else R.string.tls_client_certificate
			addView(text(context.getString(title)).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, ROW_TITLE_SIZE_SP) })
			addView(value)
			addView(LinearLayout(context).apply {
				orientation = HORIZONTAL
				buttons.forEach(::addView)
			})
		}

		fun update(enabled: Boolean, label: CertificateLabel?) {
			value.text = when {
				label == null -> context.getString(R.string.tls_none)
				label.isNew -> "${label.name} ${context.getString(R.string.tls_new)}"
				label.unavailable -> "${label.name ?: context.getString(R.string.tls_client_imported_key)} " +
					context.getString(R.string.tls_unavailable)
				else -> label.name
			}
			buttons.forEach { it.isEnabled = enabled }
			remove.visibility = if (label != null) View.VISIBLE else View.GONE
			chooseSystem?.isEnabled = enabled
			chooseSystem?.visibility = if (onChooseSystem != null) View.VISIBLE else View.GONE
		}
	}

	private fun text(value: String) = TextView(context).apply { text = value }

	private fun button(label: String, onClick: () -> Unit) = Button(context).apply {
		text = label
		isAllCaps = false
		setOnClickListener { onClick() }
	}

	private fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
