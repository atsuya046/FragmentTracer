package com.atsuya046.fragmenttracer.navigation

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.NavHostFragment
import com.atsuya046.fragmenttracer.FragmentPerformanceTracer

fun Fragment.attachPerformanceTracer() {
    val fragmentTracer = FragmentPerformanceTracer()
    this.lifecycle.addObserver(object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) = when(event) {
            Lifecycle.Event.ON_CREATE -> fragmentTracer.start(childFragmentManager)
            Lifecycle.Event.ON_DESTROY -> fragmentTracer.stop(childFragmentManager)
            else -> {
                // nothing to do
            }
        }
    })
}

open class FragmentTraceableNavHostFragment: NavHostFragment() {
    init {
        this.attachPerformanceTracer()
    }
}
