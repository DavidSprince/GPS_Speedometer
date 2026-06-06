package com.example.data

import kotlinx.coroutines.flow.Flow

class TripRepository(private val tripDao: TripDao) {
    val allTrips: Flow<List<Trip>> = tripDao.getAllTrips()

    suspend fun insertTrip(trip: Trip): Long {
        return tripDao.insertTrip(trip)
    }

    suspend fun deleteTripById(id: Long) {
        tripDao.deleteTripById(id)
    }

    suspend fun deleteAllTrips() {
        tripDao.deleteAllTrips()
    }
}
