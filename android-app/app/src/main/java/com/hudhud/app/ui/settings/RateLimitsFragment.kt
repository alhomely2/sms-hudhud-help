package com.hudhud.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.hudhud.app.R
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.databinding.FragmentSettingsRateLimitsBinding
import com.hudhud.app.domain.settings.SiteProfile
import com.hudhud.app.domain.settings.SiteProfileValidator
import com.hudhud.app.domain.settings.ThrottlePreset
import java.math.BigDecimal

/**
 * Fragment for managing rate limits, timeouts, and transmission settings
 */
class RateLimitsFragment : Fragment() {
    private var _binding: FragmentSettingsRateLimitsBinding? = null
    private val binding get() = _binding!!

    private lateinit var appSettings: AppSettingsStore
    private var currentPreset: ThrottlePreset = ThrottlePreset.BALANCED

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsRateLimitsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appSettings = AppSettingsStore(requireContext())

        setupPresetButtons()
        setupSaveButton()
        loadProfileSettings()
    }

    private fun setupPresetButtons() {
        binding.presetQuietButton.setOnClickListener { applyPreset(ThrottlePreset.QUIET) }
        binding.presetBalancedButton.setOnClickListener { applyPreset(ThrottlePreset.BALANCED) }
        binding.presetFastButton.setOnClickListener { applyPreset(ThrottlePreset.FAST) }
        binding.presetCustomButton.setOnClickListener { applyPreset(ThrottlePreset.CUSTOM) }
    }

    private fun setupSaveButton() {
        binding.saveSettingsButton.setOnClickListener { saveSettings() }
    }

    private fun loadProfileSettings() {
        val profile = appSettings.getActiveProfile()
        loadProfileForm(profile)
    }

    private fun loadProfileForm(profile: SiteProfile) {
        binding.delayInput.setText(formatDelaySeconds(profile.delayBetweenMessagesMs))
        binding.batchSizeInput.setText(profile.batchSize.toString())
        binding.maxRetriesInput.setText(profile.maxRetryAttempts.toString())
        binding.connectTimeoutInput.setText(profile.connectTimeoutSeconds.toString())
        binding.readTimeoutInput.setText(profile.readTimeoutSeconds.toString())
        binding.callTimeoutInput.setText(profile.callTimeoutSeconds.toString())
        applyPreset(profile.preset, updateFormValues = false)
        clearFormErrors()
    }

    private fun applyPreset(preset: ThrottlePreset, updateFormValues: Boolean = true) {
        currentPreset = preset
        if (updateFormValues && preset != ThrottlePreset.CUSTOM) {
            binding.delayInput.setText(formatDelaySeconds(preset.delayMs))
            binding.maxRetriesInput.setText(preset.maxRetries.toString())
            clearFormErrors()
        }

        val primaryColor = ContextCompat.getColor(requireContext(), R.color.hudhud_primary)
        val mutedColor = ContextCompat.getColor(requireContext(), R.color.hudhud_on_surface_muted)

        binding.presetQuietButton.strokeColor = android.content.res.ColorStateList.valueOf(
            if (preset == ThrottlePreset.QUIET) primaryColor else mutedColor,
        )
        binding.presetBalancedButton.strokeColor = android.content.res.ColorStateList.valueOf(
            if (preset == ThrottlePreset.BALANCED) primaryColor else mutedColor,
        )
        binding.presetFastButton.strokeColor = android.content.res.ColorStateList.valueOf(
            if (preset == ThrottlePreset.FAST) primaryColor else mutedColor,
        )
        binding.presetCustomButton.strokeColor = android.content.res.ColorStateList.valueOf(
            if (preset == ThrottlePreset.CUSTOM) primaryColor else mutedColor,
        )
    }

    private fun clearFormErrors() {
        binding.delayInputLayout.error = null
        binding.maxRetriesInputLayout.error = null
        binding.batchSizeInputLayout.error = null
        binding.connectTimeoutInputLayout.error = null
        binding.readTimeoutInputLayout.error = null
        binding.callTimeoutInputLayout.error = null
    }

    private fun formatDelaySeconds(delayMs: Long): String =
        BigDecimal.valueOf(delayMs, 3).stripTrailingZeros().toPlainString()

    private fun parseDelayMilliseconds(input: String?): Long? {
        val seconds = input?.trim()?.replace(',', '.')?.toBigDecimalOrNull() ?: return null
        return try {
            seconds.movePointRight(3).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    private fun saveSettings() {
        val delayMs = parseDelayMilliseconds(binding.delayInput.text?.toString())
        val retries = binding.maxRetriesInput.text?.toString()?.toIntOrNull()
        val batchSize = binding.batchSizeInput.text?.toString()?.toIntOrNull()
        val connectTimeout = binding.connectTimeoutInput.text?.toString()?.toIntOrNull()
        val readTimeout = binding.readTimeoutInput.text?.toString()?.toIntOrNull()
        val callTimeout = binding.callTimeoutInput.text?.toString()?.toIntOrNull()

        var hasError = false
        if (SiteProfileValidator.validateDelayMs(delayMs) is SiteProfileValidator.ValidationResult.Invalid) {
            binding.delayInputLayout.error = getString(R.string.settings_validation_delay_error)
            hasError = true
        } else {
            binding.delayInputLayout.error = null
        }

        if (SiteProfileValidator.validateMaxRetries(retries) is SiteProfileValidator.ValidationResult.Invalid) {
            binding.maxRetriesInputLayout.error = getString(R.string.settings_validation_retries_error)
            hasError = true
        } else {
            binding.maxRetriesInputLayout.error = null
        }

        if (SiteProfileValidator.validateBatchSize(batchSize) is SiteProfileValidator.ValidationResult.Invalid) {
            binding.batchSizeInputLayout.error = getString(R.string.settings_validation_batch_error)
            hasError = true
        } else {
            binding.batchSizeInputLayout.error = null
        }

        if (SiteProfileValidator.validateConnectTimeout(connectTimeout) is SiteProfileValidator.ValidationResult.Invalid) {
            binding.connectTimeoutInputLayout.error = getString(R.string.settings_validation_connect_timeout_error)
            hasError = true
        } else {
            binding.connectTimeoutInputLayout.error = null
        }

        if (SiteProfileValidator.validateReadTimeout(readTimeout) is SiteProfileValidator.ValidationResult.Invalid) {
            binding.readTimeoutInputLayout.error = getString(R.string.settings_validation_read_timeout_error)
            hasError = true
        } else {
            binding.readTimeoutInputLayout.error = null
        }

        if (SiteProfileValidator.validateCallTimeout(callTimeout) is SiteProfileValidator.ValidationResult.Invalid) {
            binding.callTimeoutInputLayout.error = getString(R.string.settings_validation_call_timeout_error)
            hasError = true
        } else {
            binding.callTimeoutInputLayout.error = null
        }

        if (hasError || delayMs == null || retries == null || batchSize == null ||
            connectTimeout == null || readTimeout == null || callTimeout == null
        ) return

        val profile = appSettings.getActiveProfile()
        val updated = profile.copy(
            delayBetweenMessagesMs = delayMs,
            batchSize = batchSize,
            maxRetryAttempts = retries,
            connectTimeoutSeconds = connectTimeout,
            readTimeoutSeconds = readTimeout,
            callTimeoutSeconds = callTimeout,
            preset = ThrottlePreset.fromValues(delayMs, retries),
        )

        if (!appSettings.saveProfile(updated)) {
            Toast.makeText(requireContext(), R.string.settings_save_failed, Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(requireContext(), R.string.settings_save_success, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}