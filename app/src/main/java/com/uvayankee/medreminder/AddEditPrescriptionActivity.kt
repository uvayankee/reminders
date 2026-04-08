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
import com.uvayankee.medreminder.databinding.ActivityAddEditPrescriptionBinding
import com.uvayankee.medreminder.databinding.ItemReminderTimeBinding
import com.uvayankee.medreminder.presentation.AddEditViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import java.text.SimpleDateFormat
import java.util.*

class AddEditPrescriptionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditPrescriptionBinding
    private val viewModel: AddEditViewModel by viewModel()
    private val timeAdapter = TimeAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditPrescriptionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prescriptionId = intent.getLongExtra("id", 0)
        
        setupUI()
        observeUiState()
        
        if (savedInstanceState == null) {
            viewModel.loadPrescription(prescriptionId)
        }
    }

    private fun setupUI() {
        binding.rvTimes.layoutManager = LinearLayoutManager(this)
        binding.rvTimes.adapter = timeAdapter

        binding.etName.doAfterTextChanged {
            viewModel.onNameChanged(it.toString())
        }

        binding.tvStartDate.setOnClickListener { showDatePicker() }
        binding.btnAddTime.setOnClickListener { showTimePicker() }
        binding.btnSave.setOnClickListener { viewModel.savePrescription() }
        binding.btnDelete.setOnClickListener { showDeleteConfirmation() }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                if (binding.etName.text.toString() != state.name) {
                    binding.etName.setText(state.name)
                }
                
                updateDateDisplay(state.startDate)
                timeAdapter.submitList(state.reminderTimes)
                
                binding.btnDelete.visibility = if (intent.getLongExtra("id", 0) != 0L) android.view.View.VISIBLE else android.view.View.GONE
                
                if (state.isSaved) {
                    Toast.makeText(this@AddEditPrescriptionActivity, "Prescription saved", Toast.LENGTH_SHORT).show()
                    finish()
                }
                
                if (state.isDeleted) {
                    Toast.makeText(this@AddEditPrescriptionActivity, "Prescription deleted", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun showDeleteConfirmation() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Delete Prescription?")
            .setMessage("Are you sure you want to delete this medication and all its scheduled doses?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deletePrescription()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = viewModel.uiState.value.startDate }
        DatePickerDialog(this, { _, year, month, day ->
            viewModel.onDateChanged(year, month, day)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun updateDateDisplay(timeInMillis: Long) {
        val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        binding.tvStartDate.text = "Start Date: ${format.format(Date(timeInMillis))}"
    }

    private fun showTimePicker() {
        val now = Calendar.getInstance()
        TimePickerDialog(this, { _, hour, minute ->
            viewModel.addTime(hour, minute)
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show()
    }

    inner class TimeAdapter : RecyclerView.Adapter<TimeAdapter.ViewHolder>() {
        private var items = listOf<Pair<Int, Float>>()

        fun submitList(newItems: List<Pair<Int, Float>>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val b = ItemReminderTimeBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (totalMinutes, dosage) = items[position]
            val hour = totalMinutes / 60
            val minute = totalMinutes % 60
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
            }
            val format = SimpleDateFormat("hh:mm a", Locale.getDefault())
            holder.binding.tvTime.text = format.format(cal.time)
            
            if (holder.binding.etDosage.text.toString() != dosage.toString()) {
                holder.binding.etDosage.setText(dosage.toString())
            }
            
            holder.binding.etDosage.doAfterTextChanged {
                val newDosage = it.toString().toFloatOrNull() ?: 0f
                viewModel.updateDosage(position, newDosage)
            }

            holder.binding.btnRemove.setOnClickListener {
                viewModel.removeTime(position)
            }
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val binding: ItemReminderTimeBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
