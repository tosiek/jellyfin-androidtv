package org.jellyfin.androidtv.tls.ui

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingRootException
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.auth.repository.ServerRepository
import org.jellyfin.androidtv.tls.ClientCertificate
import org.jellyfin.androidtv.tls.TlsEndpoint
import org.jellyfin.androidtv.tls.TlsManager
import org.jellyfin.androidtv.tls.TlsServerConfig
import org.jellyfin.androidtv.ui.startup.fragment.ServerAddFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext

/**
 * UI end-to-end test of the server add screen against a real Jellyfin server behind the mTLS proxy
 * (tls-test-proxy). Skipped unless e2eUser and e2ePassword are passed, see JellyfinProxyE2ETests for all arguments.
 */
class ServerAddE2ETests {
	@get:Rule
	val compose = createEmptyComposeRule()

	private val instrumentation = InstrumentationRegistry.getInstrumentation()
	private val arguments = InstrumentationRegistry.getArguments()
	private val context = instrumentation.targetContext
	private val tls = TlsManager.get(context)
	private val dialogs = PromptDialogs(compose)

	private val server = arguments.getString("e2eServer") ?: "https://10.0.2.2:8920"
	private val files = arguments.getString("e2eFiles") ?: "http://10.0.2.2:8930"
	private val p12Password = arguments.getString("e2eP12Password") ?: "jellyfin"
	private val endpoint = requireNotNull(TlsEndpoint.fromUrl(server))
	private var scenario: FragmentScenario<ServerAddFragment>? = null

	@Before
	fun setUp() {
		assumeTrue("Pass e2eUser and e2ePassword to run the end-to-end tests", arguments.getString("e2eUser") != null)
		tls.setConfig(endpoint, TlsServerConfig())
		scenario = launchFragmentInContainer<ServerAddFragment>(themeResId = R.style.Theme_Jellyfin).onFragment { fragment ->
			val root = fragment.requireActivity().findViewById<ViewGroup>(android.R.id.content)
			root.addView(FrameLayout(fragment.requireContext()).apply { id = R.id.content_view })
		}
	}

	@After
	fun tearDown() {
		scenario?.close()
		tls.setConfig(endpoint, TlsServerConfig())
	}

	@Test
	fun connectWithCertificatesFromUrl() {
		onView(withId(R.id.address)).perform(replaceText(server), closeSoftKeyboard())
		onView(withId(R.id.advanced)).perform(click())
		val checkBox = withTextIgnoringCase(string(R.string.tls_load_from_url))
		if (runCatching { onView(checkBox).check(matches(isNotChecked())) }.isSuccess) onView(checkBox).perform(scrollTo(), click())
		onView(checkBox).check(matches(isChecked()))

		stageFromUrl(R.string.tls_ca_certificate, "ca.pem")
		stageFromUrl(R.string.tls_client_certificate, "client-ec.p12", p12Password)

		onView(withId(R.id.confirm)).perform(click())

		// Connected: certificates kept and the server is stored
		eventually(60_000) { assertTrue(tls.getConfig(endpoint).clientCertificate is ClientCertificate.Imported) }
		assertEquals(1, tls.getConfig(endpoint).caFingerprints.size)
		val repository = GlobalContext.get().get<ServerRepository>()
		eventually { assertTrue("Server not stored", repository.storedServers.value.any { it.address.trimEnd('/') == server }) }
	}

	// Helpers

	private fun stageFromUrl(row: Int, file: String, password: String? = null) {
		onView(chooseButton(row)).perform(scrollTo(), click())
		dialogs.enter("$files/$file")
		if (password != null) {
			dialogs.enter(password)
			// The client .p12 also contains its CA: not the proxy's, not needed
			dialogs.click(string(R.string.tls_contained_ca_skip))
		}
		eventually { onView(withTextIgnoringCase("$file ${string(R.string.tls_new)}")).check(matches(isDisplayed())) }
	}

	private fun chooseButton(row: Int): Matcher<View> {
		val other = if (row == R.string.tls_ca_certificate) R.string.tls_client_certificate else R.string.tls_ca_certificate
		return allOf(
			withTextIgnoringCase(string(R.string.tls_choose)),
			isDescendantOfA(allOf(isAssignableFrom(LinearLayout::class.java), hasDescendant(withTextIgnoringCase(string(row))),
				not(hasDescendant(withTextIgnoringCase(string(other)))))),
		)
	}

	private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

	private fun eventually(timeoutMs: Long = 20_000, block: () -> Unit) {
		val deadline = System.currentTimeMillis() + timeoutMs
		while (true) {
			try {
				block()
				return
			} catch (err: Throwable) {
				val retryable = err is NoMatchingViewException || err is NoMatchingRootException || err is AssertionFailedError ||
					err is AssertionError || err is PerformException
				if (!retryable || System.currentTimeMillis() > deadline) throw err
				Thread.sleep(250)
			}
		}
	}
}
