package com.inertial.navigation.logic

class NativeLib {
    init {
        System.loadLibrary("rust_lib")
    }

    external fun updateIMU(
        gx: Float, gy: Float, gz: Float,
        ax: Float, ay: Float, az: Float,
        mx: Float, my: Float, mz: Float,
        dt: Float
    ): FloatArray
}