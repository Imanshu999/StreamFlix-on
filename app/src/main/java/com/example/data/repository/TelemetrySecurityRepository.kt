package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.data.model.BanRecord
import com.example.data.model.UserDeviceTelemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

class TelemetrySecurityRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("streamflix_security_prefs", Context.MODE_PRIVATE)

    private val _currentDeviceId = MutableStateFlow(getOrCreateDeviceId())
    val currentDeviceId: StateFlow<String> = _currentDeviceId.asStateFlow()
    val deviceId: String get() = _currentDeviceId.value

    private val _currentIpAddress = MutableStateFlow(detectDeviceIp())
    val currentIpAddress: StateFlow<String> = _currentIpAddress.asStateFlow()
    val clientIp: String get() = _currentIpAddress.value

    private val _activeDevices = MutableStateFlow<List<UserDeviceTelemetry>>(emptyList())
    val activeDevices: StateFlow<List<UserDeviceTelemetry>> = _activeDevices.asStateFlow()

    private val _bannedRecords = MutableStateFlow<List<BanRecord>>(emptyList())
    val bannedRecords: StateFlow<List<BanRecord>> = _bannedRecords.asStateFlow()

    private val _isCurrentClientBanned = MutableStateFlow(false)
    val isCurrentClientBanned: StateFlow<Boolean> = _isCurrentClientBanned.asStateFlow()

    private val _totalInstallCount = MutableStateFlow(1420)
    val totalInstallCount: StateFlow<Int> = _totalInstallCount.asStateFlow()

    init {
        // Initialize active device telemetry and load initial sample fleet for realistic admin monitoring
        initializeClientTelemetry()
    }

    private fun getOrCreateDeviceId(): String {
        var id = prefs.getString("device_uuid", null)
        if (id == null) {
            id = "DEV-" + UUID.randomUUID().toString().take(12).uppercase()
            prefs.edit().putString("device_uuid", id).apply()
        }
        return id
    }

    private fun detectDeviceIp(): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is InetAddress) {
                        val sAddr = addr.hostAddress ?: ""
                        val isIPv4 = sAddr.indexOf(':') < 0
                        if (isIPv4) return sAddr
                    }
                }
            }
        } catch (_: Exception) {}
        return "192.168.1.104"
    }

    private fun initializeClientTelemetry() {
        val myDeviceId = _currentDeviceId.value
        val myIp = _currentIpAddress.value
        val myModel = "${Build.MANUFACTURER.capitalize()} ${Build.MODEL}"
        val myOs = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

        val currentDevice = UserDeviceTelemetry(
            deviceId = myDeviceId,
            deviceModel = myModel,
            osVersion = myOs,
            ipAddress = myIp,
            firstLaunchTimestamp = System.currentTimeMillis() - 3600000,
            lastActiveTimestamp = System.currentTimeMillis(),
            isBanned = false
        )

        _activeDevices.value = listOf(currentDevice)
        _bannedRecords.value = emptyList()

        checkCurrentClientBanStatus()
    }

    private fun checkCurrentClientBanStatus() {
        val myDeviceId = _currentDeviceId.value
        val myIp = _currentIpAddress.value

        val isBanned = _bannedRecords.value.any { record ->
            (record.targetType == "DEVICE" && record.targetKey.equals(myDeviceId, ignoreCase = true)) ||
            (record.targetType == "IP" && record.targetKey == myIp)
        }
        _isCurrentClientBanned.value = isBanned
    }

    fun banDevice(deviceId: String, reason: String = "Security Policy Violation") {
        val updatedRecords = _bannedRecords.value.toMutableList()
        if (updatedRecords.none { it.targetKey == deviceId }) {
            updatedRecords.add(
                BanRecord(
                    targetKey = deviceId,
                    targetType = "DEVICE",
                    bannedAt = System.currentTimeMillis(),
                    reason = reason
                )
            )
            _bannedRecords.value = updatedRecords
        }

        // Update active devices status
        _activeDevices.value = _activeDevices.value.map {
            if (it.deviceId == deviceId) it.copy(isBanned = true, banReason = reason) else it
        }

        checkCurrentClientBanStatus()
    }

    fun unbanDevice(deviceId: String) {
        _bannedRecords.value = _bannedRecords.value.filterNot { it.targetKey == deviceId }
        _activeDevices.value = _activeDevices.value.map {
            if (it.deviceId == deviceId) it.copy(isBanned = false, banReason = null) else it
        }
        checkCurrentClientBanStatus()
    }

    fun banIp(ip: String, reason: String = "Suspicious traffic / IP blacklisted") {
        val updatedRecords = _bannedRecords.value.toMutableList()
        if (updatedRecords.none { it.targetKey == ip }) {
            updatedRecords.add(
                BanRecord(
                    targetKey = ip,
                    targetType = "IP",
                    bannedAt = System.currentTimeMillis(),
                    reason = reason
                )
            )
            _bannedRecords.value = updatedRecords
        }
        checkCurrentClientBanStatus()
    }

    fun unbanIp(ip: String) {
        _bannedRecords.value = _bannedRecords.value.filterNot { it.targetKey == ip }
        checkCurrentClientBanStatus()
    }

    fun emergencyUnbanCurrentDevice() {
        unbanDevice(_currentDeviceId.value)
        unbanIp(_currentIpAddress.value)
    }
}
