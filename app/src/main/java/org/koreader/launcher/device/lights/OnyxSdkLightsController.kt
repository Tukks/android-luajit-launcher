package org.koreader.launcher.device.lights

import android.app.Activity
import android.content.Context
import android.util.Log
import org.koreader.launcher.device.LightsInterface
import java.lang.reflect.Method

// ─── Constants mirroring FrontLightController / BaseBrightnessProvider ────────

private const val LIGHT_TYPE_FL = 1 // FLBrightnessProvider — single channel
private const val LIGHT_TYPE_CTM_WARM = 2 // WarmBrightnessProvider — warm channel
private const val LIGHT_TYPE_CTM_COLD = 3 // ColdBrightnessProvider — cold channel
private const val LIGHT_TYPE_CTM_ALL = 4 // open/close the whole CTM unit
private const val LIGHT_TYPE_TEMP = 6 // CTMTemperatureProvider — CTM warmth param
private const val LIGHT_TYPE_CTM_BR = 7 // CTMBrightnessProvider — CTM brightness param

// Mirrors BrightnessType enum
enum class OnyxBrightnessType { FL, WARM_AND_COLD, CTM, NONE }

// ─── Controller ───────────────────────────────────────────────────────────────

class OnyxSdkLightsController : LightsInterface {

    companion object {
        private const val TAG = "Lights"
        private const val MIN = 0
        private const val FALLBACK_MAX = 255
    }

    override fun getPlatform(): String = "onyx-sdk-lights"
    override fun hasFallback(): Boolean = false
    override fun needsPermission(): Boolean = false
    override fun hasStandaloneWarmth(): Boolean = false

