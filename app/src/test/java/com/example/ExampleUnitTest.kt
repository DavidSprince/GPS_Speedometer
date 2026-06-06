package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testHaversineDistance_NewYorkToTokyo() {
        // Tokyo (35.6895° N, 139.6917° E)
        val latTok = 35.6895
        val lonTok = 139.6917

        // New York City (40.7128° N, 74.0060° W)
        val latNY = 40.7128
        val lonNY = -74.0060

        // Calculated geodesic distance (Great-Circle path is approx 10,845 to 10,855 kilometers)
        val distanceKm = TrackingService.calculateHaversineDistance(latNY, lonNY, latTok, lonTok)
        
        // Assert boundary values with 1% acceptable margin of error
        assertTrue("Distance between NYC and Tokyo should be around 10850 km, but got $distanceKm", 
            distanceKm in 10800.0..10900.0)
    }

    @Test
    fun testHaversineDistance_MicroGpsTicks() {
        // Coordinate Point A (San Francisco base point)
        val latA = 37.774929
        val lonA = -122.419416

        // Coordinate Point B - Approx 100 meters east
        val latB = 37.774929
        val lonB = -122.418280

        val distanceKm = TrackingService.calculateHaversineDistance(latA, lonA, latB, lonB)
        val distanceMeters = distanceKm * 1000.0

        // A coordinate step of 0.001136 degrees longitude at this latitude is indeed approx 100.0 meters.
        assertTrue("Distance should be around 100 meters, but got $distanceMeters meters", 
            distanceMeters in 95.0..105.0)
    }

    @Test
    fun testHaversineDistance_IdenticalPoints() {
        val lat = 45.0
        val lon = 90.0
        
        val distanceKm = TrackingService.calculateHaversineDistance(lat, lon, lat, lon)
        assertEquals(0.0, distanceKm, 0.0001)
    }
}
