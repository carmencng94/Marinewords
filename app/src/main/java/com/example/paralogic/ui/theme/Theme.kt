package com.example.paralogic.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 1. Definición única de la paleta de colores oceánica
val TurquesaOceano = Color(0xFF00CED1)
val AzulProfundo = Color(0xFF021821)
val ArenaClara = Color(0xFFF5F5DC)
val MaderaTimon = Color(0xFF5D4037)
val DoradoTesoro = Color(0xFFF3E16D)
val BlancoEspuma = Color(0xFFF0FFFF)

// 2. Configuración del esquema de colores para el modo oscuro
private val DarkColorScheme = darkColorScheme(
    primary = AzulProfundo,
    secondary = MaderaTimon,
    tertiary = DoradoTesoro,
    background = Color(0xFF002B36), // Un turquesa mucho más oscuro para la noche
    surface = AzulProfundo,
    onPrimary = Color.White,
    onBackground = BlancoEspuma
)

// 3. Configuración del esquema de colores para el modo claro
private val LightColorScheme = lightColorScheme(
    primary = AzulProfundo,
    secondary = MaderaTimon,
    tertiary = DoradoTesoro,
    background = TurquesaOceano,
    surface = BlancoEspuma,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = AzulProfundo,
    onSurface = AzulProfundo
)

/**
 * Función principal del tema de la aplicación.
 * Aplica la estética náutica a todo el contenido que envuelva.
 */
@Composable
fun ParalogicTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // El color dinámico se desactiva habitualmente si queremos forzar nuestra paleta marina
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Determinamos qué esquema usar
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // Aplicamos el tema de Material con nuestra tipografía y colores
    MaterialTheme(
        colorScheme = colorScheme,
        // Aquí se usaría la tipografía definida en el archivo Typography.kt
        content = content
    )
}