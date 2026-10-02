package se.joynes.terminalhub.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class YamlScalarTest {
    @Test fun `nested escapes and literal backslash n survive one decoding pass`() {
        val original = "[{\"text\":\"line\\nnext\\t\\\"quote\\\"\"}]"
        val quoted = "\"" + original.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
        assertEquals(original, decodeYamlScalar(quoted))
    }

    @Test fun `normal newlines quotes and tabs still decode`() {
        assertEquals("line\nnext\t\"quoted\"", decodeYamlScalar("\"line\\nnext\\t\\\"quoted\\\"\""))
        assertEquals("plain", decodeYamlScalar("plain"))
    }
}
