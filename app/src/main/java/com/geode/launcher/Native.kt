package com.geode.launcher

object Native {
    var isInitRun = false
    var isGeodeLoaded = false

    @JvmStatic
    external fun postGameInit()
}