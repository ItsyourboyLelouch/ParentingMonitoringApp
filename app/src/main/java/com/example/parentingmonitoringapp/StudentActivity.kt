package com.example.parentingmonitoringapp

import android.Manifest
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StudentActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geofencingClient: GeofencingClient
    private lateinit var tvLocationStatus: TextView
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    private val FINE_LOCATION_REQUEST_CODE = 100
    private val BACKGROUND_LOCATION_REQUEST_CODE = 101
    private val GEOFENCE_ID = "SCHOOL_GEOFENCE"

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeofenceBroadcastReceiver::class.java)
        intent.action = "com.example.parentingmonitoringapp.ACTION_GEOFENCE_EVENT"
        PendingIntent.getBroadcast(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geofencingClient = LocationServices.getGeofencingClient(this)

        tvLocationStatus = findViewById(R.id.tvLocationStatus)
        val btnEnableTracking = findViewById<Button>(R.id.btnEnableTracking)

        btnEnableTracking.setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineLocationGranted) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                FINE_LOCATION_REQUEST_CODE
            )
        } else {
            checkBackgroundPermission()
        }
    }

    private fun checkBackgroundPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val backgroundGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!backgroundGranted) {
                Toast.makeText(
                    this,
                    "Sa susunod na dialog, piliin ang 'Allow all the time' para gumana ang background tracking",
                    Toast.LENGTH_LONG
                ).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_REQUEST_CODE
                )
                return
            }
        }
        onAllPermissionsGranted()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            FINE_LOCATION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkBackgroundPermission()
                } else {
                    Toast.makeText(this, "Kailangan ng location permission", Toast.LENGTH_LONG).show()
                }
            }
            BACKGROUND_LOCATION_REQUEST_CODE -> {
                onAllPermissionsGranted() // tuloy pa rin kahit tumanggi sa background (foreground na lang gagana)
            }
        }
    }

    private fun onAllPermissionsGranted() {
        tvLocationStatus.text = "Permissions granted. Setting up geofence..."
        startLiveLocationDisplay()
        setupGeofence()
    }

    // Live display lang para makita ng student yung distance nila (foreground)
    private fun startLiveLocationDisplay() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return
        if (isTracking) return

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 5000L
        ).setMinUpdateIntervalMillis(3000L).build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                displayDistance(location)
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback as LocationCallback, Looper.getMainLooper()
        )
        isTracking = true
    }

    private fun displayDistance(location: Location) {
        db.collection("settings").document("geofence").get()
            .addOnSuccessListener { doc ->
                val schoolLat = doc.getDouble("schoolLat") ?: return@addOnSuccessListener
                val schoolLng = doc.getDouble("schoolLng") ?: return@addOnSuccessListener
                val radius = doc.getDouble("radiusMeters") ?: 100.0

                val schoolLocation = Location("school").apply {
                    latitude = schoolLat
                    longitude = schoolLng
                }
                val distance = location.distanceTo(schoolLocation)

                tvLocationStatus.text = if (distance <= radius) {
                    "✅ INSIDE school area (${distance.toInt()}m) — Background tracking ON"
                } else {
                    "🚶 OUTSIDE school area (${distance.toInt()}m) — Background tracking ON"
                }
            }
    }

    // Ito ang totoong background geofence — gagana kahit sarado ang app
    private fun setupGeofence() {
        db.collection("settings").document("geofence").get()
            .addOnSuccessListener { doc ->
                val schoolLat = doc.getDouble("schoolLat") ?: return@addOnSuccessListener
                val schoolLng = doc.getDouble("schoolLng") ?: return@addOnSuccessListener
                val radius = (doc.getDouble("radiusMeters") ?: 100.0).toFloat()

                val geofence = Geofence.Builder()
                    .setRequestId(GEOFENCE_ID)
                    .setCircularRegion(schoolLat, schoolLng, radius)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
                    .build()

                val geofencingRequest = GeofencingRequest.Builder()
                    .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
                    .addGeofence(geofence)
                    .build()

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) return@addOnSuccessListener

                geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Background geofence activated!", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Geofence setup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }

                // I-save ang studentId + UID sa SharedPreferences para magamit ng Receiver later
                saveStudentInfoForReceiver()
            }
    }

    private fun saveStudentInfoForReceiver() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val studentId = doc.getString("studentId") ?: return@addOnSuccessListener
                val parentUid = doc.getString("parentUid") ?: return@addOnSuccessListener

                val prefs = getSharedPreferences("student_prefs", MODE_PRIVATE)
                prefs.edit()
                    .putString("studentId", studentId)
                    .putString("parentUid", parentUid)
                    .apply()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        // Hindi natin tinatanggal ang geofence dito - dapat tumuloy kahit closed ang activity
    }
}