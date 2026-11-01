package com.hudhud.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.hudhud.app.BuildConfig
import com.hudhud.app.R
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.databinding.FragmentSettingsAdvancedBinding

/**
 * Fragment for advanced settings and maintenance
 */
class AdvancedFragment : Fragment() {
    private var _binding: FragmentSettingsAdvancedBinding? = null
    private val binding get() = _binding!!

    private lateinit var appSettings: AppSettingsStore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsAdvancedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appSettings = AppSettingsStore(requireContext())

        setupButtons()
        displayAppInfo()
    }

    private fun setupButtons() {
        binding.resetDefaultsButton.setOnClickListener { confirmResetDefaults() }
        binding.contractApprovalSwitch.setOnCheckedChangeListener { _, isChecked ->
            val activeProfile = appSettings.getActiveProfile()
            val updated = activeProfile.copy(isContractApproved = isChecked)
            if (!appSettings.saveProfile(updated)) {
                Toast.makeText(requireContext(), R.string.settings_save_failed, Toast.LENGTH_LONG).show()
                binding.contractApprovalSwitch.isChecked = !isChecked
                return@setOnCheckedChangeListener
            }
            renderContractApprovalState()
        }
    }

    private fun displayAppInfo() {
        renderContractApprovalState()
        try {
            val versionName = BuildConfig.VERSION_NAME
            val versionCode = BuildConfig.VERSION_CODE
            binding.appVersionText.text = getString(R.string.settings_about_version, versionName, versionCode)
        } catch (e: Exception) {
            binding.appVersionText.text = getString(R.string.settings_about_version_unknown)
        }
    }

    private fun renderContractApprovalState() {
        val activeProfile = appSettings.getActiveProfile()
        binding.contractApprovalSwitch.isChecked = activeProfile.isContractApproved
        binding.contractApprovalSwitch.text = if (activeProfile.isContractApproved) {
            getString(R.string.settings_contract_approval_on)
        } else {
            getString(R.string.settings_contract_approval_off)
        }
    }

    private fun confirmResetDefaults() {
        val activeProfileId = appSettings.getActiveProfile().id
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_reset_confirm_title)
            .setMessage(R.string.settings_reset_confirm_message)
            .setPositiveButton(R.string.settings_reset_defaults) { _, _ ->
                val reset = appSettings.resetProfileToDefaults(activeProfileId)
                if (reset == null) {
                    Toast.makeText(requireContext(), R.string.settings_save_failed, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                Toast.makeText(requireContext(), R.string.settings_reset_defaults, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.settings_dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}