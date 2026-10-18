package org.jellyfin.androidtv.tls.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
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
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import junit.framework.AssertionFailedError
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.tls.ClientCertificate
import org.jellyfin.androidtv.tls.TlsEndpoint
import org.jellyfin.androidtv.tls.TlsManager
import org.jellyfin.androidtv.tls.TlsServerConfig
import org.jellyfin.androidtv.tls.hasFilePicker
import org.jellyfin.androidtv.ui.startup.fragment.ServerAddFragment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * GUI tests of the "Advanced" certificates section of the server add screen. The connection check runs against a
 * local HTTPS server answering like Jellyfin, see [FakeJellyfinServer].
 */
class ServerAddAdvancedTests {
	@get:Rule
	val compose = createEmptyComposeRule()

	private val instrumentation = InstrumentationRegistry.getInstrumentation()
	private val context = instrumentation.targetContext
	private val tls = TlsManager.get(context)
	private val dialogs = PromptDialogs(compose)

	private lateinit var fileServer: TestFileServer
	private var server: FakeJellyfinServer? = null
	private var scenario: FragmentScenario<ServerAddFragment>? = null
	private val endpoints = mutableListOf<TlsEndpoint>()

	@Before
	fun setUp() {
		fileServer = TestFileServer(::read)
		Intents.init()
		scenario = launchFragmentInContainer<ServerAddFragment>(themeResId = R.style.Theme_Jellyfin).onFragment { fragment ->
			// The fragment opens the next screen in R.id.content_view after a successful connection
			val root = fragment.requireActivity().findViewById<ViewGroup>(android.R.id.content)
			root.addView(FrameLayout(fragment.requireContext()).apply { id = R.id.content_view })
		}
	}

	@After
	fun tearDown() {
		scenario?.close()
		Intents.release()
		server?.close()
		fileServer.close()
		endpoints.forEach { tls.setConfig(it, TlsServerConfig()) }
	}

	@Test
	fun httpAddressExplainsHttpsOnly() {
		enterAddress("http://jellyfin.example")
		openAdvanced()
		onView(withTextIgnoringCase(string(R.string.tls_https_only))).check(matches(isDisplayed()))
		onView(chooseButton(R.string.tls_ca_certificate)).check(matches(not(isEnabled())))
	}

	@Test
	fun stageCertificatesFromFileAndUrl() {
		enterAddress("https://staging.example:8920")
		openAdvanced()

		// Checkbox unchecked: document picker (TV devices without one: the URL part only)
		if (context.hasFilePicker()) {
			setLoadFromUrl(false)
			stubDocumentPicker("ca/single.pem")
			onView(chooseButton(R.string.tls_ca_certificate)).perform(scrollTo(), click())
			waitForStaged("single.pem ${string(R.string.tls_new)}")
		}

		// Checkbox checked: URL, wrong password first
		setLoadFromUrl(true)
		onView(chooseButton(R.string.tls_client_certificate)).perform(scrollTo(), click())
		enterUrl("client/ec256-3des.p12")
		enterPassword("wrong")
		dialogs.expect(string(R.string.tls_password_retry, "ec256-3des.p12"))
		enterPassword("jellyfin")
		skipContainedCas()
		waitForStaged("ec256-3des.p12 ${string(R.string.tls_new)}")

		// Nothing is stored before a successful connection
		assertTrue(tls.getConfig(TlsEndpoint.fromUrl("https://staging.example:8920")!!).isEmpty)
	}

	@Test
	fun successfulConnectionKeepsCertificates() {
		val server = FakeJellyfinServer(::read, requireClientCertificate = true).also { server = it }
		val endpoint = TlsEndpoint.fromUrl(server.address)!!.also(endpoints::add)
		enterAddress(server.address)
		openAdvanced()
		setLoadFromUrl(true)
		stageFromUrl(R.string.tls_ca_certificate, "ca/single.pem")
		stageFromUrl(R.string.tls_client_certificate, "client/ec256-3des.p12", password = "jellyfin")

		onView(withId(R.id.confirm)).perform(click())

		eventually { assertTrue(tls.getConfig(endpoint).clientCertificate is ClientCertificate.Imported) }
		assertEquals(1, tls.getConfig(endpoint).caFingerprints.size)
	}

