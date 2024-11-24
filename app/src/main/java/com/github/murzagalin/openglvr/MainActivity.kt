package com.github.murzagalin.openglvr

import android.app.Activity
import android.os.Bundle
import android.view.View


class MainActivity : Activity() {

    private lateinit var myGLSurfaceView: VrGlSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        myGLSurfaceView = findViewById<View>(R.id.glSurfaceView) as VrGlSurfaceView
    }

    override fun onResume() {
        super.onResume()
        myGLSurfaceView.onResume()
    }
}
