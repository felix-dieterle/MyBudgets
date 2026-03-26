package de.mybudgets.app.ui.legal

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import de.mybudgets.app.databinding.FragmentLegalBinding

class LegalFragment : Fragment() {

    private var _binding: FragmentLegalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        _binding = FragmentLegalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Legal text is static and set via the layout string resource
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
