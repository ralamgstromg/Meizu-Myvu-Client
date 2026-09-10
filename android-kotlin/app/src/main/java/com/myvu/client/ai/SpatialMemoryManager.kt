package com.myvu.client.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.myvu.client.core.LogBus
import com.myvu.client.core.Prefs
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Gestor de Memoria Espacial para el Asistente.
 * Permite recordar y localizar dónde estacionó el usuario su vehículo o puntos de interés personal.
 */
object SpatialMemoryManager {

    fun saveParkingLocation(context: Context, note: String = ""): String {
        if (!hasLocationPermission(context)) {
            return "No tengo permiso de ubicación para recordar dónde estacionaste."
        }

        return try {
            val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val syncLoc = try {
                locManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: locManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) { null }

            if (syncLoc != null) {
                Prefs.setParkingLocation(context, syncLoc.latitude, syncLoc.longitude, note)
                LogBus.log("SpatialMemoryManager -> Estacionamiento guardado de inmediato: (${syncLoc.latitude}, ${syncLoc.longitude})")
            }

            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null) {
                    Prefs.setParkingLocation(context, loc.latitude, loc.longitude, note)
                    LogBus.log("SpatialMemoryManager -> Estacionamiento actualizado por FusedLocation: (${loc.latitude}, ${loc.longitude})")
                }
            }

            "Ubicación de estacionamiento registrada. Di '¿dónde estacioné?' cuando quieras regresar."
        } catch (e: Exception) {
            LogBus.error("SpatialMemoryManager -> Error guardando estacionamiento", e)
            "No se pudo guardar la ubicación del estacionamiento: ${e.message}"
        }
    }

    fun saveCoordinates(context: Context, lat: Double, lon: Double, note: String = ""): String {
        Prefs.setParkingLocation(context, lat, lon, note)
        return "Ubicación de estacionamiento guardada correctamente."
    }

    fun getParkingLocation(context: Context): String {
        val savedLat = Prefs.parkingLatitude(context)
        val savedLon = Prefs.parkingLongitude(context)
        val savedTime = Prefs.parkingTime(context)

        if (savedLat == 0.0 && savedLon == 0.0) {
            return "No tienes ningún estacionamiento guardado. Di 'estacioné aquí' para recordar este punto."
        }

        val elapsedMs = System.currentTimeMillis() - savedTime
        val elapsedMinutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMs)
        val timeLabel = when {
            elapsedMinutes < 1 -> "hace un momento"
            elapsedMinutes < 60 -> "hace $elapsedMinutes minutos"
            else -> "hace ${elapsedMinutes / 60} horas y ${elapsedMinutes % 60} minutos"
        }

        val note = Prefs.parkingNote(context)
        val noteSuffix = if (note.isNotBlank()) " ($note)" else ""

        val targetLoc = Location("target").apply {
            latitude = savedLat
            longitude = savedLon
        }

        // 1. Intentar cálculo síncrono con la última ubicación conocida
        if (hasLocationPermission(context)) {
            val locManager = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            val syncLoc = try {
                locManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: locManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
            } catch (e: SecurityException) { null }

            if (syncLoc != null) {
                val meters = syncLoc.distanceTo(targetLoc).roundToInt()
                val bearing = syncLoc.bearingTo(targetLoc)
                val cardinal = bearingToCardinal(bearing)
                val distText = if (meters < 1000) "$meters metros" else String.format(java.util.Locale.US, "%.1f km", meters / 1000f)
                return "Tu vehículo está a unos $distText hacia $cardinal ($timeLabel$noteSuffix)."
            }
        }

        return "Tu vehículo está guardado $timeLabel$noteSuffix. Coordenadas: $savedLat, $savedLon."
    }

    fun calculateDirection(currentLat: Double, currentLon: Double, targetLat: Double, targetLon: Double): Pair<Int, String> {
        val current = Location("current").apply {
            latitude = currentLat
            longitude = currentLon
        }
        val target = Location("target").apply {
            latitude = targetLat
            longitude = targetLon
        }
        val distance = current.distanceTo(target).roundToInt()
        val bearing = current.bearingTo(target)
        return Pair(distance, bearingToCardinal(bearing))
    }

    fun clearParkingLocation(context: Context): String {
        Prefs.clearParkingLocation(context)
        return "Registro de estacionamiento borrado."
    }

    fun bearingToCardinal(bearing: Float): String {
        val normalized = (bearing % 360 + 360) % 360
        return when {
            normalized >= 337.5 || normalized < 22.5 -> "el Norte"
            normalized in 22.5..67.5 -> "el Noreste"
            normalized in 67.5..112.5 -> "el Este"
            normalized in 112.5..157.5 -> "el Sureste"
            normalized in 157.5..202.5 -> "el Sur"
            normalized in 202.5..247.5 -> "el Suroeste"
            normalized in 247.5..292.5 -> "el Oeste"
            normalized in 292.5..337.5 -> "el Noroeste"
            else -> "adelante"
        }
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
}
