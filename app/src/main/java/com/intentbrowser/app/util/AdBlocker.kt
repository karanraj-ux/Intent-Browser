package com.intentbrowser.app.util

import kotlinx.coroutines.launch
import kotlinx.coroutines.DelicateCoroutinesApi

object AdBlocker {
    private val AD_DOMAINS = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adsystem.com",
        "advertising.com",
        "scorecardresearch.com",
        "quantserve.com",
        "outbrain.com",
        "taboola.com",
        "criteo.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "rubiconproject.com",
        "openx.net",
        "adsrvr.org",
        "turn.com",
        "moatads.com",
        "adsafeprotected.com",
        "pubmatic.com",
        "yieldmanager.com",
        "revsci.net",
        "bluekai.com",
        "exelator.com",
        "krxd.net",
        "mathtag.com",
        "dotomi.com",
        "casalemedia.com",
        "serving-sys.com",
        "rlcdn.com",
        "imrworldwide.com",
        "agkn.com",
        "specificclick.net",
        "vindicosuite.com",
        "exponential.com",
        "tribalfusion.com",
        "zedo.com",
        "media.net",
        "bidswitch.net",
        "lijit.com",
        "sitescout.com",
        "betrad.com",
        "sharethis.com",
        "addthis.com",
        "tynt.com",
        "demdex.net",
        "tapad.com",
        "everesttech.net",
        "atdmt.com",
        "nexus.ensighten.com",
        "omtrdc.net",
        "2o7.net",
        "fls.doubleclick.net",
        "gstatic.com/ads",
        "facebook.com/tr",
        "bing.com/bat.js",
        "snap.licdn.com/li.lms-analytics",
        "tiktok.com/api/ad",
        "reddit.com/api/v1/telemetry",
        "t.co/i/ads",
        "ads-twitter.com",
        "analytics.twitter.com"
    )

    private val ADULT_DOMAINS = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "youporn.com",
        "redtube.com", "spankbang.com", "eporner.com", "chaturbate.com", "bongacams.com",
        "livejasmin.com", "xhamsterlive.com", "stripchat.com", "adultwork.com",
        "onlyfans.com", "fansly.com", "tube8.com", "keezmovies.com", "tnaflix.com",
        "drtuber.com", "heavy-r.com", "motherless.com", "slutload.com", "empflix.com"
    )
    
    private val ADULT_KEYWORDS = setOf(
        "porn", "sex", "xxx", "blowjob", "nude", "naked", "escort", "milf", "camgirl",
        "boobs", "tits", "pussy", "dick", "cock", "fuck", "hentai", "incest", "orgasm"
    )
    
    private val DISTRACTING_DOMAINS = setOf(
        "facebook.com", "instagram.com", "twitter.com", "x.com", "tiktok.com",
        "reddit.com", "snapchat.com", "pinterest.com", "tumblr.com", "9gag.com",
        "netflix.com", "hulu.com", "disneyplus.com", "twitch.tv"
    )

    private val dynamicBlockedDomains = mutableSetOf<String>()
    private val dynamicSubstringRules = mutableSetOf<String>()

    @OptIn(DelicateCoroutinesApi::class)
    fun init(context: android.content.Context) {
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // Fast path: a pre-parsed one-domain-per-line cache. Parsing the
                // 60k-line hosts file with regex splits on every cold start was slow.
                val parsedFile = java.io.File(context.filesDir, "hosts_parsed.txt")
                val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                if (parsedFile.exists() && parsedFile.lastModified() >= oneWeekAgo) {
                    val cached = mutableSetOf<String>()
                    parsedFile.forEachLine { line ->
                        val d = line.trim().lowercase()
                        if (d.isNotEmpty()) cached.add(d)
                    }
                    dynamicBlockedDomains.addAll(cached)
                    return@launch
                }

                val file = java.io.File(context.filesDir, "hosts.txt")
                val lastModified = file.lastModified()
                val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                
                if (!file.exists() || lastModified < oneWeekAgo) {
                    val url = java.net.URL("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts")
                    val connection = url.openConnection()
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000
                    connection.inputStream.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                if (file.exists()) {
                    val newDomains = mutableSetOf<String>()
                    file.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            val parts = trimmed.split("\\s+".toRegex())
                            if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                                val domain = parts[1].lowercase()
                                if (domain != "localhost" && domain != "localhost.localdomain" && domain != "local" && domain != "broadcasthost") {
                                    newDomains.add(domain)
                                }
                            }
                        }
                    }
                    dynamicBlockedDomains.addAll(newDomains)
                    // Write the fast cache for next launch.
                    try {
                        java.io.File(context.filesDir, "hosts_parsed.txt").bufferedWriter().use { w ->
                            newDomains.forEach { w.write(it); w.newLine() }
                        }
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Domain-only match for MAIN-FRAME navigations. Path heuristics are deliberately
     * excluded here: blocking a whole page because its URL contains "/track" or
     * "analytics.js" (e.g. a tutorial about analytics.js) is a false positive.
     */
    fun isBlockedDomain(url: String): Boolean {
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
            var currentHost = host
            while (currentHost.isNotEmpty()) {
                if (AD_DOMAINS.contains(currentHost) || dynamicBlockedDomains.contains(currentHost)) {
                    return true
                }
                val dotIndex = currentHost.indexOf('.')
                if (dotIndex == -1) break
                currentHost = currentHost.substring(dotIndex + 1)
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    /** Domain match for adult sites (main-frame). Keyword matching is subresource-only
     *  to avoid false positives like "sussex.ac.uk". */
    fun isAdultDomain(url: String): Boolean {
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
            ADULT_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }
        } catch (e: Exception) {
            false
        }
    }

    fun isAdOrTracker(url: String): Boolean {
        // Subresources: domain match + path heuristics.
        if (isBlockedDomain(url)) return true
        return try {
            val uri = android.net.Uri.parse(url)
            // Substring rules (limit checks to prevent jank on huge lists)
            val lowerUrl = url.lowercase()
            var checks = 0
            for (rule in dynamicSubstringRules) {
                if (lowerUrl.contains(rule)) return true
                checks++
                if (checks > 2000) break
            }

            // Check common path patterns for trackers
            val path = uri.path?.lowercase() ?: ""
            if (path.contains("/ad/") || path.contains("/track") || path.contains("analytics.js") || path.contains("pixel.gif")) return true

            false
        } catch (e: Exception) {
            false
        }
    }

    fun isAdultContent(url: String): Boolean {
        return try {
            val lowerUrl = url.lowercase()
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
            
            if (ADULT_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }) {
                return true
            }
            
            val path = android.net.Uri.parse(url).path?.lowercase() ?: ""
            val query = android.net.Uri.parse(url).query?.lowercase() ?: ""
            val fullText = host + path + query
            
            var keywordMatchCount = 0
            for (keyword in ADULT_KEYWORDS) {
                if (fullText.contains(keyword)) {
                    keywordMatchCount++
                    if (keywordMatchCount >= 2) return true
                    if (host.contains(keyword) && keyword != "sex") { 
                         return true
                    }
                }
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }
    
    
    fun isHeavyMedia(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".avi") || lowerUrl.endsWith(".webm") ||
               lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".mov") || lowerUrl.endsWith(".mkv") ||
               lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".ts") || lowerUrl.endsWith(".mp3") ||
               lowerUrl.contains("youtube.com/embed/") || lowerUrl.contains("player.vimeo.com") ||
               lowerUrl.contains("youtube.com/api/") || lowerUrl.contains("video.")
    }
}
