package app.ak25.pocketflow.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ak25.pocketflow.ui.editor.AppIcons
import app.ak25.pocketflow.services.NodeCreditRatesManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageRatesScreen(onBack: () -> Unit) {
    val ratesState = remember { NodeCreditRatesManager.loadRates() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credit Deductions", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Standard credit deductions evaluated for executing each option:",
                    color = Color.Gray,
                    fontSize = 13.sp
                )

                // Category groups
                val categories = listOf(
                    "🖼️ Image Models" to listOf(
                        "gpt_image_2_low" to "GPT Image 2 – ⚡ Fast",
                        "gpt_image_2_medium" to "GPT Image 2 – ✨ Balanced",
                        "gpt_image_2_high" to "GPT Image 2 – 💎 Best",
                        "gemini_image3_pro_1K" to "Gemini 3 Pro – 1K",
                        "gemini_image3_pro_2K" to "Gemini 3 Pro – 2K",
                        "gemini_image3_pro_4K" to "Gemini 3 Pro – 4K"
                    ),
                    "🎥 Video Models (credits/sec)" to listOf(
                        "veo_3.1_fast_no_audio" to "Veo 3.1 Fast (No Audio)",
                        "veo_3.1_fast_audio" to "Veo 3.1 Fast (Audio)",
                        "veo_3.1_no_audio" to "Veo 3.1 (No Audio)",
                        "veo_3.1_audio" to "Veo 3.1 (Audio)",
                        "seedance_2_1080p" to "Seedance 2 (1080p)"
                    ),
                    "🎬 Templates & Recipes" to listOf(
                        "product_ad_720p" to "Product Ad (720p/sec)",
                        "product_ad_1080p" to "Product Ad (1080p/sec)",
                        "product_swap_720p" to "Product Swap (720p/sec)",
                        "product_swap_1080p" to "Product Swap (1080p/sec)",
                        "product_ugc_720p" to "Product UGC (720p/sec)",
                        "product_ugc_1080p" to "Product UGC (1080p/sec)",
                        "multi_shot_video_720p" to "Multi-Shot Video (720p/sec)",
                        "multi_shot_video_1080p" to "Multi-Shot Video (1080p/sec)",
                        "ad_localization" to "Ad Localization",
                        "marketing_stock_1_low" to "Marketing Stock (1 Img – ⚡ Fast)",
                        "marketing_stock_1_medium" to "Marketing Stock (1 Img – ✨ Balanced)",
                        "marketing_stock_1_high" to "Marketing Stock (1 Img – 💎 Best)",
                        "marketing_stock_2_low" to "Marketing Stock (2 Img – ⚡ Fast)",
                        "marketing_stock_2_medium" to "Marketing Stock (2 Img – ✨ Balanced)",
                        "marketing_stock_2_high" to "Marketing Stock (2 Img – 💎 Best)",
                        "marketing_stock_3_low" to "Marketing Stock (3 Img – ⚡ Fast)",
                        "marketing_stock_3_medium" to "Marketing Stock (3 Img – ✨ Balanced)",
                        "marketing_stock_3_high" to "Marketing Stock (3 Img – 💎 Best)",
                        "marketing_stock_4_low" to "Marketing Stock (4 Img – ⚡ Fast)",
                        "marketing_stock_4_medium" to "Marketing Stock (4 Img – ✨ Balanced)",
                        "marketing_stock_4_high" to "Marketing Stock (4 Img – 💎 Best)",
                        "product_campaign" to "Product Campaign (4 Images)"
                    ),
                    "🎙️ Audio / Text to Speech (Word Tiers)" to listOf(
                        "tts_tier_50_words" to "Audio – Up to 50 words",
                        "tts_tier_150_words" to "Audio – 51 to 150 words",
                        "tts_tier_300_words" to "Audio – 151 to 300 words",
                        "tts_tier_600_words" to "Audio – 301 to 600 words",
                        "tts_tier_1000_words" to "Audio – 601+ words"
                    )
                )

                categories.forEach { (catName, items) ->
                    Text(
                        catName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        letterSpacing = 1.sp
                    )
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            items.forEachIndexed { index, (key, label) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        label,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val currentValue = ratesState[key] ?: 1
                                    Text(
                                        text = "$currentValue",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (index < items.lastIndex) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                        thickness = 0.5.dp,
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}
