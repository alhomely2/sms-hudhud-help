package com.hudhud.app.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.hudhud.app.R
import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.data.remote.ApiKeyStore
import com.hudhud.app.data.remote.ConnectionOutcome
import com.hudhud.app.data.remote.ConnectionResult
import com.hudhud.app.data.remote.HudhudClient
import com.hudhud.app.databinding.FragmentSettingsApiKeysBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fragment for managing API keys and testing connectivity
 */
class ApiKeysFragment : Fragment() {
    private var _binding: FragmentSettingsApiKeysBinding? = null
    private val binding get() = _binding!!

    private lateinit var keyStore: ApiKeyStore
    private lateinit var appSettings: AppSettingsStore

    private val selectedProfileId: String
        get() = appSettings.getActiveProfile().id

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsApiKeysBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        keyStore = ApiKeyStore(requireContext())
        appSettings = AppSettingsStore(requireContext())

        setupButtons()
        renderSavedKey()
    }

    private fun setupButtons() {
        binding.saveKeyButton.setOnClickListener { saveKey() }
        binding.clearKeyButton.setOnClickListener { confirmClearKey() }
        binding.testConnectionButton.setOnClickListener { testConnection() }
    }

    private fun renderSavedKey() {
        val masked = keyStore.masked(selectedProfileId)
        if (masked == null) {
            binding.savedKeyText.text = getString(R.string.connection_no_saved_key)
            binding.clearKeyButton.visibility = View.GONE
        } else {
            binding.savedKeyText.text = getString(R.string.connection_saved_key, masked)
            binding.clearKeyButton.visibility = View.VISIBLE
        }
    }

    private fun saveKey() {
        val key = binding.apiKeyInput.text?.toString()?.trim().orEmpty()
        if (key.isEmpty()) {
            Toast.makeText(requireContext(), R.string.api_key_empty, Toast.LENGTH_SHORT).show()
            return
        }
        if (keyStore.save(selectedProfileId, key)) {
            binding.apiKeyInput.text?.clear()
            renderSavedKey()
            Toast.makeText(requireContext(), R.string.api_key_saved, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), R.string.api_key_save_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmClearKey() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.settings_key_clear_button)
            .setMessage(R.string.settings_key_clear_confirm)
            .setPositiveButton(R.string.settings_dialog_save) { _, _ ->
                keyStore.clear(selectedProfileId)
                renderSavedKey()
                Toast.makeText(requireContext(), R.string.settings_key_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.settings_dialog_cancel, null)
            .show()
    }

    private fun testConnection() {
        val key = keyStore.load(selectedProfileId)
        if (key.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.api_key_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val profile = appSettings.getActiveProfile()
        if (!profile.isEnabled) {
            Toast.makeText(requireContext(), R.string.settings_profile_operation_disabled, Toast.LENGTH_LONG).show()
            return
        }

        binding.testConnectionButton.isEnabled = false
        binding.testProgressBar.visibility = View.VISIBLE
        binding.connectionStatusCard.visibility = View.GONE

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                HudhudClient(profile).probe(key)
            }
            binding.testConnectionButton.isEnabled = true
            binding.testProgressBar.visibility = View.GONE
            displayProbeResult(result)
        }
    }

    private fun displayProbeResult(result: ConnectionResult) {
        binding.connectionStatusCard.visibility = View.VISIBLE

        val codeStr = result.httpCode?.toString() ?: "-"
        binding.hostStatusText.text = if (result.hostReachable) {
            "✔ " + getString(R.string.settings_test_host_status, getString(R.string.settings_test_reachable_ok, codeStr))
        } else {
            "✖ " + getString(R.string.settings_test_host_status, getString(R.string.settings_test_reachable_fail))
        }

        binding.authStatusText.text = when (result.authAccepted) {
            true -> "✔ " + getString(R.string.settings_test_auth_status, getString(R.string.settings_test_auth_ok))
            false -> {
                val detail = if (result.outcome == ConnectionOutcome.KEY_MISMATCH) {
                    getString(R.string.settings_test_auth_mismatch, codeStr)
                } else {
                    getString(R.string.settings_test_auth_rejected, codeStr)
                }
                "✖ " + getString(R.string.settings_test_auth_status, detail)
            }
            null -> "— " + getString(R.string.settings_test_auth_status, getString(R.string.status_unknown_detail))
        }

        binding.apiStatusText.text = when {
            result.apiOperational -> "✔ " + getString(R.string.settings_test_api_status, getString(R.string.settings_test_api_ok, codeStr))
            result.outcome == ConnectionOutcome.SERVER_ERROR -> "⚠ " + getString(R.string.settings_test_api_status, getString(R.string.settings_test_api_server_error, codeStr))
            else -> "— " + getString(R.string.settings_test_api_status, result.detail ?: describeOutcome(result.outcome, codeStr))
        }

        if (!result.detail.isNullOrBlank()) {
            binding.connectionStatusText.visibility = View.VISIBLE
            binding.connectionStatusText.text = getString(R.string.connection_detail, result.detail)
        } else {
            binding.connectionStatusText.visibility = View.GONE
        }

        if (!result.rateLimitLimit.isNullOrBlank() && !result.rateLimitRemaining.isNullOrBlank()) {
            binding.rateLimitText.visibility = View.VISIBLE
            binding.rateLimitText.text = getString(R.string.settings_test_ratelimit_info, result.rateLimitLimit, result.rateLimitRemaining)
        } else {
            binding.rateLimitText.visibility = View.GONE
        }

        if (result.retryAfterSeconds != null && result.retryAfterSeconds > 0) {
            binding.retryAfterText.visibility = View.VISIBLE
            binding.retryAfterText.text = getString(R.string.settings_test_retry_after_info, result.retryAfterSeconds)
        } else {
            binding.retryAfterText.visibility = View.GONE
        }

        if (!result.correlationId.isNullOrBlank()) {
            binding.connectionCorrelationText.visibility = View.VISIBLE
            binding.connectionCorrelationText.text = getString(R.string.connection_correlation, result.correlationId)
        } else {
            binding.connectionCorrelationText.visibility = View.GONE
        }
    }

    private fun describeOutcome(outcome: ConnectionOutcome, code: String): String = when (outcome) {
        ConnectionOutcome.CONNECTED -> getString(R.string.status_connected, code)
        ConnectionOutcome.AUTH_REJECTED -> getString(R.string.status_auth_rejected, code)
        ConnectionOutcome.KEY_MISMATCH -> getString(R.string.status_key_mismatch, code)
        ConnectionOutcome.SERVER_ERROR -> getString(R.string.status_server_error, code)
        ConnectionOutcome.NETWORK_ERROR -> getString(R.string.status_network_error, code)
        ConnectionOutcome.UNEXPECTED_RESPONSE -> getString(R.string.status_unexpected, code)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}