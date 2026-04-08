package com.novikon.pace.ui.circles

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase
import com.novikon.pace.R
import com.novikon.pace.data.CirclesRealtimeManager
import kotlinx.coroutines.launch

// Fragment que muestra la lista de círculos del usuario actual.
//
// Ciclo de vida:
//   onViewCreated → registra listener en tiempo real de Firebase
//   onDestroyView → elimina el listener para evitar memory leaks
//
// Cuando el usuario pulsa "+" se muestra un diálogo para introducir
// el nombre del nuevo círculo. Al confirmar, se crea en Firebase
// y el listener en tiempo real actualiza la lista automáticamente.
//
// Cuando pulsa en un círculo, navega a CircleChatActivity
// pasando el circleId y el circleName como extras del Intent.
class CirclesFragment : Fragment() {

    private lateinit var rvCircles: RecyclerView
    private lateinit var btnAddCircle: FloatingActionButton
    private lateinit var ivUserAvatar: ShapeableImageView

    private val circlesManager = CirclesRealtimeManager()
    private lateinit var circlesAdapter: CirclesAdapter

    // Referencia al listener activo — necesaria para eliminarlo en onDestroyView
    private var circlesListener: ValueEventListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_circles, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        setupRecyclerView()
        setupListeners()
        loadUserAvatar()
        observeCircles()
    }

    private fun initializeViews(view: View) {
        rvCircles = view.findViewById(R.id.rv_circles)
        btnAddCircle = view.findViewById(R.id.btn_add_circle)
        ivUserAvatar = view.findViewById(R.id.iv_user_avatar)
    }

    private fun setupRecyclerView() {
        circlesAdapter = CirclesAdapter { circle ->
            // Navegar al chat del círculo pulsado
            val intent = Intent(requireContext(), CircleChatActivity::class.java).apply {
                putExtra(CircleChatActivity.EXTRA_CIRCLE_ID, circle.id)
                putExtra(CircleChatActivity.EXTRA_CIRCLE_NAME, circle.name)
            }
            startActivity(intent)
        }

        rvCircles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = circlesAdapter
        }
    }

    private fun setupListeners() {
        btnAddCircle.setOnClickListener {
            showCreateCircleDialog()
        }
    }

    // Carga el avatar del usuario actual desde Firebase Auth.
    // Si tiene foto de Google la muestra, si no usa el placeholder.
    private fun loadUserAvatar() {
        val photoUrl = Firebase.auth.currentUser?.photoUrl
        if (photoUrl != null) {
            // Glide o Coil para cargar la imagen — usando Coil por ser más idiomático en Kotlin
            // Asegúrate de tener en tu build.gradle:
            //   implementation("io.coil-kt:coil:2.6.0")
            try {
                val request = coil.request.ImageRequest.Builder(requireContext())
                    .data(photoUrl)
                    .target(ivUserAvatar)
                    .crossfade(true)
                    .build()
                coil.ImageLoader(requireContext()).enqueue(request)
            } catch (e: Exception) {
                // Si Coil no está disponible, el placeholder por defecto ya está en el XML
                e.printStackTrace()
            }
        }
    }

    // Registra el listener en tiempo real de Firebase para la lista de círculos.
    // El callback se llama cada vez que cambia algo en users/{userId}/circles/ —
    // por ejemplo cuando se añade un círculo nuevo o llega un mensaje.
    private fun observeCircles() {
        circlesListener = circlesManager.observeUserCircles { circles ->
            // Actualizar el adapter en el hilo principal
            // (el callback ya viene en el main thread gracias a Firebase)
            circlesAdapter.submitList(circles)
        }
    }

    // Muestra un diálogo con un campo de texto para que el usuario
    // introduzca el nombre del nuevo círculo.
    private fun showCreateCircleDialog() {
        val context = requireContext()
        val input = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = "Nombre del círculo"
            setPadding(48, 32, 48, 16)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Nuevo círculo")
            .setMessage("¿Cómo quieres llamar a este círculo?")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val name = input.text?.toString()?.trim()
                if (!name.isNullOrBlank()) {
                    createCircle(name)
                } else {
                    Toast.makeText(context, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()

        // Dar foco automático al campo de texto al abrir el diálogo
        input.requestFocus()
    }

    // Crea el círculo en Firebase y muestra un Toast con el resultado.
    // El listener en tiempo real (observeCircles) ya actualizará la lista
    // automáticamente — no hace falta recargar manualmente.
    private fun createCircle(name: String) {
        lifecycleScope.launch {
            val circleId = circlesManager.createCircle(name)
            if (circleId != null) {
                Toast.makeText(
                    requireContext(),
                    "Círculo \"$name\" creado",
                    Toast.LENGTH_SHORT
                ).show()
                // El listener en tiempo real actualiza la lista automáticamente
            } else {
                Toast.makeText(
                    requireContext(),
                    "Error al crear el círculo. Inténtalo de nuevo.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // Limpieza obligatoria: eliminar el listener de Firebase al destruir la vista
    // para evitar callbacks sobre fragmentos ya destruidos (memory leak y crash).
    override fun onDestroyView() {
        super.onDestroyView()
        circlesListener?.let { circlesManager.removeUserCirclesListener(it) }
    }
}