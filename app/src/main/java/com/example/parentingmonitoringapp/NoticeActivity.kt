package com.example.parentingmonitoringapp

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class NoticeActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var spinnerCourse: Spinner
    private lateinit var spinnerSection: Spinner
    private lateinit var etTitle: EditText
    private lateinit var etAmount: EditText
    private lateinit var etMessage: EditText
    private lateinit var btnPreview: Button
    private lateinit var btnConfirmSend: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvPreviewResult: TextView
    private lateinit var tvStatus: TextView

    private var matchedParentEmails: List<String> = emptyList()

    private val courseSectionsMap = mutableMapOf<String, List<String>>()
    private val courseNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notice)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        spinnerCourse = findViewById(R.id.spinnerCourse)
        spinnerSection = findViewById(R.id.spinnerSection)
        etTitle = findViewById(R.id.etTitle)
        etAmount = findViewById(R.id.etAmount)
        etMessage = findViewById(R.id.etMessage)
        btnPreview = findViewById(R.id.btnPreview)
        btnConfirmSend = findViewById(R.id.btnConfirmSend)
        progressBar = findViewById(R.id.progressBar)
        tvPreviewResult = findViewById(R.id.tvPreviewResult)
        tvStatus = findViewById(R.id.tvStatus)

        btnPreview.setOnClickListener { previewRecipients() }
        btnConfirmSend.setOnClickListener { confirmAndSend() }

        loadCourses()
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

    private fun previewRecipients() {
        val course = spinnerCourse.selectedItem?.toString() ?: ""
        val section = spinnerSection.selectedItem?.toString() ?: ""
        val title = etTitle.text.toString().trim()
        val message = etMessage.text.toString().trim()

        tvStatus.text = ""
        btnConfirmSend.visibility = View.GONE
        tvPreviewResult.visibility = View.GONE

        if (course.isEmpty() || section.isEmpty()) {
            tvStatus.text = "Please select a Course and Section"
            return
        }
        if (title.isEmpty() || message.isEmpty()) {
            tvStatus.text = "Please fill in Title and Message"
            return
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
                    tvPreviewResult.text = "No students found in $course - $section."
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
                            append("Students found: ${studentIds.size}\n")
                            append("Parents to notify: ${emails.size}\n\n")
                            append("Title: $title\n")
                            val amountText = etAmount.text.toString().trim()
                            if (amountText.isNotEmpty()) append("Amount: $amountText\n")
                            if (emails.isEmpty()) {
                                append("\nNo registered parent found for this Course/Section.")
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
        val title = etTitle.text.toString().trim()
        val message = etMessage.text.toString().trim()
        val amount = etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0

        if (matchedParentEmails.isEmpty()) {
            tvStatus.text = "No recipients. Preview first."
            return
        }

        btnConfirmSend.isEnabled = false
        tvStatus.text = "Sending emails..."

        val emailSubject = "Notice: $title ($course - $section)"
        val emailBody = buildString {
            append("Good day!\n\n")
            append("$message\n\n")
            if (amount > 0) append("Amount: $amount\n\n")
            append("- Parent Monitoring App")
        }

        for (email in matchedParentEmails) {
            EmailSender.sendEmail(email, emailSubject, emailBody)
        }

        val noticeData = hashMapOf(
            "title" to title,
            "message" to message,
            "amount" to amount,
            "course" to course,
            "section" to section,
            "sentAt" to Timestamp.now(),
            "createdBy" to (auth.currentUser?.uid ?: "")
        )

        db.collection("expense_notices").add(noticeData)
            .addOnSuccessListener {
                Toast.makeText(this, "Successfully sent to ${matchedParentEmails.size} parent(s)!", Toast.LENGTH_LONG).show()
                tvStatus.text = "✅ Notice sent and logged."
                btnConfirmSend.visibility = View.GONE
            }
            .addOnFailureListener {
                tvStatus.text = "Emails sent, but failed to log record: ${it.localizedMessage}"
            }
    }
}