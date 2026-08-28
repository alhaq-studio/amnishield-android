package com.alhaq.amnishield.ui.fragments.installation
 
import android.os.Bundle
import com.alhaq.amnishield.ui.fragments.features.PremiumFeaturesFragment
 
class SupportAlHaqFragment : PremiumFeaturesFragment() {
 
    companion object {
        const val FRAGMENT_ID = "support_alhaq_fragment"
    }
 
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ true
        )
        returnTransition = com.google.android.material.transition.MaterialSharedAxis(
            com.google.android.material.transition.MaterialSharedAxis.X,
            /* forward = */ false
        )
        val args = arguments ?: Bundle()
        args.putBoolean(ARG_IS_ONBOARDING, true)
        arguments = args
    }
}
