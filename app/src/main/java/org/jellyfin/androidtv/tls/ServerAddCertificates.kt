package org.jellyfin.androidtv.tls

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.ui.platform.ComposeView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.ui.base.JellyfinTheme

/**
 * "Advanced" section of the server add screen with the certificates of the entered address, see [ServerCertificates].
 * Create it with the fragment, [attach] it to the views of the fragment.
 */
class ServerAddCertificates(private val fragment: Fragment) {
	private var certificates: ServerCertificates? = null
	private var section: ViewGroup? = null
	private var rows: CertificateRowsView? = null
	private val picker = fragment.registerForActivityResult(PickFile()) { uri -> certificates?.onFilePicked(uri) }
	private val prompts = PromptState()

	/**
	 * The dialogs are shown by a view added to [dialogHost], which has to stay visible.
	 */
	fun attach(toggle: View, section: ViewGroup, address: EditText, dialogHost: ViewGroup) {
		val context = fragment.requireContext()
		val certificates = ServerCertificates(
			fragment.requireActivity(),
			fragment.lifecycleScope,
			prompts,
			endpointOf = { TlsEndpoint.fromUrl(address.text.toString()) },
			onChanged = ::refresh,
		)
		val rows = CertificateRowsView(context).apply {
			// Many TV devices have no file picker, load from a URL there by default
			loadFromUrl = !context.hasFilePicker()
			onChoose = { kind, fromUrl -> if (certificates.choose(kind, fromUrl, picker)) loadFromUrl = true }
			onRemove = certificates::remove
			// Only the button, unlike the mobile app no chooser on its own when the server asks for a client certificate:
			// TV settings can not install keys, the store is mostly empty (only keys from apps or device management)
			if (certificates.systemStoreAvailable) onChooseSystem = { certificates.chooseSystem() }
		}
		section.addView(rows)
		dialogHost.addView(
			ComposeView(context).apply {
				setContent { JellyfinTheme { CertificatePromptDialogs(prompts) } }
			},
		)
		toggle.setOnClickListener { setVisible(section.visibility != View.VISIBLE) }
		address.doAfterTextChanged { refresh() }

		this.certificates = certificates
		this.section = section
		this.rows = rows
	}

	fun detach() {
		certificates = null
		section = null
		rows = null
	}

	/**
	 * Use the selection for the connection check. Returns the error when it can not be used.
	 */
	suspend fun apply(): String? = certificates?.apply()

	fun onConnected() {
		certificates?.onConnected()
	}

	/**
	 * Restore the previous certificates after a failed connection check. Clears the TLS diagnostics, describe the
	 * failure before.
	 */
	fun onFailed() {
		val certificates = certificates ?: return
		certificates.onFailed()
		if (certificates.endpoint != null) setVisible(true)
	}

	private fun setVisible(visible: Boolean) {
		section?.visibility = if (visible) View.VISIBLE else View.GONE
		if (visible) refresh()
	}

	private fun refresh() {
		val certificates = certificates ?: return
		val rows = rows ?: return
		fragment.viewLifecycleOwner.lifecycleScope.launch {
			val ca = certificates.caLabel()
			val client = certificates.clientLabel()
			rows.update(httpsAvailable = certificates.endpoint != null, ca = ca, client = client)
		}
	}
}
