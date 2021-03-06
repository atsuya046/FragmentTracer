package com.atsuya046.fragmenttracer

import android.util.Log
import androidx.core.app.FrameMetricsAggregator
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.logging.AndroidLogger
import com.google.firebase.perf.metrics.Trace
import java.util.*

/**
* Firebase performance monitoring auto trace for Fragment
*/
class FragmentPerformanceTracer {
    private val logger = AndroidLogger.getInstance()

    private var callbacks: FragmentManager.FragmentLifecycleCallbacks? = null
    private val fragmentAggregatorMap = WeakHashMap<Fragment, FrameMetricsAggregator>()
    private val fragmentTraceMap = WeakHashMap<Fragment, Trace>()

    companion object {
        /** フレーム数 */
        private const val TOTAL_FRAME_COUNT = "total_frame_count"
        /** 遅いレンダリングだったフレーム数 */
        private const val SLOW_RENDERING_COUNT = "slow_rendering_count"
        /** 遅いレンダリングだったフレームの割合(%) */
        private const val SLOW_RENDERING_RATIO = "slow_rendering_ratio"
        /** フリーズしたフレームだったフレーム数 */
        private const val FROZEN_FRAME_COUNT = "frozen_frame_count"
        /** フリーズしたフレームだったフレームの割合(%) */
        private const val FROZEN_FRAME_RATIO = "frozen_frame_ratio"
    }

    fun start(fragmentManager: FragmentManager) {
        callbacks = createFragmentLifecycleCallbacks()
        fragmentManager.registerFragmentLifecycleCallbacks(callbacks!!, true)
    }

    fun stop(fragmentManager: FragmentManager) {
        callbacks?.let {
            fragmentManager.unregisterFragmentLifecycleCallbacks(it)
            callbacks = null
        }

        fragmentAggregatorMap.clear()
        fragmentTraceMap.clear()
    }

    private fun createFragmentLifecycleCallbacks(): FragmentManager.FragmentLifecycleCallbacks {
        return object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
                try {
                    super.onFragmentStarted(fm, f)
                    val frameMetricsAggregator = FrameMetricsAggregator().apply {
                        add(f.requireActivity())
                    }
                    val trace = FirebasePerformance.startTrace(f.javaClass.simpleName)
                    fragmentAggregatorMap[f] = frameMetricsAggregator
                    fragmentTraceMap[f] = trace
                } catch (e: Exception) {
                    logger.error("FirebasePerformance", e)
                }
            }

            override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
                super.onFragmentStopped(fm, f)

                if (!fragmentTraceMap.containsKey(f) && !fragmentAggregatorMap.containsKey(f)) {
                    return
                }

                val trace = fragmentTraceMap[f]
                fragmentTraceMap.remove(f)

                val frameMetricsAggregator = fragmentAggregatorMap[f]
                fragmentAggregatorMap.remove(f)

                try {
                    val performanceTag = f.javaClass.simpleName

                    var totalFrameCount = 0
                    var slowRenderingCount = 0
                    var frozenFrameCount = 0

                    val collectedMetrics = frameMetricsAggregator?.remove(f.requireActivity())
                    collectedMetrics?.get(0)?.let { totalDuration ->
                        for (i in 0 until totalDuration.size()) {
                            val frameDuration = totalDuration.keyAt(i)
                            val sampleNum = totalDuration.valueAt(i)
                            totalFrameCount += sampleNum
                            if (frameDuration > 700) {
                                frozenFrameCount += sampleNum
                            }

                            if (frameDuration > 16) {
                                slowRenderingCount += sampleNum
                            }
                        }
                    }

                    // フレーム数
                    if (totalFrameCount > 0) {
                        trace?.incrementMetric(TOTAL_FRAME_COUNT, totalFrameCount.toLong())
                    }

                    // 遅いレンダリングだったフレーム数
                    if (slowRenderingCount > 0) {
                        trace?.incrementMetric(SLOW_RENDERING_COUNT, slowRenderingCount.toLong())
                    }

                    // 遅いレンダリングだったフレームの割合
                    if (totalFrameCount > 0 && slowRenderingCount > 0) {
                        val slowRenderingRatio: Float = slowRenderingCount.toFloat() / totalFrameCount.toFloat() * 100
                        trace?.incrementMetric(SLOW_RENDERING_RATIO, slowRenderingRatio.toLong())
                    }

                    // フリーズしたフレーム数
                    if (frozenFrameCount > 0) {
                        trace?.incrementMetric(FROZEN_FRAME_COUNT, frozenFrameCount.toLong())
                    }

                    // フリーズしたフレームだったフレームの割合
                    if (totalFrameCount > 0 && frozenFrameCount > 0) {
                        val frozenFrameRatio: Float = frozenFrameCount.toFloat() / totalFrameCount.toFloat() * 100
                        trace?.incrementMetric(FROZEN_FRAME_RATIO, frozenFrameRatio.toLong())
                    }

                    logger.info("FirebasePerformance",
                        (StringBuilder(81 + performanceTag.length))
                            .append("sendScreenTrace name:").append(performanceTag)
                            .append(" $TOTAL_FRAME_COUNT:").append(trace?.getLongMetric(TOTAL_FRAME_COUNT))
                            .append(" $SLOW_RENDERING_COUNT:").append(trace?.getLongMetric(SLOW_RENDERING_COUNT))
                            .append(" $SLOW_RENDERING_RATIO:").append(trace?.getLongMetric(SLOW_RENDERING_RATIO))
                            .append(" $FROZEN_FRAME_COUNT:").append(trace?.getLongMetric(FROZEN_FRAME_COUNT))
                            .append(" $FROZEN_FRAME_RATIO:").append(trace?.getLongMetric(FROZEN_FRAME_RATIO))
                            .toString()
                    )
                } catch (e: Exception) {
                    logger.error("FirebasePerformance", e)
                } finally {
                    trace?.stop()
                }
            }
        }
    }

}