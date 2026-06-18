package com.novikon.pace.ui.main

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.novikon.pace.R
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.ui.circles.CirclesActivity
import com.novikon.pace.ui.habits.DailyHabitsActivity
import com.novikon.pace.ui.habits.HabitSelectionActivity
import com.novikon.pace.ui.main.menus.NavigationMenuManager
import com.novikon.pace.ui.main.menus.UserMenuManager
import com.novikon.pace.utils.ReminderScheduler
import com.novikon.pace.utils.SessionManager
import com.novikon.pace.utils.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.novikon.pace.data.CirclesRealtimeManager
import com.novikon.pace.models.Circle
import com.novikon.pace.repositories.HabitsRepository
import com.novikon.pace.ui.circles.CirclesAdapter
import com.novikon.pace.ui.circles.CircleChatActivity
import com.novikon.pace.utils.applySystemBarInsets

// Pantalla principal: orquesta navegacion, preview de habitos y estado de sesion.
class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationViewGeneral: NavigationView
    private lateinit var navigationViewUser: NavigationView
    private lateinit var welcomeText: TextView
    private lateinit var habitsPreviewContainer: android.widget.LinearLayout

    private lateinit var sessionManager: SessionManager
    private lateinit var habitsManager: HabitsManager
    private lateinit var settingsManager: SettingsManager
    private lateinit var database: FirebaseDatabase

    private lateinit var navigationMenuManager: NavigationMenuManager
    private lateinit var userMenuManager: UserMenuManager

    private lateinit var groupsRecyclerView: RecyclerView
    private lateinit var viewAllGroupsButton: MaterialButton

    private val circlesManager by lazy { CirclesRealtimeManager(this) }
    private lateinit var circlesPreviewAdapter: CirclesAdapter

    // Controla si onCreate ya ejecutó la carga inicial de hábitos.
    // Evita que onResume vuelva a pintar la preview justo después de onCreate,
    // ya que onResume siempre se ejecuta tras onCreate y causaría duplicados.
    private var initialLoadDone = false

    // Launcher para detectar cuando el usuario vuelve de Settings —
    // recreamos la Activity para aplicar posibles cambios de tema o idioma
    private val settingsLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { recreate() }

    // Launcher para detectar cuando el usuario vuelve de seleccionar hábitos —
    // recargamos la previsualización con los nuevos hábitos
    private val habitSelectionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        lifecycleScope.launch { loadAndDisplayHabits() }
    }

    // Configura la pantalla principal, sincroniza datos del usuario y prepara navegación.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.activity_main)
        applySystemBarInsets()

        sessionManager = SessionManager(this)
        // Actualizar timestamp de última actividad al arrancar
        sessionManager.updateLastActive()
        habitsManager = HabitsManager(this)
        settingsManager = SettingsManager(this)
        database = FirebaseDatabase.getInstance()

        initializeViews()
        setupCirclesPreview()
        setupMenuManagers()
        setupListeners()

        // Escuchar cambios de nombre en tiempo real desde Firebase
        listenToNameChanges()

        // Mostrar el email en el header del drawer derecho
        userMenuManager.setupUserEmail()

        reprogramRemindersIfNeeded()

        lifecycleScope.launch {
            val userId = Firebase.auth.currentUser?.uid
            if (userId != null) {
                habitsManager.setCurrentUserId(userId)
                saveUserEmailToFirebase(userId)
            }

            // Sincroniza hábitos desde Firebase si hay red
            habitsManager.syncFromFirebase()

            // Sincroniza ajustes del usuario desde Firebase —
            // restaura tema, idioma y recordatorios si el usuario
            // cambió de dispositivo o reinstalό la app
            val settingsManager = SettingsManager(this@MainActivity)
            settingsManager.syncSettingsFromFirebase()

            // Marcamos que la carga inicial ya se hizo para que
            // onResume no vuelva a pintar nada en este mismo ciclo
            withContext(Dispatchers.Main) {
                // Aplicar tema e idioma si cambiaron tras la sincronización
                ThemeHelper.applyTheme(this@MainActivity)
                LanguageHelper.applyLanguage(this@MainActivity)

                // Carga y pinta los hábitos una única vez al arrancar
                loadAndDisplayHabits()
                loadAndDisplayCirclesPreview()
                checkFirstTimeHabitSelection()
                initialLoadDone = true
            }
        }

        // Gestionar el botón de atrás — cierra el drawer abierto si lo hay
        onBackPressedDispatcher.addCallback(this) {
            when {
                drawerLayout.isDrawerOpen(GravityCompat.START) ->
                    drawerLayout.closeDrawer(GravityCompat.START)
                drawerLayout.isDrawerOpen(GravityCompat.END) ->
                    drawerLayout.closeDrawer(GravityCompat.END)
                else -> {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    // Enlaza las vistas del layout para poder actualizar la UI desde código.
    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        navigationViewGeneral = findViewById(R.id.navigationViewGeneral)
        navigationViewUser = findViewById(R.id.navigationViewUser)
        welcomeText = findViewById(R.id.welcomeText)
        habitsPreviewContainer = findViewById(R.id.habitsPreviewContainer)
        groupsRecyclerView = findViewById(R.id.groupsRecyclerView)
        viewAllGroupsButton = findViewById(R.id.viewAllGroupsButton)
    }

    // Inicializa los dos menús laterales y conecta sus acciones de navegación.
    private fun setupMenuManagers() {
        navigationMenuManager = NavigationMenuManager(
            activity = this,
            navigationView = navigationViewGeneral,
            drawerLayout = drawerLayout,
            settingsLauncher = settingsLauncher,
            onNavigateToDailyHabits = { navigateToDailyHabits() },
            onNavigateToCircles = { navigateToCircles() }
        )

        userMenuManager = UserMenuManager(
            activity = this,
            navigationView = navigationViewUser,
            drawerLayout = drawerLayout,
            habitsManager = habitsManager,
            sessionManager = sessionManager
        )

        navigationMenuManager.setup()
        userMenuManager.setup()
    }

    // Escucha cambios de nombre en Firebase en tiempo real.
    // Actualiza el welcomeText y el header del drawer simultáneamente.
    private fun listenToNameChanges() {
        val userId = Firebase.auth.currentUser?.uid ?: return

        database.getReference("users/$userId/profile/displayName")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nameFromDb = snapshot.getValue(String::class.java)

                    val name = if (!nameFromDb.isNullOrBlank()) {
                        nameFromDb
                    } else {
                        val authName = Firebase.auth.currentUser?.displayName
                        if (!authName.isNullOrBlank()) {
                            // Solo escribir si no se está borrando la cuenta
                            val prefs = getSharedPreferences("pace_prefs", MODE_PRIVATE)
                            if (!prefs.getBoolean("deleting_account", false)) {
                                database.getReference("users/$userId/profile/displayName")
                                    .setValue(authName)
                            }
                            authName
                        } else {
                            getString(R.string.default_user)
                        }
                    }

                    welcomeText.text = "${getString(R.string.title_welcome)} $name"
                    userMenuManager.updateUserName(name)
                }

                override fun onCancelled(error: DatabaseError) {
                    val name = Firebase.auth.currentUser?.displayName
                        ?: getString(R.string.default_user)
                    welcomeText.text = "${getString(R.string.title_welcome)} $name"
                }
            })
    }

    // Asigna acciones a los botones principales de acceso rápido.
    private fun setupListeners() {
        findViewById<android.widget.ImageButton>(R.id.menuButton).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        findViewById<android.widget.ImageButton>(R.id.profileButton).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.END)
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.accessHabitsButton).setOnClickListener {
            navigateToDailyHabits()
        }
        findViewById<com.google.android.material.button.MaterialButton>(R.id.viewAllGroupsButton).setOnClickListener {
            navigateToAllGroups()
        }
    }

    // ── NAVEGACIÓN ────────────────────────────────────────────────────────────

    private fun navigateToDailyHabits() {
        startActivity(Intent(this, DailyHabitsActivity::class.java))
    }

    // Abre la pantalla de círculos para ver grupos y chats.
    private fun navigateToCircles() {
        startActivity(Intent(this, CirclesActivity::class.java))
    }

    // Placeholder temporal para la futura pantalla de todos los grupos públicos.
    private fun navigateToAllGroups() {
        Toast.makeText(this, getString(R.string.view_all_connections), Toast.LENGTH_SHORT).show()
    }

    // ── HÁBITOS ───────────────────────────────────────────────────────────────

    // Función única de carga de hábitos — decide la fuente según conectividad:
    //   - Con internet  → sincroniza Firebase al caché, luego pinta desde caché
    //   - Sin internet  → pinta directamente desde caché local
    // Al usar siempre el caché como fuente final de pintado, se garantiza
    // que la UI se actualiza una sola vez y nunca aparecen duplicados.
    private suspend fun loadAndDisplayHabits() {
        // Sincroniza desde Firebase si hay red — actualiza el caché local.
        // Si no hay red, el caché ya tiene los datos de la última sesión.
        habitsManager.syncFromFirebase()

        withContext(Dispatchers.Main) {
            paintHabitsFromCache()
        }
    }

    // Pinta la previsualización de hábitos usando el caché local.
    // Es siempre llamada después de syncFromFirebase(), por lo que
    // el caché ya contiene los datos más recientes disponibles.
    private fun paintHabitsFromCache() {
        habitsPreviewContainer.removeAllViews()
        val cachedHabits = habitsManager.getSelectedHabitsFromCache()
        val predefinedHabits = HabitsRepository.getAllPredefinedHabits(this)
        val predefinedMap = predefinedHabits.associateBy { it.id }
        val selectedHabits = cachedHabits.map { habit ->
            if (!habit.isCustom) {
                predefinedMap[habit.id]?.copy(timeOfDay = habit.timeOfDay) ?: habit
            } else {
                habit
            }
        }

        if (selectedHabits.isEmpty()) {
            val emptyView = layoutInflater.inflate(
                R.layout.habits_preview_empty, habitsPreviewContainer, false
            )
            habitsPreviewContainer.addView(emptyView)
            emptyView.findViewById<com.google.android.material.button.MaterialButton>(
                R.id.configureHabitsButton
            ).setOnClickListener {
                habitSelectionLauncher.launch(
                    Intent(this, HabitSelectionActivity::class.java)
                )
            }
        } else {
            selectedHabits.take(3).forEach { habit ->
                val habitView = layoutInflater.inflate(
                    R.layout.habit_preview_item, habitsPreviewContainer, false
                )
                habitView.findViewById<TextView>(R.id.habitPreviewEmoji).text = habit.emoji
                habitView.findViewById<TextView>(R.id.habitPreviewName).text = habit.name
                habitView.findViewById<TextView>(R.id.habitPreviewDuration).text = habit.duration
                habitsPreviewContainer.addView(habitView)
            }
            if (selectedHabits.size > 3) {
                val moreView = layoutInflater.inflate(
                    R.layout.habits_preview_more, habitsPreviewContainer, false
                )
                moreView.findViewById<TextView>(R.id.moreHabitsText).text =
                    "+${selectedHabits.size - 3} ${getString(R.string.more_habits)}"
                habitsPreviewContainer.addView(moreView)
            }
        }
    }

    // ── CÍRCULOS ───────────────────────────────────────────────────────────────
    // Prepara el RecyclerView de la preview de círculos.
    // El click abre directamente el chat del grupo seleccionado.
    private fun setupCirclesPreview() {
        circlesPreviewAdapter = CirclesAdapter { circle ->
            val intent = Intent(this, CircleChatActivity::class.java).apply {
                putExtra(CircleChatActivity.EXTRA_CIRCLE_ID, circle.id)
                putExtra(CircleChatActivity.EXTRA_CIRCLE_NAME, circle.name)
            }
            startActivity(intent)
        }

        groupsRecyclerView.layoutManager = LinearLayoutManager(this)
        groupsRecyclerView.adapter = circlesPreviewAdapter
        groupsRecyclerView.itemAnimator = null
    }

    // Carga los círculos del usuario y muestra una preview de hasta 5.
    // Si hay más de 2, muestra el botón "Ver todas".
    private fun loadAndDisplayCirclesPreview() {
        lifecycleScope.launch {
            val circles = circlesManager.getUserCircles()

            withContext(Dispatchers.Main) {
                val preview = circles.take(2)
                circlesPreviewAdapter.submitList(preview)

                viewAllGroupsButton.visibility =
                    if (circles.size > 2) android.view.View.VISIBLE else android.view.View.GONE

                viewAllGroupsButton.setOnClickListener {
                    startActivity(Intent(this@MainActivity, CirclesActivity::class.java))
                }
            }
        }
    }

    // ── UTILIDADES ────────────────────────────────────────────────────────────

    private fun saveUserEmailToFirebase(userId: String) {
        val email = Firebase.auth.currentUser?.email ?: return
        database.getReference("users/$userId/profile/email").setValue(email)
    }

    // Si el usuario aún no configuró hábitos, abre automáticamente la selección inicial.
    private fun checkFirstTimeHabitSelection() {
        lifecycleScope.launch {
            if (!habitsManager.areHabitsConfiguredAsync()) {
                withContext(Dispatchers.Main) {
                    habitSelectionLauncher.launch(
                        Intent(this@MainActivity, HabitSelectionActivity::class.java)
                    )
                }
            }
        }
    }

    // Reprograma alarmas locales al entrar en la app si los recordatorios están activos.
    private fun reprogramRemindersIfNeeded() {
        if (settingsManager.areRemindersEnabled) {
            ReminderScheduler.scheduleReminders(
                context = this,
                areRemindersEnabled = settingsManager.areRemindersEnabled,
                activeDayIndices = settingsManager.activeDayIndices,
                morningEnabled = settingsManager.morningReminderEnabled,
                morningTime = settingsManager.morningReminderTime,
                afternoonEnabled = settingsManager.afternoonReminderEnabled,
                afternoonTime = settingsManager.afternoonReminderTime,
                eveningEnabled = settingsManager.eveningReminderEnabled,
                eveningTime = settingsManager.eveningReminderTime,
                allDayEnabled = settingsManager.allDayReminderEnabled,
                allDayTime = settingsManager.allDayReminderTime
            )
        }
    }

    // onResume se ejecuta siempre después de onCreate y también al volver
    // de otra Activity. Solo recargamos si la carga inicial ya terminó —
    // si no, el propio onCreate ya está gestionando la primera carga.
    override fun onResume() {
        super.onResume()
        if (initialLoadDone) {
            lifecycleScope.launch { loadAndDisplayHabits() }
            loadAndDisplayCirclesPreview()
        }
    }
}