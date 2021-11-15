package com.karry.findmyphone

import android.Manifest
import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.karry.findmyphone.databinding.ActivityMainBinding
import java.util.*
import kotlin.concurrent.thread


@SuppressLint("HardwareIds")
class MainActivity : AppCompatActivity(), View.OnClickListener {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminName: ComponentName

    private lateinit var binding: ActivityMainBinding
    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
    }

    private val deviceName = getDeviceName()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        init()
        with(binding) {
            buttonLock.setOnClickListener(this@MainActivity)
            buttonEnable.setOnClickListener(this@MainActivity)
            buttonDisable.setOnClickListener(this@MainActivity)
            buttonWipeData.setOnClickListener(this@MainActivity)
        }
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val isAdminActive = devicePolicyManager.isAdminActive(adminName)
        binding.buttonDisable.visibility = if (isAdminActive) View.VISIBLE else View.GONE
        binding.buttonEnable.visibility = if (isAdminActive) View.GONE else View.VISIBLE
    }

    @SuppressLint("HardwareIds")
    private fun init() {
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        getSystemService(ACTIVITY_SERVICE)
        adminName = ComponentName(this, PolicyReceiver::class.java)
        Log.d(TAG, "init: $deviceId, ${getDeviceName()}")
        if (!checkDeviceExisting()) {
            addDeviceToDatabase()
        }
        getFirebaseToken()

    }

    private fun checkDeviceExisting(): Boolean {
        var result = false
        FirebaseFirestore.getInstance().collection("Devices").document(deviceId).get()
            .addOnSuccessListener {
                Log.d(TAG, "checkDeviceExisting: ${it == null}")
                result = it == null
            }.addOnFailureListener {
                result = false
                Log.e(TAG, "checkDeviceExisting", it)
            }
        return result
    }

    private fun addDeviceToDatabase() {
        val myDevice = hashMapOf(
            "Device ID" to deviceId,
            "Device Name" to deviceName,
            "FCM Token" to "",
            "Last Update" to System.currentTimeMillis()
        )
        FirebaseFirestore.getInstance().collection("Devices").document(deviceId).set(myDevice)
            .addOnSuccessListener {
                Log.d(TAG, "addDeviceToDatabase: Success to add new device")
            }.addOnFailureListener {
                Log.e(TAG, "addDeviceToDatabase: Fail to add new device", it)
            }
    }

    private fun updateTokenToDatabase(token: String) {
        Log.d(TAG, token)
        FirebaseFirestore.getInstance().collection("Devices").document(deviceId)
            .update(
                "FCM Token", token,
                "Device Name", deviceName,
                "Last Update", System.currentTimeMillis()
            )
            .addOnSuccessListener {
                Log.d(TAG, "updateTokenToDatabase: Successful")
            }.addOnFailureListener {
                Log.e(TAG, "updateTokenToDatabase: Fail", it)
            }
    }

    private fun getFirebaseToken() {
        thread {
            FirebaseMessaging.getInstance()
                .token
                .addOnSuccessListener {
                    updateTokenToDatabase(it)
                }
        }
    }

    @SuppressLint("BatteryLife")
    private fun checkBatteryOptimizations() {
        Log.d(TAG, "checkBatteryOptimizations")
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                ignoreOptimizationRequest.launch(intent)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun checkBackgroundLocationPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        ) {
            AlertDialog.Builder(this)
                .setTitle("Location Permission Needed")
                .setMessage("This app needs the Location permission, please accept to use location functionality")
                .setPositiveButton("OK") { _, _ ->
                    backgroundLocationPermissionRequest.launch(
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION
                    )
                }
                .create()
                .show()
        }
    }

    private val adminPermissionRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                Toast.makeText(
                    this@MainActivity,
                    "You have enabled the Admin Device features",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "Problem to enable the Admin Device features",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    private val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var result = true
            for (permission in permissions) {
                Log.d("PERMISSION", "${permission.key} = ${permission.value}")
                if (permission.value == false) {
                    result = false
                    break
                }
            }
            if (result) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                    Log.d("locationPermission", "SDK_INT>=Q")
                    checkBackgroundLocationPermission()
                } else {
                    Log.d("locationPermission", "SDK_INT<Q")
                    checkBatteryOptimizations()
                }
            }
        }

    private val backgroundLocationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it == true) {
                Toast.makeText(
                    this@MainActivity,
                    "Granted Background Location Permission",
                    Toast.LENGTH_SHORT
                ).show()
            }
            checkBatteryOptimizations()
        }


    private val ignoreOptimizationRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            Log.d("Optimization", "ignoreOptimizationRequest")
            if (it.resultCode == RESULT_OK) {
                Toast.makeText(
                    this@MainActivity,
                    "You have ignored battery optimizations",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "You have enabled battery optimizations",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onClick(view: View) {
        with(binding) {
            if (view.id == R.id.button_lock) {
                val isAdminActive = devicePolicyManager.isAdminActive(adminName)
                if (isAdminActive) {
                    devicePolicyManager.lockNow()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "You need to enable the Admin Device Features",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (view.id == R.id.button_wipe_data) {
                val isAdminActive = devicePolicyManager.isAdminActive(adminName)

                if (isAdminActive) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Wipe data")
                        .setMessage("This app will be wipe data of your device")
                        .setPositiveButton("OK") { _, _ ->
                            devicePolicyManager.wipeData(0)
                        }.setNegativeButton("Cancel", null)
                        .create()
                        .show()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "You need to enable the Admin Device Features",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else if (view.id == R.id.button_enable) {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Additional text explaining why we need this permission"
                )
                adminPermissionRequest.launch(intent)
            } else if (view.id == R.id.button_disable) {
                devicePolicyManager.removeActiveAdmin(adminName)
                buttonEnable.visibility = View.VISIBLE
                buttonDisable.visibility = View.GONE
            } else if (view.id == R.id.button_get_location) {
                if (ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_DENIED
                ) {
                    Toast.makeText(
                        this@MainActivity,
                        "You need to grant location permission",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

}
