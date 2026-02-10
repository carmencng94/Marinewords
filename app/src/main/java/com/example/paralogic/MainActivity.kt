package com.example.paralogic

// Importamos las herramientas necesarias.
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

// 1. MODELOS DE DATOS (LOS MOLDES DE INFORMACIÓN)

/**
 * Las "Data Class" son como carpetas donde guardamos datos ordenados.
 * @Serializable le dice a Kotlin: "Prepara esta carpeta para que pueda convertirse
 * en un mensaje de texto largo (JSON) y guardarse en un archivo".
 */
@Serializable
data class DatosNivel(
    val letraCentral: String,      // La letra dorada obligatoria
    var letrasExteriores: List<String>, // Las 6 letras blancas
    val soluciones: Set<String>,   // Todas las palabras posibles (Set evita repetidas)
    val puntuacionMaxima: Int      // Total de puntos del nivel
)

@Serializable
data class RecursoJuego(
    val diccionarioFiltrado: Set<String>, // Palabras de 3 a 10 letras del idioma
    val listaPangramas: List<String>      // Palabras que tienen 7 letras distintas
)

/**
 * Esta es la clase principal. Es el punto de partida que Android busca al abrir la app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Hace que la app use toda la pantalla, incluso detrás de la batería.
        enableEdgeToEdge()

        setContent {
            // ParalogicTheme aplica tus colores y estilos náuticos.
            ParalogicTheme {
                PantallaDeControl()
            }
        }
    }
}

// 2. SISTEMA DE MÚSICA DE FONDO

/**
 * Gestionar sonido es delicado. 'remember' hace que Android recuerde el objeto
 * para que la música no intente empezar de cero cada vez que tocas un botón.
 */
@Composable
fun MusicaDeFondo() {
    val contexto = LocalContext.current
    val mp = remember {
        MediaPlayer.create(contexto, R.raw.musicafondo).apply {
            isLooping = true     // Bucle infinito.
            setVolume(0.2f, 0.2f) // Volumen suave.
        }
    }

    // 'DisposableEffect' es como un interruptor inteligente: se enciende al entrar
    // y tiene un "limpiador" (onDispose) al salir para liberar memoria RAM.
    DisposableEffect(Unit) {
        mp.start()
        onDispose {
            mp.stop()
            mp.release()
        }
    }
}

// 3. GESTOR DE CARGA (LA ADUANA)

/**
 * Esta función decide qué mostrar: ¿Una barrita de carga o el juego?
 */
@Composable
fun PantallaDeControl() {
    val contexto = LocalContext.current
    var recursos by remember { mutableStateOf<RecursoJuego?>(null) }
    var nivelInicial by remember { mutableStateOf<DatosNivel?>(null) }

    // 'LaunchedEffect' carga archivos al inicio sin bloquear la pantalla.
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { // Trabajo pesado en segundo plano.
            val r = cargarRecursosConCacheSegura(contexto)
            val n = generarNivelPorDificultad(r, 1)
            recursos = r
            nivelInicial = n
        }
    }

    if (recursos == null || nivelInicial == null) {
        Box(modifier = Modifier.fillMaxSize().background(AzulProfundo), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = DoradoTesoro)
                Spacer(Modifier.height(20.dp))
                Text("Navegando a tu destino...", color = BlancoEspuma, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        PantallaPrincipalConMenu(recursos!!, nivelInicial!!, 1)
    }
}

/**
 * SISTEMA DE CACHÉ: Es como una "chuleta".
 * La primera vez lee el .txt (lento). La segunda lee el .json (rápido).
 */
