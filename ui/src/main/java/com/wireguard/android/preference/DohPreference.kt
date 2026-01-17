/*
 * Copyright © 2017-2025 WireGuard LLC. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.wireguard.android.preference

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.wireguard.android.R
import com.wireguard.android.fragment.DohSettingsDialogFragment
import com.wireguard.android.util.UserKnobs
import com.wireguard.android.util.activity
import com.wireguard.android.util.lifecycleScope
import com.wireguard.config.DohConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DohPreference(context: Context, attrs: AttributeSet?) : Preference(context, attrs) {
    private var isDoHEnabled = false
    private var providerName: String? = null
    private var switchView: SwitchMaterial? = null

    init {
        widgetLayoutResource = R.layout.preference_doh_switch
        lifecycleScope.launch {
            isDoHEnabled = UserKnobs.dohEnabled.first()
            providerName = UserKnobs.dohProviderName.first()
            updateSummary()
            notifyChanged()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        switchView = holder.findViewById(R.id.doh_switch) as? SwitchMaterial
        switchView?.apply {
            // Remove any existing listener to avoid duplicate calls
            setOnCheckedChangeListener(null)
            isChecked = isDoHEnabled
            setOnCheckedChangeListener { _, isChecked ->
                if (isDoHEnabled != isChecked) {
                    isDoHEnabled = isChecked
                    lifecycleScope.launch {
                        UserKnobs.setDohEnabled(isChecked)
                        if (!isChecked) {
                            // When disabled, set mode to system_only
                            UserKnobs.setDohPriorityMode(DohConfig.MODE_SYSTEM_ONLY)
                            DohConfig.configure(false, null, null, DohConfig.MODE_SYSTEM_ONLY)
                        } else {
                            // When enabled, restore DoH settings
                            val serverUrl = UserKnobs.dohServerUrl.first()
                            val provider = UserKnobs.dohProviderName.first()
                            val mode = UserKnobs.dohPriorityMode.first()
                            val actualMode = if (mode == DohConfig.MODE_SYSTEM_ONLY) DohConfig.MODE_DOH_FIRST else mode
                            UserKnobs.setDohPriorityMode(actualMode)
                            DohConfig.configure(true, serverUrl, provider, actualMode)
                        }
                        updateSummary()
                        notifyChanged()
                    }
                }
            }
        }

        // Make the preference row clickable to open settings
        holder.itemView.setOnClickListener {
            onClick()
        }

        // Prevent switch click from bubbling to the preference row
        switchView?.isClickable = true
    }

    override fun onClick() {
        val fragmentManager = activity.supportFragmentManager
        DohSettingsDialogFragment.newInstance().show(fragmentManager, "doh_settings")
    }

    private fun updateSummary() {
        summary = if (isDoHEnabled && providerName != null) {
            providerName
        } else if (isDoHEnabled) {
            context.getString(R.string.doh_enabled)
        } else {
            context.getString(R.string.doh_settings_summary_disabled)
        }
    }

    fun refreshState() {
        lifecycleScope.launch {
            isDoHEnabled = UserKnobs.dohEnabled.first()
            providerName = UserKnobs.dohProviderName.first()
            updateSummary()
            switchView?.isChecked = isDoHEnabled
            notifyChanged()
        }
    }
}
