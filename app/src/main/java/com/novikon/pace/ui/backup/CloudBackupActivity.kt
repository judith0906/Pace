package com.novikon.pace.ui.backup

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.novikon.pace.R
import com.novikon.pace.data.CloudBackupManager
import com.novikon.pace.databinding.ActivityCloudBackupBinding
import com.novikon.pace.utils.applySystemBarInsets
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CloudBackupActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCloudBackupBinding
    private lateinit var lastBackupText: TextView
    private lateinit var btnCreateBackup: MaterialButton
    private lateinit var btnRestoreBackup: MaterialButton
    private val backupManager = CloudBackupManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCloudBackupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets()

        lastBackupText = binding.lastBackupText
        btnCreateBackup = binding.btnCreateBackup
        btnRestoreBackup = binding.btnRestoreBackup

        binding.backButton.setOnClickListener { finish() }

        btnCreateBackup.setOnClickListener { createBackup() }
        btnRestoreBackup.setOnClickListener { confirmRestore() }

        loadLastBackup()
    }

    private fun loadLastBackup() {
        lifecycleScope.launch {
            val last = backupManager.getLastBackupAt()
            lastBackupText.text = if (last > 0L) {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                getString(R.string.cloud_backup_date, sdf.format(Date(last)))
            } else {
                getString(R.string.cloud_backup_none)
            }
        }
    }

    private fun createBackup() {
        btnCreateBackup.isEnabled = false
        btnCreateBackup.text = getString(R.string.cloud_backup_in_progress)
        lifecycleScope.launch {
            val ok = backupManager.createBackup()
            btnCreateBackup.isEnabled = true
            btnCreateBackup.text = getString(R.string.cloud_backup_create)
            if (ok) {
                Toast.makeText(this@CloudBackupActivity, R.string.cloud_backup_success, Toast.LENGTH_SHORT).show()
                loadLastBackup()
            } else {
                Toast.makeText(this@CloudBackupActivity, R.string.cloud_backup_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmRestore() {
        AlertDialog.Builder(this)
            .setTitle(R.string.cloud_backup_restore)
            .setMessage(R.string.cloud_backup_restore_confirm)
            .setPositiveButton(R.string.cloud_backup_restore) { _, _ -> doRestore() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doRestore() {
        btnRestoreBackup.isEnabled = false
        btnRestoreBackup.text = getString(R.string.cloud_backup_in_progress)
        lifecycleScope.launch {
            val ok = backupManager.restoreBackup()
            btnRestoreBackup.isEnabled = true
            btnRestoreBackup.text = getString(R.string.cloud_backup_restore)
            if (ok) {
                Toast.makeText(this@CloudBackupActivity, R.string.cloud_backup_restore_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@CloudBackupActivity, R.string.cloud_backup_restore_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
}