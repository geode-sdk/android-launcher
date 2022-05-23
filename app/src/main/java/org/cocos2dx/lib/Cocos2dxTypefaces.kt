package org.cocos2dx.lib

import android.content.Context
import android.graphics.Typeface
import kotlin.jvm.Synchronized
import java.util.HashMap

object Cocos2dxTypefaces {
    private val sTypefaceCache = HashMap<String, Typeface>()

    @Synchronized
    operator fun get(pContext: Context, pAssetName: String): Typeface? {
        var typeface: Typeface?
        var typeface2: Typeface
        synchronized(Cocos2dxTypefaces::class.java) {
            if (!sTypefaceCache.containsKey(pAssetName)) {
                typeface2 = if (pAssetName.startsWith("/")) {
                    Typeface.createFromFile(pAssetName)
                } else {
                    Typeface.createFromAsset(pContext.assets, pAssetName)
                }
                sTypefaceCache[pAssetName] = typeface2
            }
            typeface = sTypefaceCache[pAssetName]
        }
        return typeface
    }
}