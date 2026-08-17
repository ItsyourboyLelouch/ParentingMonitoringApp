package com.example.parentingmonitoringapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvGoToRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)
        tvGoToRegister = findViewById(R.id.tvGoToRegister)

        btnLogin.setOnClickListener { attemptLogin() }
        tvGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            tvError.text = "Please enter email and password"
            return
        }

        progressBar.visibility = android.view.View.VISIBLE
        tvError.text = ""

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val user = result.user
                val uid = user?.uid ?: return@addOnSuccessListener

                db.collection("users").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val role = doc.getString("role") ?: "parent"

                        // Admin accounts (made via Firebase Console) skip email verification
                        if (role == "parent" && user != null && !user.isEmailVerified) {
                            progressBar.visibility = android.view.View.GONE
                            tvError.text = "Please verify your email first. Check your Gmail inbox."
                            auth.signOut()
                            return@addOnSuccessListener
                        }

                        progressBar.visibility = android.view.View.GONE
                        val nextActivity = when (role) {
                            "admin" -> AdminDashboardActivity::class.java
                            "student" -> StudentActivity::class.java
                            else -> ParentDashboardActivity::class.java
                        }

                        startActivity(Intent(this, nextActivity))
                        finish()
                    }
                    .addOnFailureListener {
                        progressBar.visibility = android.view.View.GONE
                        tvError.text = "Failed to load user profile"
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = android.view.View.GONE
                tvError.text = e.localizedMessage ?: "Login failed"
            }
    }
}