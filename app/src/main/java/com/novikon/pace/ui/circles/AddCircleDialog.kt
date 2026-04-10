package com.novikon.pace.ui.circles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.novikon.pace.R
import com.novikon.pace.databinding.DialogAddCircleBinding

class AddCircleDialog(
    private val onJoinCircleByCode: (code: String) -> Unit,
    private val onCreateCircle: (name: String, maxParticipants: Int) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogAddCircleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogAddCircleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        showJoin()

        binding.closeButton.setOnClickListener { dismiss() }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                if (tab?.position == 0) showJoin() else showCreate()
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.sliderMembers.addOnChangeListener { _, value, _ ->
            binding.tvMembersValue.text = value.toInt().toString()
        }

        binding.btnJoin.setOnClickListener {
            val code = binding.etJoinCode.text?.toString()?.trim().orEmpty()

            if (!code.matches(Regex("^\\d{6}$"))) {
                binding.etJoinCode.error = getString(R.string.error_circle_code_required)
                return@setOnClickListener
            }

            onJoinCircleByCode(code)
            dismiss()
        }

        binding.btnCreate.setOnClickListener {
            val name = binding.etGroupName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                binding.etGroupName.error = getString(R.string.error_group_name_required)
                return@setOnClickListener
            }

            val maxParticipants = binding.sliderMembers.value.toInt()
            onCreateCircle(name, maxParticipants)
            dismiss()
        }
    }

    private fun showJoin() {
        binding.panelJoin.visibility = View.VISIBLE
        binding.panelCreate.visibility = View.GONE
    }

    private fun showCreate() {
        binding.panelJoin.visibility = View.GONE
        binding.panelCreate.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}