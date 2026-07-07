package com.novikon.pace.ui.habits

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.novikon.pace.R
import com.novikon.pace.adapters.HabitSelectionAdapter
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.helpers.LanguageHelper
import com.novikon.pace.helpers.ThemeHelper
import com.novikon.pace.models.Habit
import com.novikon.pace.models.HabitCategory
import com.novikon.pace.models.TimeOfDay
import com.novikon.pace.repositories.HabitsRepository
import com.novikon.pace.utils.applySystemBarInsets
import kotlinx.coroutines.launch

// Pantalla de seleccion de habitos: configura que rutinas activara el usuario.
class HabitSelectionActivity : AppCompatActivity() {

    private lateinit var habitsManager: HabitsManager
    private lateinit var categoryChipGroup: ChipGroup
    private lateinit var habitsRecyclerView: RecyclerView
    private lateinit var customHabitsLayout: LinearLayout
    private lateinit var customHabitsRecyclerView: RecyclerView
    private lateinit var addCustomHabitButton: MaterialButton
    private lateinit var selectedCountText: TextView
    private lateinit var laterButton: MaterialButton
    private lateinit var saveButton: MaterialButton

    private lateinit var habitAdapter: HabitSelectionAdapter
    private lateinit var customHabitAdapter: HabitSelectionAdapter

    private var currentCategory = HabitCategory.PHYSICAL
    private val allHabits = mutableListOf<Habit>()
    private val customHabits = mutableListOf<Habit>()

    // Set con los ids de los hábitos seleccionados
    private val selectedHabitIds = mutableSetOf<String>()

    // Mapa de overrides de personalización elegidos por el usuario.
    // Solo contiene entradas para los hábitos que el usuario personalizó —
    // los que no están aquí conservan los valores originales del repositorio.
    // Guarda el Habit completo con los campos sobreescritos (timeOfDay, duration, color).
    private val habitOverrides = mutableMapOf<String, Habit>()

// onCreate: inicializa la pantalla y prepara vistas/eventos iniciales.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeHelper.applyTheme(this)
        LanguageHelper.applyLanguage(this)

        setContentView(R.layout.dialog_habit_selection)
        applySystemBarInsets()

        habitsManager = HabitsManager(this)

        initializeViews()
        setupListeners()

