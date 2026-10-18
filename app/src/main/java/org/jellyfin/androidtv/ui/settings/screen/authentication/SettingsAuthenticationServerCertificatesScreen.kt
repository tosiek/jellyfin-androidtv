package org.jellyfin.androidtv.ui.settings.screen.authentication

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.tls.CertificateKind
import org.jellyfin.androidtv.tls.CertificatePromptDialogs
import org.jellyfin.androidtv.tls.CertificateSource
import org.jellyfin.androidtv.tls.CertificatesScreenModel
import org.jellyfin.androidtv.tls.ClientCertificate
import org.jellyfin.androidtv.tls.PickFile
import org.jellyfin.androidtv.tls.TlsEndpoint
import org.jellyfin.androidtv.tls.findActivity
import org.jellyfin.androidtv.tls.launchFilePicker
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.base.list.ListButton
import org.jellyfin.androidtv.ui.base.list.ListSection
import org.jellyfin.androidtv.ui.settings.composable.SettingsColumn
import org.koin.compose.koinInject
import java.util.UUID

@Composable
fun SettingsAuthenticationServerCertificatesScreen(serverId: UUID) {
	val serverRepository = koinInject<ServerRepository>()
	LaunchedEffect(serverRepository) { serverRepository.loadStoredServers() }
	val address by remember(serverRepository.storedServers) {
		serverRepository.storedServers.map { servers -> servers.find { it.id == serverId }?.address }
	}.collectAsState(null)

	val endpoint = remember(address) { TlsEndpoint.fromUrl(address) } ?: return
	CertificatesScreen(endpoint)
}

/**
 * The CA certificates and the client certificate of [endpoint].
 */
@Suppress("LongMethod")
@Composable
fun CertificatesScreen(endpoint: TlsEndpoint) {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val model = remember(endpoint) { CertificatesScreenModel(context, endpoint) }
	var picking by remember { mutableStateOf(CertificateKind.CA) }
	val picker = rememberLauncherForActivityResult(PickFile()) { uri ->
		if (uri != null) scope.launch { model.import(picking, CertificateSource.File(uri)) }
	}
	// Many TV devices have no file picker, load from a URL there
	val import = { kind: CertificateKind ->
		picking = kind
		if (!context.launchFilePicker(picker)) scope.launch { model.importFromUrl(kind) }
	}

	LaunchedEffect(model) { model.refresh() }
	CertificatePromptDialogs(model.prompts)

	SettingsColumn {
		item {
			ListSection(
				overlineContent = { Text(stringResource(R.string.tls_certificates).uppercase()) },
				headingContent = { Text(endpoint.display) },
			)
		}

		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.tls_ca_section)) },
				captionContent = { Text(stringResource(R.string.tls_ca_system_info, endpoint.display)) },
			)
		}
		if (model.cas.isEmpty()) {
			item { ListButton(onClick = {}, enabled = false, headingContent = { Text(stringResource(R.string.tls_ca_none)) }) }
		}
		items(model.cas) { certificate ->
			ListButton(
				onClick = { scope.launch { model.removeCa(certificate) } },
				headingContent = { Text(model.caText(certificate)) },
			)
		}
		item {
			ListButton(onClick = { import(CertificateKind.CA) }, headingContent = { Text(stringResource(R.string.tls_ca_import)) })
		}

		item {
			ListSection(
				headingContent = { Text(stringResource(R.string.tls_client_section)) },
				captionContent = if (model.clientRequested) {
					{ Text(stringResource(R.string.tls_client_requested)) }
				} else {
					null
				},
			)
		}
		model.client?.let { client ->
			item {
				ListButton(
					onClick = { scope.launch { model.removeClient(client) } },
					enabled = client.config != ClientCertificate.None,
					headingContent = { Text(model.clientText(client)) },
				)
			}
		}
		item {
			ListButton(onClick = { import(CertificateKind.CLIENT) }, headingContent = { Text(stringResource(R.string.tls_client_import)) })
		}
		if (model.systemStoreAvailable) {
			item {
				ListButton(
					onClick = { context.findActivity()?.let { scope.launch { model.chooseSystem(it) } } },
					headingContent = { Text(stringResource(R.string.tls_client_choose_system)) },
				)
			}
		}

		item {
			ListButton(
				onClick = { scope.launch { model.importFromUrl() } },
				headingContent = { Text(stringResource(R.string.tls_import_from_url)) },
			)
		}
	}
}
