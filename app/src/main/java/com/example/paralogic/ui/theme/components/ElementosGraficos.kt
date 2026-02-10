package com.example.paralogic.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.paralogic.ui.theme.*

/**
 * Componente de cada letra (boya) con efecto de latido de burbuja.
 */
@Composable
fun BoyaLetra(letra: String, esCentral: Boolean = false, onClick: (String) -> Unit) {
    val fondoCromado = if (esCentral) DoradoTesoro else BlancoEspuma
    val colorDelTexto = if (esCentral) Color.Black else AzulProfundo

    // Animación de escala infinita (Efecto Burbuja) [cite: 2026-01-28]
    val infiniteTransition = rememberInfiniteTransition(label = "burbuja_letra")
    val escala by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f, // Crece un 7% para simular el latido
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "escala"
    )

    Card(
        modifier = Modifier
            .size(75.dp)
            .padding(4.dp)
            .scale(escala) // Aplicamos el latido aquí
            .clickable { onClick(letra) }, // Notifica qué letra se pulsó
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = fondoCromado),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = letra.uppercase(),
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = colorDelTexto
            )
        }
    }
}

/**
 * Dibuja el timón usando las letras aleatorias del nivel actual.
 */
@Composable
fun PanelDeJuego(
    letrasExteriores: List<String>,
    letraObligatoria: String,
    alPulsarLetra: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(420.dp),
        contentAlignment = Alignment.Center
    ) {
        // Círculo de madera del timón
        Surface(
            modifier = Modifier.size(280.dp),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(12.dp, MaderaTimon)
        ) {}

        // Letra Central (Obligatoria) [cite: 2026-01-28]
        BoyaLetra(letra = letraObligatoria, esCentral = true, onClick = alPulsarLetra)

        // Posiciones matemáticas para colocar las 6 letras exteriores en círculo
        val posiciones = listOf(
            Modifier.offset(y = (-115).dp),
            Modifier.offset(x = 100.dp, y = (-55).dp),
            Modifier.offset(x = 100.dp, y = 55.dp),
            Modifier.offset(y = 115.dp),
            Modifier.offset(x = (-100).dp, y = 55.dp),
            Modifier.offset(x = (-100).dp, y = (-55).dp)
        )

        // Dibujamos cada una de las 6 letras exteriores recibidas del nivel [cite: 2026-01-28]
        letrasExteriores.take(6).forEachIndexed { indice, letra ->
            Box(modifier = posiciones[indice]) {
                BoyaLetra(letra = letra, esCentral = false, onClick = alPulsarLetra)
            }
        }
    }
}