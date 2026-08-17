package com.example.parentingmonitoringapp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class ExamScheduleActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore

    private lateinit var spinnerCourse: Spinner
    private lateinit var spinnerSection: Spinner
    private lateinit var examEntriesContainer: LinearLayout
    private lateinit var btnAddSubject: Button
    private lateinit var btnPreview: Button
    private lateinit var btnConfirmSend: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvPreviewResult: TextView
    private lateinit var tvStatus: TextView

    data class ExamEntryViews(
        val subject: EditText,
        val date: EditText,
        val time: EditText,
        val room: EditText,
        val notes: EditText,
        val rowView: View
    )

    private val examEntryList = mutableListOf<ExamEntryViews>()
    private var matchedParentEmails: List<String> = emptyList()

    private val courseSectionsMap = mutableMapOf<String, List<String>>()
    private val courseNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_schedule)

        db = FirebaseFirestore.getInstance()

        spinnerCourse = findViewById(R.id.spinnerCourse)
        spinnerSection = findViewById(R.id.spinnerSection)
        examEntriesContainer = findViewById(R.id.examEntriesContainer)
        btnAddSubject = findViewById(R.id.btnAddSubject)
        btnPreview = findViewById(R.id.btnPreview)
        btnConfirmSend = findViewById(R.id.btnConfirmSend)
        progressBar = findViewById(R.id.progressBar)
        tvPreviewResult = findViewById(R.id.tvPreviewResult)
        tvStatus = findViewById(R.id.tvStatus)

        btnAddSubject.setOnClickListener { addExamEntryRow() }
        btnPreview.setOnClickListener { previewRecipients() }
        btnConfirmSend.setOnClickListener { confirmAndSend() }

        loadCourses()
        addExamEntryRow()
    }

    private fun loadCourses() {
        db.collection("courses").get()
            .addOnSuccessListener { docs ->
                courseNames.clear()
                courseSectionsMap.clear()

                for (doc in docs) {
                    val courseName = doc.getString("courseName") ?: doc.id

                    val sectionsRaw = doc.get("sections")
                    val sections: List<String> = when (sectionsRaw) {
                        is List<*> -> sectionsRaw.mapNotNull { it?.toString()?.trim() }
                        is String -> sectionsRaw.trim('[', ']').split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        else -> emptyList()
                    }

                    courseNames.add(courseName)
                    courseSectionsMap[courseName] = sections
                }

                val courseAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, courseNames)
                spinnerCourse.adapter = courseAdapter

                updateSectionSpinner(courseNames.firstOrNull())

                spinnerCourse.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                        updateSectionSpinner(courseNames.getOrNull(position))
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                }
            }
            .addOnFailureListener {
                tvStatus.text = "Failed to load courses. Check your internet connection."
            }
    }

    private fun updateSectionSpinner(courseName: String?) {
        val sections = courseSectionsMap[courseName] ?: emptyList()
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sections)
        spinnerSection.adapter = sectionAdapter
    }

    private fun addExamEntryRow() {
        val rowContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#EEEEEE"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        val etSubject = EditText(this).apply { hint = "Subject (e.g. Mathematics)" }
        val etDate = EditText(this).apply { hint = "Exam Date (e.g. August 15, 2026)" }
        val etTime = EditText(this).apply { hint = "Exam Time (e.g. 8:00-10:00 AM)" }
        val etRoom = EditText(this).apply { hint = "Room/Venue" }
        val etNotes = EditText(this).apply { hint = "Notes (optional)" }

        val btnRemove = Button(this).apply {
            text = "Remove this subject"
            setBackgroundColor(Color.parseColor("#D32F2F"))
            setTextColor(Color.WHITE)
        }

        rowContainer.addView(etSubject)
        rowContainer.addView(etDate)
        rowContainer.addView(etTime)
        rowContainer.addView(etRoom)
        rowContainer.addView(etNotes)
        rowContainer.addView(btnRemove)

        val entry = ExamEntryViews(etSubject, etDate, etTime, etRoom, etNotes, rowContainer)
        examEntryList.add(entry)

        btnRemove.setOnClickListener {
            examEntriesContainer.removeView(rowContainer)
            examEntryList.remove(entry)
        }

        examEntriesContainer.addView(rowContainer)
    }

    private fun previewRecipients() {
        val course = spinnerCourse.selectedItem?.toString() ?: ""
        val section = spinnerSection.selectedItem?.toString() ?: ""

        tvStatus.text = ""
        btnConfirmSend.visibility = View.GONE
        tvPreviewResult.visibility = View.GONE

        if (course.isEmpty() || section.isEmpty()) {
            tvStatus.text = "Please select a Course and Section"
            return
        }
        if (examEntryList.isEmpty()) {
            tvStatus.text = "Please add at least one subject entry"
            return
        }
        for (entry in examEntryList) {
            if (entry.subject.text.toString().trim().isEmpty() || entry.date.text.toString().trim().isEmpty()) {
                tvStatus.text = "Please fill in Subject and Date for all entries"
                return
            }
        }

        progressBar.visibility = View.VISIBLE

        db.collection("students")
            .whereEqualTo("course", course)
            .whereEqualTo("section", section)
            .get()
            .addOnSuccessListener { studentDocs ->
                if (studentDocs.isEmpty) {
                    progressBar.visibility = View.GONE
                    tvPreviewResult.visibility = View.VISIBLE
                    tvPreviewResult.text = "Walang estudyanteng nahanap sa $course - $section."
                    return@addOnSuccessListener
                }

                val studentIds = studentDocs.documents.map { it.id }

                db.collection("users")
                    .whereEqualTo("role", "parent")
                    .whereIn("studentId", studentIds.take(30))
                    .get()
                    .addOnSuccessListener { parentDocs ->
                        progressBar.visibility = View.GONE

                        val emails = parentDocs.documents.mapNotNull { it.getString("email") }
                        matchedParentEmails = emails

                        tvPreviewResult.visibility = View.VISIBLE
                        tvPreviewResult.text = buildString {
                            append("Course/Section: $course - $section\n")
                            append("Estudyante na nahanap: ${studentIds.size}\n")
                            append("Parent na tatanggap ng email: ${emails.size}\n\n")
                            append("Exam Entries (${examEntryList.size}):\n")
                            examEntryList.forEachIndexed { index, entry ->
                                append("${index + 1}. ${entry.subject.text} - ${entry.date.text} ${entry.time.text}\n")
                            }
                            if (emails.isEmpty()) {
                                append("\nWalang naka-register na parent para dito.")
                            }
                        }

                        if (emails.isNotEmpty()) {
                            btnConfirmSend.visibility = View.VISIBLE
                        }
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        tvStatus.text = "Failed to fetch parents: ${it.localizedMessage}"
                    }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                tvStatus.text = "Failed to fetch students: ${it.localizedMessage}"
            }
    }

    private fun confirmAndSend() {
        val course = spinnerCourse.selectedItem?.toString() ?: ""
        val section = spinnerSection.selectedItem?.toString() ?: ""

        if (matchedParentEmails.isEmpty()) {
            tvStatus.text = "Walang recipients. I-preview muna."
            return
        }

        btnConfirmSend.isEnabled = false
        tvStatus.text = "Sending emails..."

        val emailSubject = "Exam Schedule for $course - $section"
        val emailBody = buildString {
            append("Magandang araw!\n\n")
            append("Narito ang buong exam schedule para sa $course - $section:\n\n")
            examEntryList.forEachIndexed { index, entry ->
                append("${index + 1}. ${entry.subject.text}\n")
                append("   Date: ${entry.date.text}\n")
                if (entry.time.text.isNotEmpty()) append("   Time: ${entry.time.text}\n")
                if (entry.room.text.isNotEmpty()) append("   Room: ${entry.room.text}\n")
                if (entry.notes.text.isNotEmpty()) append("   Notes: ${entry.notes.text}\n")
                append("\n")
            }
            append("- Parent Monitoring App")
        }

        for (email in matchedParentEmails) {
            EmailSender.sendEmail(email, emailSubject, emailBody)
        }

        val batch = db.batch()
        val examCollection = db.collection("exam_schedules")

        for (entry in examEntryList) {
            val docRef = examCollection.document()
            val examData = hashMapOf(
                "course" to course,
                "section" to section,
                "subject" to entry.subject.text.toString(),
                "examDate" to entry.date.text.toString(),
                "examTime" to entry.time.text.toString(),
                "room" to entry.room.text.toString(),
                "notes" to entry.notes.text.toString(),
                "sentAt" to Timestamp.now()
            )
            batch.set(docRef, examData)
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Successfully sent to ${matchedParentEmails.size} parent(s)!", Toast.LENGTH_LONG).show()
                tvStatus.text = "✅ ${examEntryList.size} exam(s) sent and logged."
                btnConfirmSend.visibility = View.GONE
            }
            .addOnFailureListener {
                tvStatus.text = "Emails sent, but failed to log records: ${it.localizedMessage}"
            }
    }
}