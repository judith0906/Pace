package com.novikon.pace.ui.habits

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.novikon.pace.R
import com.novikon.pace.databinding.DialogHabitCustomizationBinding
import com.novikon.pace.models.Habit
import com.novikon.pace.models.TimeOfDay

class HabitCustomizationSheet(
    private val habit: Habit,
    private val onSave: (updatedHabit: Habit) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogHabitCustomizationBinding? = null
    private val binding get() = _binding!!

    private var selectedColor: String = habit.color

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogHabitCustomizationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHabitInfo()
        setupTimeOfDay()
        setupDuration()
        setupColorPicker()
        setupButtons()
    }

    private fun setupHabitInfo() {
        binding.habitEmojiTitle.text = habit.emoji
        binding.habitNameTitle.text = habit.name
    }

    private fun setupTimeOfDay() {
        val chipId = when (habit.timeOfDay) {
            TimeOfDay.MORNING -> R.id.chipMorning
            TimeOfDay.AFTERNOON -> R.id.chipAfternoon
            TimeOfDay.EVENING -> R.id.chipEvening
            TimeOfDay.ALL_DAY -> R.id.chipAllDay
        }
        binding.chipGroupTimeOfDay.check(chipId)
    }

    private fun setupDuration() {
        binding.durationInput.setText(habit.duration)
    }

    private fun setupColorPicker() {
        val hexArray = resources.getStringArray(R.array.habit_color_hex)
        val colorArray = resources.getIntArray(R.array.habit_colors)

        hexArray.forEachIndexed { index, hex ->
            val circle = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(44, 44).apply {
                    setMargins(6, 0, 6, 0)
                }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(colorArray[index])
                    if (hex == selectedColor) {
                        setStroke(3, resources.getColor(R.color.accent_primary, null))
                    }
                }
                background = bg
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedColor = hex
                    refreshColorPicker(hexArray, colorArray)
                }
            }
            binding.colorContainer.addView(circle)
        }
    }

    private fun refreshColorPicker(hexArray: Array<String>, colorArray: IntArray) {
        for (i in 0 until binding.colorContainer.childCount) {
            val child = binding.colorContainer.getChildAt(i)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorArray[i])
                if (hexArray[i] == selectedColor) {
                    setStroke(3, resources.getColor(R.color.accent_primary, null))
                }
            }
            child.background = bg
        }
    }

    private fun setupButtons() {
        binding.btnCancel.setOnClickListener { dismiss() }

        binding.btnSave.setOnClickListener {
            val selectedChipId = binding.chipGroupTimeOfDay.checkedChipId
            val timeOfDay = when (selectedChipId) {
                R.id.chipMorning -> TimeOfDay.MORNING
                R.id.chipAfternoon -> TimeOfDay.AFTERNOON
                R.id.chipEvening -> TimeOfDay.EVENING
                else -> TimeOfDay.ALL_DAY
            }
            val duration = binding.durationInput.text?.toString()?.trim()
                ?.ifEmpty { habit.duration } ?: habit.duration

            val updated = habit.copy(
                timeOfDay = timeOfDay,
                duration = duration,
                color = selectedColor
            )
            onSave(updated)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
