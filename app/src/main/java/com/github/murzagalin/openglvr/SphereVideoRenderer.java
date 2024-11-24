package com.github.murzagalin.openglvr;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by Администратор on 09.01.2016.
 */
public class SphereVideoRenderer implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {



    private final String vertexShaderCode =
            "attribute vec4 vPosition;" +
                    "attribute vec4 a_TexCoordinate;" +
                    "uniform mat4 u_Matrix;" +
                    "uniform mat4 uSTMatrix;" +
                    "varying vec2 v_TexCoordinate; "+
                    "void main() {" +
                    "  gl_Position = u_Matrix * vPosition;" +
                    "  v_TexCoordinate = (uSTMatrix * a_TexCoordinate).xy;\n" +
                    "}";

    private final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "varying vec2 v_TexCoordinate;" +
                    "void main() {" +
                    "  gl_FragColor =  texture2D(sTexture, v_TexCoordinate);" +
                    "}";


    private SurfaceTexture mSurface;
    private boolean updateSurface = false;

    private MediaPlayer mMediaPlayer;

    private float left, right, bottom, top;

    private int mHandleMatrix;

    private float[] mProjectionMatrix = new float[16];
    private float[] mViewMatrix = new float[16];
    private float[] mProjectionViewMatrix = new float[16];

    private float[] mRotationMatrixX = new float[16];
    private float[] mRotationMatrixY = new float[16];
    private float[] mRotationMatrix = new float[16];
    private float[] mScrtch = new float[16];
    private float[] mSTMatrix = new float[16];


    private int mTettaSteps = 10;
    private int mPhiSteps = 18;

    private float[] mSphereCoords = new float[((mPhiSteps + 1) * (2 * (mTettaSteps + 1) - 2)) * 3];

    private float mR = 5;

    final float[] sphereTextureCoordinateData = new float[((mPhiSteps + 1) * (2 * (mTettaSteps + 1) - 2)) * 2];

    float[] mRectangleTexCoords = {
            0, 0,
            0, 1,
            1, 0,
            1, 1
    };

    float[] mRectangleCoords = new float[12];

    private int mPositionHandle;
    private int mTextureID;
    private int muSTMatrixHandle;

    private int COORDS_PER_VERTEX = 3;

    private int vertexCount;
    private int vertexStride; // 4 bytes per vertex

    private int mProgram;

    private FloatBuffer mVertexBuffer;

    private FloatBuffer mRectangleTextureCoordinates;
    private FloatBuffer mRectangleVertexBuffer;


    /** Store our model data in a float buffer. */
    private FloatBuffer mSphereTextureCoordinates = null;

    /** This will be used to pass in the texture. */
    private int mTextureUniformHandle;

    /** This will be used to pass in model texture coordinate information. */
    private int mTextureCoordinateHandle;

    /** Size of the texture coordinate data in elements. */
    private final int mTextureCoordinateDataSize = 2;

    private Context mContext;
    private float angleX;
    private float angleY;
    private int mScreenWidth;
    private int mScreenHeight;

    private static int GL_TEXTURE_EXTERNAL_OES = 0x8D65;


    public int getScreenWidth() {
        return mScreenWidth;
    }

    public int getScreenHeight() {
        return mScreenHeight;
    }

    public float getAngleX() {
        return angleX;
    }

    public void setAngleX(float angleX) {
        this.angleX = angleX;
    }

    public float getAngleY() {
        return angleY;
    }

    public void setAngleY(float angleY) {
        this.angleY = angleY;
    }