        lifecycleScope.launch {
            loadHabits()
            showCategory(HabitCategory.PHYSICAL)
        }
    }
    private fun initializeViews() {
        categoryChipGroup = findViewById(R.id.categoryChipGroup)
        habitsRecyclerView = findViewById(R.id.habitsRecyclerView)
        customHabitsLayout = findViewById(R.id.customHabitsLayout)
        customHabitsRecyclerView = findViewById(R.id.customHabitsRecyclerView)
        addCustomHabitButton = findViewById(R.id.addCustomHabitButton)
        selectedCountText = findViewById(R.id.selectedCountText)
        laterButton = findViewById(R.id.laterButton)
        saveButton = findViewById(R.id.saveButton)

        habitsRecyclerView.layoutManager = LinearLayoutManager(this)
        customHabitsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    // Carga los hábitos predefinidos y los personalizados,
    // y marca como seleccionados los que el usuario ya tenía guardados.
    private suspend fun loadHabits() {
        allHabits.addAll(HabitsRepository.getAllPredefinedHabits(this))

        val savedHabits = habitsManager.getSelectedHabitsAsync()
        savedHabits.forEach { savedHabit ->
            selectedHabitIds.add(savedHabit.id)
            // Restaurar los overrides que el usuario ya había elegido —
            // si difieren del valor original, los guardamos para que al volver
            // a esta pantalla los hábitos ya muestren sus valores personalizados
            val original = allHabits.find { it.id == savedHabit.id }
            // Guardar siempre los hábitos personalizados como override, ya que
            // no tienen un valor "original" en el repositorio contra el que comparar
            if (savedHabit.isCustom) {
                habitOverrides[savedHabit.id] = savedHabit
            } else if (original != null && (
                    savedHabit.timeOfDay != original.timeOfDay ||
                    savedHabit.duration != original.duration ||
                    savedHabit.color != original.color
                )
            ) {
                habitOverrides[savedHabit.id] = savedHabit
            }
        }

        customHabits.addAll(savedHabits.filter { it.isCustom })
    }
    private fun setupListeners() {
        categoryChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                when (checkedIds[0]) {
                    R.id.chipPhysical -> showCategory(HabitCategory.PHYSICAL)
                    R.id.chipMental -> showCategory(HabitCategory.MENTAL)
                    R.id.chipStudy -> showCategory(HabitCategory.STUDY)
                    R.id.chipRoutine -> showCategory(HabitCategory.ROUTINE)
                    R.id.chipBadHabits -> showCategory(HabitCategory.BAD_HABITS)
                    R.id.chipWellbeing -> showCategory(HabitCategory.WELLBEING)
                    R.id.chipCustom -> showCustomCategory()
                }
            }
        }

        addCustomHabitButton.setOnClickListener {
            showAddCustomHabitDialog()
        }

        laterButton.setOnClickListener {
            finish()
        }

        saveButton.setOnClickListener {
            saveSelectedHabits()
        }
    }
    private fun showCategory(category: HabitCategory) {
        currentCategory = category
        habitsRecyclerView.visibility = View.VISIBLE
        customHabitsLayout.visibility = View.GONE

        val categoryHabits = allHabits.filter { it.category == category }

        habitAdapter = HabitSelectionAdapter(categoryHabits, selectedHabitIds, onHabitToggled = { habit, isSelected ->
            if (isSelected) {
                showHabitCustomizationSheet(habit)
            } else {
                habitOverrides.remove(habit.id)
            }
            updateSelectedCount()
        })
        habitsRecyclerView.adapter = habitAdapter

        updateSelectedCount()
    }
    private fun showCustomCategory() {
        habitsRecyclerView.visibility = View.GONE
        customHabitsLayout.visibility = View.VISIBLE

        customHabitAdapter = HabitSelectionAdapter(
            customHabits,
            selectedHabitIds,
            onHabitToggled = { habit, isSelected ->
                if (isSelected) {
                    showHabitCustomizationSheet(habit)
                } else {
                    habitOverrides.remove(habit.id)
                }
                updateSelectedCount()
            },
            onDelete = { habit ->
                showDeleteCustomHabitDialog(habit)
            }
        )
        customHabitsRecyclerView.adapter = customHabitAdapter

        updateSelectedCount()
    }

    // Muestra el BottomSheet de personalización para que el usuario edite
    // la franja horaria, duración y color de la tarjeta del hábito.
    private fun showHabitCustomizationSheet(habit: Habit) {
        val effectiveHabit = habitOverrides[habit.id] ?: habit

        val sheet = HabitCustomizationSheet(effectiveHabit) { updatedHabit ->
            val original = allHabits.find { it.id == habit.id }
                ?: customHabits.find { it.id == habit.id }
            val isDifferent = original != null && (
                updatedHabit.timeOfDay != original.timeOfDay ||
                updatedHabit.duration != original.duration ||
                updatedHabit.color != original.color
            )
            if (isDifferent) {
                habitOverrides[habit.id] = updatedHabit
            } else {
                habitOverrides.remove(habit.id)
            }
        }
        sheet.show(supportFragmentManager, "HabitCustomizationSheet")
    }

    // Muestra el diálogo para crear un hábito personalizado.
    private fun showAddCustomHabitDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_habit, null)
        val habitNameInput = dialogView.findViewById<TextInputEditText>(R.id.habitNameInput)
        val durationInput = dialogView.findViewById<TextInputEditText>(R.id.durationInput)
        val emojiContainer = dialogView.findViewById<LinearLayout>(R.id.emojiContainer)

        val emojis = listOf("💪", "🏃", "📚", "🧘", "🎯", "⭐", "🎨", "🎵", "🍎", "💼",
            "🌟", "✨", "🔥", "💡", "🚀", "🎉", "🌈", "☕", "🌸", "🎭")

        var selectedEmoji = emojis[0]

        emojis.forEach { emoji ->
            val emojiButton = TextView(this).apply {
                text = emoji
                textSize = 28f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                    setMargins(8, 8, 8, 8)
                }
                isClickable = true
                isFocusable = true
                setBackgroundResource(android.R.drawable.list_selector_background)
                setOnClickListener {
                    selectedEmoji = emoji
                    alpha = 1.0f
                    emojiContainer.children.forEach { child ->
                        if (child != this) child.alpha = 0.5f
                    }
                }
            }
            emojiContainer.addView(emojiButton)
        }

        (emojiContainer.getChildAt(0) as? TextView)?.alpha = 1.0f
        emojiContainer.children.drop(1).forEach { it.alpha = 0.5f }

        AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(getString(R.string.add_custom_habit)) { _, _ ->
                val name = habitNameInput.text.toString().trim()
                val duration = durationInput.text.toString().trim()
                    .ifEmpty { getString(R.string.custom_duration) }

                if (name.isNotEmpty()) {
                    val newHabit = Habit(
                        id = "custom_${System.currentTimeMillis()}",
                        name = name,
                        emoji = selectedEmoji,
                        duration = duration,
                        category = HabitCategory.CUSTOM,
                        timeOfDay = TimeOfDay.ALL_DAY,
                        isCustom = true
                    )

                    customHabits.add(newHabit)
                    selectedHabitIds.add(newHabit.id)
                    // El dialog de franja se mostrará automáticamente
                    // desde el callback del adapter al seleccionarse
                    customHabitAdapter.updateHabits(customHabits)
                    updateSelectedCount()

                    Toast.makeText(this, getString(R.string.habit_added), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, getString(R.string.habit_name_required), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
    private fun updateSelectedCount() {
        selectedCountText.text = getString(R.string.selected_habits_count, selectedHabitIds.size)
    }

    // Recoge todos los hábitos seleccionados, aplica los overrides de
    // personalización elegidos por el usuario, y los guarda en Firebase y caché.
    private fun saveSelectedHabits() {
        // Los hábitos predefinidos solo se guardan si están seleccionados.
        // Los personalizados se guardan SIEMPRE (seleccionados o no) para no perderlos —
        // desseleccionar no es eliminar.
        val selectedHabits = (allHabits + customHabits)
            .filter { habit ->
                if (habit.isCustom) true else selectedHabitIds.contains(habit.id)
            }
            .map { habit ->
                habitOverrides[habit.id] ?: habit
            }

        if (selectedHabits.isEmpty()) {
            Toast.makeText(this, getString(R.string.select_at_least_one), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val success = habitsManager.saveSelectedHabits(selectedHabits)
            if (success) {
                Toast.makeText(
                    this@HabitSelectionActivity,
                    getString(R.string.habits_saved),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@HabitSelectionActivity,
                    getString(R.string.habits_saved_locally),
                    Toast.LENGTH_SHORT
                ).show()
            }
            finish()
        }
    }

    private fun showDeleteCustomHabitDialog(habit: Habit) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_habit_title))
            .setMessage(getString(R.string.delete_habit_message, habit.name))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                customHabits.remove(habit)
                selectedHabitIds.remove(habit.id)
                habitOverrides.remove(habit.id)
                customHabitAdapter.updateHabits(customHabits)
                updateSelectedCount()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
}