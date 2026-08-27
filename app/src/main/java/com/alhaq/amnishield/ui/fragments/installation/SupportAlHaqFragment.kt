package com.alhaq.amnishield.ui.fragments.installation
 
import android.os.Bundle
import com.alhaq.amnishield.ui.fragments.features.PremiumFeaturesFragment
 
class SupportAlHaqFragment : PremiumFeaturesFragment() {
 
    companion object {
        const val FRAGMENT_ID = "support_alhaq_fragment"
    }
 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = arguments ?: Bundle()
        args.putBoolean(ARG_IS_ONBOARDING, true)
        arguments = args
    }
}
