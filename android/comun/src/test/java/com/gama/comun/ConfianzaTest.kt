package com.gama.comun

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class ConfianzaTest {
    private lateinit var servidor: MockWebServer
    private val certificado = HeldCertificate.Builder().commonName("gama-planta").addSubjectAlternativeName("localhost").build()

    @Before fun iniciar() {
        servidor = MockWebServer()
        servidor.useHttps(HandshakeCertificates.Builder().heldCertificate(certificado).build().sslSocketFactory(), false)
        servidor.start()
    }

    @After fun terminar() = servidor.shutdown()

    private fun pedir(cliente: OkHttpClient): Int =
        cliente.newCall(Request.Builder().url(servidor.url("/api/v1/salud")).build()).execute().use { it.code }

    @Test fun acepta_solo_el_certificado_fijado() {
        servidor.enqueue(MockResponse().setBody("{}"))
        val huella = Confianza.huella(certificado.certificate)
        assertEquals(200, pedir(Confianza.fijar(OkHttpClient.Builder(), huella).build()))

        val impostor = Confianza.huella(HeldCertificate.Builder().build().certificate)
        try {
            pedir(Confianza.fijar(OkHttpClient.Builder(), impostor).build())
            fail("Debió rechazar un certificado con otra huella")
        } catch (e: IOException) {
            // esperado: el saludo TLS falla
        }
    }

    @Test fun lee_la_huella_sin_confiar_y_coincide_con_el_formato_del_servidor() = runTest {
        servidor.enqueue(MockResponse().setBody("{}"))
        val leida = Confianza.leerHuella(servidor.url("/").toString())
        assertEquals(Confianza.huella(certificado.certificate), leida)
        assertTrue(Regex("([0-9A-F]{2}:){31}[0-9A-F]{2}").matches(leida))
    }

    @Test fun normaliza_formatos() {
        val h = Confianza.huella(certificado.certificate)
        assertEquals(Confianza.normalizar(h), Confianza.normalizar(h.lowercase().replace(":", " ")))
        assertEquals(h, Confianza.legible(h.replace(":", "")))
        assertTrue(Confianza.esValida(h))
        assertFalse(Confianza.esValida("AB:CD"))
    }
}
