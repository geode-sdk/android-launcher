package com.customRobTop

@Suppress("unused")
object JniToCpp {
    @JvmStatic
    external fun didCacheInterstitial(str: String?)

    @JvmStatic
    external fun didClickInterstitial()

    @JvmStatic
    external fun didCloseInterstitial()

    @JvmStatic
    external fun didDismissInterstitial()

    @JvmStatic
    external fun everyplayRecordingStopped()

    @JvmStatic
    external fun googlePlaySignedIn()

    @JvmStatic
    external fun hideLoadingCircle()

    @JvmStatic
    external fun itemPurchased(str: String)

    @JvmStatic
    external fun itemRefunded(str: String)

    @JvmStatic
    external fun promoImageDownloaded()

    @JvmStatic
    external fun resumeSound()

    @JvmStatic
    external fun rewardedVideoAdFinished(i: Int)

    @JvmStatic
    external fun rewardedVideoAdHidden()

    @JvmStatic
    external fun setupHSSAssets(str: String?, str2: String?)

    @JvmStatic
    external fun showInterstitialFailed()

    @JvmStatic
    external fun userDidAttemptToRateApp()

    @JvmStatic
    external fun videoAdHidden()

    @JvmStatic
    external fun videoAdShowed()
}