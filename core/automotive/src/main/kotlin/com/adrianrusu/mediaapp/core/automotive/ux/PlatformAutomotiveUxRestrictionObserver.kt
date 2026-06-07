package com.adrianrusu.mediaapp.core.automotive.ux

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler

/**
 * AAOS implementation backed by `CarUxRestrictionsManager`.
 */
class PlatformAutomotiveUxRestrictionObserver(context: Context, private val handler: Handler? = null) :
    AutomotiveUxRestrictionObserver {
    private val applicationContext = context.applicationContext
    private var car: Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private var onChanged: ((AutomotiveUxRestrictions) -> Unit)? = null

    private val listener =
        CarUxRestrictionsManager.OnUxRestrictionsChangedListener { restrictions ->
            onChanged?.invoke(restrictions.toAppRestrictions())
        }

    override fun current(): AutomotiveUxRestrictions {
        if (!applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            return AutomotiveUxRestrictions.unrestricted(
                AutomotiveUxRestrictions.Source.NotAutomotive
            )
        }

        return runCatching {
            val restrictionsManager = manager ?: connectManager()
            restrictionsManager.currentCarUxRestrictions.toAppRestrictions()
        }.getOrElse {
            AutomotiveUxRestrictions.unrestricted(
                AutomotiveUxRestrictions.Source.Unavailable
            )
        }
    }

    override fun start(onChanged: (AutomotiveUxRestrictions) -> Unit) {
        this.onChanged = onChanged
        val restrictionsManager = runCatching {
            if (!applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                null
            } else {
                manager ?: connectManager()
            }
        }.getOrNull()

        if (restrictionsManager == null) {
            onChanged(current())
            return
        }

        restrictionsManager.registerListener(listener)
        onChanged(current())
    }

    override fun close() {
        runCatching {
            manager?.unregisterListener()
        }
        manager = null

        runCatching {
            car?.disconnect()
        }
        car = null
        onChanged = null
    }

    private fun connectManager(): CarUxRestrictionsManager {
        val carInstance = Car.createCar(applicationContext, handler)
        val restrictionsManager =
            carInstance.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager

        car = carInstance
        manager = restrictionsManager

        return restrictionsManager
    }
}

private fun CarUxRestrictions.toAppRestrictions(): AutomotiveUxRestrictions = AutomotiveUxRestrictions(
    source = AutomotiveUxRestrictions.Source.AutomotivePlatform,
    requiresDistractionOptimization = isRequiresDistractionOptimization,
    activeRestrictions = activeRestrictions,
    maxContentDepth = maxContentDepth,
    maxCumulativeContentItems = maxCumulativeContentItems,
    maxRestrictedStringLength = maxRestrictedStringLength
)
