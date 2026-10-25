package com.example.meteocompose

import android.app.Application
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

data class CitySearchResponse(
    @SerializedName("results") val results: List<City>?
)

data class City(
    @SerializedName("name") val name: String,
    @SerializedName("country") val country: String?,
    @SerializedName("admin1") val admin1: String? = null,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("country_code") val countryCode: String? = null
) {

    val plaka: Int? get() = Plaka.bul(admin1) ?: Plaka.bul(name)

    val badgeText: String
        get() = plaka?.toString()?.padStart(2, '0') ?: name.take(1).uppercase()

    val subtitle: String
        get() = listOfNotNull(admin1?.takeIf { it != name }, country).joinToString(", ")

    val key: String get() = "$name|$latitude|$longitude"
}


object Plaka {
    private val iller = listOf(
        "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Amasya", "Ankara", "Antalya", "Artvin", "Aydın",
        "Balıkesir", "Bilecik", "Bingöl", "Bitlis", "Bolu", "Burdur", "Bursa", "Çanakkale", "Çankırı",
        "Çorum", "Denizli", "Diyarbakır", "Edirne", "Elazığ", "Erzincan", "Erzurum", "Eskişehir",
        "Gaziantep", "Giresun", "Gümüşhane", "Hakkari", "Hatay", "Isparta", "Mersin", "İstanbul", "İzmir",
        "Kars", "Kastamonu", "Kayseri", "Kırklareli", "Kırşehir", "Kocaeli", "Konya", "Kütahya", "Malatya",
        "Manisa", "Kahramanmaraş", "Mardin", "Muğla", "Muş", "Nevşehir", "Niğde", "Ordu", "Rize",
        "Sakarya", "Samsun", "Siirt", "Sinop", "Sivas", "Tekirdağ", "Tokat", "Trabzon", "Tunceli",
        "Şanlıurfa", "Uşak", "Van", "Yozgat", "Zonguldak", "Aksaray", "Bayburt", "Karaman", "Kırıkkale",
        "Batman", "Şırnak", "Bartın", "Ardahan", "Iğdır", "Yalova", "Karabük", "Kilis", "Osmaniye", "Düzce"
    )

    private fun norm(s: String): String =
        s.trim().lowercase(Locale.forLanguageTag("tr"))
            .replace('â', 'a').replace('î', 'i').replace('û', 'u')

    private val map: Map<String, Int> =
        iller.mapIndexed { i, ad -> norm(ad) to (i + 1) }.toMap() +
                mapOf("afyon" to 3, "içel" to 33, "maraş" to 46, "urfa" to 63)

    fun bul(ad: String?): Int? = ad?.let { map[norm(it)] }
}


object FeaturedCities {
    val list = listOf(
        City("Ankara", "Türkiye", "Ankara", 39.9334, 32.8597),
        City("İstanbul", "Türkiye", "İstanbul", 41.0082, 28.9784),
        City("İzmir", "Türkiye", "İzmir", 38.4237, 27.1428),
        City("Bursa", "Türkiye", "Bursa", 40.1885, 29.0610),
        City("Antalya", "Türkiye", "Antalya", 36.8969, 30.7133),
        City("Adana", "Türkiye", "Adana", 37.0000, 35.3213),
        City("Konya", "Türkiye", "Konya", 37.8746, 32.4932),
        City("Trabzon", "Türkiye", "Trabzon", 41.0027, 39.7168),
        City("Bolu", "Türkiye", "Bolu", 40.7395, 31.6116),
        City("Eskişehir", "Türkiye", "Eskişehir", 39.7767, 30.5206),
        City("Gaziantep", "Türkiye", "Gaziantep", 37.0662, 37.3833),
        City("Erzurum", "Türkiye", "Erzurum", 39.9055, 41.2658)
    )
}


data class ForecastResponse(
    @SerializedName("current") val current: CurrentDto?,
    @SerializedName("hourly") val hourly: HourlyDto?,
    @SerializedName("daily") val daily: DailyDto?
)

data class CurrentDto(
    @SerializedName("time") val time: String?,
    @SerializedName("temperature_2m") val temperature: Double,
    @SerializedName("apparent_temperature") val feelsLike: Double?,
    @SerializedName("weather_code") val weatherCode: Int,
    @SerializedName("relative_humidity_2m") val humidity: Int?,
    @SerializedName("wind_speed_10m") val windSpeed: Double?,
    @SerializedName("is_day") val isDay: Int?
)

data class HourlyDto(
    @SerializedName("time") val time: List<String>?,
    @SerializedName("temperature_2m") val temperature: List<Double?>?,
    @SerializedName("weather_code") val weatherCode: List<Int?>?,
    @SerializedName("precipitation_probability") val rain: List<Int?>?
)

data class DailyDto(
    @SerializedName("time") val time: List<String>?,
    @SerializedName("weather_code") val weatherCode: List<Int?>?,
    @SerializedName("temperature_2m_max") val max: List<Double?>?,
    @SerializedName("temperature_2m_min") val min: List<Double?>?,
    @SerializedName("sunrise") val sunrise: List<String>?,
    @SerializedName("sunset") val sunset: List<String>?
)


