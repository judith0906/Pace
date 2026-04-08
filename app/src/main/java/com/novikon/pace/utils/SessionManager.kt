package com.novikon.pace.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import com.novikon.pace.constants.PrefsConstants
import com.novikon.pace.data.RealtimeDatabaseManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SessionManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PrefsConstants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val databaseManager = RealtimeDatabaseManager()

    companion object {
        private const val KEY_LAST_LOGIN_TIME = "last_login_time"
        private const val KEY_USER_LOGGED_OUT = "user_logged_out"
        private const val KEY_DEVICE_ID = "device_id"
        private const val TWO_WEEKS_IN_MILLIS = 14 * 24 * 60 * 60 * 1000L
    }

    // Guarda el momento exacto en que el usuario inició sesión,
    // registra el dispositivo en Firebase y marca que NO cerró sesión
    // manualmente, por si acaso quedaba algún valor anterior guardado.
    // Se llama desde LoginActivity y SignUpActivity tras un login exitoso.
    fun saveLastLoginTime() {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_LAST_LOGIN_TIME, now)
            .putBoolean(KEY_USER_LOGGED_OUT, false)
            .apply()

        // Registrar el dispositivo en Firebase en segundo plano
        CoroutineScope(Dispatchers.IO).launch {
            databaseManager.registerDevice(
                deviceId = getDeviceId(),
                deviceName = getDeviceName(),
                osVersion = getOsVersion(),
                loginTimestamp = now
            )
        }
    }

    // Marca que el usuario cerró sesión manualmente (pulsó "Cerrar sesión").
    // Elimina TODOS los dispositivos de Firebase — así los otros dispositivos
    // también quedan expulsados en su próxima apertura de la app.
    // Se llama desde MainActivity y AccountSettingsActivity al hacer logout.
    fun markUserLoggedOut() {
        prefs.edit()
            .putBoolean(KEY_USER_LOGGED_OUT, true)
            .apply()

        val deviceId = getDeviceId()
        val userId = databaseManager.getUserId() ?: return // guarda el uid ANTES de que auth.signOut() lo mate

        CoroutineScope(Dispatchers.IO).launch {
            databaseManager.removeDevice(deviceId, userId)
        }
    }

    // Cierra sesión en TODOS los dispositivos (opción "cerrar sesión en todos")
    fun markAllDevicesLoggedOut(onComplete: () -> Unit) {
        prefs.edit().putBoolean(KEY_USER_LOGGED_OUT, true).apply()

        CoroutineScope(Dispatchers.IO).launch {
            databaseManager.removeAllDevices()  // primero Firebase, auth aún válido
            onComplete()                         // luego lo que venga después
        }
    }

    // Marca logout local sin tocar Firebase.
    // Se usa cuando SplashActivity detecta que este dispositivo fue eliminado
    // remotamente — Firebase ya está limpio, solo hay que actualizar el estado local.
    fun forceLogout() {
        prefs.edit()
            .putBoolean(KEY_USER_LOGGED_OUT, true)
            .apply()
        com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
    }

    // Decide si hay que mostrar la pantalla de login.
    // Devuelve true (mostrar login) en tres casos:
    //   1. El usuario cerró sesión manualmente
    //   2. Nunca ha iniciado sesión en este dispositivo
    //   3. Han pasado más de 2 semanas desde el último login
    // Devuelve false si la sesión sigue siendo válida.
    fun shouldShowLogin(): Boolean {

        // Caso 1: el usuario cerró sesión manualmente → siempre pedir login
        if (prefs.getBoolean(KEY_USER_LOGGED_OUT, false)) {
            return true
        }

        // Caso 2: nunca ha iniciado sesión → el valor por defecto es 0
        val lastLoginTime = prefs.getLong(KEY_LAST_LOGIN_TIME, 0)
        if (lastLoginTime == 0L) {
            return true
        }

        // Caso 3: han pasado más de 2 semanas
        val timeSinceLastLogin = System.currentTimeMillis() - lastLoginTime
        return timeSinceLastLogin > TWO_WEEKS_IN_MILLIS
    }

    // Actualiza el timestamp de última actividad del dispositivo en Firebase.
    // Se llama al arrancar MainActivity para reflejar el uso más reciente.
    fun updateLastActive() {
        CoroutineScope(Dispatchers.IO).launch {
            databaseManager.updateDeviceLastActive(getDeviceId())
        }
    }

    // Borra todos los datos de sesión guardados.
    // Se llama cuando el usuario elimina su cuenta, para no dejar
    // datos huérfanos en el dispositivo.
    fun clearSession() {
        prefs.edit().clear().apply()
    }

    // ── IDENTIFICACIÓN DEL DISPOSITIVO ────────────────────────────────────────

    // Devuelve un ID único y estable para este dispositivo.
    // Usa el ANDROID_ID del sistema — es único por dispositivo y app,
    // y persiste aunque el usuario desinstale y vuelva a instalar la app
    // (en Android 8+). Si no está disponible, genera uno aleatorio y
    // lo guarda en SharedPreferences para que sea consistente entre sesiones.
    fun getDeviceId(): String {
        val savedId = prefs.getString(KEY_DEVICE_ID, null)
        if (savedId != null) return savedId

        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        val deviceId = androidId?.takeIf { it.isNotEmpty() && it != "9774d56d682e549c" }
            ?: java.util.UUID.randomUUID().toString()

        prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        return deviceId
    }

    // Devuelve el nombre del dispositivo para mostrarlo en la lista.
    // Combina marca y modelo (ej: "Samsung Galaxy S23") o usa el nombre
    // de Bluetooth si está configurado, que suele ser más descriptivo.
    fun getDeviceName(): String {
        val bluetoothName = try {
            Settings.System.getString(context.contentResolver, "bluetooth_name")
        } catch (e: Exception) {
            null
        }

        return when {
            !bluetoothName.isNullOrBlank() -> bluetoothName
            Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true) -> Build.MODEL
            else -> "${Build.MANUFACTURER} ${Build.MODEL}"
        }
    }

    // Devuelve la versión de Android del dispositivo (ej: "Android 14").
    fun getOsVersion(): String = "Android ${Build.VERSION.RELEASE}"
}