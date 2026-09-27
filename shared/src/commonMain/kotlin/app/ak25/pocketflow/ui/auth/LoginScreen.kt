package app.ak25.pocketflow.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource
import pocketflow.shared.generated.resources.EduAUVICWANTHand
import pocketflow.shared.generated.resources.Res
import pocketflow.shared.generated.resources.pocketflow_logo

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.SolidColor

private val GoogleIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Google",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Red Segment
        path(fill = SolidColor(Color(0xFFEA4335))) {
            moveTo(12.0f, 5.04f)
            curveTo(13.62f, 5.04f, 15.06f, 5.6f, 16.21f, 6.68f)
            lineTo(19.36f, 3.53f)
            curveTo(17.45f, 1.73f, 14.99f, 1.0f, 12.0f, 1.0f)
            curveTo(7.37f, 1.0f, 3.37f, 3.65f, 1.4f, 7.56f)
            lineTo(5.19f, 10.5f)
            curveTo(6.09f, 7.79f, 8.61f, 6.04f, 12.0f, 6.04f)
            close()
        }
        // Green Segment
        path(fill = SolidColor(Color(0xFF34A853))) {
            moveTo(12.0f, 23.0f)
            curveTo(14.97f, 23.0f, 17.46f, 22.02f, 19.28f, 20.34f)
            lineTo(15.71f, 17.57f)
            curveTo(14.73f, 18.23f, 13.48f, 18.63f, 12.0f, 18.63f)
            curveTo(8.61f, 18.63f, 6.09f, 16.88f, 5.07f, 13.8f)
            lineTo(1.28f, 16.74f)
            curveTo(3.37f, 20.35f, 7.37f, 23.0f, 12.0f, 23.0f)
            close()
        }
        // Yellow Segment
        path(fill = SolidColor(Color(0xFFFBBC05))) {
            moveTo(5.07f, 13.8f)
            curveTo(4.82f, 13.04f, 4.67f, 12.23f, 4.67f, 11.4f)
            curveTo(4.67f, 10.57f, 4.82f, 9.76f, 5.07f, 9.0f)
            lineTo(1.28f, 6.06f)
            curveTo(0.46f, 7.7f, 0.0f, 9.55f, 0.0f, 11.4f)
            curveTo(0.0f, 13.25f, 0.46f, 15.1f, 1.28f, 16.74f)
            lineTo(5.07f, 13.8f)
            close()
        }
        // Blue Segment
        path(fill = SolidColor(Color(0xFF4285F4))) {
            moveTo(23.49f, 12.27f)
            curveTo(23.49f, 11.46f, 23.42f, 10.65f, 23.29f, 9.84f)
            lineTo(12.0f, 9.84f)
            verticalLineTo(14.35f)
            horizontalLineTo(18.46f)
            curveTo(18.17f, 15.83f, 17.32f, 17.08f, 16.06f, 17.92f)
            lineTo(19.63f, 20.69f)
            curveTo(21.71f, 18.77f, 23.49f, 15.95f, 23.49f, 12.27f)
            close()
        }
    }.build()

@Composable
fun LoginScreen(
    viewModel: SharedAuthViewModel,
    onGoogleSignInClick: () -> Unit = {},
    onAppleSignInClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 28.dp)
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── TOP / CENTER AREA: Logo & Title ──────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))
            
            androidx.compose.foundation.Image(
                painter = painterResource(Res.drawable.pocketflow_logo),
                contentDescription = "PocketFlow Logo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "pocketflow",
                fontSize = 32.sp,
                fontFamily = FontFamily(Font(Res.font.EduAUVICWANTHand)),
                color = Color(0xFF1E293B)
            )
            Text(
                text = "Sign in to continue",
                fontSize = 14.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Error message display (social sign-in failures)
            viewModel.errorMessage?.let { err ->
                Text(
                    text = err,
                    fontSize = 12.sp,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ── BOTTOM AREA: Buttons ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Google Sign-In Button
            OutlinedButton(
                onClick = { onGoogleSignInClick() },
                enabled = !viewModel.isLoading,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, Color(0xFFD6DCE5)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF1E293B)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = GoogleIcon,
                        contentDescription = "Google Icon",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Continue with Google",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E293B)
                    )
                }
            }
        }
    }
}