package com.novikon.pace.ui.main.menus

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.novikon.pace.R
import com.novikon.pace.data.HabitsManager
import com.novikon.pace.data.ProfilePhotoManager
import com.novikon.pace.ui.login.LoginActivity
import com.novikon.pace.ui.settings.AccountSettingsActivity
import com.novikon.pace.utils.SessionManager
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Gestiona la barra lateral derecha (menú de usuario).
 * Se encarga de mostrar los datos del perfil y de manejar acciones
 * como cambiar foto, cambiar contraseña, abrir ajustes y cerrar sesión.
 */
class UserMenuManager(
    private val activity: AppCompatActivity,
    private val navigationView: NavigationView,
    private val drawerLayout: DrawerLayout,
    private val habitsManager: HabitsManager,
    private val sessionManager: SessionManager
) {
    private val profilePhotoManager = ProfilePhotoManager(activity)
    private val ioExecutor = Executors.newSingleThreadExecutor()

    // Permite elegir una imagen del sistema para usarla como foto de perfil.
    private val pickImageLauncher = activity.registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            startCrop(uri)
        }
    }

    // Recibe el resultado del recorte y dispara la subida del avatar.
    private val cropLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != AppCompatActivity.RESULT_OK) return@registerForActivityResult
        val resultData = result.data ?: return@registerForActivityResult
        val outputUri = UCrop.getOutput(resultData) ?: return@registerForActivityResult

        activity.lifecycleScope.launch {
            uploadCroppedPhoto(outputUri)
        }
    }

    fun setup() {
        styleMenuItems()
        setupUserPhoto()

        navigationView.setNavigationItemSelectedListener { menuItem ->
            handleMenuClick(menuItem)
            drawerLayout.closeDrawer(GravityCompat.END)
            true
        }
    }

    // Muestra el email del usuario en el header del drawer.
    fun setupUserEmail() {
        val headerView = navigationView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.userEmailText).text =
            Firebase.auth.currentUser?.email ?: ""
    }

    // Actualiza el nombre visible del usuario en el header.
    fun updateUserName(name: String) {
        val headerView = navigationView.getHeaderView(0)
        headerView.findViewById<TextView>(R.id.userNameText).text = name
    }

    // Carga la foto de perfil desde Firebase y la pinta en el header.
    private fun setupUserPhoto() {
        val uid = Firebase.auth.currentUser?.uid ?: return
        FirebaseDatabase.getInstance()
            .getReference("users/$uid/profile/photoUrl")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val url = snapshot.getValue(String::class.java)
                    if (!url.isNullOrBlank()) {
                        setProfileImageFromUrl(url)
                    }
                }

                override fun onCancelled(error: DatabaseError) = Unit
            })
    }

    // Resalta visualmente el item de cerrar sesión.
    private fun styleMenuItems() {
        navigationView.menu.findItem(R.id.menu_logout)?.let { item ->
            val spannable = SpannableString(item.title)
            spannable.setSpan(StyleSpan(Typeface.BOLD), 0, spannable.length, 0)
            spannable.setSpan(
                ForegroundColorSpan(activity.getColor(R.color.error_red)),
                0,
                spannable.length,
                0
            )
            item.title = spannable
        }
    }

    // Enruta cada pulsación del menú de usuario a su funcionalidad.
    private fun handleMenuClick(menuItem: MenuItem) {
        when (menuItem.itemId) {
            R.id.menu_change_photo -> changeProfilePhoto()
            R.id.menu_change_password -> changePassword()
            R.id.menu_account_settings ->
                activity.startActivity(Intent(activity, AccountSettingsActivity::class.java))
            R.id.menu_logout -> confirmLogout()
        }
    }

    // Abre el selector de imágenes del sistema para iniciar el cambio de avatar.
    private fun changeProfilePhoto() {
        pickImageLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    // Abre la pantalla de recorte con máscara circular para que el usuario
    // ajuste zoom/posición y vea cómo quedará el avatar redondo.
    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(
            File(activity.cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
        )

        val options = UCrop.Options().apply {
            setCircleDimmedLayer(true)
            setShowCropFrame(false)
            setShowCropGrid(false)
            setCompressionQuality(88)
            setHideBottomControls(false)
            setFreeStyleCropEnabled(false)
        }

        val cropIntent = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(1080, 1080)
            .withOptions(options)
            .getIntent(activity)

        cropLauncher.launch(cropIntent)
    }

    // Convierte la imagen recortada a Bitmap, la sube a Supabase,
    // guarda la URL en Firebase y actualiza el header del menú.
    private suspend fun uploadCroppedPhoto(croppedUri: Uri) {
        val bitmap = withContext(Dispatchers.IO) { decodeBitmap(croppedUri) }
        if (bitmap == null) {
            Toast.makeText(activity, activity.getString(R.string.error), Toast.LENGTH_SHORT).show()
            return
        }

        val photoUrl = profilePhotoManager.uploadProfilePhoto(bitmap)
        if (photoUrl.isNullOrBlank()) {
            Toast.makeText(activity, activity.getString(R.string.error), Toast.LENGTH_SHORT).show()
            return
        }

        val saved = profilePhotoManager.saveProfilePhotoUrl(photoUrl)
        if (!saved) {
            Toast.makeText(activity, activity.getString(R.string.error), Toast.LENGTH_SHORT).show()
            return
        }

        setProfileImageFromUrl(photoUrl)
        Toast.makeText(activity, activity.getString(R.string.change_profile_photo_title), Toast.LENGTH_SHORT).show()
    }

    // Decodifica una URI de imagen del sistema a Bitmap.
    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = android.graphics.ImageDecoder.createSource(activity.contentResolver, uri)
                android.graphics.ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(activity.contentResolver, uri)
            }
        } catch (_: Exception) {
            null
        }
    }

    // Convierte un bitmap cuadrado en un avatar circular listo para mostrarse en el header.
    // Se usa porque el recorte de uCrop solo muestra una máscara circular, pero devuelve
    // un archivo final 1:1 (cuadrado). Con esto garantizamos que en UI se vea redondo.
    private fun Bitmap.toCircularAvatar(): Bitmap {
        val size = min(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2

        val squared = Bitmap.createBitmap(this, x, y, size, size)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)

        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        val r = size / 2f
        canvas.drawCircle(r, r, r, paint)
        return output
    }

    // Descarga la imagen de perfil desde su URL pública y la pinta en el header.
    // Antes de asignarla al ImageView, la recorta a circular para que se vea igual
    // que en el editor de ajuste (avatar redondo).
    private fun setProfileImageFromUrl(url: String) {
        val headerView = navigationView.getHeaderView(0)
        val profileImage = headerView.findViewById<ImageView>(R.id.profileImage)

        ioExecutor.execute {
            runCatching {
                URL(url).openStream().use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            }.onSuccess { bitmap ->
                if (bitmap != null) {
                    val circular = bitmap.toCircularAvatar()
                    activity.runOnUiThread {
                        profileImage.setImageBitmap(circular)
                    }
                }
            }
        }
    }

    // Muestra un diálogo para cambiar la contraseña con reautenticación.
    private fun changePassword() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_change_password, null)
        val currentPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.currentPasswordInput)
        val newPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.newPasswordInput)
        val confirmPasswordInput = dialogView.findViewById<TextInputEditText>(R.id.confirmPasswordInput)

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.change_password))
            .setView(dialogView)
            .setPositiveButton(activity.getString(R.string.save)) { _, _ ->
                val currentPassword = currentPasswordInput.text.toString()
                val newPassword = newPasswordInput.text.toString()
                val confirmPassword = confirmPasswordInput.text.toString()

                if (newPassword != confirmPassword) {
                    Toast.makeText(activity, activity.getString(R.string.password_mismatch), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val user = Firebase.auth.currentUser
                val email = user?.email

                if (email != null) {
                    val credential = EmailAuthProvider.getCredential(email, currentPassword)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPassword)
                                .addOnSuccessListener {
                                    Toast.makeText(
                                        activity,
                                        activity.getString(R.string.password_changed_success),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        activity,
                                        "${activity.getString(R.string.error)}${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(
                                activity,
                                activity.getString(R.string.current_password_incorrect),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Toast.makeText(
                        activity,
                        activity.getString(R.string.google_account_no_password),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            val dialog = AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.change_password))
                .setView(dialogView)
                .setPositiveButton(activity.getString(R.string.save)) { _, _ ->
                    val currentPassword = currentPasswordInput.text.toString()
                    val newPassword = newPasswordInput.text.toString()
                    val confirmPassword = confirmPasswordInput.text.toString()

                    if (newPassword != confirmPassword) {
                        Toast.makeText(activity, activity.getString(R.string.password_mismatch), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }

                    val user = Firebase.auth.currentUser
                    val email = user?.email

                    if (email != null) {
                        val credential = EmailAuthProvider.getCredential(email, currentPassword)
                        user.reauthenticate(credential)
                            .addOnSuccessListener {
                                user.updatePassword(newPassword)
                                    .addOnSuccessListener {
                                        Toast.makeText(
                                            activity,
                                            activity.getString(R.string.password_changed_success),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    .addOnFailureListener { e ->
                                        Toast.makeText(
                                            activity,
                                            "${activity.getString(R.string.error)}${e.message}",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                            }
                            .addOnFailureListener {
                                Toast.makeText(
                                    activity,
                                    activity.getString(R.string.current_password_incorrect),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } else {
                        Toast.makeText(
                            activity,
                            activity.getString(R.string.google_account_no_password),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton(activity.getString(R.string.cancel), null)
                .show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                androidx.core.content.ContextCompat.getColor(activity, R.color.text_secondary)
            )
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                androidx.core.content.ContextCompat.getColor(activity, R.color.text_secondary)
            )
    }

    // Pide confirmación antes de cerrar la sesión.
    private fun confirmLogout() {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.logout_title))
            .setMessage(activity.getString(R.string.logout_message))
            .setPositiveButton(activity.getString(R.string.yes)) { _, _ -> signOut() }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    // Cierra sesión local, Firebase y Google, y vuelve a Login limpio.
    private fun signOut() {
        habitsManager.clearLocalData()
        sessionManager.markUserLoggedOut()
        Firebase.auth.signOut()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(activity.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(activity, gso).signOut()

        val intent = Intent(activity, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        activity.startActivity(intent)
        activity.finish()
    }
}