data class CurrentWeather(
    val temperature: Double,
    val feelsLike: Double,
    val weatherCode: Int,
    val humidity: Int,
    val windSpeed: Double,
    val isDay: Boolean
)

data class HourlyItem(val hour: String, val temperature: Int, val weatherCode: Int, val rainChance: Int?)

data class DailyItem(val day: String, val weatherCode: Int, val max: Int, val min: Int)


data class AirResponse(
    @SerializedName("current") val current: AirCurrentDto?
)

data class AirCurrentDto(
    @SerializedName("european_aqi") val aqi: Double?,
    @SerializedName("pm10") val pm10: Double?,
    @SerializedName("pm2_5") val pm25: Double?,
    @SerializedName("uv_index") val uv: Double?
)

data class AirQuality(val aqi: Int?, val pm25: Double?, val pm10: Double?, val uv: Double?)

fun AirCurrentDto.toAirQuality(): AirQuality? =
    if (aqi == null && uv == null) null else AirQuality(aqi?.roundToInt(), pm25, pm10, uv)

data class Forecast(
    val current: CurrentWeather,
    val hourly: List<HourlyItem>,
    val daily: List<DailyItem>,
    val sunrise: String?,
    val sunset: String?,
    val air: AirQuality? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val fromCache: Boolean = false
)

class Level(val label: String, val color: Color)

fun aqiLevel(aqi: Int): Level = when {
    aqi <= 20 -> Level("Çok iyi", Color(0xFF43A047))
    aqi <= 40 -> Level("İyi", Color(0xFF7CB342))
    aqi <= 60 -> Level("Orta", Color(0xFFF9A825))
    aqi <= 80 -> Level("Kötü", Color(0xFFFB8C00))
    aqi <= 100 -> Level("Çok kötü", Color(0xFFE53935))
    else -> Level("Tehlikeli", Color(0xFF8E24AA))
}

fun uvLevel(uv: Double): Level = when {
    uv < 3 -> Level("Düşük", Color(0xFF43A047))
    uv < 6 -> Level("Orta", Color(0xFFF9A825))
    uv < 8 -> Level("Yüksek", Color(0xFFFB8C00))
    uv < 11 -> Level("Çok yüksek", Color(0xFFE53935))
    else -> Level("Aşırı", Color(0xFF8E24AA))
}

fun airAdvice(air: AirQuality): String? = when {
    (air.aqi ?: 0) > 80 -> "😷 Hava çok kirli, mümkünse dışarıda uzun kalma"
    (air.aqi ?: 0) > 60 -> "😷 Hava kalitesi kötü, hassas biriysen maske tak"
    (air.uv ?: 0.0) >= 8 -> "🕶️ UV çok yüksek, gölgede kal ve güneş kremi sür"
    (air.uv ?: 0.0) >= 3 -> "🧴 Güneş kremi sürmeyi unutma"
    else -> null
}


data class WeatherCondition(val emoji: String, val label: String) {
    companion object {
        fun fromCode(code: Int): WeatherCondition = when (code) {
            0 -> WeatherCondition("☀️", "Açık")
            1, 2 -> WeatherCondition("🌤️", "Az bulutlu")
            3 -> WeatherCondition("☁️", "Kapalı")
            45, 48 -> WeatherCondition("🌫️", "Sisli")
            in 51..57 -> WeatherCondition("🌦️", "Çisenti")
            in 61..67 -> WeatherCondition("🌧️", "Yağmurlu")
            in 71..77 -> WeatherCondition("❄️", "Karlı")
            in 80..82 -> WeatherCondition("🌦️", "Sağanak")
            85, 86 -> WeatherCondition("🌨️", "Kar sağanağı")
            in 95..99 -> WeatherCondition("⛈️", "Fırtına")
            else -> WeatherCondition("🌡️", "Bilinmiyor")
        }
    }
}


fun weatherGradient(code: Int, isDay: Boolean): List<Color> = when {
    !isDay -> listOf(Color(0xFF3949AB), Color(0xFF0D1B4F))
    code in 0..2 -> listOf(Color(0xFFFFA726), Color(0xFFEF6C00))
    code == 3 || code in 45..48 -> listOf(Color(0xFF90A4AE), Color(0xFF546E7A))
    code in 51..67 || code in 80..82 -> listOf(Color(0xFF4FC3F7), Color(0xFF01579B))
    code in 71..77 || code in 85..86 -> listOf(Color(0xFF81B4D8), Color(0xFF3C6E9E))
    code >= 95 -> listOf(Color(0xFF5E35B1), Color(0xFF1A0F3C))
    else -> listOf(Color(0xFF7C4DFF), Color(0xFF3F2BB5))
}


fun clothingTip(temp: Double, code: Int): String = when {
    code >= 95 -> "⛈️ Fırtına var, mümkünse içeride kal"
    code in 51..67 || code in 80..82 -> "☂️ Şemsiyeni unutma"
    code in 71..77 || code in 85..86 -> "🧣 Kar var, kalın giyin ve dikkatli yürü"
    temp < 5 -> "🧥 Çok soğuk, kalın mont şart"
    temp < 15 -> "🧥 Serin, ceket ya da hırka al"
    temp < 25 -> "👕 Rahat bir hava, tişört yeter"
    temp < 32 -> "😎 Sıcak, güneş gözlüğünü unutma"
    else -> "🥵 Çok sıcak, bol su iç, gölgede kal"
}