    public SphereVideoRenderer(Context context, MediaPlayer mediaPlayer) {
        mContext = context;
        mMediaPlayer = mediaPlayer;
    }

    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background frame color

        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);

        initSphereCoords();

        makeProgram();

        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition");
        mHandleMatrix = GLES20.glGetUniformLocation(mProgram, "u_Matrix");

        mTextureUniformHandle = GLES20.glGetUniformLocation(mProgram, "u_Texture");
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "a_TexCoordinate");

        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle);
        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle);



        // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 0);

        mSphereTextureCoordinates.position(0);
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, mTextureCoordinateDataSize, GLES20.GL_FLOAT, false,
                0, mSphereTextureCoordinates);

        Matrix.setIdentityM(mSTMatrix, 0);

        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        mTextureID = textures[0];

        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);


        /*
        * Create the SurfaceTexture that will feed this textureID,
        * and pass it to the MediaPlayer
        */
        mSurface = new SurfaceTexture(mTextureID);
        mSurface.setOnFrameAvailableListener(this);

        Surface surface = new Surface(mSurface);
        mMediaPlayer.setSurface(surface);
        mMediaPlayer.setScreenOnWhilePlaying(true);
        surface.release();



        synchronized(this) {
            updateSurface = false;
        }

        mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                mp.start();
            }
        });
        mMediaPlayer.prepareAsync();

    }

    public void onDrawFrame(GL10 unused) {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        synchronized(this) {
            if (updateSurface) {
                mSurface.updateTexImage();
                mSurface.getTransformMatrix(mSTMatrix);
                updateSurface = false;
            }
        }

        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

        drawSphere();
        drawRectangle();
    }

    private void drawSphere() {
        //sphere
        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, mTextureCoordinateDataSize, GLES20.GL_FLOAT, false,
                0, mSphereTextureCoordinates);

        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, mVertexBuffer);

        Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, 1.2f, 5.0f);

        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, 0, 0, -1, 0, 1, 0);

        Matrix.multiplyMM(mProjectionViewMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        Matrix.setRotateM(mRotationMatrixX, 0, angleY, -1.0f, 0.0f, 0);
        Matrix.setRotateM(mRotationMatrixY, 0, angleX, 0.0f, -1.0f, 0);

        Matrix.multiplyMM(mRotationMatrix, 0, mRotationMatrixX, 0, mRotationMatrixY, 0);

        Matrix.multiplyMM(mScrtch, 0, mProjectionViewMatrix, 0, mRotationMatrix, 0);

        GLES20.glUniformMatrix4fv(mHandleMatrix, 1, false, mScrtch, 0);

        // Draw the sphere
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount);
    }

    private void drawRectangle() {
        //rectangle
        //projection matrixes for rectangle
        Matrix.orthoM(mProjectionMatrix, 0, left, right, bottom, top, 0.0f, 5.0f);
        Matrix.setLookAtM(mViewMatrix, 0, 0, 0, 0, 0, 0, -1, 0, 1, 0);
        Matrix.multiplyMM(mProjectionViewMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0);

        GLES20.glVertexAttribPointer(mTextureCoordinateHandle, mTextureCoordinateDataSize, GLES20.GL_FLOAT, false,
                0, mRectangleTextureCoordinates);

        GLES20.glUniformMatrix4fv(mHandleMatrix, 1, false, mProjectionViewMatrix, 0);

        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX,
                GLES20.GL_FLOAT, false,
                vertexStride, mRectangleVertexBuffer);

        //draw the rectangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    public void onSurfaceChanged(GL10 unused, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        mScreenHeight = height;
        mScreenWidth = width;

        float ratio;
        left = -1.0f;
        right = 1.0f;
        bottom = -1.0f;
        top = 1.0f;
        if (width > height) {
            ratio = (float) width / height;
            left *= ratio;
            right *= ratio;
        } else {
            ratio = (float) height / width;
            bottom *= ratio;
            top *= ratio;
        }

        setRectangleCoords();
    }


    @Override
    synchronized public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        updateSurface = true;
    }


    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mMediaPlayer = mediaPlayer;
    }


    private void makeProgram() {

        int vertexShader = SphereVideoRenderer.loadShader(GLES20.GL_VERTEX_SHADER,
                vertexShaderCode);
        int fragmentShader = SphereVideoRenderer.loadShader(GLES20.GL_FRAGMENT_SHADER,
                fragmentShaderCode);

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram();

        // add the vertex shader to program
        GLES20.glAttachShader(mProgram, vertexShader);

        // add the fragment shader to program
        GLES20.glAttachShader(mProgram, fragmentShader);

        // creates OpenGL ES program executables
        GLES20.glLinkProgram(mProgram);

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram);

    }

    public static int loadShader(int type, String shaderCode){

        // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
        // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
        int shader = GLES20.glCreateShader(type);

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, shaderCode);
        GLES20.glCompileShader(shader);

        final String ErrorLog = GLES20.glGetShaderInfoLog(shader);


        int errorCode = GLES20.glGetError();


        return shader;
    }


    //set the rectangle coordinates and buffers to draw it
    private void setRectangleCoords() {
        mRectangleCoords[0] = left;
        mRectangleCoords[1] = bottom;
        mRectangleCoords[2] = -0.0f;
        mRectangleCoords[3] = left;
        mRectangleCoords[4] = bottom + (top - bottom) / 5;
        mRectangleCoords[5] = -0.0f;
        mRectangleCoords[6] = right;
        mRectangleCoords[7] = bottom;
        mRectangleCoords[8] = -0.0f;
        mRectangleCoords[9] = right;
        mRectangleCoords[10] = bottom + (top - bottom) / 5;
        mRectangleCoords[11] = -0.0f;

        //buffers for rectangle
        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        ByteBuffer textureByteBuffer = ByteBuffer.allocateDirect(mRectangleTexCoords.length * 4);
        textureByteBuffer.order(ByteOrder.nativeOrder());

        // allocates the memory from the byte buffer
        mRectangleTextureCoordinates = textureByteBuffer.asFloatBuffer();

        // fill the vertexBuffer with the vertices
        mRectangleTextureCoordinates.put(mRectangleTexCoords);

        // set the cursor position to the beginning of the buffer
        mRectangleTextureCoordinates.position(0);

        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        ByteBuffer vertexByteBuffer = ByteBuffer.allocateDirect(mRectangleCoords.length * 4);
        vertexByteBuffer.order(ByteOrder.nativeOrder());

        // allocates the memory from the byte buffer
        mRectangleVertexBuffer = vertexByteBuffer.asFloatBuffer();

        // fill the vertexBuffer with the vertices
        mRectangleVertexBuffer.put(mRectangleCoords);

        // set the cursor position to the beginning of the buffer
        mRectangleVertexBuffer.position(0);

    }

    /**
     * inits sphere coordinates and buffers to draw this sphere
     */
    private void initSphereCoords() {

        double phiStep = 2 * Math.PI / mPhiSteps;
        double tettaStep = Math.PI / mTettaSteps;

        int spherePointCounter = 0;
        int texturePointCounter = 0;


        for (double tetta = 0; tetta <= (Math.PI - tettaStep) + tettaStep / 2 ; tetta += tettaStep) {
            for(double phi = 0; phi <= 2 * Math.PI + phiStep / 2; phi += phiStep) {

                mSphereCoords[spherePointCounter++] = (float)(mR * Math.sin(tetta) * Math.cos(phi));
                mSphereCoords[spherePointCounter++] = (float)(mR * Math.cos(tetta));
                mSphereCoords[spherePointCounter++] = (float)(mR * Math.sin(tetta) * Math.sin(phi));

                sphereTextureCoordinateData[texturePointCounter++] = (float) (phi / (2 * Math.PI));
                sphereTextureCoordinateData[texturePointCounter++] = 1.f - (float) (tetta / (Math.PI));

                mSphereCoords[spherePointCounter++] = (float)(mR * Math.sin(tetta + tettaStep) * Math.cos(phi));
                mSphereCoords[spherePointCounter++] = (float)(mR * Math.cos(tetta + tettaStep));
                mSphereCoords[spherePointCounter++] = (float)(mR * Math.sin(tetta + tettaStep) * Math.sin(phi));

                sphereTextureCoordinateData[texturePointCounter++] = (float) (phi / (2 * Math.PI));
                sphereTextureCoordinateData[texturePointCounter++] = 1.f - (float) ((tetta + tettaStep) / (Math.PI));
            }
        }

        vertexCount = mSphereCoords.length / COORDS_PER_VERTEX;
        vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        ByteBuffer vertexByteBuffer = ByteBuffer.allocateDirect(mSphereCoords.length * 4);
        vertexByteBuffer.order(ByteOrder.nativeOrder());

        // allocates the memory from the byte buffer
        mVertexBuffer = vertexByteBuffer.asFloatBuffer();

        // fill the vertexBuffer with the vertices
        mVertexBuffer.put(mSphereCoords);

        // set the cursor position to the beginning of the buffer
        mVertexBuffer.position(0);


        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        ByteBuffer textureByteBuffer = ByteBuffer.allocateDirect(sphereTextureCoordinateData.length * 4);
        textureByteBuffer.order(ByteOrder.nativeOrder());

        // allocates the memory from the byte buffer
        mSphereTextureCoordinates = textureByteBuffer.asFloatBuffer();

        // fill the vertexBuffer with the vertices
        mSphereTextureCoordinates.put(sphereTextureCoordinateData);

        // set the cursor position to the beginning of the buffer
        mSphereTextureCoordinates.position(0);
    }

    private void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e("error", op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }
}

