package com.gama.suite.datos

import com.gama.comun.Confianza
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Error de la API con un mensaje listo para mostrar al usuario. */
class ApiError(val codigo: Int, mensaje: String) : Exception(mensaje)

/**
 * Cliente de la API de GAMA Suite.
 *
 * Renueva el token de acceso automáticamente al recibir 401 y avisa por
 * [alCambiarTokens] cuando hay tokens nuevos o cuando la sesión terminó (null).
 */
class ApiCliente(
    servidor: String,
    tokensIniciales: Pair<String, String>?,
    /** Huella SHA-256 del certificado propio del servidor de planta; null = confianza del sistema. */
    huella: String? = null,
    private val alCambiarTokens: (Tokens?) -> Unit,
) {
    val base: String = normalizarServidor(servidor)

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
    private val tipoJson = "application/json; charset=utf-8".toMediaType()
    private val candado = Any()

    @Volatile private var acceso: String? = tokensIniciales?.first

    @Volatile private var refresco: String? = tokensIniciales?.second

    private val renovador = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? = renovarYReintentar(response)
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .authenticator(renovador)
        .let { b -> if (huella != null) Confianza.fijar(b, huella) else b }
        .build()

    // Cliente sin renovador para las llamadas de /auth (evita bucles).
    private val httpAuth: OkHttpClient = http.newBuilder().authenticator(Authenticator.NONE).build()

    val tieneSesion: Boolean get() = refresco != null

    // --- Autenticación -------------------------------------------------------
    suspend fun login(usuario: String, clave: String, dispositivo: String?): Tokens {
        val cuerpo = json.encodeToString(LoginPeticion.serializer(), LoginPeticion(usuario, clave, dispositivo))
        val tokens = llamar(httpAuth, post("/auth/login", cuerpo, conToken = false), Tokens.serializer())
        fijarTokens(tokens)
        return tokens
    }

    suspend fun cambiarClave(actual: String, nueva: String) {
        val cuerpo = json.encodeToString(CambioClavePeticion.serializer(), CambioClavePeticion(actual, nueva))
        llamarSinCuerpo(http, post("/auth/clave", cuerpo, conToken = true))
        // El servidor cierra todas las sesiones al cambiar la clave.
        olvidarTokens()
    }

    suspend fun salir() {
        val r = refresco
        olvidarTokens()
        if (r != null) {
            val cuerpo = json.encodeToString(RefrescoPeticion.serializer(), RefrescoPeticion(r))
            runCatching { llamarSinCuerpo(httpAuth, post("/auth/salir", cuerpo, conToken = false)) }
        }
    }

    // --- Consultas -----------------------------------------------------------
    suspend fun salud(): Boolean = runCatching {
        llamarSinCuerpo(httpAuth, Request.Builder().url("$base/api/v1/salud").get().build())
    }.isSuccess

    suspend fun sesion(): Sesion = llamar(http, get("/sesion"), Sesion.serializer())

    suspend fun jerarquia(): List<Nodo> = llamar(http, get("/jerarquia"), ListSerializer(Nodo.serializer()))

    suspend fun usuarios(): List<UsuarioSesion> =
        llamar(http, get("/usuarios"), ListSerializer(UsuarioSesion.serializer()))

    suspend fun auditoria(limite: Int = 100): PaginaAuditoria =
        llamar(http, get("/auditoria?limite=$limite"), PaginaAuditoria.serializer())

    // --- GAMA Activos ---------------------------------------------------------
    suspend fun catalogosFalla(): List<CatalogoFalla> =
        llamar(http, get("/activos/catalogos"), ListSerializer(CatalogoFalla.serializer()))

    suspend fun solicitudes(estado: String? = null): Pagina<SolicitudMtto> = llamar(
        http,
        get("/activos/solicitudes?limite=100" + (estado?.let { "&estado=$it" } ?: "")),
        Pagina.serializer(SolicitudMtto.serializer()),
    )

    suspend fun crearSolicitud(nueva: SolicitudNueva): SolicitudMtto =
        enviar("/activos/solicitudes", nueva, SolicitudNueva.serializer(), SolicitudMtto.serializer())

    suspend fun aprobarSolicitud(id: Int, tecnicoId: Int? = null): OrdenTrabajo = enviar(
        "/activos/solicitudes/$id/aprobar", AprobacionSolicitud(tecnicoId),
        AprobacionSolicitud.serializer(), OrdenTrabajo.serializer(),
    )

    suspend fun rechazarSolicitud(id: Int, motivo: String): SolicitudMtto =
        enviar("/activos/solicitudes/$id/rechazar", Motivo(motivo), Motivo.serializer(), SolicitudMtto.serializer())

    /** filtro: "mias", "abiertas" o "todas". */
    suspend fun ordenes(filtro: String): Pagina<OrdenTrabajo> {
        val q = when (filtro) {
            "mias" -> "&mias=true&abiertas=true"
            "abiertas" -> "&abiertas=true"
            else -> ""
        }
        return llamar(http, get("/activos/ordenes?limite=100$q"), Pagina.serializer(OrdenTrabajo.serializer()))
    }

    suspend fun orden(id: Int): OrdenTrabajo = llamar(http, get("/activos/ordenes/$id"), OrdenTrabajo.serializer())

    /** Acciones sin datos: "iniciar", "reanudar", "cerrar". */
    suspend fun accionOrden(id: Int, accion: String): OrdenTrabajo =
        enviar("/activos/ordenes/$id/$accion", NotaOrden(), NotaOrden.serializer(), OrdenTrabajo.serializer())

    suspend fun pausarOrden(id: Int, motivo: String): OrdenTrabajo =
        enviar("/activos/ordenes/$id/pausar", NotaOrden(motivo), NotaOrden.serializer(), OrdenTrabajo.serializer())

    suspend fun completarOrden(id: Int, cierre: CierreOrden): OrdenTrabajo =
        enviar("/activos/ordenes/$id/completar", cierre, CierreOrden.serializer(), OrdenTrabajo.serializer())

    suspend fun indicadoresMtto(): IndicadoresMtto =
        llamar(http, get("/activos/indicadores"), IndicadoresMtto.serializer())

    suspend fun confiabilidad(equipoId: Int, costoPreventivo: Double?, costoFalla: Double?): Confiabilidad {
        val costos = if (costoPreventivo != null && costoFalla != null) {
            "&costo_preventivo=$costoPreventivo&costo_falla=$costoFalla"
        } else {
            ""
        }
        return llamar(http, get("/activos/confiabilidad?equipo_id=$equipoId$costos"), Confiabilidad.serializer())
    }

    // --- GAMA Ventas ----------------------------------------------------------
    suspend fun clientesVenta(): List<ClienteVenta> =
        llamar(http, get("/ventas/clientes"), ListSerializer(ClienteVenta.serializer()))

    /** filtro: "mias", "abiertas" o "todas". */
    suspend fun oportunidades(filtro: String): Pagina<Oportunidad> {
        val q = when (filtro) {
            "mias" -> "&mias=true&abiertas=true"
            "abiertas" -> "&abiertas=true"
            else -> ""
        }
        return llamar(http, get("/ventas/oportunidades?limite=200$q"), Pagina.serializer(Oportunidad.serializer()))
    }

    suspend fun crearOportunidad(nueva: OportunidadNueva): Oportunidad =
        enviar("/ventas/oportunidades", nueva, OportunidadNueva.serializer(), Oportunidad.serializer())

    suspend fun cambiarEtapa(id: Int, cambio: CambioEtapa): Oportunidad =
        enviar("/ventas/oportunidades/$id/etapa", cambio, CambioEtapa.serializer(), Oportunidad.serializer())

    suspend fun embudo(moneda: String): Embudo =
        llamar(http, get("/ventas/embudo?moneda=$moneda"), Embudo.serializer())

    suspend fun valorClientes(moneda: String): ValorClientes =
        llamar(http, get("/ventas/valor-clientes?moneda=$moneda"), ValorClientes.serializer())

    // --- GAMA Compras ---------------------------------------------------------
    suspend fun reponer(): Reposicion = llamar(http, get("/compras/reponer"), Reposicion.serializer())

    suspend fun desempenoProveedores(dias: Int = 365): DesempenoProveedores =
        llamar(http, get("/compras/proveedores/desempeno?dias=$dias"), DesempenoProveedores.serializer())

    suspend fun crearRequisicion(nueva: RequisicionNueva): Requisicion =
        enviar("/compras/requisiciones", nueva, RequisicionNueva.serializer(), Requisicion.serializer())

    // --- GAMA Dispositivos (MDM) -------------------------------------------------
    suspend fun dispositivos(): List<DispositivoMdm> =
        llamar(http, get("/dispositivos"), ListSerializer(DispositivoMdm.serializer()))

    suspend fun enviarComando(dispositivoId: Int, comando: ComandoNuevo): ComandoMdm =
        enviar("/dispositivos/$dispositivoId/comandos", comando, ComandoNuevo.serializer(), ComandoMdm.serializer())

    suspend fun crearCodigoRegistro(nuevo: CodigoNuevo): CodigoRegistro =
        enviar("/dispositivos/codigos", nuevo, CodigoNuevo.serializer(), CodigoRegistro.serializer())

    /** PNG del QR de registro (lo escanea GAMA MDM). */
    suspend fun qrRegistro(codigo: String): ByteArray = llamarBytes(get("/dispositivos/codigos/$codigo/qr.png"))

    // --- GAMA Asistente -------------------------------------------------------
    suspend fun resumenAsistente(area: String): ResumenAsistente =
        llamar(http, get("/asistente/resumen?area=$area"), ResumenAsistente.serializer())

    suspend fun guiaUso(): GuiaUso = llamar(http, get("/asistente/guia"), GuiaUso.serializer())

    suspend fun completarLeccion(codigo: String): LeccionUso =
        llamar(http, post("/asistente/guia/$codigo/completar", "{}", conToken = true), LeccionUso.serializer())

    suspend fun ayudaUso(pregunta: String): AyudaUso =
        llamar(http, get("/asistente/ayuda?pregunta=${URLEncoder.encode(pregunta, "UTF-8")}"), AyudaUso.serializer())

    // --- GAMA Conecta ---------------------------------------------------------
    suspend fun canales(): List<Canal> = llamar(http, get("/conecta/canales"), ListSerializer(Canal.serializer()))

    suspend fun mensajes(canalId: Int): List<Mensaje> =
        llamar(http, get("/conecta/canales/$canalId/mensajes?limite=50"), ListSerializer(Mensaje.serializer()))

    suspend fun comentarios(mensajeId: Long): List<Mensaje> =
        llamar(http, get("/conecta/mensajes/$mensajeId/comentarios"), ListSerializer(Mensaje.serializer()))

    suspend fun publicar(canalId: Int, nuevo: MensajeNuevo): Mensaje =
        enviar("/conecta/canales/$canalId/mensajes", nuevo, MensajeNuevo.serializer(), Mensaje.serializer())

    suspend fun reaccionar(mensajeId: Long, emoji: String): Mensaje = enviar(
        "/conecta/mensajes/$mensajeId/reacciones", ReaccionNueva(emoji), ReaccionNueva.serializer(),
        Mensaje.serializer(),
    )

    suspend fun confirmarAviso(mensajeId: Long): Mensaje =
        enviar("/conecta/mensajes/$mensajeId/acuse", NotaOrden(), NotaOrden.serializer(), Mensaje.serializer())

    suspend fun personas(): List<Persona> =
        llamar(http, get("/conecta/personas"), ListSerializer(Persona.serializer()))

    suspend fun abrirDirecto(usuarioId: Int): Canal =
        enviar("/conecta/directos", DirectoNuevo(usuarioId), DirectoNuevo.serializer(), Canal.serializer())

    // --- Departamentos (motor común de registros) ---------------------------
    suspend fun metaModulo(modulo: String): ModuloMeta = llamar(http, get("/r/$modulo/meta"), ModuloMeta.serializer())

    suspend fun indicadoresArea(modulo: String, dias: Int = 30): List<IndicadorArea> =
        llamar(http, get("/r/$modulo/indicadores?dias=$dias"), ListSerializer(IndicadorArea.serializer()))

    suspend fun registros(modulo: String, entidad: String, estado: String? = null, buscar: String? = null): PaginaRegistros {
        val filtros = buildList {
            add("limite=200")
            estado?.let { add("estado=${codificar(it)}") }
            buscar?.takeIf { it.isNotBlank() }?.let { add("buscar=${codificar(it.trim())}") }
        }.joinToString("&")
        return llamar(http, get("/r/$modulo/$entidad?$filtros"), PaginaRegistros.serializer())
    }

    suspend fun registro(modulo: String, entidad: String, id: Long): Registro =
        llamar(http, get("/r/$modulo/$entidad/$id"), Registro.serializer())

    suspend fun crearRegistro(modulo: String, entidad: String, datos: JsonObject): Registro =
        enviar("/r/$modulo/$entidad", EntradaRegistro(datos), EntradaRegistro.serializer(), Registro.serializer())

    suspend fun editarRegistro(modulo: String, entidad: String, id: Long, datos: JsonObject): Registro {
        val cuerpo = json.encodeToString(EntradaRegistro.serializer(), EntradaRegistro(datos)).toRequestBody(tipoJson)
        val peticion = Request.Builder().url("$base/api/v1/r/$modulo/$entidad/$id").patch(cuerpo).conToken().build()
        return llamar(http, peticion, Registro.serializer())
    }

    suspend fun accionRegistro(modulo: String, entidad: String, id: Long, accion: String, nota: String?): Registro =
        enviar(
            "/r/$modulo/$entidad/$id/acciones/$accion", NotaRegistro(nota), NotaRegistro.serializer(),
            Registro.serializer(),
        )

    /** Lee ahora una variable del PLC por Modbus TCP (gemelo digital). */
    suspend fun leerPlc(id: Long): Registro =
        enviar("/r/twin/variable/$id/leer", NotaRegistro(), NotaRegistro.serializer(), Registro.serializer())

    suspend fun opcionesReferencia(referencia: String): List<OpcionRef> {
        val (modulo, entidad) = referencia.split('.', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
        return llamar(http, get("/r/opciones/$modulo/$entidad"), ListSerializer(OpcionRef.serializer()))
    }

    suspend fun opcionesUsuarios(): List<OpcionRef> =
        llamar(http, get("/r/opciones/usuarios"), ListSerializer(OpcionRef.serializer()))

    suspend fun opcionesNodos(): List<OpcionRef> =
        llamar(http, get("/r/opciones/nodos"), ListSerializer(OpcionRef.serializer()))

    private fun codificar(texto: String): String = URLEncoder.encode(texto, "UTF-8")

    // --- Infraestructura -----------------------------------------------------
    private suspend fun <B, T> enviar(ruta: String, cuerpo: B, sb: KSerializer<B>, st: KSerializer<T>): T =
        llamar(http, post(ruta, json.encodeToString(sb, cuerpo), conToken = true), st)

    private fun get(ruta: String): Request = Request.Builder().url("$base/api/v1$ruta").get().conToken().build()

    private fun post(ruta: String, cuerpo: String, conToken: Boolean): Request {
        val b = Request.Builder().url("$base/api/v1$ruta").post(cuerpo.toRequestBody(tipoJson))
        return (if (conToken) b.conToken() else b).build()
    }

    private fun Request.Builder.conToken(): Request.Builder {
        acceso?.let { header("Authorization", "Bearer $it") }
        return this
    }

    private suspend fun <T> llamar(cliente: OkHttpClient, peticion: Request, serializador: KSerializer<T>): T =
        withContext(Dispatchers.IO) {
            ejecutar(cliente, peticion) { cuerpo -> json.decodeFromString(serializador, cuerpo) }
        }

    private suspend fun llamarBytes(peticion: Request): ByteArray = withContext(Dispatchers.IO) {
        val respuesta = try {
            http.newCall(peticion).execute()
        } catch (e: IOException) {
            throw ApiError(0, "No se pudo conectar con el servidor. Verifique la red y la dirección.")
        }
        respuesta.use {
            if (!it.isSuccessful) throw ApiError(it.code, mensajeDeError(it.code, it.body?.string().orEmpty()))
            it.body?.bytes() ?: ByteArray(0)
        }
    }

    private suspend fun llamarSinCuerpo(cliente: OkHttpClient, peticion: Request) {
        withContext(Dispatchers.IO) { ejecutar(cliente, peticion) { } }
    }

    private fun <T> ejecutar(cliente: OkHttpClient, peticion: Request, leer: (String) -> T): T {
        val respuesta = try {
            cliente.newCall(peticion).execute()
        } catch (e: SSLException) {
            throw ApiError(
                0,
                "El certificado del servidor no coincide con el registrado. Si el servidor se reinstaló, " +
                    "vuelva a conectarlo y compare la huella con la consola.",
            )
        } catch (e: IOException) {
            throw ApiError(0, "No se pudo conectar con el servidor. Verifique la red y la dirección.")
        }
        return respuesta.use {
            val texto = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ApiError(it.code, mensajeDeError(it.code, texto))
            try {
                leer(texto)
            } catch (e: SerializationException) {
                throw ApiError(it.code, "Respuesta inesperada del servidor. Verifique que la app y el servidor sean versiones compatibles.")
            }
        }
    }

    private fun mensajeDeError(codigo: Int, texto: String): String {
        val detalle = runCatching { json.parseToJsonElement(texto).jsonObject["detail"] }.getOrNull()
        val mensaje = when (detalle) {
            is JsonPrimitive -> detalle.content
            is JsonArray -> (detalle.firstOrNull() as? JsonObject)?.get("msg")?.jsonPrimitive?.content
            else -> null
        }
        return mensaje ?: when (codigo) {
            401 -> "La sesión venció. Inicie sesión de nuevo."
            403 -> "Su rol no tiene permiso para esta acción."
            404 -> "No encontrado."
            in 500..599 -> "Error del servidor ($codigo)."
            else -> "Error inesperado ($codigo)."
        }
    }

    private fun fijarTokens(t: Tokens) {
        acceso = t.tokenAcceso
        refresco = t.tokenRefresco
        alCambiarTokens(t)
    }

    private fun olvidarTokens() {
        acceso = null
        refresco = null
        alCambiarTokens(null)
    }

    /** Se ejecuta en un hilo de OkHttp al recibir 401. */
    private fun renovarYReintentar(respuesta: Response): Request? {
        if (respuesta.priorResponse != null) return null // ya se reintentó una vez
        val usado = respuesta.request.header("Authorization")?.removePrefix("Bearer ") ?: return null
        return synchronized(candado) {
            val actual = acceso
            if (actual != null && actual != usado) {
                // Otro hilo ya renovó: reintentar con el token vigente.
                return respuesta.request.newBuilder().header("Authorization", "Bearer $actual").build()
            }
            val r = refresco ?: return null
            val cuerpo = json.encodeToString(RefrescoPeticion.serializer(), RefrescoPeticion(r))
            val peticion = post("/auth/refresco", cuerpo, conToken = false)
            val nuevos = try {
                ejecutar(httpAuth, peticion) { json.decodeFromString(Tokens.serializer(), it) }
            } catch (e: ApiError) {
                if (e.codigo == 401) olvidarTokens()
                return null
            }
            fijarTokens(nuevos)
            respuesta.request.newBuilder().header("Authorization", "Bearer ${nuevos.tokenAcceso}").build()
        }
    }

    companion object {
        /** Acepta "192.168.1.10:8000" y le agrega el esquema; quita la barra final. */
        fun normalizarServidor(entrada: String): String {
            var s = entrada.trim().trimEnd('/')
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "https://$s"
            s.toHttpUrl() // valida; lanza IllegalArgumentException si no es una URL
            return s
        }
    }
}
