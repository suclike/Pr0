package com.pr0gramm.app.ui

import android.net.Uri
import android.text.style.URLSpan
import android.view.View
import com.pr0gramm.app.Settings
import com.pr0gramm.app.util.CustomTabsHelper

/**
 */
class PrivateBrowserSpan(url: String) : URLSpan(url) {
    override fun onClick(widget: View) {
        val url = url

        val settings = Settings.get()
        var useIncognitoBrowser = settings.useIncognitoBrowser

        // check if youtube-links should be opened in normal app
        if (useIncognitoBrowser && settings.overrideYouTubeLinks) {
            val host = Uri.parse(url).host
            if (host != null && BLACKLIST.contains(host.toLowerCase()))
                useIncognitoBrowser = false
        }

        if (useIncognitoBrowser) {
            CustomTabsHelper.newWebviewBuilder(widget.context).show(url)
        } else {
            // dispatch link normally
            super.onClick(widget)
        }
    }

    companion object {
        private val BLACKLIST = listOf(
                "youtube.com", "youtu.be", "www.youtube.com", "m.youtube.com",
                "vimeo.com")
    }
}
