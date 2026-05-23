package de.mybudgets.app.ui.log

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import de.mybudgets.app.R
import de.mybudgets.app.databinding.FragmentLogConsoleBinding
import de.mybudgets.app.util.AppLogger
import de.mybudgets.app.util.LogEntry
import de.mybudgets.app.util.LogLevel
import kotlinx.coroutines.launch
import java.io.File

class LogConsoleFragment : Fragment() {

    private var _binding: FragmentLogConsoleBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LogEntryAdapter
    private var currentMinLevel: LogLevel? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogConsoleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = LogEntryAdapter()
        binding.rvLogEntries.adapter = adapter

        // Filter chips: each shows entries at that level and above
        binding.chipAll.setOnCheckedChangeListener   { _, c -> if (c) applyFilter(null) }
        binding.chipInfo.setOnCheckedChangeListener  { _, c -> if (c) applyFilter(LogLevel.INFO) }
        binding.chipWarn.setOnCheckedChangeListener  { _, c -> if (c) applyFilter(LogLevel.WARN) }
        binding.chipError.setOnCheckedChangeListener { _, c -> if (c) applyFilter(LogLevel.ERROR) }

        binding.btnClearLog.setOnClickListener { AppLogger.clear() }
        binding.btnExportLog.setOnClickListener { exportLog() }

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AppLogger.entries.collect { entries -> updateList(entries) }
            }
        }
    }

    private fun applyFilter(minLevel: LogLevel?) {
        currentMinLevel = minLevel
        updateList(AppLogger.entries.value)
    }

    private fun updateList(entries: List<LogEntry>) {
        val filtered = if (currentMinLevel == null) {
            entries
        } else {
            entries.filter { it.level.ordinal >= currentMinLevel!!.ordinal }
        }
        adapter.submitList(filtered)
        // Show/hide empty state
        binding.tvLogEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun exportLog() {
        try {
            val text = AppLogger.export()
            
            // Write to cache file (avoids 1MB EXTRA_TEXT limit)
            val cacheDir = requireContext().cacheDir
            val logFile = File(cacheDir, "mybudgets-log.txt")
            logFile.writeText(text)
            
            // Share via FileProvider
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                logFile
            )
            
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "MyBudgets Fehlerprotokoll")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.log_export_chooser_title)))
        } catch (e: Exception) {
            Snackbar.make(requireView(), "Export fehlgeschlagen: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