interface CityApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 15,
        @Query("language") language: String = "tr",
        @Query("countryCode") countryCode: String = "TR"
    ): CitySearchResponse
}

interface ForecastApi {
    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String =
            "temperature_2m,apparent_temperature,weather_code,relative_humidity_2m,wind_speed_10m,is_day",
        @Query("hourly") hourly: String = "temperature_2m,weather_code,precipitation_probability",
        @Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset",
        @Query("forecast_days") forecastDays: Int = 7,
        @Query("timezone") timezone: String = "auto"
    ): ForecastResponse
}

interface AirApi {
    @GET("v1/air-quality")
    suspend fun getAir(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "european_aqi,pm10,pm2_5,uv_index",
        @Query("timezone") timezone: String = "auto"
    ): AirResponse
}

object ApiClient {
    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val cityApi: CityApi by lazy {
        retrofit("https://geocoding-api.open-meteo.com/").create(CityApi::class.java)
    }
    val forecastApi: ForecastApi by lazy {
        retrofit("https://api.open-meteo.com/").create(ForecastApi::class.java)
    }
    val airApi: AirApi by lazy {
        retrofit("https://air-quality-api.open-meteo.com/").create(AirApi::class.java)
    }
}

class CityRepository(private val api: CityApi = ApiClient.cityApi) {
    suspend fun search(name: String): List<City> =
        api.search(name.trim()).results.orEmpty()
            .filter { it.countryCode == null || it.countryCode == "TR" } // ikinci güvence
}

class WeatherRepository(
    private val api: ForecastApi = ApiClient.forecastApi,
    private val airApi: AirApi = ApiClient.airApi
) {

    suspend fun getForecast(city: City, withAir: Boolean = true): Forecast = coroutineScope {
        val airJob = if (withAir) {
            async {
                runCatching { airApi.getAir(city.latitude, city.longitude).current?.toAirQuality() }
                    .getOrNull()
            }
        } else {
            null
        }
        val forecast = api.getForecast(city.latitude, city.longitude).toForecast()
        forecast.copy(air = airJob?.await())
    }
}

private fun dayLabel(date: String, index: Int): String {
    if (index == 0) return "Bugün"
    if (index == 1) return "Yarın"
    return try {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
        SimpleDateFormat("EEEE", Locale.forLanguageTag("tr")).format(parsed!!)
    } catch (e: Exception) {
        date
    }
}


fun ForecastResponse.toForecast(): Forecast {
    val c = current ?: throw IllegalStateException("Hava durumu verisi boş geldi")

    val now = CurrentWeather(
        temperature = c.temperature,
        feelsLike = c.feelsLike ?: c.temperature,
        weatherCode = c.weatherCode,
        humidity = c.humidity ?: 0,
        windSpeed = c.windSpeed ?: 0.0,
        isDay = (c.isDay ?: 1) == 1
    )

    val hTimes = hourly?.time ?: emptyList()
    val nowKey = (c.time ?: "").take(13)
    val start = hTimes.indexOfFirst { it.take(13) >= nowKey }.coerceAtLeast(0)
    val hourlyItems = hTimes.indices.drop(start).take(24).mapNotNull { i ->
        val t = hourly?.temperature?.getOrNull(i) ?: return@mapNotNull null
        HourlyItem(
            hour = hTimes[i].takeLast(5),
            temperature = t.roundToInt(),
            weatherCode = hourly?.weatherCode?.getOrNull(i) ?: 0,
            rainChance = hourly?.rain?.getOrNull(i)
        )
    }

    val dTimes = daily?.time ?: emptyList()
    val dailyItems = dTimes.indices.mapNotNull { i ->
        val max = daily?.max?.getOrNull(i) ?: return@mapNotNull null
        val min = daily?.min?.getOrNull(i) ?: return@mapNotNull null
        DailyItem(
            day = dayLabel(dTimes[i], i),
            weatherCode = daily?.weatherCode?.getOrNull(i) ?: 0,
            max = max.roundToInt(),
            min = min.roundToInt()
        )
    }

    return Forecast(
        current = now,
        hourly = hourlyItems,
        daily = dailyItems,
        sunrise = daily?.sunrise?.firstOrNull()?.takeLast(5),
        sunset = daily?.sunset?.firstOrNull()?.takeLast(5)
    )
}


object Prefs {
    private val gson = Gson()
    private fun sp(c: Context) = c.getSharedPreferences("hava_prefs", Context.MODE_PRIVATE)

    fun saveLastCity(c: Context, city: City) {
        sp(c).edit().putString("last_city", gson.toJson(city)).apply()
    }

    fun lastCity(c: Context): City? =
        sp(c).getString("last_city", null)?.let {
            runCatching { gson.fromJson(it, City::class.java) }.getOrNull()
        }

