package com.example.myhearing.data

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.heatmaps.WeightedLatLng

class WeightedLocation(initLatLng: LatLng, initIntensity: Double, initTime: Long) : WeightedLatLng(initLatLng, initIntensity) {
    var lat: Double
    var lng: Double
    var time: Long

    init {
        lat = initLatLng.latitude
        lng = initLatLng.longitude
        time = initTime
    }
}