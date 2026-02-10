package com.example.paralogic

// Importamos las herramientas necesarias. Piensa en esto como "traer la caja de herramientas" al taller.
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
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

// ==========================================================
// 1. MODELOS DE DATOS (LOS MOLDES DE INFORMACIÓN)
// ==========================================================

/**
 * Las "Data Class" son como carpetas donde guardamos datos ordenados.
 * @Serializable es una etiqueta que le dice a Kotlin: "Prepara esta carpeta para que
 * pueda convertirse en un mensaje de texto largo (JSON) y guardarse en un archivo".
 */
@Serializable
data class DatosNivel(
    val letraCentral: String,      // La letra roja obligatoria
    var letrasExteriores: List<String>, // Las 6 letras azules
    val soluciones: Set<String>,   // Todas las palabras posibles (Set evita repetidas)
    val puntuacionMaxima: Int      // Total de puntos del nivel
)

@Serializable
data class RecursoJuego(
    val diccionarioFiltrado: Set<String>, // Todas las palabras de 3 a 10 letras del idioma
    val listaPangramas: List<String>      // Solo las palabras que tienen 7 letras distintas
)

/**
 * Esta es la clase principal. Es el punto de partida que Android busca al abrir la app.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Esto hace que la app use toda la pantalla, incluso detrás de la hora y la batería.
        enableEdgeToEdge()

        setContent {
            // ParalogicTheme aplica tus colores y estilos personalizados.
            ParalogicTheme {
                // Llamamos a la primera función que controla si cargamos datos o jugamos.
                PantallaDeControl()
            }
        }
    }
}

// ==========================================================
// 2. SISTEMA DE MÚSICA DE FONDO (DISPOSABLE EFFECT)
// ==========================================================

/**
 * Gestionar sonido en apps es delicado. Si no lo hacemos bien, la música sigue sonando
 * incluso si cierras la app.
 */
@Composable
fun MusicaDeFondo() {
    val contexto = LocalContext.current

    // 'remember' hace que Android "recuerde" este objeto.
    // Sin esto, la música intentaría empezar de cero cada vez que tocas un botón.
    val mp = remember {
        // Buscamos el archivo en la carpeta res/raw/tema_fondo_di
        MediaPlayer.create(contexto, R.raw.musicafondo).apply {
            isLooping = true     // Cuando acabe, que empiece otra vez.
            setVolume(0.2f, 0.2f) // Volumen bajito (30%) para no molestar.
        }
    }

    // 'DisposableEffect' es como un interruptor inteligente:
    // Se enciende al entrar a la pantalla y tiene un "limpiador" al salir.
    DisposableEffect(Unit) {
        mp.start() // Dale al Play.

        // Esta parte es CRUCIAL: se ejecuta cuando la pantalla se destruye o cierras la app.
        onDispose {
            mp.stop()    // Para la música.
            mp.release() // Borra el reproductor de la memoria RAM para que el móvil no vaya lento.
        }
    }
}

// ==========================================================
// 3. GESTOR DE CARGA (LA ADUANA)
// ==========================================================

/**
 * Esta función decide qué mostrar: ¿Una barrita de carga o el juego?
 */