    override fun hasWarmth(): Boolean {
        return when (OnyxDevice.brightnessType) {
            OnyxBrightnessType.WARM_AND_COLD,
            OnyxBrightnessType.CTM -> true

            else -> !OnyxDevice.isInitialized // optimistic before init
        }
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    override fun getBrightness(activity: Activity): Int {
        OnyxDevice.init(activity)
        return when (OnyxDevice.brightnessType) {
            OnyxBrightnessType.FL -> OnyxDevice.getLightValue(activity, LIGHT_TYPE_FL) ?: 0
            OnyxBrightnessType.WARM_AND_COLD -> OnyxDevice.getLightValue(activity, LIGHT_TYPE_CTM_COLD) ?: 0
            OnyxBrightnessType.CTM -> OnyxDevice.getLightValue(activity, LIGHT_TYPE_CTM_BR) ?: 0
            OnyxBrightnessType.NONE -> 0
        }
    }

    override fun getWarmth(activity: Activity): Int {
        OnyxDevice.init(activity)
        return when (OnyxDevice.brightnessType) {
            OnyxBrightnessType.WARM_AND_COLD -> OnyxDevice.getLightValue(activity, LIGHT_TYPE_CTM_WARM) ?: 0
            OnyxBrightnessType.CTM -> OnyxDevice.getLightValue(activity, LIGHT_TYPE_TEMP) ?: 0
            else -> 0
        }
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    override fun setBrightness(activity: Activity, brightness: Int) {
        OnyxDevice.init(activity)
        val max = getMaxBrightness()
        if (brightness < MIN || brightness > max) {
            Log.w(TAG, "brightness out of range: $brightness (max=$max)")
            return
        }
        Log.v(TAG, "setBrightness=$brightness type=${OnyxDevice.brightnessType}")
        when (OnyxDevice.brightnessType) {
            OnyxBrightnessType.FL ->
                OnyxDevice.setLight(activity, LIGHT_TYPE_FL, brightness)

            OnyxBrightnessType.WARM_AND_COLD ->
                OnyxDevice.setLight(activity, LIGHT_TYPE_CTM_COLD, brightness)

            OnyxBrightnessType.CTM ->
                OnyxDevice.setLight(activity, LIGHT_TYPE_CTM_BR, brightness)

            OnyxBrightnessType.NONE -> Unit
        }
    }

    override fun setWarmth(activity: Activity, warmth: Int) {
        OnyxDevice.init(activity)
        val max = getMaxWarmth()
        if (warmth < MIN || warmth > max) {
            Log.w(TAG, "warmth out of range: $warmth (max=$max)")
            return
        }
        Log.v(TAG, "setWarmth=$warmth type=${OnyxDevice.brightnessType}")
        when (OnyxDevice.brightnessType) {
            OnyxBrightnessType.WARM_AND_COLD ->
                OnyxDevice.setLight(activity, LIGHT_TYPE_CTM_WARM, warmth)

            OnyxBrightnessType.CTM ->
                OnyxDevice.setLight(activity, LIGHT_TYPE_TEMP, warmth)

            else -> Unit
        }
    }

    // ── Range ─────────────────────────────────────────────────────────────────

    override fun getMinBrightness(): Int = MIN
    override fun getMinWarmth(): Int = MIN

    override fun getMaxBrightness(): Int = when (OnyxDevice.brightnessType) {
        OnyxBrightnessType.FL -> OnyxDevice.getMaxLightValue(LIGHT_TYPE_FL) ?: FALLBACK_MAX
        OnyxBrightnessType.WARM_AND_COLD -> OnyxDevice.getMaxLightValue(LIGHT_TYPE_CTM_COLD) ?: FALLBACK_MAX
        OnyxBrightnessType.CTM -> OnyxDevice.getMaxLightValue(LIGHT_TYPE_CTM_BR) ?: FALLBACK_MAX
        OnyxBrightnessType.NONE -> FALLBACK_MAX
    }

    override fun getMaxWarmth(): Int = when (OnyxDevice.brightnessType) {
        OnyxBrightnessType.WARM_AND_COLD -> OnyxDevice.getMaxLightValue(LIGHT_TYPE_CTM_WARM) ?: FALLBACK_MAX
        OnyxBrightnessType.CTM -> OnyxDevice.getMaxLightValue(LIGHT_TYPE_TEMP) ?: FALLBACK_MAX
        else -> FALLBACK_MAX
    }

    override fun enableFrontlightSwitch(activity: Activity): Int = 1
}

// ─── Low-level SDK bridge ─────────────────────────────────────────────────────

object OnyxDevice {
    private const val TAG = "OnyxDevice"

    private val controller: Class<*>? = try {
        Class.forName("android.onyx.hardware.DeviceController")
    } catch (e: Exception) {
        Log.w(TAG, "DeviceController not found: $e"); null
    }

    // Integer getLightValue(int type)
    private val mGetLightValue: Method? = method("getLightValue", Integer.TYPE)
        ?: method("getLightValues", Integer.TYPE)

    // Integer getMaxLightValue(int type)
    private val mGetMaxLightValue: Method? = method("getMaxLightValue", Integer.TYPE)
        ?: method("getMaxLightValues", Integer.TYPE)

    // boolean setLightValue(int type, int value)
    private val mSetLightValue: Method? = method("setLightValue", Integer.TYPE, Integer.TYPE)
        ?: method("setLightValues", Integer.TYPE, Integer.TYPE)

    // boolean openFrontLight(int type)
    private val mOpenFrontLight: Method? = method("openFrontLight", Integer.TYPE)

    // boolean closeFrontLight(int type)
    private val mCloseFrontLight: Method? = method("closeFrontLight", Integer.TYPE)

    // boolean isLightOn(int type)
    private val mIsLightOn: Method? = method("isLightOn", Context::class.java, Integer.TYPE)
        ?: method("isLightOn", Integer.TYPE)

    // boolean hasFLBrightness(Context)
    private val mHasFLBrightness: Method? = method("hasFLBrightness", Context::class.java)

    // boolean hasCTMBrightness(Context)
    private val mHasCTMBrightness: Method? = method("hasCTMBrightness", Context::class.java)

    // boolean checkCTM()
    private val mCheckCTM: Method? = method("checkCTM")

    var brightnessType: OnyxBrightnessType = OnyxBrightnessType.NONE
        private set

    var isInitialized = false
        private set

    /**
     * Call once from Activity.onCreate.
     * Mirrors BrightnessController.initProviderMap() priority order:
     *   checkCTM()       → CTM           (open/close via type 4, read/write via types 6+7)
     *   hasCTMBrightness → WARM_AND_COLD (types 2+3)
     *   hasFLBrightness  → FL            (type 1)
     */
    fun init(context: Context) {
        // Allow re-initialization if we are currently in a state without warmth,
        // to handle cases where detection might be state-dependent (like "OFF" vs "Custom" mode).
        if (isInitialized && brightnessType != OnyxBrightnessType.NONE && brightnessType != OnyxBrightnessType.FL) return

        val checkCTM = mCheckCTM?.invoke(controller) as? Boolean ?: false
        val hasFL = mHasFLBrightness?.invoke(controller, context) as? Boolean ?: false
        val hasCTM = mHasCTMBrightness?.invoke(controller, context) as? Boolean ?: false

        // Check actual hardware channel availability as fallback/confirmation
        val maxCTM = getMaxLightValue(LIGHT_TYPE_TEMP) ?: 0
        val maxWarm = getMaxLightValue(LIGHT_TYPE_CTM_WARM) ?: 0

        val oldType = brightnessType
        brightnessType = when {
            checkCTM || maxCTM > 0 -> OnyxBrightnessType.CTM
            hasCTM || maxWarm > 0 -> OnyxBrightnessType.WARM_AND_COLD
            hasFL -> OnyxBrightnessType.FL
            else -> OnyxBrightnessType.NONE
        }

        if (brightnessType != OnyxBrightnessType.NONE) {
            isInitialized = true
        }

        if (oldType != brightnessType) {
            Log.d(
                TAG,
                "Detection: checkCTM=$checkCTM hasCTM=$hasCTM hasFL=$hasFL maxCTM=$maxCTM maxWarm=$maxWarm → $brightnessType"
            )
        }
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    fun getLightValue(context: Context, type: Int): Int? = mGetLightValue?.invoke(controller, type) as? Int
    fun getMaxLightValue(type: Int): Int? {
        val max = mGetMaxLightValue?.invoke(controller, type) as? Number
        return if (max == null || max.toInt() == 0) null else max.toInt()
    }

    fun isLightOn(context: Context, type: Int): Boolean = mIsLightOn?.let { method ->
        if (method.parameterTypes.size == 2) {
            method.invoke(controller, context, type)
        } else {
            method.invoke(controller, type)
        }
    } as? Boolean ?: false

    // ── Write ─────────────────────────────────────────────────────────────────

    fun setLight(context: Context, type: Int, value: Int): Boolean {
        // Map logical parameter type → physical channel type for open/close.
        // For CTM, type 4 (ALL) controls the master switch.
        // For WARM_AND_COLD, channels 2 and 3 are independent.
        val channelType = when (type) {
            LIGHT_TYPE_CTM_BR,
            LIGHT_TYPE_TEMP -> LIGHT_TYPE_CTM_ALL // CTM: always open/close the whole unit
            else -> type // FL / WARM / COLD: direct channel
        }

        return if (value == 0) {
            // Only close the master switch if it's CTM brightness (7) or FL brightness (1).
            // Closing warmth channel (2 or 6) shouldn't turn off all lights.
            val shouldClose = when (type) {
                LIGHT_TYPE_FL,
                LIGHT_TYPE_CTM_BR,
                LIGHT_TYPE_CTM_COLD,
                LIGHT_TYPE_CTM_WARM -> true

                else -> false
            }

            if (shouldClose) {
                val ok = mCloseFrontLight?.invoke(controller, channelType) as? Boolean ?: false
                Log.v(TAG, "closeFrontLight(channelType=$channelType) → $ok")
                ok
            } else {
                // For warmth, just set value to 0
                val ok = mSetLightValue?.invoke(controller, type, 0) as? Boolean ?: false
                Log.v(TAG, "setLightValue(type=$type, value=0) → $ok")
                ok
            }
        } else {
            if (!isLightOn(context, channelType)) {
                val opened = mOpenFrontLight?.invoke(controller, channelType) as? Boolean ?: false
                Log.v(TAG, "openFrontLight(channelType=$channelType) → $opened")
            }
            val ok = mSetLightValue?.invoke(controller, type, value) as? Boolean ?: false
            Log.v(TAG, "setLightValue(type=$type, value=$value) → $ok")
            ok
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun method(name: String, vararg params: Class<*>): Method? = try {
        controller?.getMethod(name, *params)
    } catch (e: Exception) {
        Log.w(TAG, "Method '$name' not found: $e"); null
    }
}