    fun saveFavorites(c: Context, list: List<City>) {
        sp(c).edit().putString("favorites", gson.toJson(list)).apply()
    }

    fun favorites(c: Context): List<City> =
        sp(c).getString("favorites", null)?.let {
            runCatching { gson.fromJson(it, Array<City>::class.java).toList() }.getOrNull()
        }.orEmpty()

    fun saveForecast(c: Context, city: City, f: Forecast) {
        sp(c).edit().putString("cache_${city.key}", gson.toJson(f)).apply()
    }

    fun cachedForecast(c: Context, city: City): Forecast? =
        sp(c).getString("cache_${city.key}", null)?.let {
            runCatching { gson.fromJson(it, Forecast::class.java) }.getOrNull()
        }

    fun darkMode(c: Context): Boolean? =
        if (sp(c).contains("dark")) sp(c).getBoolean("dark", false) else null

    fun saveDarkMode(c: Context, dark: Boolean) {
        sp(c).edit().putBoolean("dark", dark).apply()
    }
}


sealed interface LoadState<out T> {
    object Loading : LoadState<Nothing>
    data class Success<T>(val data: T) : LoadState<T>
    object Empty : LoadState<Nothing>
    data class Error(val message: String) : LoadState<Nothing>
}

class CityViewModel(app: Application) : AndroidViewModel(app) {
    private val appContext: Context = app.applicationContext
    private val cityRepository = CityRepository()
    private val weatherRepository = WeatherRepository()

    private val _cities = MutableStateFlow<LoadState<List<City>>>(LoadState.Success(FeaturedCities.list))
    val cities: StateFlow<LoadState<List<City>>> = _cities.asStateFlow()

    private val _selected = MutableStateFlow<City?>(null)
    val selected: StateFlow<City?> = _selected.asStateFlow()

    private val _weather = MutableStateFlow<LoadState<Forecast>>(LoadState.Empty)
    val weather: StateFlow<LoadState<Forecast>> = _weather.asStateFlow()

    private val _favorites = MutableStateFlow(Prefs.favorites(appContext))
    val favorites: StateFlow<List<City>> = _favorites.asStateFlow()

    private var searchJob: Job? = null
    private var weatherJob: Job? = null
    private var lastQuery = ""


    fun onQueryChange(query: String) {
        searchJob?.cancel()
        lastQuery = query
        if (query.isBlank()) {
            _cities.value = LoadState.Success(FeaturedCities.list)
            return
        }
        _cities.value = LoadState.Loading
        searchJob = viewModelScope.launch {
            delay(400)
            runSearch(query)
        }
    }

    fun searchNow(query: String) {
        searchJob?.cancel()
        lastQuery = query
        if (query.isBlank()) {
            _cities.value = LoadState.Success(FeaturedCities.list)
            return
        }
        _cities.value = LoadState.Loading
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    fun retry() = searchNow(lastQuery)

    private suspend fun runSearch(query: String) {
        try {
            val result = cityRepository.search(query)
            _cities.value = if (result.isEmpty()) LoadState.Empty else LoadState.Success(result)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _cities.value = LoadState.Error(friendlyMessage(e))
        }
    }

    fun selectCity(city: City) {
        Log.d(TAG, "Tıklandı: ${city.name}")
        _selected.value = city
        Prefs.saveLastCity(appContext, city)
        HavaWidget.refreshAll(appContext)
        loadWeather(city)
    }

    fun retryWeather() {
        _selected.value?.let { loadWeather(it) }
    }

    fun clearSelection() {
        weatherJob?.cancel()
        _selected.value = null
        _weather.value = LoadState.Empty
    }

    fun toggleFavorite(city: City) {
        val current = _favorites.value
        val updated = if (current.any { it.key == city.key }) {
            current.filterNot { it.key == city.key }
        } else {
            current + city
        }
        _favorites.value = updated
        Prefs.saveFavorites(appContext, updated)
    }

    private fun loadWeather(city: City) {
        weatherJob?.cancel()
        _weather.value = LoadState.Loading
        weatherJob = viewModelScope.launch {
            try {
                val forecast = weatherRepository.getForecast(city)
                Prefs.saveForecast(appContext, city, forecast)
                _weather.value = LoadState.Success(forecast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val cached = Prefs.cachedForecast(appContext, city)
                _weather.value = if (cached != null) {
                    LoadState.Success(cached.copy(fromCache = true))
                } else {
                    LoadState.Error(friendlyMessage(e))
                }
            }
        }
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is IOException -> "İnternet bağlantısı yok. Bağlantını kontrol edip tekrar dene."
        is HttpException -> "Sunucu hatası (${e.code()}). Biraz sonra tekrar dene."
        else -> "Bir sorun oluştu: ${e.localizedMessage}"
    }

    private companion object {
        const val TAG = "CityViewModel"
    }
}

class HavaWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = buildViews(context)
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private suspend fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.hava_widget)
            val city = Prefs.lastCity(context)

