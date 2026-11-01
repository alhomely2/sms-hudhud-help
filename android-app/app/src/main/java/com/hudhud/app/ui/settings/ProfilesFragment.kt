package com.hudhud.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.hudhud.app.R
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.databinding.FragmentSettingsProfilesBinding
import com.hudhud.app.databinding.DialogSiteProfileBinding
import com.hudhud.app.domain.settings.SiteProfile
import com.hudhud.app.domain.settings.SiteProfileValidator
import java.util.UUID

/**
 * Fragment for managing site profiles (add, edit, delete, activate)
 */
class ProfilesFragment : Fragment() {
    private var _binding: FragmentSettingsProfilesBinding? = null
    private val binding get() = _binding!!

    private lateinit var appSettings: AppSettingsStore
    private var currentProfiles: List<SiteProfile> = emptyList()
    private var selectedProfileId: String = SiteProfile.DEFAULT_STAGING_ID

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsProfilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appSettings = AppSettingsStore(requireContext())
        selectedProfileId = appSettings.getActiveProfile().id

        setupProfileSpinner()
        setupButtons()
        loadProfilesAndSelect(selectedProfileId)
    }

    private fun setupProfileSpinner() {
        binding.profileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in currentProfiles.indices) {
                    val profile = currentProfiles[position]
                    selectedProfileId = profile.id
                    renderProfileDetails(profile)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        binding.activateProfileButton.setOnClickListener {
            if (appSettings.setActiveProfileId(selectedProfileId)) {
                loadProfilesAndSelect(selectedProfileId)
                Toast.makeText(requireContext(), R.string.settings_profile_activated_toast, Toast.LENGTH_SHORT).show()
            }
        }

        binding.addProfileButton.setOnClickListener { showAddProfileDialog() }
        binding.editProfileButton.setOnClickListener { showEditProfileDialog() }
        binding.deleteProfileButton.setOnClickListener { confirmDeleteProfile() }
    }

    private fun loadProfilesAndSelect(targetId: String) {
        currentProfiles = appSettings.getAllProfiles()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            currentProfiles.map { it.name },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.profileSpinner.adapter = adapter

        val index = currentProfiles.indexOfFirst { it.id == targetId }.coerceAtLeast(0)
        binding.profileSpinner.setSelection(index)

        if (index in currentProfiles.indices) {
            val selected = currentProfiles[index]
            selectedProfileId = selected.id
            renderProfileDetails(selected)
        }
    }

    private fun renderProfileDetails(profile: SiteProfile) {
        val isActive = profile.id == appSettings.getActiveProfile().id
        binding.baseUrlText.text = getString(R.string.connection_base_url, profile.baseUrl)

        if (isActive) {
            binding.profileStatusBadge.text = getString(R.string.settings_profile_active_badge)
            binding.profileStatusBadge.setTextColor(resources.getColor(R.color.hudhud_primary, null))
            binding.activateProfileButton.visibility = View.GONE
        } else {
            binding.profileStatusBadge.text = if (!profile.isEnabled) {
                getString(R.string.settings_profile_disabled_badge)
            } else {
                getString(R.string.settings_profile_inactive_badge)
            }
            binding.profileStatusBadge.setTextColor(resources.getColor(R.color.hudhud_on_surface_muted, null))
            binding.activateProfileButton.visibility = if (profile.isEnabled) View.VISIBLE else View.GONE
        }
    }

    private fun showAddProfileDialog() {
        val dialogBinding = DialogSiteProfileBinding.inflate(layoutInflater)
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_dialog_add_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.settings_dialog_save, null)
            .setNegativeButton(R.string.settings_dialog_cancel, null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = dialogBinding.dialogProfileNameInput.text?.toString().orEmpty()
                    val url = dialogBinding.dialogProfileUrlInput.text?.toString().orEmpty()

                    val nameValid = SiteProfileValidator.validateName(name)
                    val urlValid = SiteProfileValidator.validateBaseUrl(url)

                    if (nameValid is SiteProfileValidator.ValidationResult.Invalid) {
                        dialogBinding.dialogProfileNameLayout.error = getString(R.string.settings_validation_name_error)
                        return@setOnClickListener
                    } else {
                        dialogBinding.dialogProfileNameLayout.error = null
                    }

                    if (urlValid is SiteProfileValidator.ValidationResult.Invalid) {
                        dialogBinding.dialogProfileUrlLayout.error = getString(R.string.settings_validation_url_error)
                        return@setOnClickListener
                    } else {
                        dialogBinding.dialogProfileUrlLayout.error = null
                    }

                    val newProfile = SiteProfile(
                        id = "profile_" + UUID.randomUUID().toString().take(8),
                        name = name.trim(),
                        baseUrl = url.trim(),
                        isEnabled = true,
                    )
                    if (!appSettings.saveProfile(newProfile)) {
                        Toast.makeText(requireContext(), R.string.settings_save_failed, Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    loadProfilesAndSelect(newProfile.id)
                    dismiss()
                }
            }
    }

    private fun showEditProfileDialog() {
        val profile = appSettings.getProfile(selectedProfileId) ?: return
        val dialogBinding = DialogSiteProfileBinding.inflate(layoutInflater)
        dialogBinding.dialogProfileNameInput.setText(profile.name)
        dialogBinding.dialogProfileUrlInput.setText(profile.baseUrl)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_dialog_edit_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.settings_dialog_save, null)
            .setNegativeButton(R.string.settings_dialog_cancel, null)
            .create()
            .apply {
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val name = dialogBinding.dialogProfileNameInput.text?.toString().orEmpty()
                    val url = dialogBinding.dialogProfileUrlInput.text?.toString().orEmpty()

                    val nameValid = SiteProfileValidator.validateName(name)
                    val urlValid = SiteProfileValidator.validateBaseUrl(url)

                    if (nameValid is SiteProfileValidator.ValidationResult.Invalid) {
                        dialogBinding.dialogProfileNameLayout.error = getString(R.string.settings_validation_name_error)
                        return@setOnClickListener
                    } else {
                        dialogBinding.dialogProfileNameLayout.error = null
                    }

                    if (urlValid is SiteProfileValidator.ValidationResult.Invalid) {
                        dialogBinding.dialogProfileUrlLayout.error = getString(R.string.settings_validation_url_error)
                        return@setOnClickListener
                    } else {
                        dialogBinding.dialogProfileUrlLayout.error = null
                    }

                    val updated = profile.copy(name = name.trim(), baseUrl = url.trim())
                    if (!appSettings.saveProfile(updated)) {
                        Toast.makeText(requireContext(), R.string.settings_save_failed, Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    loadProfilesAndSelect(updated.id)
                    dismiss()
                }
            }
    }

    private fun confirmDeleteProfile() {
        if (selectedProfileId == appSettings.getActiveProfile().id) {
            Toast.makeText(requireContext(), R.string.settings_profile_delete_error_active, Toast.LENGTH_LONG).show()
            return
        }
        if (currentProfiles.size <= 1) {
            Toast.makeText(requireContext(), R.string.settings_profile_delete_error_last, Toast.LENGTH_LONG).show()
            return
        }

        val profile = appSettings.getProfile(selectedProfileId) ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_profile_delete_confirm_title)
            .setMessage(getString(R.string.settings_profile_delete_confirm_message, profile.name))
            .setPositiveButton(R.string.settings_profile_delete_button) { _, _ ->
                if (!appSettings.deleteProfile(selectedProfileId)) {
                    Toast.makeText(requireContext(), R.string.settings_save_failed, Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                loadProfilesAndSelect(appSettings.getActiveProfile().id)
                Toast.makeText(requireContext(), R.string.settings_profile_deleted_toast, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.settings_dialog_cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}