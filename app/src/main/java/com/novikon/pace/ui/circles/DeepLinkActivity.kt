package com.novikon.pace.ui.circles

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.novikon.pace.R
import com.novikon.pace.data.CirclesRealtimeManager
import com.novikon.pace.data.JoinFailReason
import com.novikon.pace.ui.login.LoginActivity
import kotlinx.coroutines.launch

class DeepLinkActivity : AppCompatActivity() {

    private val circlesManager by lazy { CirclesRealtimeManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDeepLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent) {
        val uri = intent.data ?: run {
            finish()
            return
        }

        val code = uri.getQueryParameter("c")
        if (code.isNullOrBlank() || !code.matches(Regex("^\\d{6}$"))) {
            Toast.makeText(this, getString(R.string.deeplink_invalid_code), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val userId = circlesManager.getUserId()
        if (userId != null) {
            joinCircleAndNavigate(code)
        } else {
            getSharedPreferences("deep_link", MODE_PRIVATE)
                .edit()
                .putString(PENDING_JOIN_CODE, code)
                .apply()

            Toast.makeText(this, getString(R.string.deeplink_login_required), Toast.LENGTH_SHORT).show()

            val loginIntent = Intent(this, LoginActivity::class.java)
            loginIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(loginIntent)
            finish()
        }
    }

    private fun joinCircleAndNavigate(code: String) {
        lifecycleScope.launch {
            try {
                val result = circlesManager.joinCircleByCode(code)

                val msg = when {
                    result.success -> getString(R.string.circle_joined)
                    result.reason == JoinFailReason.INVALID_CODE ->
                        getString(R.string.error_code_6_digits)
                    result.reason == JoinFailReason.CODE_NOT_FOUND ->
                        getString(R.string.circle_code_not_found)
                    result.reason == JoinFailReason.GROUP_NOT_FOUND ->
                        getString(R.string.circle_not_found)
                    result.reason == JoinFailReason.BLOCKED ->
                        getString(R.string.cannot_join_blocked_user)
                    result.reason == JoinFailReason.GROUP_FULL_OR_TRANSACTION_FAILED ->
                        getString(R.string.circle_join_error_group_full_or_permissions)
                    else -> getString(R.string.circle_join_error)
                }

                Toast.makeText(this@DeepLinkActivity, msg, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@DeepLinkActivity, getString(R.string.circle_join_error), Toast.LENGTH_SHORT).show()
            }

            val navigate = Intent(this@DeepLinkActivity, CirclesActivity::class.java)
            navigate.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(navigate)
            finish()
        }
    }

    companion object {
        const val PENDING_JOIN_CODE = "pending_join_code"
    }
}