            if (city == null) {
                views.setTextViewText(R.id.widget_city, "Hava")
                views.setTextViewText(R.id.widget_emoji, "🌍")
                views.setTextViewText(R.id.widget_temp, "--")
                views.setTextViewText(R.id.widget_label, "Uygulamadan bir şehir seç")
            } else {
                views.setTextViewText(R.id.widget_city, city.name)
                try {
                    val forecast = WeatherRepository().getForecast(city, withAir = false)
                    Prefs.saveForecast(context, city, forecast)
                    val current = forecast.current
                    val cond = WeatherCondition.fromCode(current.weatherCode)
                    views.setTextViewText(R.id.widget_emoji, cond.emoji)
                    views.setTextViewText(R.id.widget_temp, "${current.temperature.roundToInt()}°")
                    views.setTextViewText(R.id.widget_label, cond.label)
                } catch (e: Exception) {
                    val cached = Prefs.cachedForecast(context, city)
                    if (cached != null) {
                        val cond = WeatherCondition.fromCode(cached.current.weatherCode)
                        views.setTextViewText(R.id.widget_emoji, cond.emoji)
                        views.setTextViewText(R.id.widget_temp, "${cached.current.temperature.roundToInt()}°")
                        views.setTextViewText(R.id.widget_label, "📴 Çevrimdışı · ${cond.label}")
                    } else {
                        views.setTextViewText(R.id.widget_emoji, "📡")
                        views.setTextViewText(R.id.widget_temp, "--")
                        views.setTextViewText(R.id.widget_label, "Bağlantı yok")
                    }
                }
            }

            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            return views
        }

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HavaWidget::class.java))
            if (ids.isNotEmpty()) {
                val intent = Intent(context, HavaWidget::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                context.sendBroadcast(intent)
            }
        }
    }
}


val Purple = Color(0xFF5B3FD9)
val PurpleLight = Color(0xFF7C4DFF)
val PurpleDark = Color(0xFF3F2BB5)

data class AppColors(
    val screenBg: Color,
    val card: Color,
    val selected: Color,
    val textSecondary: Color,
    val sectionTitle: Color,
    val track: Color,
    val shimmer: List<Color>
)

val LightAppColors = AppColors(
    screenBg = Color(0xFFF4F2FB),
    card = Color.White,
    selected = Color(0xFFEDE7FF),
    textSecondary = Color.Gray,
    sectionTitle = Color(0xFF4A4560),
    track = Color(0xFFE6E2F3),
    shimmer = listOf(Color(0xFFE3DFF2), Color(0xFFF7F5FD), Color(0xFFE3DFF2))
)

val DarkAppColors = AppColors(
    screenBg = Color(0xFF14121F),
    card = Color(0xFF221F35),
    selected = Color(0xFF3A2F66),
    textSecondary = Color(0xFFA9A4BF),
    sectionTitle = Color(0xFFC9C3E0),
    track = Color(0xFF3A3652),
    shimmer = listOf(Color(0xFF2A2740), Color(0xFF38345A), Color(0xFF2A2740))
)

val LocalAppColors = staticCompositionLocalOf { LightAppColors }

object AppTheme {
    val colors: AppColors
        @Composable get() = LocalAppColors.current
}

private val LightScheme = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    background = LightAppColors.screenBg,
    surface = Color.White
)

private val DarkScheme = darkColorScheme(
    primary = PurpleLight,
    onPrimary = Color.White,
    background = DarkAppColors.screenBg,
    surface = DarkAppColors.card,
    onBackground = Color(0xFFECE9F7),
    onSurface = Color(0xFFECE9F7)
)

@Composable
fun HavaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    MaterialTheme(colorScheme = if (darkTheme) DarkScheme else LightScheme) {
        CompositionLocalProvider(LocalAppColors provides appColors) {
            Surface(color = MaterialTheme.colorScheme.background, content = content)
        }
    }
}


@Composable
fun ShimmerList(modifier: Modifier = Modifier, count: Int = 6) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f,
        targetValue = 1400f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerX"
    )
    val brush = Brush.linearGradient(
        colors = AppTheme.colors.shimmer,
        start = Offset(x, 0f),
        end = Offset(x + 400f, 300f)
    )
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(count) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(brush)
            )
        }
    }
}

@Composable
fun EmptyView(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = AppTheme.colors.textSecondary, textAlign = TextAlign.Center)
    }
}

@Composable
fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("😕", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color(0xFFC62828), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Tekrar dene") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = AppTheme.colors.sectionTitle,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp)
    )
}


