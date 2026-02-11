package com.example.paralogic

// Importo las herramientas necesarias para que mi app sepa dibujar, sonar y pensar.
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.paralogic.components.PanelDeJuego
import com.example.paralogic.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

// --- 1. MIS MOLDES DE INFORMACIÓN ---

@Serializable
data class DatosNivel(
    val letraCentral: String,
    var letrasExteriores: List<String>,
    val soluciones: Set<String>,
    val puntuacionMaxima: Int
)

@Serializable
data class RecursoJuego(
    val diccionarioFiltrado: Set<String>,
    val listaPangramas: List<String>
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ParalogicTheme {
                PantallaDeControl()
            }
        }
    }
}

// --- 2. MI AMBIENTACIÓN SONORA ---

@Composable
fun MusicaDeFondo() {
    val contexto = LocalContext.current
    val mp = remember {
        MediaPlayer.create(contexto, R.raw.musicafondo)?.apply {
            isLooping = true
            setVolume(0.2f, 0.2f)
        }
    }

    DisposableEffect(mp) {
        mp?.start()
        onDispose {
            mp?.stop()
            mp?.release()
        }
    }
}

// --- 3. MI GESTOR DE CARGA CON TÍTULO PRINCIPAL ---

@Composable
fun PantallaDeControl() {
    val contexto = LocalContext.current
    var recursos by remember { mutableStateOf<RecursoJuego?>(null) }
    var nivelInicial by remember { mutableStateOf<DatosNivel?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val r = cargarRecursosConCacheSegura(contexto)
            val n = generarNivelPorDificultad(r, 1)
            recursos = r
            nivelInicial = n
        }
    }

    if (recursos == null || nivelInicial == null) {
        Box(modifier = Modifier.fillMaxSize().background(AzulProfundo), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "MARINE WORDS",
                    color = DoradoTesoro,
                    fontSize = 50.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                )
                Spacer(Modifier.height(40.dp))
                CircularProgressIndicator(color = DoradoTesoro, strokeWidth = 6.dp)
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "PREPARANDO LA TRAVESÍA",
                    color = BlancoEspuma.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
        }
    } else {
        PantallaPrincipalConMenu(recursos!!, nivelInicial!!, 1)
    }
}

fun cargarRecursosConCacheSegura(context: Context): RecursoJuego {
    val archivoCache = File(context.filesDir, "memoria_maestra_v7.json")
    if (archivoCache.exists()) {
        try {
            return Json.decodeFromString<RecursoJuego>(archivoCache.readText())
        } catch (e: Exception) {
            archivoCache.delete()
        }
    }

    val completo = mutableSetOf<String>()
    val pangramas = mutableListOf<String>()
    try {
        context.assets.open("dictionary_es.txt").bufferedReader().useLines { lineas ->
            lineas.forEach {
                val limpia = it.trim().lowercase()
                if (limpia.length in 3..10) {
                    completo.add(limpia)
                    if (limpia.toSet().size == 7) pangramas.add(limpia)
                }
            }
        }
        val nuevoRecurso = RecursoJuego(completo, pangramas)
        archivoCache.writeText(Json.encodeToString(nuevoRecurso))
        return nuevoRecurso
    } catch (e: Exception) {
        return RecursoJuego(setOf("mar", "tesoro"), listOf("marinos"))
    }
}

