package com.gama.suite

import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.Nodo
import com.gama.suite.datos.Tokens
import com.gama.suite.ui.pantallas.ordenarArbol
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ApiClienteTest {
    private lateinit var servidor: MockWebServer
    private val eventos = mutableListOf<Tokens?>()

    private val sesionJson = """
        {"usuario":{"id":1,"usuario":"ana","nombre":"Ana","correo":null,"activo":true,
          "debe_cambiar_clave":false,"ultimo_acceso":null,"roles":[{"id":3,"codigo":"operador","nombre":"Operador"}]},
         "empresa":"Industrias Demo","licencia":{"cliente":"Desarrollo","modo":"desarrollo","soporte_hasta":null,"soporte_vigente":true},
         "modulos":[{"codigo":"planta","nombre":"Planta","descripcion":"d","icono":"factory","grupo":"Operaciones",
           "secciones":["OEE y paros"],"acciones":["consultar","registrar"],"campo_futuro":1}]}
    """.trimIndent()

    private fun tokens(n: Int) =
        """{"token_acceso":"acc$n","token_refresco":"ref$n","tipo":"Bearer","expira_en_segundos":900,"debe_cambiar_clave":false}"""

    @Before fun iniciar() {
        servidor = MockWebServer()
        servidor.start()
        eventos.clear()
    }

    @After fun terminar() = servidor.shutdown()

    private fun cliente(tokens: Pair<String, String>? = null) =
        ApiCliente(servidor.url("/").toString(), tokens) { eventos += it }

    @Test fun normaliza_la_direccion_del_servidor() {
        assertEquals("https://192.168.1.50:8000", ApiCliente.normalizarServidor(" 192.168.1.50:8000/ "))
        assertEquals("http://planta.local", ApiCliente.normalizarServidor("http://planta.local"))
    }

    @Test fun login_guarda_tokens_y_lee_la_sesion_ignorando_campos_nuevos() = runTest {
        servidor.enqueue(MockResponse().setBody(tokens(1)))
        servidor.enqueue(MockResponse().setBody(sesionJson))
        val api = cliente()
        api.login("ana", "x", "prueba")
        val sesion = api.sesion()
        assertEquals("Planta", sesion.modulos.single().nombre)
        assertTrue(sesion.modulos.single().puede("registrar"))
        assertFalse(sesion.modulos.single().puede("aprobar"))
        servidor.takeRequest() // login
        assertEquals("Bearer acc1", servidor.takeRequest().getHeader("Authorization"))
        assertEquals("ref1", eventos.single()?.tokenRefresco)
    }

    @Test fun renueva_el_token_al_recibir_401_y_reintenta() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"vencido"}"""))
        servidor.enqueue(MockResponse().setBody(tokens(2)))
        servidor.enqueue(MockResponse().setBody(sesionJson))
        val sesion = cliente("acc1" to "ref1").sesion()
        assertEquals("Ana", sesion.usuario.nombre)
        assertEquals("/api/v1/sesion", servidor.takeRequest().path)
        val refresco = servidor.takeRequest()
        assertEquals("/api/v1/auth/refresco", refresco.path)
        assertTrue(refresco.body.readUtf8().contains("ref1"))
        assertEquals("Bearer acc2", servidor.takeRequest().getHeader("Authorization"))
        assertEquals("acc2", eventos.single()?.tokenAcceso)
    }

    @Test fun si_el_refresco_falla_la_sesion_termina() = runTest {
        servidor.enqueue(MockResponse().setResponseCode(401))
        servidor.enqueue(MockResponse().setResponseCode(401).setBody("""{"detail":"La sesión venció"}"""))
        val api = cliente("acc1" to "ref1")
        val error = runCatching { api.sesion() }.exceptionOrNull()
        assertTrue(error is ApiError && error.codigo == 401)
        assertNull(eventos.single())
        assertFalse(api.tieneSesion)
    }

    @Test fun muestra_el_mensaje_de_validacion_del_servidor() = runTest {
        servidor.enqueue(
            MockResponse().setResponseCode(422)
                .setBody("""{"detail":[{"loc":["body","clave"],"msg":"La contraseña es muy corta","type":"x"}]}"""),
        )
        val error = runCatching { cliente().login("ana", "x", null) }.exceptionOrNull()
        assertEquals("La contraseña es muy corta", error?.message)
    }

    @Test fun ordena_la_jerarquia_como_arbol() {
        val nodos = listOf(
            Nodo(3, 2, "equipo", "EQ-1", "Equipo", activo = true),
            Nodo(1, null, "sitio", "S1", "Sitio", activo = true),
            Nodo(2, 1, "area", "A1", "Área", activo = true),
            Nodo(9, 99, "equipo", "HUERFANO", "Sin padre visible", activo = true),
        )
        val orden = ordenarArbol(nodos).map { it.first.codigo to it.second }
        assertEquals(listOf("S1" to 0, "A1" to 1, "EQ-1" to 2, "HUERFANO" to 0), orden)
    }

    @Test fun lee_confiabilidad_y_ventas_con_valores_nulos() = runTest {
        servidor.enqueue(
            MockResponse().setBody(
                """{"equipo":"Compresor","fallas_registradas":7,"advertencias":[],
                "weibull":{"metodo":"m","beta":3.2,"eta":690.5,"beta_ic90":[2.1,4.9],"eta_ic90":[600,790],
                "mttf":618.0,"b10":340.2,"fallas":6,"censuras":1,"interpretacion":"Desgaste",
                "reemplazo_optimo":{"intervalo":null,"costo_por_hora_optimo":7.1,"costo_por_hora_correctivo":7.1,
                "ahorro_pct":0.0,"recomendacion":"No conviene"}},
                "tendencia":{"metodo":"m","beta":1.0,"mtbf_instantaneo_horas":null,"laplace_p":0.4,"conclusion":"Estable"}}""",
            ),
        )
        servidor.enqueue(
            MockResponse().setBody(
                """{"total":1,"elementos":[{"id":1,"numero":"OPP-000001","cliente_id":3,"cliente":"Cliente C001",
                "titulo":"Suministro","monto_estimado":"1500.00","moneda":"USD","etapa":"propuesta","vendedor_id":2,
                "cierre_estimado":null,"creada_en":"2026-11-21T10:00:00+00:00","cerrada_en":null,
                "motivo_perdida":null,"competidor":null}]}""",
            ),
        )
        val api = cliente("acc" to "ref")
        val conf = api.confiabilidad(5, 500.0, 5000.0)
        assertEquals(3.2, conf.weibull?.beta ?: 0.0, 1e-9)
        assertNull(conf.weibull?.reemplazoOptimo?.intervalo)
        assertNull(conf.tendencia?.mtbfInstantaneoHoras)
        assertEquals("/api/v1/activos/confiabilidad?equipo_id=5&costo_preventivo=500.0&costo_falla=5000.0",
            servidor.takeRequest().path)
        val ops = api.oportunidades("abiertas")
        assertEquals("1500.00", ops.elementos.single().montoEstimado)
    }

    /** JSON real devuelto por /compras/reponer con los datos de demostración. */
    @Test fun lee_la_reposicion_de_compras() = runTest {
        servidor.enqueue(MockResponse().setBody(tokens(1)))
        servidor.enqueue(MockResponse().setBody("""{"metodo": "Política (s, Q): punto de reorden con stock de seguridad y lote económico; patrón de demanda según Syntetos, Boylan y Croston (2005)", "articulos_revisados": 3, "reponer": [{"articulo_id": 1, "codigo": "RES-PEBD", "nombre": "Resina PEBD película", "unidad": "kg", "stock": 4079.1, "en_transito": 0.0, "posicion": 4079.1, "demanda_diaria": 411.4335, "patron": {"clase": "suave", "adi": 1, "cv2": 0.005, "metodo": "Promedio y desviación estándar de la demanda"}, "tiempo_entrega_dias": 12.0, "fuente_tiempo_entrega": "parámetro del artículo", "stock_seguridad": 2071.887, "punto_reorden": 7009.09, "lote_economico": 6033.708, "reponer": true, "cantidad_sugerida": 6033.708, "advertencias": []}]}"""))
        val api = cliente()
        api.login("ana", "x", "prueba")
        val r = api.reponer()
        assertEquals(3, r.articulosRevisados)
        val p = r.reponer.single()
        assertEquals("RES-PEBD", p.codigo)
        assertEquals("suave", p.patron.clase)
        assertEquals(1.0, p.patron.adi ?: 0.0, 1e-9)
        assertTrue(p.reponer && p.posicion < (p.puntoReorden ?: 0.0))
        servidor.takeRequest()
        assertEquals("/api/v1/compras/reponer", servidor.takeRequest().path)
    }

    @Test fun lee_el_resumen_del_asistente_y_codifica_la_pregunta() = runTest {
        servidor.enqueue(MockResponse().setBody(tokens(1)))
        servidor.enqueue(
            MockResponse().setBody(
                """{"area":"activos","generado_en":"2026-11-25T10:00:00-04:00","areas_incluidas":["activos"],
                  "areas_sin_permiso":[],"narrativa":{"texto":"Urgente","fuente":"plantilla","nota":"n"},
                  "hechos":[{"clave":"equipos_detenidos","area":"activos","severidad":"critico","titulo":"Equipos detenidos",
                  "texto":"1 equipo detenido","explicacion":"e","accion":null,"cifras":{"equipos":1},"seccion":"Órdenes",
                  "entidades":["EQ1 Compresor"],"accion_permiso":"aprobar"}]}""".trimIndent(),
            ),
        )
        servidor.enqueue(MockResponse().setBody("""{"pregunta":"x","respuesta":"No encontré","lecciones":[]}"""))
        val api = cliente()
        api.login("ana", "x", "prueba")
        val r = api.resumenAsistente("activos")
        assertEquals("critico", r.hechos.single().severidad)
        assertNull(r.hechos.single().accion)
        assertEquals("No encontré", api.ayudaUso("¿cómo reporto una falla?").respuesta)
        servidor.takeRequest()
        assertEquals("/api/v1/asistente/resumen?area=activos", servidor.takeRequest().path)
        assertEquals("/api/v1/asistente/ayuda?pregunta=%C2%BFc%C3%B3mo+reporto+una+falla%3F", servidor.takeRequest().path)
    }
}