@Composable
fun CityCard(
    city: City,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (selected) AppTheme.colors.selected else AppTheme.colors.card,
        label = "cardColor"
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = if (selected) BorderStroke(2.dp, Purple) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            PlateBadge(city.badgeText)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(city.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (city.subtitle.isNotEmpty()) {
                    Text(city.subtitle, fontSize = 13.sp, color = AppTheme.colors.textSecondary)
                }
            }
            if (selected) {
                Text("✓", color = Purple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlateBadge(text: String) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(PurpleLight, PurpleDark))),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Preview(showBackground = true)
@Composable
private fun CityCardPreview() {
    HavaTheme { CityCard(city = FeaturedCities.list[0], selected = false, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
private fun CityCardSelectedPreview() {
    HavaTheme { CityCard(city = FeaturedCities.list[1], selected = true, onClick = {}) }
}

private fun rnd(i: Int, salt: Int): Float {
    val v = sin(i * 12.9898 + salt * 78.233) * 43758.5453
    return (v - kotlin.math.floor(v)).toFloat()
}

private fun DrawScope.drawRain(t: Float, count: Int) {
    for (i in 0 until count) {
        val x = rnd(i, 1) * size.width
        val speed = 2 + (rnd(i, 2) * 2f).toInt()
        val y = ((t * speed + rnd(i, 3)) % 1f) * (size.height + 40f) - 20f
        drawLine(
            color = Color.White.copy(alpha = 0.55f),
            start = Offset(x, y),
            end = Offset(x - 6f, y + 22f),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawSnow(t: Float, count: Int) {
    for (i in 0 until count) {
        val sway = (sin(2.0 * PI * (t + rnd(i, 4))) * 14.0).toFloat()
        val x = rnd(i, 1) * size.width + sway
        val y = ((t + rnd(i, 3)) % 1f) * (size.height + 20f) - 10f
        drawCircle(Color.White.copy(alpha = 0.85f), 2.5f + rnd(i, 5) * 3.5f, Offset(x, y))
    }
}

private fun DrawScope.drawClouds(t: Float, alpha: Float) {
    for (i in 0 until 4) {
        val x = ((t + rnd(i, 1)) % 1f) * (size.width + 240f) - 120f
        val y = rnd(i, 2) * size.height * 0.7f
        val r = 34f + rnd(i, 3) * 34f
        val c = Color.White.copy(alpha = alpha)
        drawCircle(c, r, Offset(x, y))
        drawCircle(c, r * 0.75f, Offset(x + r * 0.9f, y + r * 0.2f))
        drawCircle(c, r * 0.65f, Offset(x - r * 0.9f, y + r * 0.25f))
    }
}

private fun DrawScope.drawSun(t: Float) {
    val pulse = 1f + 0.06f * sin(2.0 * PI * t).toFloat()
    val center = Offset(size.width * 0.85f, size.height * 0.18f)
    val radius = size.width * 0.5f * pulse
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.5f), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawStars(t: Float) {
    for (i in 0 until 26) {
        val alpha = 0.25f + 0.75f * abs(sin(PI * (t * 2f + rnd(i, 4))).toFloat())
        drawCircle(
            Color.White.copy(alpha = alpha),
            1.5f + rnd(i, 5) * 1.5f,
            Offset(rnd(i, 1) * size.width, rnd(i, 2) * size.height * 0.8f)
        )
    }
}

@Composable
private fun WeatherEffects(code: Int, isDay: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "fx")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "fxT"
    )
    Canvas(modifier = modifier) {
        when {
            code in 51..67 || code in 80..82 -> drawRain(t, 36)
            code >= 95 -> {
                drawRain(t, 54)
                // şimşek çakması
                if ((t * 3f) % 1f < 0.05f) drawRect(Color.White.copy(alpha = 0.25f))
            }
            code in 71..77 || code in 85..86 -> drawSnow(t, 40)
            code in 0..2 && isDay -> {
                drawSun(t)
                if (code != 0) drawClouds(t, 0.14f)
            }
            code in 0..2 -> drawStars(t)
            else -> drawClouds(t, 0.16f)
        }
    }
}



fun shareWeather(context: Context, city: City, f: Forecast) {
    val cond = WeatherCondition.fromCode(f.current.weatherCode)
    val text = "${city.name}: ${f.current.temperature.roundToInt()}°C ${cond.emoji} ${cond.label}\n" +
            "${clothingTip(f.current.temperature, f.current.weatherCode)}\n— Hava (KAMP+)"
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Paylaş"))
}


@Composable
fun WeatherPanel(
    city: City,
    state: LoadState<Forecast>,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = if (state is LoadState.Success) {
        weatherGradient(state.data.current.weatherCode, state.data.current.isDay)
    } else {
        listOf(PurpleLight, PurpleDark)
    }

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(colors))
            .animateContentSize()
    ) {

        if (state is LoadState.Success) {
            WeatherEffects(
                code = state.data.current.weatherCode,
                isDay = state.data.current.isDay,
                modifier = Modifier.matchParentSize()
            )
        }

        Box(modifier = Modifier.padding(20.dp)) {
            when (state) {
                is LoadState.Loading -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text("${city.name} için hava durumu alınıyor…", color = Color.White)
                }

                is LoadState.Success -> {
                    val f = state.data
                    val w = f.current
                    val condition = WeatherCondition.fromCode(w.weatherCode)
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                city.name,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "📤",
                                fontSize = 20.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { shareWeather(context, city, f) }
                                    .padding(6.dp)
                            )
                            Text(
                                if (isFavorite) "⭐" else "☆",
                                color = Color.White,
                                fontSize = 22.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onFavorite)
                                    .padding(6.dp)
                            )
                            Text(
                                "✕",
                                color = Color.White,
                                fontSize = 18.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onClose)
                                    .padding(6.dp)
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(condition.emoji, fontSize = 56.sp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(
                                    "${w.temperature.roundToInt()}°C",
                                    color = Color.White,
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    "${condition.label} · Hissedilen ${w.feelsLike.roundToInt()}°",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Text("💧 Nem %${w.humidity}", color = Color.White, fontSize = 14.sp)
                            Text("💨 ${w.windSpeed.roundToInt()} km/sa", color = Color.White, fontSize = 14.sp)
                        }
                        if (f.sunrise != null && f.sunset != null) {
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Text("🌅 ${f.sunrise}", color = Color.White, fontSize = 14.sp)
                                Text("🌇 ${f.sunset}", color = Color.White, fontSize = 14.sp)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(12.dp)
                        ) {
                            Text(
                                clothingTip(w.temperature, w.weatherCode),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                is LoadState.Error -> Column {
                    Text(state.message, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Purple)
                        ) { Text("Tekrar dene") }
                        TextButton(onClick = onClose) { Text("Kapat", color = Color.White) }
                    }
                }

                is LoadState.Empty -> Unit
            }
        }
    }
}


@Composable
private fun OfflineBanner(updatedAt: Long, onRetry: () -> Unit) {
    val time = SimpleDateFormat("d MMM HH:mm", Locale.forLanguageTag("tr")).format(Date(updatedAt))
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFA726).copy(alpha = 0.2f))
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "📴 Çevrimdışısın · Son güncelleme $time",
            modifier = Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        TextButton(onClick = onRetry) { Text("Yenile") }
    }
}


