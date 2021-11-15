package com.karry.findmyphone

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage


class FirebaseService : FirebaseMessagingService() {
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminName: ComponentName


    override fun onNewToken(newToken: String) {
        super.onNewToken(newToken)
        Log.d(TAG, "onNewToken: Token is $newToken")
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "onMessageReceived: $remoteMessage")
        super.onMessageReceived(remoteMessage)
        val message = remoteMessage.data["message"]!!
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminName = ComponentName(this, PolicyReceiver::class.java)
        val isAdminActive = devicePolicyManager.isAdminActive(adminName)
        when (message) {
            "Lock" -> {
                requestCurrentLocation()
                if (isAdminActive) {
                    devicePolicyManager.lockNow()
                } else {
                    Log.e(TAG, "You need to enable the Admin Device Features", )
                }
            }
            "Wipe Data" -> {
                requestCurrentLocation()
                if (isAdminActive) {
                    devicePolicyManager.wipeData(0)
                } else {
                    Log.e(TAG, "You need to enable the Admin Device Features", )
                }
            }
            "Get Location" -> {
                requestCurrentLocation()
            }
        }
    }

    companion object {
        const val TAG = "FCM"
    }
}