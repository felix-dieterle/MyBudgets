package de.mybudgets.app.ui.${FEATURE}

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import de.mybudgets.app.databinding.Fragment${FEATURE_PASCAL}Binding
import de.mybudgets.app.util.AppLogger
import kotlinx.coroutines.launch

private const val TAG = "${FEATURE_PASCAL}Fragment"

/**
 * Display and manage ${FEATURE} items.
 *
 * **State Management:**
 * - ViewModel holds StateFlow<${FEATURE_PASCAL}State>
 * - Fragment observes via repeatOnLifecycle(STARTED)
 * - State sealed class: Idle | Loading | Success(data) | Error(msg)
 *
 * **Lifecycle:**
 * - onCreateView: Inflate binding
 * - onViewCreated: Set up listeners, observe ViewModel
 * - onDestroyView: Clear binding (MUST NOT access binding after this)
 *
 * **Navigation:**
 * - TODO: Add navigation to detail/edit screens if needed
 */
@AndroidEntryPoint
class ${FEATURE_PASCAL}Fragment : Fragment() {
    private var _binding: Fragment${FEATURE_PASCAL}Binding? = null
    private val binding get() = _binding ?: error("Binding is null! (accessed after onDestroyView?)")
    
    private val viewModel: ${FEATURE_PASCAL}ViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = Fragment${FEATURE_PASCAL}Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        AppLogger.i(TAG, "onViewCreated")
        
        setupListeners()
        observeViewModel()
    }

    private fun setupListeners() {
        // TODO: Set click listeners, form handlers, etc.
        // binding.btnAction.setOnClickListener {
        //     viewModel.performAction()
        // }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        when (state) {
                            is ${FEATURE_PASCAL}State.Idle -> showIdle()
                            is ${FEATURE_PASCAL}State.Loading -> showLoading(state.message)
                            is ${FEATURE_PASCAL}State.Success -> showSuccess(state.data)
                            is ${FEATURE_PASCAL}State.Error -> showError(state.message)
                        }
                    }
                }
                // Add more flows here if needed:
                // launch {
                //     viewModel.items.collect { items ->
                //         adapter.submitList(items)
                //     }
                // }
            }
        }
    }

    private fun showIdle() {
        AppLogger.d(TAG, "State: Idle")
        // binding.progressBar.visibility = View.GONE
        // binding.content.visibility = View.VISIBLE
    }

    private fun showLoading(message: String) {
        AppLogger.d(TAG, "State: Loading - $message")
        // binding.progressBar.visibility = View.VISIBLE
        // binding.tvLoading.text = message
        // binding.content.visibility = View.GONE
    }

    private fun showSuccess(data: List<${FEATURE_PASCAL}>) {
        AppLogger.i(TAG, "State: Success - ${data.size} items")
        // binding.progressBar.visibility = View.GONE
        // binding.content.visibility = View.VISIBLE
        // adapter.submitList(data)
    }

    private fun showError(message: String) {
        AppLogger.e(TAG, "State: Error - $message")
        // Show snackbar or error dialog
        // Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    override fun onDestroyView() {
        AppLogger.d(TAG, "onDestroyView - clearing binding")
        super.onDestroyView()
        _binding = null  // CRITICAL: Must clear before super.onDestroyView() completes
    }
}
