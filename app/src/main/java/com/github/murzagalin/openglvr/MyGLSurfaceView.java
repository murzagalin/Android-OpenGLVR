package com.github.murzagalin.openglvr;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

/**
 * Created by Администратор on 09.01.2016.
 */

@SuppressLint("ViewConstructor")
public class MyGLSurfaceView extends GLSurfaceView {

    protected Resources mResources;
    private SphereVideoRenderer mRenderer;
    private MediaPlayer mMediaPlayer;
    private final float TOUCH_SCALE_FACTOR = 180.0f / 320 / 3.8f;
    private float mPreviousX;
    private float mPreviousY;


    public MyGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Create an OpenGL ES 2.0 context
        setEGLContextClientVersion(2);

        mMediaPlayer = new MediaPlayer();

        mResources = context.getResources();

        try {
            AssetFileDescriptor afd = mResources.openRawResourceFd(R.raw.videoplayback2);
            mMediaPlayer.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } catch (Exception e) {
            Log.e("123", e.getMessage(), e);
        }


        mRenderer = new SphereVideoRenderer(context, mMediaPlayer);
        // Set the Renderer for drawing on the GLSurfaceView
        setRenderer(mRenderer);
    }


    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // MotionEvent reports input details from the touch screen
        // and other input controls. In this case, you are only
        // interested in events where the touch position changed.

        float x = e.getX();
        float y = e.getY();

        switch (e.getAction()) {
            //when we move our finger
            case MotionEvent.ACTION_MOVE:
                // if we touch spherical part of screen or rectangular
                if (y < mRenderer.getScreenHeight() * (1 - 1.f / 5.f)) {
                    float dx = x - mPreviousX;
                    float dy = y - mPreviousY;


                    mRenderer.setAngleX(
                            mRenderer.getAngleX() +
                                    (dx * TOUCH_SCALE_FACTOR));
                    mRenderer.setAngleY(
                            mRenderer.getAngleY() +
                                    (dy * TOUCH_SCALE_FACTOR));
                    requestRender();
                } else {
                    mRenderer.setAngleX( -x / (float) mRenderer.getScreenWidth() * 360 - 90);
                    float relativeYCoordinate = y - mRenderer.getScreenHeight() * (1 - 1.f / 5.f);
                    mRenderer.setAngleY(90 - (180.f / (mRenderer.getScreenHeight() / 5.f))  * relativeYCoordinate);
                    requestRender();
                }
                break;

            //if we touch screen
            case MotionEvent.ACTION_DOWN :
                //here is only rectangular part of screen available
                if (y >= mRenderer.getScreenHeight() * (1 - 1.f / 5.f)) {
                    mRenderer.setAngleX(-x / (float) mRenderer.getScreenWidth() * 360 - 90);

                    float relativeYCoordinate = y - mRenderer.getScreenHeight() * (1 - 1.f / 5.f);
                    mRenderer.setAngleY(90 - (180.f / (mRenderer.getScreenHeight() / 5.f))  * relativeYCoordinate);
                    requestRender();
                }
                break;
        }

        mPreviousX = x;
        mPreviousY = y;
        return true;
    }

    @Override
    public void onResume() {
        queueEvent(new Runnable(){
            public void run() {
                mRenderer.setMediaPlayer(mMediaPlayer);
            }});
        super.onResume();
    }

}
