package com.gama.mdm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class UtilTest {
    private val huella = (1..32).joinToString(":") { "%02X".format(it) }

    @Test fun lee_el_qr_de_la_consola() {
        val qr = leerQr("""{"gama":1,"s":"https://192.168.1.50:8443/","h":"${huella.lowercase()}","c":"SSHF3KBVW4"}""")!!
        assertEquals("https://192.168.1.50:8443", qr.s)
        assertEquals(huella, qr.h)
        assertEquals("SSHF3KBVW4", qr.c)
    }

    @Test fun rechaza_qr_ajenos_o_mal_formados() {
        assertNull(leerQr("https://ejemplo.com"))
        assertNull(leerQr("""{"gama":2,"s":"https://a:1","c":"SSHF3KBVW4"}"""))
        assertNull(leerQr("""{"gama":1,"s":"ftp://a","c":"SSHF3KBVW4"}"""))
        assertNull(leerQr("""{"gama":1,"s":"https://a:1","h":"AB:CD","c":"SSHF3KBVW4"}"""))
        assertNull(leerQr("""{"gama":1,"s":"http://a:1","h":"$huella","c":"SSHF3KBVW4"}""")) // huella sin TLS
        assertNull(leerQr("""{"gama":1,"s":"https://a:1","c":"CORTO"}"""))
    }

    @Test fun normaliza_el_codigo_y_calcula_sha256() {
        assertEquals("SSHF3KBVW4", normalizarCodigo(" sshf3-kbvw4 "))
        val f = File.createTempFile("gama", ".apk")
        f.writeText("abc")
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", sha256(f))
        f.delete()
    }
}
