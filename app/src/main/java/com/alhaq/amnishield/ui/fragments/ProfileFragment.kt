package com.alhaq.amnishield.ui.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.app.Activity
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.alhaq.amnishield.R
import com.alhaq.amnishield.premium.PremiumManager
import com.alhaq.amnishield.ui.activity.MainActivity
import com.alhaq.amnishield.ui.screens.ProfileScreen
import com.alhaq.amnishield.ui.theme.AmniShieldTheme
import com.alhaq.amnishield.ui.viewmodel.AmniShieldViewModel
import com.alhaq.amnishield.utils.GoogleSignInHelper

import androidx.lifecycle.lifecycleScope
import com.alhaq.amnishield.data.AmniShieldAccount
import com.alhaq.amnishield.data.sync.SupabaseRest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private lateinit var googleSignInHelper: GoogleSignInHelper
    private lateinit var viewModel: AmniShieldViewModel

    // Activity result launcher for Google Sign-In flow
    private val googleSignInLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val account = googleSignInHelper.handleSignInResult(result.data)
            if (account != null) {
                // Update SharedPreferences profile info
                val sharedPrefs = requireContext().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
                sharedPrefs.edit().apply {
                    putString("profile_name", account.displayName)
                    putString("profile_email", account.email)
                    apply()
                }
                // Refresh ViewModel state
                loadProfileData()
                Toast.makeText(requireContext(), getString(R.string.signed_in_as, account.email), Toast.LENGTH_SHORT).show()
                
                // Automatically connect and verify with Supabase backend
                syncAccountWithBackend(account)
            } else {
                Toast.makeText(requireContext(), "Sign in failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun syncAccountWithBackend(account: AmniShieldAccount) {
        val appContext = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val rest = SupabaseRest()
                var profile: SupabaseRest.UserProfile? = null

                if (!account.idToken.isNullOrBlank()) {
                    try {
                        val session = rest.signInWithGoogleIdToken(account.idToken)
                        if (session != null) {
                            profile = rest.fetchProfile(session)
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("ProfileFragment", "Supabase id_token exchange failed, falling back to email profile fetch", e)
                    }
                }

                if (profile == null && !account.email.isNullOrBlank()) {
                    try {
                        profile = rest.fetchProfileByEmail(account.email)
                    } catch (e: Exception) {
                        android.util.Log.w("ProfileFragment", "Fetch profile by email failed", e)
                    }
                }

                if (profile != null) {
                    val key = profile.licenseKey
                    if (!key.isNullOrBlank()) {
                        val activated = PremiumManager.getInstance(appContext).redeemLicenseKey(key)
                        if (activated) {
                            withContext(Dispatchers.Main) {
                                loadProfileData()
                                Toast.makeText(appContext, "Pro License Synced and Activated from Account!", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else if (profile.isPremium) {
                        PremiumManager.getInstance(appContext).updatePremiumStatus(true)
                        withContext(Dispatchers.Main) {
                            loadProfileData()
                            Toast.makeText(appContext, "AmniShield Pro Status Synced!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ProfileFragment", "Background account sync error", e)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[AmniShieldViewModel::class.java]
        googleSignInHelper = GoogleSignInHelper(requireContext().applicationContext)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                val activeTheme = com.alhaq.amnishield.utils.ThemeUtils.resolveAppTheme(requireContext())
                viewModel.updateTheme(activeTheme)
                val state by viewModel.state.collectAsState()
                
                // Track Google sign-in status dynamically
                val isGoogleSignedIn = remember(state.userName, state.userEmail) {
                    googleSignInHelper.isSignedIn()
                }
                
                AmniShieldTheme(appTheme = activeTheme) {
                    ProfileScreen(
                        state = state,
                        viewModel = viewModel,
                        isGoogleSignedIn = isGoogleSignedIn,
                        onGoogleSignIn = { signInWithGoogle() },
                        onGoogleSignOut = { signOutFromGoogle() },
                        onBack = {
                            if (!parentFragmentManager.popBackStackImmediate()) {
                                requireActivity().finish()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadProfileData()
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInHelper.getSignInIntent()
        googleSignInLauncher.launch(signInIntent)
    }

    private fun signOutFromGoogle() {
        googleSignInHelper.signOut {
            val sharedPrefs = requireContext().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            sharedPrefs.edit().apply {
                remove("profile_name")
                remove("profile_email")
                apply()
            }
            loadProfileData()
            Toast.makeText(requireContext(), getString(R.string.signed_out_successfully), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadProfileData() {
        val account = googleSignInHelper.getLastSignedInAccount()
        val defaultName = account?.displayName ?: ""
        val defaultEmail = account?.email ?: ""
        
        val sharedPrefs = requireContext().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val name = sharedPrefs.getString("profile_name", defaultName) ?: defaultName
        val email = sharedPrefs.getString("profile_email", defaultEmail) ?: defaultEmail
        val bio = sharedPrefs.getString("profile_bio", "") ?: ""
        val profileType = sharedPrefs.getString("profile_type", "Deep Focus") ?: "Deep Focus"
        
        val imageUri = sharedPrefs.getString("profile_image_uri", null)
        val avatarId = sharedPrefs.getString("profile_avatar_id", "avatar_shield") ?: "avatar_shield"
        val goalMinutes = sharedPrefs.getInt("profile_goal_minutes", 120)

        // Compute production-ready dynamic guardian stats
        val guardianStats = com.alhaq.amnishield.utils.GuardianStatsEngine.computeGuardianStats(requireContext().applicationContext)
        viewModel.updateGuardianStats(
            streakDays = guardianStats.focusStreakDays,
            shieldScore = guardianStats.focusShieldScore,
            threatsBlocked = guardianStats.totalThreatsBlocked
        )

        viewModel.updateProfile(
            name = name,
            email = email,
            bio = bio,
            goalMinutes = goalMinutes,
            profileType = profileType,
            pinEnabled = viewModel.state.value.isPinProtectionEnabled,
            pin = viewModel.state.value.profilePin,
            imageUri = imageUri,
            avatarId = avatarId
        )

        val isAdvanced = true

        // Ensure ViewModel state has the correct premium status and advanced mode as well
        val isPremium = com.alhaq.amnishield.premium.PremiumManager.getInstance(requireContext().applicationContext).isPremium()
        if (viewModel.state.value.isPremiumUser != isPremium || viewModel.state.value.isAdvancedMode != isAdvanced) {
            viewModel.loadState(viewModel.state.value.copy(isPremiumUser = isPremium, isAdvancedMode = isAdvanced))
        }
    }

    override fun onResume() {
        super.onResume()
        loadProfileData()
    }
    
    fun refreshProfile() {
        if (isAdded) {
            loadProfileData()
        }
    }

    companion object {
        const val FRAGMENT_ID = "profile"
    }
}
