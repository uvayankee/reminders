package com.uvayankee.medreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.uvayankee.medreminder.databinding.ActivityMainBinding
import com.uvayankee.medreminder.databinding.ItemDateBinding
import com.uvayankee.medreminder.databinding.ItemDoseBinding
import com.uvayankee.medreminder.databinding.ItemPrescriptionBinding
import com.uvayankee.medreminder.db.DoseLog
import com.uvayankee.medreminder.db.DoseStatus
import com.uvayankee.medreminder.db.Prescription
import com.uvayankee.medreminder.presentation.DoseItem
import com.uvayankee.medreminder.presentation.MainUiState
import com.uvayankee.medreminder.presentation.MainViewModel
import com.uvayankee.medreminder.presentation.PrescriptionItem
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModel()
    private lateinit var binding: ActivityMainBinding
    
    private val prescriptionAdapter = PrescriptionAdapter()
    private val doseAdapter = DoseAdapter()
    private val dateAdapter = DateAdapter()
    
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        checkPermissions()
        observeUiState()
    }

    private fun setupUI() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        
        binding.rvDates.adapter = dateAdapter

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                viewModel.onTabSelected(tab?.position ?: 0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditPrescriptionActivity::class.java))
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is MainUiState.Loading -> {
                        // Optional: Show a loading indicator
                    }
                    is MainUiState.Schedule -> {
                        binding.fabAdd.visibility = View.GONE
                        binding.rvDates.visibility = View.VISIBLE
                        dateAdapter.setSelectedDate(state.selectedDate)
                        binding.recyclerView.adapter = doseAdapter
                        doseAdapter.submitList(state.doses)
                    }
                    is MainUiState.Medications -> {
                        binding.fabAdd.visibility = View.VISIBLE
                        binding.rvDates.visibility = View.GONE
                        binding.recyclerView.adapter = prescriptionAdapter
                        prescriptionAdapter.submitList(state.prescriptions)
                    }
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(android.app.AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
        }
    }

    // --- Adapters ---

    inner class DateAdapter : RecyclerView.Adapter<DateAdapter.ViewHolder>() {
        private var selectedDate: Calendar = Calendar.getInstance()
        private val items: List<Calendar> = (0..6).map { i ->
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, i) }
        }

        fun setSelectedDate(newDate: Calendar) {
            selectedDate = newDate
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSelected = item.get(Calendar.DAY_OF_YEAR) == selectedDate.get(Calendar.DAY_OF_YEAR) &&
                             item.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR)

            val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
            holder.binding.tvDayName.text = dayFormat.format(item.time)
            holder.binding.tvDayNumber.text = item.get(Calendar.DAY_OF_MONTH).toString()

            if (isSelected) {
                holder.binding.llContainer.setBackgroundColor(Color.parseColor("#E3F2FD"))
                holder.binding.tvDayNumber.setTextColor(Color.parseColor("#1976D2"))
            } else {
                holder.binding.llContainer.setBackgroundColor(Color.TRANSPARENT)
                holder.binding.tvDayNumber.setTextColor(Color.BLACK)
            }

            holder.itemView.setOnClickListener {
                viewModel.onDateChanged(item.get(Calendar.YEAR), item.get(Calendar.MONTH), item.get(Calendar.DAY_OF_MONTH))
            }
        }

        override fun getItemCount() = items.size
        inner class ViewHolder(val binding: ItemDateBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class DoseAdapter : RecyclerView.Adapter<DoseAdapter.ViewHolder>() {
        private var items = listOf<List<DoseItem>>()
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun submitList(newItems: List<DoseItem>) {
            items = newItems.groupBy { it.dose.scheduledTime }.values.toList()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDoseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val group = items[position]
            val firstItem = group.first()
            val firstDose = firstItem.dose
            
            holder.binding.tvMedName.text = group.joinToString("\n") { it.medName }
            
            val cal = Calendar.getInstance().apply { timeInMillis = firstDose.scheduledTime }
            holder.binding.tvTime.text = timeFormat.format(cal.time)
            
            val now = System.currentTimeMillis()
            val isNearOrPast = now >= (firstDose.scheduledTime - 30 * 60 * 1000)
            val isAtOrAfter = now >= firstDose.scheduledTime

            val hasPending = group.any { it.dose.status == DoseStatus.PENDING }
            val hasSnoozed = group.any { it.dose.status == DoseStatus.SNOOZED }
            val allTaken = group.all { it.dose.status == DoseStatus.TAKEN }
            val allSkipped = group.all { it.dose.status == DoseStatus.SKIPPED }
            val isMissed = group.any { now > (it.dose.scheduledTime + 60 * 60 * 1000) && it.dose.status == DoseStatus.PENDING }

            var statusText = when {
                allTaken -> "TAKEN"
                allSkipped -> "SKIPPED"
                isMissed -> "MISSED"
                hasSnoozed -> "SNOOZED"
                hasPending -> "PENDING"
                else -> "MIXED"
            }
            
            if (allTaken && firstDose.actualTime != null) {
                val diffMin = (firstDose.actualTime - firstDose.scheduledTime) / (60 * 1000)
                if (diffMin < -5) statusText += " (${-diffMin} min early)"
            }
            holder.binding.tvStatus.text = statusText
            
            when {
                allTaken -> {
                    holder.binding.tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                    holder.binding.btnTake.visibility = View.GONE
                    holder.binding.btnSnooze.visibility = View.GONE
                }
                allSkipped -> {
                    holder.binding.tvStatus.setTextColor(Color.GRAY)
                    holder.binding.btnTake.visibility = View.GONE
                    holder.binding.btnSnooze.visibility = View.GONE
                }
                isMissed -> {
                    holder.binding.tvStatus.setTextColor(Color.RED)
                    holder.binding.btnTake.visibility = View.VISIBLE
                    holder.binding.btnSnooze.visibility = if (isAtOrAfter) View.VISIBLE else View.GONE
                }
                hasSnoozed -> {
                    holder.binding.tvStatus.setTextColor(Color.MAGENTA)
                    holder.binding.btnTake.visibility = View.VISIBLE
                    holder.binding.btnSnooze.visibility = if (isAtOrAfter) View.VISIBLE else View.GONE
                }
                hasPending -> {
                    holder.binding.tvStatus.setTextColor(if (isNearOrPast) Color.BLUE else Color.GRAY)
                    holder.binding.btnTake.visibility = View.VISIBLE
                    holder.binding.btnSnooze.visibility = if (isAtOrAfter) View.VISIBLE else View.GONE
                }
                else -> {
                    holder.binding.tvStatus.setTextColor(Color.GRAY)
                    holder.binding.btnTake.visibility = View.GONE
                    holder.binding.btnSnooze.visibility = View.GONE
                }
            }

            holder.binding.btnTake.text = if (group.size > 1) "Take All" else "Take"
            holder.binding.btnSnooze.text = if (group.size > 1) "Snooze All" else "Snooze"

            val doseIds = group.map { it.dose.id }.toLongArray()

            holder.binding.btnTake.setOnClickListener {
                if (isNearOrPast || allTaken || allSkipped) {
                    viewModel.takeDoses(doseIds)
                } else {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Take Early?")
                        .setMessage("These doses are scheduled for ${timeFormat.format(cal.time)}. Are you sure you want to take them now?")
                        .setPositiveButton("Take Now") { _, _ ->
                            viewModel.takeDoses(doseIds)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            holder.binding.btnSnooze.setOnClickListener {
                val options = arrayOf("5 min", "15 min", "30 min", "60 min")
                val minutes = intArrayOf(5, 15, 30, 60)
                
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Snooze for how long?")
                    .setItems(options) { _, which ->
                        viewModel.snoozeDoses(doseIds, minutes[which])
                    }
                    .show()
            }

            holder.itemView.setOnClickListener {
                // Row click intentionally left empty now that 'Take' button handles early logic
            }
        }

        override fun getItemCount() = items.size
        inner class ViewHolder(val binding: ItemDoseBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class PrescriptionAdapter : RecyclerView.Adapter<PrescriptionAdapter.ViewHolder>() {
        private var items = listOf<PrescriptionItem>()
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun submitList(newItems: List<PrescriptionItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemPrescriptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val prescription = item.prescription
            holder.binding.tvName.text = prescription.name
            
            val timesText = item.reminderTimes.sorted().joinToString(", ") { minutes ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, minutes / 60)
                    set(Calendar.MINUTE, minutes % 60)
                }
                timeFormat.format(cal.time)
            }
            holder.binding.tvTimes.text = "Reminders: $timesText"
            
            holder.binding.btnDelete.setOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Delete Prescription?")
                    .setMessage("Are you sure you want to delete ${prescription.name} and all its scheduled doses?")
                    .setPositiveButton("Delete") { _, _ ->
                        viewModel.deletePrescription(prescription)
                        Toast.makeText(this@MainActivity, "Prescription deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, AddEditPrescriptionActivity::class.java).apply {
                    putExtra("id", prescription.id)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
        inner class ViewHolder(val binding: ItemPrescriptionBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
