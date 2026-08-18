package com.example.parentingmonitoringapp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e("GeofenceReceiver", "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e("GeofenceReceiver", "Geofence error: $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition

        val type = when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> "IN"
            Geofence.GEOFENCE_TRANSITION_EXIT -> "OUT"
            else -> return // ignore other transition types (e.g. DWELL)
        }

        // Kunin ang naka-save na studentId at parentUid mula SharedPreferences
        val prefs = context.getSharedPreferences("student_prefs", Context.MODE_PRIVATE)
        val studentId = prefs.getString("studentId", null)
        val parentUid = prefs.getString("parentUid", null)

        if (studentId == null || parentUid == null) {
            Log.e("GeofenceReceiver", "Missing studentId or parentUid in SharedPreferences")
            return
        }

        logAttendanceAndNotify(studentId, parentUid, type)
    }

    private fun logAttendanceAndNotify(studentId: String, parentUid: String, type: String) {
        val db = FirebaseFirestore.getInstance()

        if (type == "OUT") {
            // Hanapin ang pinaka-huling IN record para makuha ang time spent
            db.collection("attendance")
                .whereEqualTo("studentId", studentId)
                .whereEqualTo("type", "IN")
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { docs ->
                    val lastIn = docs.documents.firstOrNull()?.getTimestamp("timestamp")
                    val now = Timestamp.now()
                    val durationMinutes = if (lastIn != null) {
                        (now.seconds - lastIn.seconds) / 60
                    } else null

                    saveAttendanceRecord(studentId, type, now, durationMinutes, lastIn)
                }
                .addOnFailureListener {
                    // Kung hindi mahanap ang IN record, i-save pa rin ang OUT nang walang duration
                    saveAttendanceRecord(studentId, type, Timestamp.now(), null, null)
                }
        } else {
            saveAttendanceRecord(studentId, type, Timestamp.now(), null, null)
        }

        // Kunin ang parent email at student name, tapos magpadala ng email
        db.collection("users").document(parentUid).get()
            .addOnSuccessListener { parentDoc ->
                val parentEmail = parentDoc.getString("email") ?: return@addOnSuccessListener

                db.collection("students").document(studentId).get()
                    .addOnSuccessListener { studentDoc ->
                        val studentName = studentDoc.getString("studentName") ?: "Your child"

                        val subject: String
                        val body: String

                        if (type == "IN") {
                            subject = "Attendance Alert: $studentName has arrived at school"
                            body = "Hi! This is to inform you that $studentName has arrived at school.\n\nTime: ${java.util.Date()}\n\n- Parent Monitoring App"
                        } else {
                            subject = "Attendance Alert: $studentName has left school"
                            body = "Hi! This is to inform you that $studentName has left the school premises.\n\nTime: ${java.util.Date()}\n\n- Parent Monitoring App"
                        }

                        EmailSender.sendEmail(parentEmail, subject, body)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceReceiver", "Failed to get parent email: ${e.message}")
            }
    }

    private fun saveAttendanceRecord(
        studentId: String, type: String, timestamp: Timestamp,
        durationMinutes: Long?, pairedInTimestamp: Timestamp?
    ) {
        val db = FirebaseFirestore.getInstance()

        val attendanceData = hashMapOf<String, Any>(
            "studentId" to studentId,
            "type" to type,
            "timestamp" to timestamp
        )
        if (durationMinutes != null) attendanceData["durationMinutes"] = durationMinutes
        if (pairedInTimestamp != null) attendanceData["pairedInTimestamp"] = pairedInTimestamp

        db.collection("attendance").add(attendanceData)
            .addOnSuccessListener {
                Log.d("GeofenceReceiver", "Attendance logged: $studentId - $type")
            }
            .addOnFailureListener { e ->
                Log.e("GeofenceReceiver", "Failed to log attendance: ${e.message}")
            }
    }
}