fun cargarRecursosConCacheSegura(context: Context): RecursoJuego {
    val archivoCache = File(context.filesDir, "memoria_maestra_v7.json")

    if (archivoCache.exists()) {
        try {
            val contenido = archivoCache.readText()
            return Json.decodeFromString<RecursoJuego>(contenido)
        } catch (e: Exception) {
            archivoCache.delete()
        }
    }

    val completo = mutableSetOf<String>()
    val pangramas = mutableListOf<String>()
    try {
        context.assets.open("dictionary_es.txt").bufferedReader().use { reader ->
            reader.forEachLine { linea ->
                val limpia = linea.trim().lowercase()
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

// 4. INTERFAZ PRINCIPAL (EL CORAZÓN DEL JUEGO)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipalConMenu(recursos: RecursoJuego, nivelCargado: DatosNivel, nivelInicialNumero: Int) {
    MusicaDeFondo()

    // --- VARIABLES DE ESTADO ---
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerState = drawerState,
                drawerContainerColor = AzulProfundo,
                modifier = Modifier.width(320.dp).fillMaxHeight()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MAPA DE SOLUCIONES", color = DoradoTesoro, fontWeight = FontWeight.Black, fontSize = 20.sp)
                    Button(
                        onClick = { solucionesReveladas = !solucionesReveladas },
                        colors = ButtonDefaults.buttonColors(containerColor = MaderaTimon),
                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth()
                    ) { Text(if (solucionesReveladas) "OCULTAR MAPA" else "VER MAPA COMPLETO") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(nivelActual.soluciones.toList().sorted()) { palabra ->
                            val encontrada = palabrasEncontradas.contains(palabra)
                            Surface(
                                color = if (encontrada) DoradoTesoro else BlancoEspuma.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    val texto = if (encontrada || solucionesReveladas) palabra.uppercase() else "• ".repeat(palabra.length)
                                    Text(text = texto, color = if (encontrada) Color.Black else BlancoEspuma, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    if (encontrada) Text("✓", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 20.sp)
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
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // SECCIÓN DEL BOTÍN: Ubicado arriba para jerarquía visual.
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaderaTimon),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(65.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Stars, contentDescription = null, tint = DoradoTesoro)
                                Spacer(Modifier.width(8.dp))
                                Text("BOTÍN TOTAL", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Text("$puntos Puntos", color = DoradoTesoro, fontSize = 24.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. EL TIMÓN
                    PanelDeJuego(
                        letrasExteriores = nivelActual.letrasExteriores,
                        letraObligatoria = nivelActual.letraCentral,
                        alPulsarLetra = { if (palabraActual.length < 10) palabraActual += it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. CAJA DE ENTRADA (Cerca del timón para comodidad)
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

                    // 3. BOTONES DE ACCIÓN
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
                                } else { mensajeFeedback = "Esa no sirve, marinero" }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DoradoTesoro)
                        ) { Text("ENVIAR", color = Color.Black, fontWeight = FontWeight.Black) }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 4. HISTORIAL DE PALABRAS (Recuadro más opaco para legibilidad)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PALABRAS DESCUBIERTAS (${palabrasEncontradas.size})", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color.White.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                        ) {
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                items(palabrasEncontradas.toList()) { palabra ->
                                    Text(text = palabra.uppercase() + "   ", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                }
                            }
                        }
                    }

                    // Spacer final con weight(1f) para empujar todo el contenido hacia arriba.
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// 5. CEREBRO: GENERADOR DE NIVELES (ALGORITMO)

/**
 * Esta función es pura lógica matemática. Elige un pangrama, baraja
 * letras y filtra el diccionario para asegurar un nivel de calidad.
 */
fun generarNivelPorDificultad(recursos: RecursoJuego, nivel: Int): DatosNivel {
    val minSoluciones = 10
    var resultado: DatosNivel? = null
    var intentos = 0
    while (resultado == null && intentos < 100) {
        intentos++
        val pangrama = recursos.listaPangramas.randomOrNull() ?: "marinos"
        val letrasSet = pangrama.toSet().map { it.toString().uppercase() }.shuffled()
        val central = letrasSet[0]
        val validasChar = pangrama.lowercase().toSet()
        val soluciones = recursos.diccionarioFiltrado.filter { p ->
            p.contains(central.lowercase()) && p.all { it in validasChar }
        }.toSet()
        if (soluciones.size >= minSoluciones) {
            resultado = DatosNivel(central, letrasSet.subList(1, 7), soluciones, 0)
        }
    }
    return resultado ?: DatosNivel("E", listOf("A", "T", "M", "S", "R", "O"), setOf("tesoro", "meta"), 0)
}