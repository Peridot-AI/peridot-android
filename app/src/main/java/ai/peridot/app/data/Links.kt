package ai.peridot.app.data

import ai.peridot.app.BuildConfig
import java.util.Locale

/** The public page of a shared chat. */
fun shareLink(id: String, web: String = BuildConfig.WEB_URL): String = "${web.trimEnd('/')}/share/$id"

/** The invite link: a friend who signs up through it is credited to you. */
fun inviteLink(code: String, web: String = BuildConfig.WEB_URL): String = "${web.trimEnd('/')}/?ref=$code"

/** A price in euro cents as shown in the apps: "€ 10", "€ 7.50". */
fun euros(cents: Int): String =
    if (cents % 100 == 0) "€ ${cents / 100}" else "€ ${cents / 100}." + String.format(Locale.ROOT, "%02d", cents % 100)