@Composable
private fun MetricTile(title: String, value: String, level: Level, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(level.color.copy(alpha = 0.14f))
            .padding(12.dp)
    ) {
        Text(title, fontSize = 12.sp, color = AppTheme.colors.textSecondary)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text(level.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = level.color)
    }
}

@Composable
private fun AirQualityCard(air: AirQuality) {
    val advice = airAdvice(air)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppTheme.colors.card)
            .padding(14.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            air.aqi?.let { aqi ->
                MetricTile("🌫️ Hava kalitesi", "$aqi", aqiLevel(aqi), Modifier.weight(1f))
            }
            air.uv?.let { uv ->
                MetricTile("☀️ UV indeksi", "%.1f".format(uv), uvLevel(uv), Modifier.weight(1f))
            }
        }
        if (advice != null) {
            Spacer(Modifier.height(10.dp))
            Text(advice, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HourlyStrip(hours: List<HourlyItem>) {
    Column {
        SectionTitle("Saatlik tahmin")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(hours) { h ->
                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppTheme.colors.card)
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(h.hour, fontSize = 12.sp, color = AppTheme.colors.textSecondary)
                    Text(WeatherCondition.fromCode(h.weatherCode).emoji, fontSize = 24.sp)
                    Text("${h.temperature}°", fontWeight = FontWeight.Bold)
                    val rain = h.rainChance
                    if (rain != null && rain > 0) {
                        Text("💧%$rain", fontSize = 10.sp, color = Color(0xFF1976D2))
                    } else {
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyList(days: List<DailyItem>) {
    if (days.isEmpty()) return
    val weekMin = days.minOf { it.min }
    val weekMax = days.maxOf { it.max }
    val span = (weekMax - weekMin).coerceAtLeast(1).toFloat()

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppTheme.colors.card)
            .padding(vertical = 8.dp)
    ) {
        days.forEach { d ->
            val startFrac = (d.min - weekMin) / span
            val widthFrac = (d.max - d.min) / span
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(d.day, modifier = Modifier.width(84.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(WeatherCondition.fromCode(d.weatherCode).emoji, fontSize = 20.sp)
                Spacer(Modifier.width(8.dp))
                Text("${d.min}°", modifier = Modifier.width(34.dp), color = AppTheme.colors.textSecondary, textAlign = TextAlign.End)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AppTheme.colors.track)
                ) {
                    Spacer(Modifier.weight(startFrac.coerceAtLeast(0.001f)))
                    Box(
                        modifier = Modifier
                            .weight(widthFrac.coerceAtLeast(0.03f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFF4FC3F7), Color(0xFFFF8A65))))
                    )
                    Spacer(Modifier.weight((1f - startFrac - widthFrac).coerceAtLeast(0.001f)))
                }
                Text("${d.max}°", modifier = Modifier.width(34.dp), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DetailPane(
    city: City,
    state: LoadState<Forecast>,
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        WeatherPanel(
            city = city,
            state = state,
            isFavorite = isFavorite,
            onFavorite = onFavorite,
            onClose = onClose,
            onRetry = onRetry
        )
        if (state is LoadState.Success) {
            if (state.data.fromCache) OfflineBanner(state.data.updatedAt, onRetry)
            state.data.air?.let { AirQualityCard(it) }
            if (state.data.hourly.isNotEmpty()) HourlyStrip(state.data.hourly)
            if (state.data.daily.isNotEmpty()) {
                SectionTitle("7 günlük tahmin")
                DailyList(state.data.daily)
            }
        }
    }
}


@Composable
private fun CityListPane(
    query: String,
    state: LoadState<List<City>>,
    favorites: List<City>,
    selected: City?,
    onCityClick: (City) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when (state) {
            is LoadState.Loading -> ShimmerList()
            is LoadState.Empty -> EmptyView(
                title = "Şehir bulunamadı 📭",
                subtitle = "Türkiye'de bu isimde bir yer yok. Yazımı kontrol edip tekrar dene."
            )
            is LoadState.Error -> ErrorView(message = state.message, onRetry = onRetry)
            is LoadState.Success -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp)
            ) {
                if (query.isBlank() && favorites.isNotEmpty()) {
                    item { SectionTitle("⭐ Favoriler") }
                    items(favorites) { city ->
                        CityCard(city, selected?.key == city.key, { onCityClick(city) })
                    }
                }
                item {
                    SectionTitle(if (query.isBlank()) "Popüler şehirler" else "${state.data.size} sonuç")
                }
                items(state.data) { city ->
                    CityCard(city, selected?.key == city.key, { onCityClick(city) })
                }
            }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    compact: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    isDark: Boolean,
    onToggleTheme: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brush.verticalGradient(listOf(PurpleLight, PurpleDark)))
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = if (compact) 10.dp else 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hava ☁️",
                    color = Color.White,
                    fontSize = if (compact) 22.sp else 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                if (!compact) {
                    Text("KAMP+ · Türkiye'de şehrini bul, havayı gör", color = Color(0xFFE6DEFF), fontSize = 14.sp)
                }
            }
            Text(
                if (isDark) "☀️" else "🌙",
                fontSize = 24.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onToggleTheme)
                    .padding(8.dp)
            )
        }
        Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Şehir ara… (Örn: Bolu)") },
            leadingIcon = { Text("🔍") },
            trailingIcon = if (query.isNotEmpty()) {
                { IconButton(onClick = onClear) { Text("✕", color = Color.Gray) } }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppTheme.colors.card,
                unfocusedContainerColor = AppTheme.colors.card,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = Purple
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() })
        )
    }
}


