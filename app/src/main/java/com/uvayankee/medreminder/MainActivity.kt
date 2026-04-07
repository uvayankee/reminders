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
import com.uvayankee.medreminder.alarm.AlarmRepository
import com.uvayankee.medreminder.alarm.PrescriptionRepository
import com.uvayankee.medreminder.databinding.ActivityMainBinding
import com.uvayankee.medreminder.databinding.ItemDoseBinding
import com.uvayankee.medreminder.databinding.ItemPrescriptionBinding
import com.uvayankee.medreminder.db.DoseLog
import com.uvayankee.medreminder.db.DoseStatus
import com.uvayankee.medreminder.db.Prescription
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    private val alarmRepository: AlarmRepository by inject()
    private val prescriptionRepository: PrescriptionRepository by inject()
    private lateinit var binding: ActivityMainBinding
    
    private val prescriptionAdapter = PrescriptionAdapter()
    private val doseAdapter = DoseAdapter()
    
    private var currentJob: Job? = null
    private var selectedDate = Calendar.getInstance()

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
        
        lifecycleScope.launch {
            alarmRepository.scheduleInitialAlarms()
            switchToSchedule()
        }
    }

    private fun setupUI() {
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        
        binding.calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            selectedDate.set(year, month, dayOfMonth)
            switchToSchedule()
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> switchToSchedule()
                    1 -> switchToMedications()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditPrescriptionActivity::class.java))
        }
    }

    private fun switchToSchedule() {
        binding.fabAdd.visibility = View.GONE
        binding.calendarView.visibility = View.VISIBLE
        binding.recyclerView.adapter = doseAdapter
        currentJob?.cancel()
        currentJob = lifecycleScope.launch {
            val cal = Calendar.getInstance().apply {
                timeInMillis = selectedDate.timeInMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            val end = cal.timeInMillis
            
            prescriptionRepository.getDosesForDay(start, end).collectLatest {
                doseAdapter.submitList(it)
            }
        }
    }

    private fun switchToMedications() {
        binding.fabAdd.visibility = View.VISIBLE
        binding.calendarView.visibility = View.GONE
        binding.recyclerView.adapter = prescriptionAdapter
        currentJob?.cancel()
        currentJob = lifecycleScope.launch {
            prescriptionRepository.getAllPrescriptions().collectLatest {
                prescriptionAdapter.submitList(it)
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

    inner class DoseAdapter : RecyclerView.Adapter<DoseAdapter.ViewHolder>() {
        private var items = listOf<List<DoseLog>>()
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun submitList(newItems: List<DoseLog>) {
            items = newItems.groupBy { it.scheduledTime }.values.toList()
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemDoseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val group = items[position]
            val firstDose = group.first()
            
            lifecycleScope.launch {
                val namesAndDosages = group.map { dose ->
                    val p = prescriptionRepository.getPrescriptionByIdImmediate(dose.prescriptionId)
                    "${p?.name ?: "Unknown"} - ${dose.dosage} units"
                }
                holder.binding.tvMedName.text = namesAndDosages.joinToString("\n")
            }
            
            val cal = Calendar.getInstance().apply { timeInMillis = firstDose.scheduledTime }
            holder.binding.tvTime.text = timeFormat.format(cal.time)
            
            val now = System.currentTimeMillis()
            val isNearOrPast = now >= (firstDose.scheduledTime - 30 * 60 * 1000)

            val hasPending = group.any { it.status == DoseStatus.PENDING }
            val hasSnoozed = group.any { it.status == DoseStatus.SNOOZED }
            val allTaken = group.all { it.status == DoseStatus.TAKEN }
            val allSkipped = group.all { it.status == DoseStatus.SKIPPED }
            val isMissed = group.any { now > (it.scheduledTime + 60 * 60 * 1000) && it.status == DoseStatus.PENDING }

            var statusText = ""
            when {
                allTaken -> statusText = "TAKEN"
                allSkipped -> statusText = "SKIPPED"
                isMissed -> statusText = "MISSED"
                hasSnoozed -> statusText = "SNOOZED"
                hasPending -> statusText = "PENDING"
                else -> statusText = "MIXED"
            }
            
            if (allTaken) {
                if (firstDose.actualTime != null) {
                    val diffMin = (firstDose.actualTime - firstDose.scheduledTime) / (60 * 1000)
                    if (diffMin > 5) {
                        statusText += " ($diffMin min late)"
                    } else if (diffMin < -5) {
                        statusText += " (${-diffMin} min early)"
                    }
                }
            }
            holder.binding.tvStatus.text = statusText
            
            when {
                allTaken -> {
                    holder.binding.tvStatus.setTextColor(Color.parseColor("#4CAF50")) // Green
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
                    holder.binding.btnSnooze.visibility = View.VISIBLE
                }
                hasSnoozed -> {
                    holder.binding.tvStatus.setTextColor(Color.MAGENTA)
                    holder.binding.btnTake.visibility = View.VISIBLE
                    holder.binding.btnSnooze.visibility = View.VISIBLE
                }
                isNearOrPast -> {
                    holder.binding.tvStatus.setTextColor(Color.BLUE)
                    holder.binding.btnTake.visibility = View.VISIBLE
                    holder.binding.btnSnooze.visibility = View.VISIBLE
                }
                else -> {
                    holder.binding.tvStatus.setTextColor(Color.GRAY)
                    holder.binding.btnTake.visibility = View.GONE
                    holder.binding.btnSnooze.visibility = View.GONE
                }
            }

            holder.binding.btnTake.text = if (group.size > 1) "Take All" else "Take"
            holder.binding.btnSnooze.text = if (group.size > 1) "Snooze All" else "Snooze"

            val doseIds = group.map { it.id }.toLongArray()

            holder.binding.btnTake.setOnClickListener {
                lifecycleScope.launch { alarmRepository.takeDoses(doseIds) }
            }
            holder.binding.btnSnooze.setOnClickListener {
                val options = arrayOf("5 min", "15 min", "30 min", "60 min")
                val minutes = intArrayOf(5, 15, 30, 60)
                
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Snooze for how long?")
                    .setItems(options) { _, which ->
                        lifecycleScope.launch { 
                            alarmRepository.snoozeDoses(doseIds, minutes[which])
                        }
                    }
                    .show()
            }

            holder.itemView.setOnClickListener {
                if (!isNearOrPast && hasPending) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Take Early?")
                        .setMessage("These doses are scheduled for ${timeFormat.format(cal.time)}. Are you sure you want to take them now?")
                        .setPositiveButton("Take Now") { _, _ ->
                            lifecycleScope.launch { alarmRepository.takeDoses(doseIds) }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        override fun getItemCount() = items.size
        inner class ViewHolder(val binding: ItemDoseBinding) : RecyclerView.ViewHolder(binding.root)
    }

    inner class PrescriptionAdapter : RecyclerView.Adapter<PrescriptionAdapter.ViewHolder>() {
        private var items = listOf<Prescription>()
        private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

        fun submitList(newItems: List<Prescription>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemPrescriptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvName.text = item.name
            lifecycleScope.launch {
                val times = prescriptionRepository.getTimesForPrescription(item.id)
                val timeStrings = times.map { 
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, it.reminderTimeMinutes / 60)
                        set(Calendar.MINUTE, it.reminderTimeMinutes % 60)
                    }
                    timeFormat.format(cal.time)
                }
                holder.binding.tvTimes.text = "Reminders: ${timeStrings.joinToString(", ")}"
            }
            holder.binding.btnDelete.setOnClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle("Delete Prescription?")
                    .setMessage("Are you sure you want to delete ${item.name} and all its scheduled doses?")
                    .setPositiveButton("Delete") { _, _ ->
                        lifecycleScope.launch {
                            prescriptionRepository.deletePrescription(item)
                            Toast.makeText(this@MainActivity, "Prescription deleted", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            holder.itemView.setOnClickListener {
                val intent = Intent(this@MainActivity, AddEditPrescriptionActivity::class.java).apply {
                    putExtra("id", item.id)
                }
                startActivity(intent)
            }
        }

        override fun getItemCount() = items.size
        inner class ViewHolder(val binding: ItemPrescriptionBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