@Composable
fun PantallaDeControl() {
    val contexto = LocalContext.current

    // Estos son estados (States). Si cambian, la pantalla se actualiza sola ("Recomposición").
    var recursos by remember { mutableStateOf<RecursoJuego?>(null) }
    var nivelInicial by remember { mutableStateOf<DatosNivel?>(null) }

    // 'LaunchedEffect' ejecuta código una sola vez. Es perfecto para cargar archivos al inicio.
    LaunchedEffect(Unit) {
        // 'withContext(Dispatchers.IO)' significa: "Haz este trabajo pesado en segundo plano".
        // Así la pantalla no se queda congelada mientras lee miles de palabras.
        withContext(Dispatchers.IO) {
            val r = cargarRecursosConCacheSegura(contexto) // Lee el diccionario
            val n = generarNivelPorDificultad(r, 1)        // Crea el primer nivel

            // Guardamos los resultados en los estados. Esto avisa a Compose para que cambie la pantalla.
            recursos = r
            nivelInicial = n
        }
    }

    // Lógica visual:
    if (recursos == null || nivelInicial == null) {
        // Si aún no han cargado los datos, dibujamos la carga.
        Box(modifier = Modifier.fillMaxSize().background(AzulProfundo), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = DoradoTesoro) // El circulito que da vueltas.
                Spacer(Modifier.height(20.dp))
                Text("Navegando a tu destino...", color = BlancoEspuma, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        // Si ya tenemos los datos (recursos y nivel), saltamos al juego real.
        PantallaPrincipalConMenu(recursos!!, nivelInicial!!, 1)
    }
}

/**
 * SISTEMA DE CACHÉ: Es como una "chuleta".
 * La primera vez lee el archivo .txt (lento). La segunda lee un .json (rápido).
 */
fun cargarRecursosConCacheSegura(context: Context): RecursoJuego {
    // Creamos un archivo en la memoria interna del móvil.
    val archivoCache = File(context.filesDir, "memoria_maestra_v7.json")

    // Paso 1: ¿Ya existe el archivo rápido?
    if (archivoCache.exists()) {
        try {
            val contenido = archivoCache.readText()
            // Convertimos el texto JSON de vuelta a la clase RecursoJuego.
            return Json.decodeFromString<RecursoJuego>(contenido)
        } catch (e: Exception) {
            // Si algo falló (archivo corrupto), lo borramos para forzar recarga.
            archivoCache.delete()
        }
    }

    // Paso 2: Si no hay caché, leemos el diccionario original línea por línea.
    val completo = mutableSetOf<String>()
    val pangramas = mutableListOf<String>()
    try {
        // Abrimos el archivo de la carpeta 'assets'.
        context.assets.open("dictionary_es.txt").bufferedReader().use { reader ->
            reader.forEachLine { linea ->
                val limpia = linea.trim().lowercase()
                // Aplicamos tu regla: Solo palabras de 3 a 10 letras.
                if (limpia.length in 3..10) {
                    completo.add(limpia)
                    // Si tiene 7 letras distintas, guárdalo para crear niveles con él.
                    if (limpia.toSet().size == 7) pangramas.add(limpia)
                }
            }
        }
        val nuevoRecurso = RecursoJuego(completo, pangramas)
        // Guardamos este resultado en JSON para que la próxima vez sea instantáneo.
        archivoCache.writeText(Json.encodeToString(nuevoRecurso))
        return nuevoRecurso
    } catch (e: Exception) {
        // Red de seguridad: si el archivo de texto no existe, devolvemos algo para que no falle.
        return RecursoJuego(setOf("mar", "tesoro"), listOf("marinos"))
    }
}

// ==========================================================
// 4. INTERFAZ PRINCIPAL (EL CORAZÓN DEL JUEGO)
// ==========================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaPrincipalConMenu(recursos: RecursoJuego, nivelCargado: DatosNivel, nivelInicialNumero: Int) {
    // Encendemos la música.
    MusicaDeFondo()

    // --- VARIABLES DE ESTADO ---
    // En Compose, si quieres que algo cambie en pantalla (como los puntos),
    // debes usar 'mutableStateOf' y 'remember'.
    var nivelActual by remember { mutableStateOf(nivelCargado) }
    var numeroDeNivel by remember { mutableIntStateOf(nivelInicialNumero) }
    var palabraActual by remember { mutableStateOf("") }
    var puntos by remember { mutableIntStateOf(0) }
    var mensajeFeedback by remember { mutableStateOf("¡A toda vela!") }
    var palabrasEncontradas by remember { mutableStateOf(setOf<String>()) }
    var solucionesReveladas by remember { mutableStateOf(false) }

    // Herramientas para el menú lateral (Drawer).
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope() // Se usa para lanzar animaciones (como abrir el menú).

    // Título dinámico: cambia según el número de nivel.
    val rangoActual = when(numeroDeNivel) {
        in 1..3 -> "Grumete"
        in 4..7 -> "Marinero"
        else -> "Capitán"
    }

    // El 'ModalNavigationDrawer' es el componente que permite deslizar el menú desde la izquierda.
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // Esto es lo que hay DENTRO del menú lateral.
            ModalDrawerSheet(
                drawerState = drawerState,
                drawerContainerColor = AzulProfundo,
                modifier = Modifier.width(300.dp).fillMaxHeight()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("MAPA DE SOLUCIONES", color = DoradoTesoro, fontWeight = FontWeight.Black)
                    Button(
                        onClick = { solucionesReveladas = !solucionesReveladas },
                        colors = ButtonDefaults.buttonColors(containerColor = MaderaTimon),
                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth()
                    ) {
                        Text(if (solucionesReveladas) "OCULTAR MAPA" else "VER MAPA COMPLETO")
                    }
                    // LazyColumn es como un listado que solo gasta memoria en lo que se ve.
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(nivelActual.soluciones.toList().sorted()) { palabra ->
                            val encontrada = palabrasEncontradas.contains(palabra)
                            Surface(
                                color = if (encontrada) DoradoTesoro else BlancoEspuma.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    // Si no la has encontrado, se ve con puntitos.
                                    val texto = if (encontrada || solucionesReveladas) palabra.uppercase() else "• ".repeat(palabra.length)
                                    Text(texto, color = if (encontrada) Color.Black else BlancoEspuma)
                                    if (encontrada) Text("✓", color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) {
        // CAPA DE FONDO (Debajo de todo)
        Box(modifier = Modifier.fillMaxSize()) {
            // Imagen del mar
            Image(painter = painterResource(id = R.drawable.fondo2), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            // Capa oscura para que las letras blancas se lean bien.
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

            // Scaffold es el "esqueleto" de la pantalla (Barra arriba, barra abajo y centro).
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
                },
                bottomBar = {
                    // SECCIÓN INFERIOR: Botín acumulado.
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaderaTimon),
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(65.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("BOTÍN TOTAL", color = Color.White, fontWeight = FontWeight.Bold)
                            Text("$puntos Puntos", color = DoradoTesoro, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            ) { innerPadding ->
                // CONTENIDO CENTRAL (El juego en sí)
                Column(
                    modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    // 1. EL TIMÓN (Componente personalizado que creaste)
                    PanelDeJuego(
                        letrasExteriores = nivelActual.letrasExteriores,
                        letraObligatoria = nivelActual.letraCentral,
                        alPulsarLetra = { if (palabraActual.length < 10) palabraActual += it }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // 2. CAJA DE ENTRADA BLANCA
                    Surface(color = Color.White.copy(alpha = 0.9f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(0.8f).height(55.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(palabraActual.uppercase(), fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.DarkGray)
                        }
                    }

                    Text(mensajeFeedback, color = Color.White, modifier = Modifier.padding(vertical = 10.dp))

                    // 3. BOTONES DE ACCIÓN
                    Row {
                        Button(
                            onClick = { if (palabraActual.isNotEmpty()) palabraActual = palabraActual.dropLast(1) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaderaTimon)
                        ) { Text("BORRAR") }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = {
                                val intento = palabraActual.lowercase().trim()
                                // Lógica de validación: ¿está en el diccionario del nivel?
                                if (nivelActual.soluciones.contains(intento) && !palabrasEncontradas.contains(intento)) {
                                    palabrasEncontradas = palabrasEncontradas + intento
                                    puntos += (intento.length - 2) // Por ejemplo: 3 letras = 1 punto.
                                    mensajeFeedback = "¡Excelente hallazgo!"
                                    palabraActual = ""
                                } else {
                                    mensajeFeedback = "Esa no sirve, marinero"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DoradoTesoro)
                        ) { Text("ENVIAR", color = Color.Black, fontWeight = FontWeight.Bold) }
                    }

                    Spacer(modifier = Modifier.height(30.dp))

                    // 4. HISTORIAL DE PALABRAS (LazyRow para deslizar de lado)
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text("PALABRAS DESCUBIERTAS (${palabrasEncontradas.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                items(palabrasEncontradas.toList()) { palabra ->
                                    Text(palabra.uppercase() + "   ", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================
// 5. CEREBRO: GENERADOR DE NIVELES (ALGORITMO)
// ==========================================================

/**
 * Esta función no dibuja nada, es pura lógica matemática.
 */
fun generarNivelPorDificultad(recursos: RecursoJuego, nivel: Int): DatosNivel {
    val minSoluciones = 10
    var resultado: DatosNivel? = null
    var intentos = 0

    // Bucle 'while': repetimos el proceso hasta encontrar un nivel que tenga sentido jugar.
    while (resultado == null && intentos < 100) {
        intentos++

        // Elige una palabra de 7 letras al azar de la lista.
        val pangrama = recursos.listaPangramas.randomOrNull() ?: "marinos"

        // Mezclamos sus letras y elegimos la primera para el centro.
        val letrasSet = pangrama.toSet().map { it.toString().uppercase() }.shuffled()
        val central = letrasSet[0]
        val validasChar = pangrama.lowercase().toSet()

        // Filtrado: Buscamos en el diccionario general qué palabras cumplen que:
        // 1. Contengan la letra central.
        // 2. SOLO usen letras de las 7 que hemos elegido.
        val soluciones = recursos.diccionarioFiltrado.filter { p ->
            p.contains(central.lowercase()) && p.all { it in validasChar }
        }.toSet()

        // Si el nivel tiene al menos 10 palabras posibles, lo damos por bueno.
        if (soluciones.size >= minSoluciones) {
            resultado = DatosNivel(central, letrasSet.subList(1, 7), soluciones, 0)
        }
    }
    // Si después de 100 intentos falla todo, devolvemos un nivel de emergencia.
    return resultado ?: DatosNivel("E", listOf("A", "T", "M", "S", "R", "O"), setOf("tesoro", "meta"), 0)
}