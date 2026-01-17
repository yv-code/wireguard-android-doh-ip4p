/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.fragment

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RadioGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.wireguard.android.R
import com.wireguard.android.preference.DohPreference
import com.wireguard.android.util.UserKnobs
import com.wireguard.config.DohConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DohSettingsDialogFragment : DialogFragment() {

    private lateinit var providerLayout: TextInputLayout
    private lateinit var providerDropdown: AutoCompleteTextView
    private lateinit var customUrlLayout: TextInputLayout
    private lateinit var customUrlInput: TextInputEditText
    private lateinit var priorityModeGroup: RadioGroup
    private lateinit var activityScope: CoroutineScope

    private val providerNames = mutableListOf<String>()
    private val providerUrls = mutableMapOf<String, String>()

    // Custom URL loaded from storage
    private var savedCustomUrl: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val activity = requireActivity()
        activityScope = activity.lifecycleScope
        val alertDialogBuilder = MaterialAlertDialogBuilder(activity)
        alertDialogBuilder.setTitle(R.string.doh_settings_title)

        val view = activity.layoutInflater.inflate(R.layout.doh_settings_dialog, null)
        initViews(view)
        setupProviders()
        loadCurrentSettings()

        alertDialogBuilder.setView(view)
        alertDialogBuilder.setPositiveButton(R.string.save, null) // Set listener later to prevent auto-dismiss
        alertDialogBuilder.setNegativeButton(R.string.cancel) { _, _ -> dismiss() }

        val dialog = alertDialogBuilder.create()

        // Override positive button to add validation
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                if (validateAndSave()) {
                    dialog.dismiss()
                }
            }
        }

        return dialog
    }

    private fun initViews(view: View) {
        providerLayout = view.findViewById(R.id.doh_provider_layout)
        providerDropdown = view.findViewById(R.id.doh_provider_dropdown)
        customUrlLayout = view.findViewById(R.id.doh_custom_url_layout)
        customUrlInput = view.findViewById(R.id.doh_custom_url_input)
        priorityModeGroup = view.findViewById(R.id.doh_priority_mode_group)

        val customLabel = getString(R.string.doh_custom_provider)
        providerDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedProvider = providerNames[position]
            if (selectedProvider == customLabel) {
                customUrlLayout.visibility = View.VISIBLE
                // Restore saved custom URL
                if (savedCustomUrl != null) {
                    customUrlInput.setText(savedCustomUrl)
                }
            } else {
                // Save current custom URL before hiding
                if (customUrlLayout.visibility == View.VISIBLE) {
                    savedCustomUrl = customUrlInput.text.toString()
                }
                customUrlLayout.visibility = View.GONE
            }
        }
    }

    private fun setupProviders() {
        // Add preset providers
        for ((name, url) in DohConfig.PRESET_PROVIDERS) {
            providerNames.add(name)
            providerUrls[name] = url
        }
        // Add custom option
        val customLabel = getString(R.string.doh_custom_provider)
        providerNames.add(customLabel)

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, providerNames)
        providerDropdown.setAdapter(adapter)
    }

    private fun loadCurrentSettings() {
        activityScope.launch {
            val serverUrl = UserKnobs.dohServerUrl.first()
            val providerName = UserKnobs.dohProviderName.first()
            val priorityMode = UserKnobs.dohPriorityMode.first()
            val customLabel = getString(R.string.doh_custom_provider)

            // Load saved custom URL from storage
            savedCustomUrl = UserKnobs.dohCustomUrl.first()

            // Set provider selection
            if (providerName != null && providerNames.contains(providerName)) {
                providerDropdown.setText(providerName, false)
                if (providerName == customLabel) {
                    customUrlLayout.visibility = View.VISIBLE
                    customUrlInput.setText(savedCustomUrl ?: serverUrl)
                }
            } else if (serverUrl != null) {
                // Find provider by URL
                val foundProvider = providerUrls.entries.find { it.value == serverUrl }?.key
                if (foundProvider != null) {
                    providerDropdown.setText(foundProvider, false)
                } else {
                    providerDropdown.setText(customLabel, false)
                    customUrlLayout.visibility = View.VISIBLE
                    customUrlInput.setText(serverUrl)
                    savedCustomUrl = serverUrl
                }
            } else {
                // Default to first provider
                if (providerNames.isNotEmpty()) {
                    providerDropdown.setText(providerNames[0], false)
                }
            }

            // Set priority mode (default to doh_first if system_only since dialog implies DoH is enabled)
            when (priorityMode) {
                DohConfig.MODE_DOH_FIRST -> priorityModeGroup.check(R.id.mode_doh_first)
                DohConfig.MODE_SYSTEM_FIRST -> priorityModeGroup.check(R.id.mode_system_first)
                DohConfig.MODE_DOH_ONLY -> priorityModeGroup.check(R.id.mode_doh_only)
                else -> priorityModeGroup.check(R.id.mode_doh_first) // Default to doh_first
            }
        }
    }

    private fun validateAndSave(): Boolean {
        val selectedProvider = providerDropdown.text.toString()
        val customLabel = getString(R.string.doh_custom_provider)

        // Get custom URL from input
        val customUrl = customUrlInput.text.toString().trim()

        // Validate: custom provider requires a non-empty URL
        if (selectedProvider == customLabel && customUrl.isBlank()) {
            customUrlLayout.error = getString(R.string.doh_custom_url_required)
            customUrlInput.requestFocus()
            return false
        }

        // Clear any previous error
        customUrlLayout.error = null

        val serverUrl = if (selectedProvider == customLabel) {
            customUrl.takeIf { it.isNotBlank() }
        } else {
            providerUrls[selectedProvider]
        }

        val priorityMode = when (priorityModeGroup.checkedRadioButtonId) {
            R.id.mode_doh_first -> DohConfig.MODE_DOH_FIRST
            R.id.mode_system_first -> DohConfig.MODE_SYSTEM_FIRST
            R.id.mode_doh_only -> DohConfig.MODE_DOH_ONLY
            else -> DohConfig.MODE_DOH_FIRST
        }

        // Sync to DohConfig immediately (synchronous)
        DohConfig.configure(true, serverUrl, selectedProvider, priorityMode)

        // Find preference before launching coroutine
        val preference = findDohPreference()

        // Save to UserKnobs asynchronously using activity scope
        activityScope.launch {
            UserKnobs.setDohEnabled(true)
            UserKnobs.setDohServerUrl(serverUrl)
            UserKnobs.setDohProviderName(selectedProvider)
            UserKnobs.setDohPriorityMode(priorityMode)
            // Always save custom URL to preserve it
            if (customUrl.isNotBlank()) {
                UserKnobs.setDohCustomUrl(customUrl)
            }

            // Refresh preference after data is saved
            preference?.refreshState()
        }

        return true
    }

    private fun findDohPreference(): DohPreference? {
        return try {
            val settingsFragment = parentFragmentManager.fragments.find {
                it is PreferenceFragmentCompat
            } as? PreferenceFragmentCompat

            settingsFragment?.findPreference<DohPreference>("doh_settings")
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun newInstance(): DohSettingsDialogFragment {
            return DohSettingsDialogFragment()
        }
    }
}
