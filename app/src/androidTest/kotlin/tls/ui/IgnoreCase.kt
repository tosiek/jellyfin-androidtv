package org.jellyfin.androidtv.tls.ui

import android.view.View
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

/** Text matches of the UI tests ignore case: "Requires a client certificate" and "requires a client certificate" are the same. */
fun withTextIgnoringCase(text: String): Matcher<View> = withText(textMatcher("equal to", text) { it.equals(text, ignoreCase = true) })

fun withTextContainingIgnoringCase(text: String): Matcher<View> =
	withText(textMatcher("containing", text) { it.contains(text, ignoreCase = true) })

private fun textMatcher(
	what: String,
	expected: String,
	matches: (String) -> Boolean,
): Matcher<String> = object : TypeSafeMatcher<String>() {
	override fun matchesSafely(item: String) = matches(item)
	override fun describeTo(description: Description) {
		description.appendText("a string $what ").appendValue(expected).appendText(" ignoring case")
	}
}
