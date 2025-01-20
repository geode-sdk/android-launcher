package org.cocos2dx.lib

import android.content.Context
import android.graphics.Typeface
import kotlin.jvm.Synchronized
import java.util.HashMap

object Cocos2dxTypefaces {
    private val typefaceCache = HashMap<String, Typeface>()

    @Synchronized
    operator fun get(pContext: Context, pAssetName: String): Typeface? {
        val typeface: Typeface?
        val typeface2: Typeface

        synchronized(Cocos2dxTypefaces::class.java) {
            if (!typefaceCache.containsKey(pAssetName)) {
                typeface2 = if (pAssetName.startsWith("/")) {
                    Typeface.createFromFile(pAssetName)
                } else {
                    Typeface.createFromAsset(pContext.assets, pAssetName)
                }
                typefaceCache[pAssetName] = typeface2
            }
            typeface = typefaceCache[pAssetName]
        }
        return typeface
    }
}