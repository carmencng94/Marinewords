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
 * Representa una única letra interactiva del juego, con un diseño de "boya" flotante.
 *
 * @param letra El carácter a mostrar en la boya.
 * @param esCentral Booleano para determinar si es la letra principal (true) o una secundaria (false).
 *                  Esto cambia su color de fondo y de texto para destacarla.
 * @param onClick Lambda que se ejecuta al pulsar la boya, devolviendo la letra que contiene.
 */
@Composable
fun BoyaLetra(letra: String, esCentral: Boolean = false, onClick: (String) -> Unit) {
    // Determina el esquema de color basado en si la boya es la central o no.
    val fondoCromado = if (esCentral) DoradoTesoro else BlancoEspuma
    val colorDelTexto = if (esCentral) Color.Black else AzulProfundo

    // ---- Animación de "Latido" o "Burbuja" ----
    // `rememberInfiniteTransition` crea una animación que se repite de forma indefinida.
    val infiniteTransition = rememberInfiniteTransition(label = "burbuja_letra")

    // `animateFloat` define el valor que cambiará con el tiempo. En este caso, la escala.
    val escala by infiniteTransition.animateFloat(
        initialValue = 1f,      // Tamaño normal
        targetValue = 1.07f,    // Crece un 7%
        animationSpec = infiniteRepeatable(
            // `tween` define cómo se pasa del valor inicial al final (duración y ritmo).
            animation = tween(1400, easing = FastOutSlowInEasing),
            // `RepeatMode.Reverse` hace que la animación vaya de 1 a 1.07 y luego de 1.07 a 1.
            repeatMode = RepeatMode.Reverse
        ),
        label = "escala_boya"
    )
    // ---- Fin de la Animación ----

    Card(
        modifier = Modifier
            .size(75.dp) // Tamaño fijo para la boya.
            .padding(4.dp) // Espacio para que no se peguen entre sí.
            .scale(escala) // Aplicamos el valor de la animación a la escala del componente.
            .clickable { onClick(letra) }, // El componente es pulsable y notifica la letra.
        shape = CircleShape, // Forma circular.
        colors = CardDefaults.cardColors(containerColor = fondoCromado), // Color de fondo.
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp) // Sombra para dar profundidad.
    ) {
        // `Box` se usa para centrar el texto dentro de la `Card`.
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
 * Dibuja el panel de juego completo, conocido como el "Timón", que contiene
 * la letra central y las 6 letras exteriores dispuestas en círculo.
 *
 * @param letrasExteriores Una lista con las 6 letras que rodearán la letra central.
 * @param letraObligatoria La letra central del juego.
 * @param alPulsarLetra Lambda que se ejecuta cuando se pulsa cualquier `BoyaLetra`.
 */
@Composable
fun PanelDeJuego(
    letrasExteriores: List<String>,
    letraObligatoria: String,
    alPulsarLetra: (String) -> Unit
) {
    // `Box` actúa como un lienzo para posicionar las boyas libremente.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp), // Altura fija para el área del timón.
        contentAlignment = Alignment.Center // Centra los elementos hijos por defecto.
    ) {
        // Dibuja el borde de madera del timón.
        Surface(
            modifier = Modifier.size(250.dp), // El diámetro del borde.
            shape = CircleShape,
            color = Color.Transparent, // Es solo un borde, el interior es transparente.
            border = BorderStroke(12.dp, MaderaTimon)
        ) {}

        // Coloca la BoyaLetra central. Se renderiza justo en el centro del Box.
        BoyaLetra(letra = letraObligatoria, esCentral = true, onClick = alPulsarLetra)

        // ---- Posicionamiento de las 6 letras exteriores ----
        // Se definen 6 modificadores `offset` para desplazar cada boya desde el centro
        // y formar un hexágono perfecto.
        val posiciones = listOf(
            Modifier.offset(y = (-115).dp),                     // Arriba
            Modifier.offset(x = 100.dp, y = (-55).dp),          // Arriba-derecha
            Modifier.offset(x = 100.dp, y = 55.dp),           // Abajo-derecha
            Modifier.offset(y = 115.dp),                      // Abajo
            Modifier.offset(x = (-100).dp, y = 55.dp),          // Abajo-izquierda
            Modifier.offset(x = (-100).dp, y = (-55).dp)           // Arriba-izquierda
        )

        // Itera sobre las letras exteriores y las coloca en su posición.
        letrasExteriores.take(6).forEachIndexed { indice, letra ->
            // `Box` con el modificador de posición para colocar la boya.
            Box(modifier = posiciones[indice]) {
                BoyaLetra(letra = letra, esCentral = false, onClick = alPulsarLetra)
            }
        }
    }
}
