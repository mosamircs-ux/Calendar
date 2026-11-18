package org.fossify.calendar.activities

import android.os.Bundle
import org.fossify.calendar.BuildConfig
import org.fossify.calendar.R
import org.fossify.calendar.databinding.ActivityPlanoraAboutBinding
import org.fossify.calendar.helpers.AdManager
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon

class PlanoraAboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityPlanoraAboutBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.aboutNestedScrollview))
        setupMaterialScrollListener(binding.aboutNestedScrollview, binding.aboutAppbar)

        binding.planoraVersionText.text = getString(
            R.string.planora_version_format,
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )

        binding.adPrivacyRow.setOnClickListener {
            if (AdManager.isPrivacyOptionsRequired(this)) {
                AdManager.showPrivacyOptionsForm(this) { errorMsg ->
                    if (!errorMsg.isNullOrBlank()) {
                        toast(errorMsg)
                    }
                }
            } else {
                toast(R.string.planora_ad_privacy_unavailable)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.aboutHolder)
    }
}
