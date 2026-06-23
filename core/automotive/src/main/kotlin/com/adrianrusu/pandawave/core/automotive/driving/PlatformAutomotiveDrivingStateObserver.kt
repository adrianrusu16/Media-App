package com.adrianrusu.pandawave.core.automotive.driving

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler

class PlatformAutomotiveDrivingStateObserver(context: Context, private val handler: Handler? = null) :
    AutomotiveDrivingStateObserver {
    private val applicationContext = context.applicationContext
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var onChanged: ((AutomotiveDrivingState) -> Unit)? = null
    private var gearSelection: Int? = null
    private var speedMetersPerSecond: Float? = null

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> gearSelection = value.value as? Int
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> speedMetersPerSecond = value.value as? Float
            }
            onChanged?.invoke(projectState())
        }

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            when (propertyId) {
                VehiclePropertyIds.GEAR_SELECTION -> gearSelection = null

                VehiclePropertyIds.PERF_VEHICLE_SPEED -> speedMetersPerSecond = null

                else -> {
                    gearSelection = null
                    speedMetersPerSecond = null
                }
            }
            onChanged?.invoke(AutomotiveDrivingState.Unknown)
        }
    }

    override fun current(): AutomotiveDrivingState {
        if (!applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return AutomotiveDrivingState.Unknown
        }
        return runCatching {
            val propertyManager = manager ?: connectManager()
            gearSelection = propertyManager.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, GLOBAL_AREA_ID)
            speedMetersPerSecond =
                propertyManager.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, GLOBAL_AREA_ID)
            projectState()
        }.getOrElse {
            gearSelection = null
            speedMetersPerSecond = null
            AutomotiveDrivingState.Unknown
        }
    }

    override fun start(onChanged: (AutomotiveDrivingState) -> Unit) {
        this.onChanged = onChanged
        val propertyManager = runCatching {
            if (!applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                null
            } else {
                manager ?: connectManager()
            }
        }.getOrNull()

        if (propertyManager == null) {
            onChanged(AutomotiveDrivingState.Unknown)
            return
        }

        val registered = runCatching {
            propertyManager.subscribePropertyEvents(
                VehiclePropertyIds.GEAR_SELECTION,
                callback
            ) && propertyManager.subscribePropertyEvents(
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                callback
            )
        }.getOrDefault(false)

        onChanged(if (registered) current() else AutomotiveDrivingState.Unknown)
    }

    override fun close() {
        runCatching { manager?.unsubscribePropertyEvents(callback) }
        manager = null
        runCatching { car?.disconnect() }
        car = null
        gearSelection = null
        speedMetersPerSecond = null
        onChanged = null
    }

    private fun connectManager(): CarPropertyManager {
        val carInstance = Car.createCar(applicationContext, handler)
        val propertyManager = carInstance.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        car = carInstance
        manager = propertyManager
        return propertyManager
    }

    private fun projectState(): AutomotiveDrivingState = AutomotiveDrivingState.fromVehicleSignals(
        gearSelection = gearSelection,
        speedMetersPerSecond = speedMetersPerSecond
    )

    private companion object {
        const val GLOBAL_AREA_ID = 0
    }
}
