package com.gama.suite.ui.pantallas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gama.suite.datos.ApiCliente
import com.gama.suite.datos.ApiError
import com.gama.suite.datos.ModuloSesion
import com.gama.suite.datos.Nodo
import com.gama.suite.ui.tema.Gama
import com.gama.suite.ui.Cargar
import com.gama.suite.ui.tema.LocalTactil

// Secciones de Administración (núcleo).
private val SECCIONES_LISTAS = setOf("Jerarquía de planta", "Usuarios", "Auditoría")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModuloPantalla(modulo: ModuloSesion, api: ApiCliente, alVolver: () -> Unit) {
    var seccion by rememberSaveable(modulo.codigo) { mutableStateOf<String?>(null) }
    BackHandler { if (seccion != null) seccion = null else alVolver() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(seccion ?: modulo.nombre) },
                navigationIcon = {
                    IconButton(onClick = { if (seccion != null) seccion = null else alVolver() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Volver")
                    }
                },
            )
        },
    ) { relleno ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(relleno),
        ) {
            when (val s = seccion) {
                null -> when (modulo.codigo) {
                    "activos" -> ActivosPantalla(modulo, api)
                    "conecta" -> ConectaPantalla(modulo, api)
                    "comercial" -> VentasPantalla(modulo, api)
                    "abastecimiento" -> ComprasPantalla(modulo, api)
                    "asistente" -> AsistentePantalla(api)
                    "dispositivos" -> DispositivosPantalla(modulo, api)
                    "core" -> ListaSecciones(modulo) { seccion = it }
                    // Producción, Calidad, Mejora, Logística, Costos, SST, Talento, Energía, Proyectos,
                    // Marketing y Gemelo digital: pantalla común sobre la declaración del servidor.
                    else -> RegistrosPantalla(modulo.codigo, api)
                }
                "Jerarquía de planta" -> JerarquiaContenido(api)
                "Usuarios" -> UsuariosContenido(api)
                "Auditoría" -> AuditoriaContenido(api)
                else -> SeccionPendiente(s)
            }
        }
    }
}

@Composable
private fun ListaSecciones(modulo: ModuloSesion, alElegir: (String) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(modulo.descripcion, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Su acceso: ${nivelDeAcceso(modulo)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(modulo.secciones) { s ->
            ListItem(
                headlineContent = { Text(s) },
                supportingContent = if (s in SECCIONES_LISTAS) null else ({ Text("Se administra desde la consola web") }),
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null) },
                modifier = Modifier
                    .heightIn(min = LocalTactil.current)
                    .clickable { alElegir(s) },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SeccionPendiente(nombre: String) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(nombre, style = MaterialTheme.typography.titleLarge)
        Text(
            "Esta sección se administra desde la consola web del servidor de planta.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val NOMBRE_NIVEL = mapOf(
    "sitio" to "Sitio", "area" to "Área", "linea" to "Línea", "equipo" to "Equipo", "componente" to "Componente",
)

/** Ordena la jerarquía como árbol (padre antes que hijos) y devuelve la profundidad de cada nodo. */
fun ordenarArbol(nodos: List<Nodo>): List<Pair<Nodo, Int>> {
    val hijos = nodos.groupBy { it.padreId }
    val ids = nodos.map { it.id }.toSet()
    val resultado = mutableListOf<Pair<Nodo, Int>>()
    fun visitar(padre: Int?, profundidad: Int) {
        hijos[padre].orEmpty().sortedBy { it.codigo }.forEach {
            resultado += it to profundidad
            visitar(it.id, profundidad + 1)
        }
    }
    visitar(null, 0)
    // Nodos cuyo padre no vino en la lista (por ejemplo, padre inactivo) se muestran al final.
    nodos.filter { it.padreId != null && it.padreId !in ids }.sortedBy { it.codigo }.forEach {
        resultado += it to 0
        visitar(it.id, 1)
    }
    return resultado
}

@Composable
private fun JerarquiaContenido(api: ApiCliente) {
    Cargar(cargar = { api.jerarquia() }) { nodos ->
        if (nodos.isEmpty()) {
            Text("Todavía no hay sitios, áreas ni equipos registrados.", Modifier.padding(24.dp))
        } else {
            LazyColumn {
                items(ordenarArbol(nodos), key = { it.first.id }) { (n, nivel) ->
                    val criticidad = n.criticidad
                    ListItem(
                        headlineContent = { Text("${n.codigo} · ${n.nombre}") },
                        supportingContent = { Text(NOMBRE_NIVEL[n.nivel] ?: n.nivel) },
                        trailingContent = if (criticidad == null) {
                            null
                        } else {
                            { EtiquetaCriticidad(criticidad) }
                        },
                        modifier = Modifier.padding(start = (nivel * 20).dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun UsuariosContenido(api: ApiCliente) {
    Cargar(cargar = { api.usuarios() }) { usuarios ->
        LazyColumn {
            items(usuarios, key = { it.id }) { u ->
                ListItem(
                    headlineContent = { Text(u.nombre) },
                    supportingContent = { Text("${u.usuario} · ${u.roles.joinToString { it.nombre }}") },
                    trailingContent = if (u.activo) null else ({ Text("Inactivo", color = Gama.Critico) }),
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AuditoriaContenido(api: ApiCliente) {
    Cargar(cargar = { api.auditoria() }) { pagina ->
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Text(
                    "Últimos ${pagina.elementos.size} de ${pagina.total} registros",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            items(pagina.elementos, key = { it.id }) { r ->
                ListItem(
                    headlineContent = { Text("${r.accion} · ${r.modulo}") },
                    supportingContent = {
                        Text("${r.momento.take(19).replace('T', ' ')} · ${r.usuario ?: "sistema"}")
                    },
                    trailingContent = { Text(listOfNotNull(r.entidad, r.entidadId).joinToString(" ")) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EtiquetaCriticidad(criticidad: String) {
    Text(
        "Criticidad $criticidad",
        color = when (criticidad) {
            "A" -> Gama.Critico
            "B" -> Gama.Alerta
            else -> Gama.Ok
        },
        style = MaterialTheme.typography.labelMedium,
    )
}