// --- 4. MI INTERFAZ DE JUEGO DINÁMICA --- (CORREGIDA)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipalConMenu(recursos: RecursoJuego, nivelCargado: DatosNivel, nivelInicialNumero: Int) {
    MusicaDeFondo()

    var nivelActual by remember { mutableStateOf(nivelCargado) }
    var numeroDeNivel by remember { mutableIntStateOf(nivelInicialNumero) }
    var palabraActual by remember { mutableStateOf("") }
    var puntos by remember { mutableIntStateOf(0) }
    var mensajeFeedback by remember { mutableStateOf("¡A toda vela!") }
    var palabrasEncontradas by remember { mutableStateOf(setOf<String>()) }
    var solucionesReveladas by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val rangoActual = when(numeroDeNivel) { in 1..3 -> "Grumete"; in 4..7 -> "Marinero"; else -> "Capitán" }

    val metaPalabras = 5
    val progreso = (palabrasEncontradas.size.toFloat() / metaPalabras.toFloat()).coerceAtMost(1f)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AzulProfundo,
                modifier = Modifier.width(300.dp).fillMaxHeight()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MAPA DE SOLUCIONES", color = DoradoTesoro, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(15.dp))
                    Text("Tu Botín Descubierto (${palabrasEncontradas.size})", color = BlancoEspuma, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(palabrasEncontradas.toList()) { palabra ->
                            Surface(color = DoradoTesoro, shape = RoundedCornerShape(8.dp)) {
                                Text(
                                    text = palabra.uppercase(),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(15.dp))

                    Button(
                        onClick = { solucionesReveladas = !solucionesReveladas },
                        colors = ButtonDefaults.buttonColors(containerColor = MaderaTimon),
                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth()
                    ) { Text(if (solucionesReveladas) "OCULTAR MAPA" else "VER MAPA COMPLETO") }

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(nivelActual.soluciones.toList().sorted()) { palabra ->
                            val encontrada = palabrasEncontradas.contains(palabra)
                            Surface(
                                color = if (encontrada) DoradoTesoro.copy(alpha = 0.6f) else BlancoEspuma.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    val texto = if (encontrada || solucionesReveladas) palabra.uppercase() else "• ".repeat(palabra.length)
                                    Text(text = texto, color = if (encontrada) Color.Black else BlancoEspuma, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    if (encontrada) Text("✓", color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(painter = painterResource(id = R.drawable.fondo2), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text("Nivel $numeroDeNivel: $rangoActual", color = Color.White, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, null, tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent)
                    )
                }
            ) { innerPadding ->
                // **CORRECCIÓN**: He cambiado `SpaceEvenly` por una estructura de 3 bloques con `weight`.
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {

                    // --- BLOQUE SUPERIOR (Información) ---
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaderaTimon),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Stars, contentDescription = null, tint = DoradoTesoro, modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("BOTÍN TOTAL", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                                Text("$puntos Puntos", color = DoradoTesoro, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                        Text("Rumbo al siguiente puerto: ${palabrasEncontradas.size}/$metaPalabras", color = BlancoEspuma, fontSize = 20.sp)
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progreso },
                            modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(10.dp)),
                            color = DoradoTesoro,
                            trackColor = MaderaTimon.copy(alpha = 0.4f)
                        )
                    }

                    // --- BLOQUE CENTRAL (Juego) ---
                    // Este `Column` usa `weight(1f)` para ocupar todo el espacio sobrante
                    // y luego centra el timón en su interior.
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PanelDeJuego(
                            letrasExteriores = nivelActual.letrasExteriores,
                            letraObligatoria = nivelActual.letraCentral,
                            alPulsarLetra = { if (palabraActual.length < 10) palabraActual += it }
                        )
                    }


                    // --- BLOQUE INFERIOR (Controles) ---
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 16.dp)) {
                        Surface(
                            color = Color.White.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(0.85f).height(60.dp),
                            border = BorderStroke(3.dp, MaderaTimon)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(palabraActual.uppercase(), fontSize = 34.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                            }
                        }

                        Text(text = mensajeFeedback, color = DoradoTesoro, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 10.dp))

                        Row {
                            Button(
                                onClick = { if (palabraActual.isNotEmpty()) palabraActual = palabraActual.dropLast(1) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaderaTimon)
                            ) { Text("BORRAR", fontWeight = FontWeight.Bold) }
                            Spacer(modifier = Modifier.width(16.dp))
                            Button(
                                onClick = {
                                    val intento = palabraActual.lowercase().trim()
                                    if (nivelActual.soluciones.contains(intento) && !palabrasEncontradas.contains(intento)) {
                                        palabrasEncontradas = palabrasEncontradas + intento
                                        puntos += (intento.length - 2)
                                        mensajeFeedback = "¡Excelente hallazgo!"
                                        palabraActual = ""
                                    } else {
                                        mensajeFeedback = "Esa no sirve, marinero"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DoradoTesoro)
                            ) { Text("ENVIAR", color = Color.Black, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
        }
    }
}

fun generarNivelPorDificultad(recursos: RecursoJuego, nivel: Int): DatosNivel {
    repeat(100) {
        val pangrama = recursos.listaPangramas.randomOrNull() ?: "marinos"
        val letrasSet = pangrama.toSet().map { it.toString().uppercase() }.shuffled()
        val central = letrasSet[0]
        val validasChar = pangrama.lowercase().toSet()

        val soluciones = recursos.diccionarioFiltrado.filter { p ->
            p.contains(central.lowercase()) && p.all { it in validasChar }
        }.toSet()

        if (soluciones.size >= 10) {
            return DatosNivel(central, letrasSet.subList(1, 7), soluciones, soluciones.sumOf { it.length - 2 })
        }
    }
    return DatosNivel("A", listOf("E", "T", "M", "S", "R", "O"), setOf("mar", "amor", "arte"), 10)
}
