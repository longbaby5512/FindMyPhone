package com.karry.findmyphone

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

fun getDeviceName(): String  {
    val manufacturer = Build.MANUFACTURER
    val model = Build.MODEL
    return if (model.startsWith(manufacturer)) {
        capitalize(model)
    } else {
        capitalize(manufacturer) + " " + model
    }
}

fun Context.checkPermission(permission: String): Boolean {
    if (permission == Manifest.permission.ACCESS_BACKGROUND_LOCATION && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        return true
    }

    return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

@SuppressLint("HardwareIds")
fun Context.requestCurrentLocation() {
    val fusedLocationClient: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(applicationContext)
    }
    val cancellationTokenSource = CancellationTokenSource()
    if (checkPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
        val currentLocationTask: Task<Location> = fusedLocationClient.getCurrentLocation(
            LocationRequest.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        )
        currentLocationTask.addOnSuccessListener {
            val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val currentLocation = hashMapOf(
                "Device ID" to deviceId,
                "Latitude" to it.latitude,
                "Longitude" to it.longitude,
                "Time" to it.time
            )
            FirebaseFirestore.getInstance().collection("Location").document().set(currentLocation)
                .addOnSuccessListener {
                    Log.d("UPDATE LOCATION", "requestCurrentLocation: Successful")
                }.addOnFailureListener { e ->
                    Log.e("UPDATE LOCATION", "requestCurrentLocation: Fail", e)
                }
            val simpleDateFormat = SimpleDateFormat("dd/MM/yyyy hh:mm aa", Locale("vi", "VN"))
            sendNotification("Location (success): ${it.latitude}, ${it.longitude}, ${simpleDateFormat.format(it.time)}")
            "Location (success): ${it.latitude}, ${it.longitude}, ${simpleDateFormat.format(it.time)}"
        }
    }
}

fun Context.sendNotification(message: String) {
    val notificationId = Random().nextInt()
    val channelId = "message"
    val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    val builder = NotificationCompat.Builder(this, channelId).apply {
        setSmallIcon(R.drawable.ic_launcher_foreground)
        setContentTitle("Find my phone")
        setContentText(message)
        priority = NotificationCompat.PRIORITY_HIGH
        setAutoCancel(true)
        setSound(soundUri)
    }
    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channelName = "Firebase message"
        val channelDescription = "This notification channel is used for message from firebase"
        val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            description = channelDescription
            enableVibration(true)
            vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    } else {
        builder.setVibrate(longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400))
    }
    val notificationManagerCompat = NotificationManagerCompat.from(this)
    notificationManagerCompat.notify(notificationId, builder.build())
}

private fun capitalize(s: String?): String {
    if (s == null || s.isEmpty()) {
        return ""
    }
    val first = s[0]
    return if (Character.isUpperCase(first)) {
        s
    } else {
        Character.toUpperCase(first).toString() + s.substring(1)
    }
}