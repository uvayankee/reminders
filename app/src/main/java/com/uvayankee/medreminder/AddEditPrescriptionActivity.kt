package com.uvayankee.medreminder

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.uvayankee.medreminder.alarm.PrescriptionRepository
import com.uvayankee.medreminder.databinding.ActivityAddEditPrescriptionBinding
import com.uvayankee.medreminder.databinding.ItemReminderTimeBinding
import com.uvayankee.medreminder.db.Prescription
import com.uvayankee.medreminder.db.TimeSchedule
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.*

class AddEditPrescriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditPrescriptionBinding
    private val repository: PrescriptionRepository by inject()
    private var selectedDate = Calendar.getInstance()
    private var reminderTimes = mutableListOf<Pair<Int, Float>>() // Time in minutes to Dosage
    private var prescriptionId: Long = 0

    private val timeAdapter = TimeAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditPrescriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prescriptionId = intent.getLongExtra("id", 0)
        
        setupUI()
        if (prescriptionId != 0L) {
            loadPrescription()
        }
    }

    private fun setupUI() {
        binding.rvTimes.layoutManager = LinearLayoutManager(this)
        binding.rvTimes.adapter = timeAdapter

        binding.tvStartDate.setOnClickListener { showDatePicker() }
        binding.btnAddTime.setOnClickListener { showTimePicker() }
        binding.btnSave.setOnClickListener { savePrescription() }
        
        if (prescriptionId != 0L) {
            binding.btnDelete.visibility = android.view.View.VISIBLE
            binding.btnDelete.setOnClickListener { showDeleteConfirmation() }
        }

        updateDateDisplay()
    }

    private fun showDeleteConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Delete Prescription?")
            .setMessage("Are you sure you want to delete this medication and all its scheduled doses?")
            .setPositiveButton("Delete") { _, _ ->
                deletePrescription()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePrescription() {
        lifecycleScope.launch {
            val p = repository.getPrescriptionById(prescriptionId)
            if (p != null) {
                repository.deletePrescription(p)
                Toast.makeText(this@AddEditPrescriptionActivity, "Prescription deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun loadPrescription() {
        lifecycleScope.launch {
            val p = repository.getPrescriptionById(prescriptionId) ?: return@launch
            binding.etName.setText(p.name)
            selectedDate.timeInMillis = p.startDate
            updateDateDisplay()
            
            val times = repository.getTimesForPrescription(prescriptionId)
            reminderTimes.clear()
            reminderTimes.addAll(times.map { it.reminderTimeMinutes to it.dosage })
            timeAdapter.notifyDataSetChanged()
        }
    }

    private fun showDatePicker() {
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate.set(year, month, day)
            updateDateDisplay()
        }, selectedDate.get(Calendar.YEAR), selectedDate.get(Calendar.MONTH), selectedDate.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateDisplay() {
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.tvStartDate.text = "Start Date: ${format.format(selectedDate.time)}"
    }

    private fun showTimePicker() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            val totalMinutes = hour * 60 + minute
            if (reminderTimes.none { it.first == totalMinutes }) {
                reminderTimes.add(totalMinutes to 1.0f)
                reminderTimes.sortBy { it.first }
                timeAdapter.notifyDataSetChanged()
            }
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }

    private fun savePrescription() {
        val name = binding.etName.text.toString()
        if (name.isBlank()) {
            Toast.makeText(this, "Please enter a medication name", Toast.LENGTH_SHORT).show()
            return
        }
        if (reminderTimes.isEmpty()) {
            Toast.makeText(this, "Please add at least one reminder time", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val p = Prescription(
                id = prescriptionId,
                name = name,
                startDate = selectedDate.timeInMillis,
                endDate = selectedDate.timeInMillis + 365L * 24 * 60 * 60 * 1000 // 1 year default
            )
            val times = reminderTimes.map { 
                TimeSchedule(prescriptionId = prescriptionId, reminderTimeMinutes = it.first, dosage = it.second)
            }
            repository.savePrescription(p, times)
            finish()
        }
    }

    inner class TimeAdapter : RecyclerView.Adapter<TimeAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemReminderTimeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (totalMinutes, dosage) = reminderTimes[position]
            val hour = totalMinutes / 60
            val minute = totalMinutes % 60
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
            holder.binding.tvTime.text = format.format(cal.time)
            
            // Set text without triggering watcher immediately if possible, or handle carefully
            holder.binding.etDosage.setText(dosage.toString())
            
            holder.binding.etDosage.doAfterTextChanged {
                val newDosage = it.toString().toFloatOrNull() ?: 0f
                if (position < reminderTimes.size) {
                    reminderTimes[position] = reminderTimes[position].first to newDosage
                }
            }

            holder.binding.btnRemove.setOnClickListener {
                reminderTimes.removeAt(position)
                notifyDataSetChanged()
            }
        }

        override fun getItemCount() = reminderTimes.size

        inner class ViewHolder(val binding: ItemReminderTimeBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