	@Test
	fun failedConnectionRestoresAndExplains() {
		val server = FakeJellyfinServer(::read, requireClientCertificate = true).also { server = it }
		val endpoint = TlsEndpoint.fromUrl(server.address)!!.also(endpoints::add)
		enterAddress(server.address)
		openAdvanced()
		setLoadFromUrl(true)
		// Trusted server but no client certificate
		stageFromUrl(R.string.tls_ca_certificate, "ca/single.pem")

		onView(withId(R.id.confirm)).perform(click())

		eventually(30_000) { onView(withId(R.id.error)).check(matches(withTextContainingIgnoringCase("requires a client certificate"))) }
		assertTrue(tls.getConfig(endpoint).isEmpty)
		// Selection kept for another try, section expanded
		onView(withTextIgnoringCase("single.pem ${string(R.string.tls_new)}")).perform(scrollTo()).check(matches(isDisplayed()))
	}

	// Helpers

	private fun enterAddress(address: String) {
		onView(withId(R.id.address)).perform(replaceText(address), closeSoftKeyboard())
	}

	private fun openAdvanced() {
		onView(withId(R.id.advanced)).perform(click())
		onView(withTextIgnoringCase(string(R.string.tls_load_from_url))).check(matches(isDisplayed()))
	}

	private fun setLoadFromUrl(checked: Boolean) {
		val checkBox = withTextIgnoringCase(string(R.string.tls_load_from_url))
		val wrongState = runCatching { onView(checkBox).check(matches(if (checked) isNotChecked() else isChecked())) }.isSuccess
		if (wrongState) onView(checkBox).perform(scrollTo(), click())
	}

	private fun stageFromUrl(row: Int, path: String, password: String? = null) {
		onView(chooseButton(row)).perform(scrollTo(), click())
		enterUrl(path)
		password?.let {
			enterPassword(it)
			skipContainedCas()
		}
		waitForStaged("${path.substringAfterLast('/')} ${string(R.string.tls_new)}")
	}

	private fun waitForStaged(label: String) = eventually {
		onView(withTextIgnoringCase(label)).perform(scrollTo()).check(matches(isDisplayed()))
	}

	/** A client .p12 with its CA: the app offers to trust that CA too, not needed here. */
	private fun skipContainedCas() = dialogs.click(string(R.string.tls_contained_ca_skip))

	private fun enterUrl(path: String) = dialogs.enter(fileServer.url(path))

	private fun enterPassword(password: String) = dialogs.enter(password)

	// The "Choose…" button in the row of the CA or client certificate
	private fun chooseButton(row: Int): Matcher<View> {
		val other = if (row == R.string.tls_ca_certificate) R.string.tls_client_certificate else R.string.tls_ca_certificate
		return allOf(
			withTextIgnoringCase(string(R.string.tls_choose)),
			isDescendantOfA(allOf(isAssignableFrom(LinearLayout::class.java), hasDescendant(withTextIgnoringCase(string(row))),
				not(hasDescendant(withTextIgnoringCase(string(other)))))),
		)
	}

	private fun stubDocumentPicker(path: String) {
		val file = File(context.cacheDir, path.substringAfterLast('/')).apply { writeBytes(read(path)) }
		intending(hasAction(Intent.ACTION_OPEN_DOCUMENT))
			.respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(Uri.fromFile(file))))
	}

	private fun string(id: Int, vararg args: Any) = context.getString(id, *args)

	private fun read(path: String) = instrumentation.context.assets.open("tls/$path").use { it.readBytes() }

	private fun eventually(timeoutMs: Long = 15_000, block: () -> Unit) {
		val deadline = System.currentTimeMillis() + timeoutMs
		while (true) {
			try {
				block()
				return
			} catch (err: Throwable) {
				val retryable = err is NoMatchingViewException || err is NoMatchingRootException || err is AssertionFailedError ||
					err is AssertionError || err is PerformException
				if (!retryable || System.currentTimeMillis() > deadline) throw err
				Thread.sleep(200)
			}
		}
	}
}