@Composable
fun CityScreen(
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    onToggleTheme: () -> Unit = {},
    viewModel: CityViewModel = viewModel()
) {
    var query by rememberSaveable { mutableStateOf("") }
    val cities by viewModel.cities.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val weather by viewModel.weather.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val focus: FocusManager = LocalFocusManager.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = selected != null) { viewModel.clearSelection() }

    val onQueryChange: (String) -> Unit = { q ->
        query = q
        viewModel.onQueryChange(q)
        if (!isLandscape && selected != null) viewModel.clearSelection()
    }
    val onSearch: () -> Unit = {
        viewModel.searchNow(query)
        focus.clearFocus()
    }
    val onClear: () -> Unit = {
        query = ""
        viewModel.onQueryChange("")
    }
    val onCityClick: (City) -> Unit = { city ->
        viewModel.selectCity(city)
        focus.clearFocus()
    }

    val city = selected

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .background(AppTheme.colors.screenBg)
                .displayCutoutPadding()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.weight(0.42f).fillMaxHeight()) {
                SearchHeader(
                    query = query,
                    compact = true,
                    onQueryChange = onQueryChange,
                    onSearch = onSearch,
                    onClear = onClear,
                    isDark = isDark,
                    onToggleTheme = onToggleTheme
                )
                CityListPane(
                    query = query,
                    state = cities,
                    favorites = favorites,
                    selected = city,
                    onCityClick = onCityClick,
                    onRetry = viewModel::retry,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
            Box(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
                    .statusBarsPadding()
            ) {
                if (city != null) {
                    DetailPane(
                        city = city,
                        state = weather,
                        isFavorite = favorites.any { it.key == city.key },
                        onFavorite = { viewModel.toggleFavorite(city) },
                        onClose = viewModel::clearSelection,
                        onRetry = viewModel::retryWeather,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    EmptyView(
                        title = "🌍 Bir şehir seç",
                        subtitle = "Soldaki listeden dokun, saatlik ve 7 günlük tahmin burada açılsın."
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(AppTheme.colors.screenBg)
                .imePadding()
                .navigationBarsPadding()
        ) {
            SearchHeader(
                query = query,
                compact = false,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                onClear = onClear,
                isDark = isDark,
                onToggleTheme = onToggleTheme
            )
            if (city != null) {
                DetailPane(
                    city = city,
                    state = weather,
                    isFavorite = favorites.any { it.key == city.key },
                    onFavorite = { viewModel.toggleFavorite(city) },
                    onClose = viewModel::clearSelection,
                    onRetry = viewModel::retryWeather,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            } else {
                CityListPane(
                    query = query,
                    state = cities,
                    favorites = favorites,
                    selected = null,
                    onCityClick = onCityClick,
                    onRetry = viewModel::retry,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val system = isSystemInDarkTheme()
            var dark by rememberSaveable { mutableStateOf(Prefs.darkMode(context) ?: system) }
            HavaTheme(darkTheme = dark) {
                CityScreen(
                    isDark = dark,
                    onToggleTheme = {
                        dark = !dark
                        Prefs.saveDarkMode(context, dark)
                    }
                )
            }
        }
    }
}