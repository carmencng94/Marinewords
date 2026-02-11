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
import kotlin.math.cos
import kotlin.math.sin

/**
 * Representa una única letra interactiva del juego, con un diseño de "boya" flotante.
 */
@Composable
fun BoyaLetra(letra: String, esCentral: Boolean = false, onClick: (String) -> Unit) {
    val fondoCromado = if (esCentral) DoradoTesoro else BlancoEspuma
    val colorDelTexto = if (esCentral) Color.Black else AzulProfundo

    val infiniteTransition = rememberInfiniteTransition(label = "burbuja_letra")
    val escala by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "escala_boya"
    )

    Card(
        modifier = Modifier
            .size(75.dp) // Mantenemos un tamaño fijo para las boyas para que sean legibles.
            .padding(4.dp)
            .scale(escala)
            .clickable { onClick(letra) },
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
 * Dibuja el panel de juego completo, conocido como el "Timón", de forma adaptativa.
 * Se ajusta a cualquier tamaño de pantalla usando BoxWithConstraints y trigonometría.
 */
@Composable
fun PanelDeJuego(
    letrasExteriores: List<String>,
    letraObligatoria: String,
    alPulsarLetra: (String) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f), // Forzamos a que el área de juego sea un cuadrado perfecto.
        contentAlignment = Alignment.Center
    ) {
        // ---- Cálculo de Radios para un Diseño Adaptativo ----
        // El objetivo es que el aro de madera pase por el centro de las boyas exteriores
        // y que todo el conjunto quepa perfectamente en la pantalla.

        // 1. Definimos el diámetro total que ocupará el timón con las boyas incluidas.
        //    Dejamos un pequeño margen del 5% a cada lado (total 10%).
        val diametroTotalVisible = maxWidth * 0.9f

        // 2. Para que el aro pase por el centro de las boyas, su diámetro debe ser el
        //    diámetro total menos el tamaño de una boya (la mitad de una boya a cada lado).
        val diametroDelAro = diametroTotalVisible - 75.dp // 75.dp es el tamaño de BoyaLetra

        // 3. El radio para posicionar las boyas es, por tanto, la mitad del diámetro del aro.
        val radioDeBoyas = diametroDelAro / 2

        // Dibuja el aro de madera del timón usando el diámetro dinámico.
        Surface(
            modifier = Modifier.size(diametroDelAro),
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(12.dp, MaderaTimon)
        ) {}

        // Colocamos la letra central. Siempre estará en el centro del `Box`.
        BoyaLetra(letra = letraObligatoria, esCentral = true, onClick = alPulsarLetra)

        // ---- Posicionamiento Trigonométrico de las Letras Exteriores ----
        val anguloEntreLetras = (2 * Math.PI / letrasExteriores.size).toFloat()

        letrasExteriores.forEachIndexed { indice, letra ->
            val anguloActual = anguloEntreLetras * indice

            // Usamos el `radioDeBoyas` que hemos calculado para asegurar la alineación.
            val offsetX = radioDeBoyas * cos(anguloActual)
            val offsetY = radioDeBoyas * sin(anguloActual)

            Box(modifier = Modifier.offset(x = offsetX, y = offsetY)) {
                BoyaLetra(letra = letra, esCentral = false, onClick = alPulsarLetra)
            }
        }
    }
}
