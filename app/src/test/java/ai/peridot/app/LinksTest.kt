package ai.peridot.app

import ai.peridot.app.data.euros
import ai.peridot.app.data.inviteLink
import ai.peridot.app.data.shareLink
import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {
    @Test
    fun linksPointAtTheWebsite() {
        assertEquals("https://tryperidot.io/share/abc123", shareLink("abc123", "https://tryperidot.io/"))
        assertEquals("https://tryperidot.io/?ref=qr7auyzw", inviteLink("qr7auyzw", "https://tryperidot.io"))
    }

    @Test
    fun pricesInEuros() {
        assertEquals("€ 10", euros(1000))
        assertEquals("€ 7.50", euros(750))
        assertEquals("€ 0.05", euros(5))
    }
}
