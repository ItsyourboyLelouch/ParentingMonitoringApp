package com.example.parentingmonitoringapp

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var etStudentId: EditText
    private lateinit var spinnerCourse: Spinner
    private lateinit var spinnerSection: Spinner
    private lateinit var etStudentEmail: EditText
    private lateinit var etStudentPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvGoToLogin: TextView

    // course name -> list of sections
    private val courseSectionsMap = mutableMapOf<String, List<String>>()
    private val courseNames = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        etStudentId = findViewById(R.id.etStudentId)
        spinnerCourse = findViewById(R.id.spinnerCourse)
        spinnerSection = findViewById(R.id.spinnerSection)
        etStudentEmail = findViewById(R.id.etStudentEmail)
        etStudentPassword = findViewById(R.id.etStudentPassword)
        btnRegister = findViewById(R.id.btnRegister)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)
        tvGoToLogin = findViewById(R.id.tvGoToLogin)

        btnRegister.setOnClickListener { attemptRegister() }
        tvGoToLogin.setOnClickListener { finish() }

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
                tvError.text = "Failed to load courses. Check your internet connection."
            }
    }

    private fun updateSectionSpinner(courseName: String?) {
        val sections = courseSectionsMap[courseName] ?: emptyList()
        val sectionAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sections)
        spinnerSection.adapter = sectionAdapter
    }

    private fun attemptRegister() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()
        val studentId = etStudentId.text.toString().trim()
        val selectedCourse = spinnerCourse.selectedItem?.toString() ?: ""
        val selectedSection = spinnerSection.selectedItem?.toString() ?: ""
        val studentEmail = etStudentEmail.text.toString().trim()
        val studentPassword = etStudentPassword.text.toString().trim()

        tvError.setTextColor(android.graphics.Color.parseColor("#D32F2F"))
        tvError.text = ""

        if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || studentId.isEmpty()
            || studentEmail.isEmpty() || studentPassword.isEmpty()) {
            tvError.text = "Please fill in all fields"
            return
        }
        if (selectedCourse.isEmpty() || selectedSection.isEmpty()) {
            tvError.text = "Please select a Course and Section"
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tvError.text = "Please enter a valid parent email address"
            return
        }
        if (!email.endsWith("@gmail.com")) {
            tvError.text = "Parent email must be a valid Gmail account (@gmail.com)"
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(studentEmail).matches()) {
            tvError.text = "Please enter a valid student email address"
            return
        }
        if (email == studentEmail) {
            tvError.text = "Parent and student email must be different"
            return
        }
        if (password.length < 6 || studentPassword.length < 6) {
            tvError.text = "Passwords must be at least 6 characters"
            return
        }
        if (password != confirmPassword) {
            tvError.text = "Passwords do not match"
            return
        }

        progressBar.visibility = View.VISIBLE
        btnRegister.isEnabled = false

        // Step 1: Verify Student ID exists AND its course/section match the selected ones
        db.collection("students").document(studentId).get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true
                    tvError.text = "Student ID not found. Please contact the school admin."
                    return@addOnSuccessListener
                }

                val actualCourse = doc.getString("course") ?: ""
                val actualSection = doc.getString("section") ?: ""

                if (actualCourse != selectedCourse || actualSection != selectedSection) {
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true
                    tvError.text = "Course/Section does not match our records for this Student ID. Please double-check."
                    return@addOnSuccessListener
                }

                createParentAccount(fullName, email, password, studentId, selectedCourse, selectedSection, studentEmail, studentPassword)
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                btnRegister.isEnabled = true
                tvError.text = "Unable to verify Student ID. Check your internet connection."
            }
    }

    private fun createParentAccount(
        fullName: String, email: String, password: String,
        studentId: String, course: String, section: String,
        studentEmail: String, studentPassword: String
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { parentResult ->
                val parentUid = parentResult.user?.uid ?: return@addOnSuccessListener
                parentResult.user?.sendEmailVerification()

                val parentMap = hashMapOf(
                    "name" to fullName,
                    "email" to email,
                    "role" to "parent",
                    "studentId" to studentId,
                    "course" to course,
                    "section" to section
                )

                db.collection("users").document(parentUid).set(parentMap)
                    .addOnSuccessListener {
                        createStudentAccount(parentUid, studentEmail, studentPassword, studentId, course, section)
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        btnRegister.isEnabled = true
                        tvError.text = "Parent account created but failed to save profile. Contact admin."
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnRegister.isEnabled = true
                tvError.text = "Parent account error: ${e.localizedMessage}"
            }
    }

    private fun createStudentAccount(
        parentUid: String, studentEmail: String, studentPassword: String,
        studentId: String, course: String, section: String
    ) {
        auth.createUserWithEmailAndPassword(studentEmail, studentPassword)
            .addOnSuccessListener { studentResult ->
                val studentUid = studentResult.user?.uid ?: return@addOnSuccessListener

                val studentMap = hashMapOf(
                    "email" to studentEmail,
                    "role" to "student",
                    "studentId" to studentId,
                    "course" to course,
                    "section" to section,
                    "parentUid" to parentUid
                )

                db.collection("users").document(studentUid).set(studentMap)
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        auth.signOut()
                        tvError.setTextColor(android.graphics.Color.parseColor("#2E7D32"))
                        tvError.text = "Registration successful! Parent: please verify your Gmail. Student: you can log in directly."
                    }
                    .addOnFailureListener {
                        progressBar.visibility = View.GONE
                        btnRegister.isEnabled = true
                        tvError.text = "Student account created but failed to save profile. Contact admin."
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnRegister.isEnabled = true
                tvError.text = "Student account error: ${e.localizedMessage}"
            }
    }
}