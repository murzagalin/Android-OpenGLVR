package com.github.murzagalin.openglvr

import android.content.Context
import android.media.MediaPlayer
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent


class VrGlSurfaceView(context: Context, attrs: AttributeSet?) : GLSurfaceView(context, attrs) {

    companion object {
        private const val TAG = "VrGlSurfaceView"
        private const val TOUCH_SCALE_FACTOR = 180.0f / 320 / 3.8f
        private const val SPHERE_VIEW_SCREEN_RATIO = 1f - SphereRenderer.RECT_VIEW_SCREEN_PERCENTAGE / 100f
    }

    private val renderer: SphereRenderer
    private val mediaPlayer: MediaPlayer
    private var previousX = 0f
    private var previousY = 0f


    init {
        setEGLContextClientVersion(2)
        mediaPlayer = MediaPlayer()

        try {
            val videoFile = context.resources.openRawResourceFd(R.raw.videoplayback2)
            mediaPlayer.setDataSource(
                videoFile.fileDescriptor,
                videoFile.startOffset,
                videoFile.length
            )
            videoFile.close()
        } catch (e: Exception) {
            Log.e(TAG, e.message, e)
        }

        renderer = SphereRenderer(mediaPlayer)
        setRenderer(renderer)
    }


    override fun onTouchEvent(e: MotionEvent): Boolean {
        // MotionEvent reports input details from the touch screen
        // and other input controls. In this case, you are only
        // interested in events where the touch position changed.
        val x = e.x
        val y = e.y

        when (e.action) {
            MotionEvent.ACTION_MOVE -> // if we touch spherical part of screen or rectangular
                if (y < renderer.screenHeight * SPHERE_VIEW_SCREEN_RATIO) {
                    val dx = x - previousX
                    val dy = y - previousY

                    renderer.angleX += dx * TOUCH_SCALE_FACTOR
                    renderer.angleY += dy * TOUCH_SCALE_FACTOR
                    requestRender()
                } else {
                    renderer.angleX = -x / renderer.screenWidth.toFloat() * 360 - 90
                    val relativeYCoordinate = y - renderer.screenHeight * SPHERE_VIEW_SCREEN_RATIO
                    renderer.angleY =
                        90 - (180f / (renderer.screenHeight / 5f)) * relativeYCoordinate
                    requestRender()
                }

            MotionEvent.ACTION_DOWN -> //here is only rectangular part of screen available
                if (y >= renderer.screenHeight * SPHERE_VIEW_SCREEN_RATIO) {
                    renderer.angleX = -x / renderer.screenWidth.toFloat() * 360 - 90
                    val relativeYCoordinate = y - renderer.screenHeight * SPHERE_VIEW_SCREEN_RATIO
                    renderer.angleY = 90 - relativeYCoordinate * SphereRenderer.RECT_SCREEN_SPLIT * 180f / renderer.screenHeight
                    requestRender()
                }
        }

        previousX = x
        previousY = y
        return true
    }

    override fun onResume() {
        queueEvent { renderer.setMediaPlayer(mediaPlayer) }
        super.onResume()
    }
}
