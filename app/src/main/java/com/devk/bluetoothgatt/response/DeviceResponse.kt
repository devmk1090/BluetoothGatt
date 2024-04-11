package com.devk.bluetoothgatt.response

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DeviceResponse(
    var id: String,
    var name: String,
): Parcelable