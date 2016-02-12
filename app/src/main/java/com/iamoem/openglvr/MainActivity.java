package com.iamoem.openglvr;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {

    private MyGLSurfaceView myGLSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        myGLSurfaceView = (MyGLSurfaceView) findViewById(R.id.glSurfaceView);
    }

    @Override
    protected void onResume() {
        super.onResume();
        myGLSurfaceView.onResume();
    }
}
