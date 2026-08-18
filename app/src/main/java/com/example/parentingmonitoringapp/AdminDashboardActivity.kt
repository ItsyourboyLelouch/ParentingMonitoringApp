package com.example.parentingmonitoringapp

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent

class AdminDashboardActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        findViewById<Button>(R.id.btnManageStudents).setOnClickListener {
            Toast.makeText(this, "Manage Students - coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAttendance).setOnClickListener {
            startActivity(Intent(this, AttendanceRecordsActivity::class.java))
        }
        findViewById<Button>(R.id.btnExamSchedule).setOnClickListener {
            startActivity(Intent(this, ExamScheduleActivity::class.java))
        }
        findViewById<Button>(R.id.btnMeetings).setOnClickListener {
            Toast.makeText(this, "Meetings - coming soon", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btnAllowance).setOnClickListener {
            startActivity(Intent(this, NoticeActivity::class.java))
        }
        findViewById<Button>(R.id.btnReports).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<Button>(R.id.btnTestEmail).setOnClickListener {
            EmailSender.sendEmail(
                toEmail = "charlesmadurog6@gmail.com",
                subject = "parentmonitoringtcu@gmail.com",
                body = "Testttt"
            )
            Toast.makeText(this, "Sending test email...", Toast.LENGTH_SHORT).show()
        }
    }
}