package app.ak25.pocketflow.services

import app.ak25.pocketflow.storage.LocalStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

object NodeCreditRatesManager {
    private const val STORAGE_KEY = "node_credit_rates"

    val defaultRates = mapOf(
        // =========================
        // 🖼️ Image Generation
        // =========================
        "gpt_image_2_low" to 1,
        "gpt_image_2_medium" to 4,
        "gpt_image_2_high" to 15,

        "gemini_image3_pro_1K" to 15,
        "gemini_image3_pro_2K" to 15,
        "gemini_image3_pro_4K" to 30,

        // =========================
        // 🎥 Video Generation (per second)
        // =========================
        "veo_3.1_fast_no_audio" to 8,
        "veo_3.1_fast_audio" to 10,
        "veo_3.1_no_audio" to 15,
        "veo_3.1_audio" to 30,
        "seedance_2_1080p" to 25,

        // =========================
        // 🎬 Templates
        // =========================
        "product_ad_720p" to 35,
        "product_ad_1080p" to 38,

        "product_swap_720p" to 38,
        "product_swap_1080p" to 40,

        "product_ugc_720p" to 33,
        "product_ugc_1080p" to 35,

        "multi_shot_video_720p" to 10,    // per second
        "multi_shot_video_1080p" to 13,   // per second

        "ad_localization" to 18,
        "marketing_stock_1_low" to 21,
        "marketing_stock_1_medium" to 24,
        "marketing_stock_1_high" to 35,
        "marketing_stock_2_low" to 22,
        "marketing_stock_2_medium" to 28,
        "marketing_stock_2_high" to 50,
        "marketing_stock_3_low" to 23,
        "marketing_stock_3_medium" to 32,
        "marketing_stock_3_high" to 65,
        "marketing_stock_4_low" to 24,
        "marketing_stock_4_medium" to 36,
        "marketing_stock_4_high" to 80,
        "product_campaign" to 100
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun loadRates(): Map<String, Int> {
        val saved = LocalStorage.loadString(STORAGE_KEY) ?: return defaultRates
        return try {
            json.decodeFromString(MapSerializer(String.serializer(), Int.serializer()), saved)
        } catch (e: Exception) {
            defaultRates
        }
    }

    fun saveRates(rates: Map<String, Int>) {
        try {
            val serialized = json.encodeToString(MapSerializer(String.serializer(), Int.serializer()), rates)
            LocalStorage.saveString(STORAGE_KEY, serialized)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getRate(key: String, defaultRate: Int = 1): Int {
        return loadRates()[key] ?: defaultRate
    }
}
