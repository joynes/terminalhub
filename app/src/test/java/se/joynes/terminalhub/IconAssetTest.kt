package se.joynes.terminalhub

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconAssetTest {
    @Test
    fun `launcher foreground has transparent outer background`() {
        val image = ImageIO.read(
            File("src/main/res/drawable-nodpi/ic_launcher_foreground_image.png")
        )

        assertEquals(0, image.getRGB(0, 0).ushr(24))
        assertTrue(image.getRGB(image.width / 2, image.height / 2).ushr(24) > 0)
    }

    @Test
    fun `notification foreground is scaled down thirty percent`() {
        val vector = File("src/main/res/drawable/ic_notification_terminal.xml").readText()

        assertTrue(vector.contains("android:scaleX=\"0.7\""))
        assertTrue(vector.contains("android:scaleY=\"0.7\""))
    }
}
