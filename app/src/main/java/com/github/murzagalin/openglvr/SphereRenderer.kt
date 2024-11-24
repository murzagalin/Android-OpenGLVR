package com.github.murzagalin.openglvr

import android.content.Context
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.media.MediaPlayer
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin


class SphereRenderer(private val context: Context, private var mediaPlayer: MediaPlayer) :
    GLSurfaceView.Renderer, OnFrameAvailableListener {
    private val vertexShaderCode =
        """
        attribute vec4 vPosition;attribute vec4 a_TexCoordinate;uniform mat4 u_Matrix;uniform mat4 uSTMatrix;varying vec2 v_TexCoordinate; void main() {  gl_Position = u_Matrix * vPosition;  v_TexCoordinate = (uSTMatrix * a_TexCoordinate).xy;
        }
        """.trimIndent()

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;uniform samplerExternalOES sTexture;
        varying vec2 v_TexCoordinate;void main() {  gl_FragColor =  texture2D(sTexture, v_TexCoordinate);}
        """.trimIndent()


    private var mSurface: SurfaceTexture? = null
    private var updateSurface = false

    private var left = 0f
    private var right = 0f
    private var bottom = 0f
    private var top = 0f

    private var mHandleMatrix = 0

    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)
    private val mProjectionViewMatrix = FloatArray(16)

    private val mRotationMatrixX = FloatArray(16)
    private val mRotationMatrixY = FloatArray(16)
    private val mRotationMatrix = FloatArray(16)
    private val mScrtch = FloatArray(16)
    private val mSTMatrix = FloatArray(16)


    private val mTettaSteps = 10
    private val mPhiSteps = 18

    private val mSphereCoords = FloatArray(((mPhiSteps + 1) * (2 * (mTettaSteps + 1) - 2)) * 3)

    private val mR = 5f

    val sphereTextureCoordinateData: FloatArray =
        FloatArray(((mPhiSteps + 1) * (2 * (mTettaSteps + 1) - 2)) * 2)

    var mRectangleTexCoords: FloatArray = floatArrayOf(
        0f, 0f,
        0f, 1f,
        1f, 0f,
        1f, 1f
    )

    var mRectangleCoords: FloatArray = FloatArray(12)

    private var mPositionHandle = 0
    private var mTextureID = 0
    private var muSTMatrixHandle = 0

    private val COORDS_PER_VERTEX = 3

    private var vertexCount = 0
    private var vertexStride = 0 // 4 bytes per vertex

    private var mProgram = 0

    private lateinit var mVertexBuffer: FloatBuffer

    private lateinit var mRectangleTextureCoordinates: FloatBuffer
    private lateinit var mRectangleVertexBuffer: FloatBuffer


    /** Store our model data in a float buffer.  */
    private lateinit var mSphereTextureCoordinates: FloatBuffer

    /** This will be used to pass in the texture.  */
    private var mTextureUniformHandle = 0

    /** This will be used to pass in model texture coordinate information.  */
    private var mTextureCoordinateHandle = 0

    /** Size of the texture coordinate data in elements.  */
    private val mTextureCoordinateDataSize = 2

    var angleX: Float = 0f
    var angleY: Float = 0f
    var screenWidth: Int = 0
        private set
    var screenHeight: Int = 0
        private set

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // Set the background frame color

        GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )

        initSphereCoords()

        makeProgram()

        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")
        mHandleMatrix = GLES20.glGetUniformLocation(mProgram, "u_Matrix")

        mTextureUniformHandle = GLES20.glGetUniformLocation(mProgram, "u_Texture")
        mTextureCoordinateHandle = GLES20.glGetAttribLocation(mProgram, "a_TexCoordinate")

        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix")

        GLES20.glEnableVertexAttribArray(mTextureCoordinateHandle)
        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle)


        // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(mTextureUniformHandle, 0)

        mSphereTextureCoordinates!!.position(0)
        GLES20.glVertexAttribPointer(
            mTextureCoordinateHandle, mTextureCoordinateDataSize, GLES20.GL_FLOAT, false,
            0, mSphereTextureCoordinates
        )

        Matrix.setIdentityM(mSTMatrix, 0)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        mTextureID = textures[0]

        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )


        /*
        * Create the SurfaceTexture that will feed this textureID,
        * and pass it to the MediaPlayer
        */
        mSurface = SurfaceTexture(mTextureID)
        mSurface!!.setOnFrameAvailableListener(this)

        val surface = Surface(mSurface)
        mediaPlayer.setSurface(surface)
        mediaPlayer.setScreenOnWhilePlaying(true)
        surface.release()



        synchronized(this) {
            updateSurface = false
        }

        mediaPlayer.setOnPreparedListener { mp -> mp.start() }
        mediaPlayer.prepareAsync()
    }

    override fun onDrawFrame(unused: GL10) {
        // Redraw background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        synchronized(this) {
            if (updateSurface) {
                mSurface!!.updateTexImage()
                mSurface!!.getTransformMatrix(mSTMatrix)
                updateSurface = false
            }
        }

        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0)

        drawSphere()
        drawRectangle()
    }

    private fun drawSphere() {
        //sphere
        GLES20.glVertexAttribPointer(
            mTextureCoordinateHandle, mTextureCoordinateDataSize, GLES20.GL_FLOAT, false,
            0, mSphereTextureCoordinates
        )

        GLES20.glVertexAttribPointer(
            mPositionHandle, COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false,
            vertexStride, mVertexBuffer
        )

        Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, 1.2f, 5.0f)

        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)

        Matrix.multiplyMM(mProjectionViewMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)

        Matrix.setRotateM(mRotationMatrixX, 0, angleY, -1.0f, 0.0f, 0f)
        Matrix.setRotateM(mRotationMatrixY, 0, angleX, 0.0f, -1.0f, 0f)

        Matrix.multiplyMM(mRotationMatrix, 0, mRotationMatrixX, 0, mRotationMatrixY, 0)

        Matrix.multiplyMM(mScrtch, 0, mProjectionViewMatrix, 0, mRotationMatrix, 0)

        GLES20.glUniformMatrix4fv(mHandleMatrix, 1, false, mScrtch, 0)

        // Draw the sphere
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)
    }

    private fun drawRectangle() {
        //rectangle
        //projection matrixes for rectangle
        Matrix.orthoM(mProjectionMatrix, 0, left, right, bottom, top, 0.0f, 5.0f)
        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        Matrix.multiplyMM(mProjectionViewMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)

        GLES20.glVertexAttribPointer(
            mTextureCoordinateHandle, mTextureCoordinateDataSize, GLES20.GL_FLOAT, false,
            0, mRectangleTextureCoordinates
        )

        GLES20.glUniformMatrix4fv(mHandleMatrix, 1, false, mProjectionViewMatrix, 0)

        GLES20.glVertexAttribPointer(
            mPositionHandle, COORDS_PER_VERTEX,
            GLES20.GL_FLOAT, false,
            vertexStride, mRectangleVertexBuffer
        )

        //draw the rectangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        screenHeight = height
        screenWidth = width

        val ratio: Float
        left = -1.0f
        right = 1.0f
        bottom = -1.0f
        top = 1.0f
        if (width > height) {
            ratio = width.toFloat() / height
            left *= ratio
            right *= ratio
        } else {
            ratio = height.toFloat() / width
            bottom *= ratio
            top *= ratio
        }

        setRectangleCoords()
    }


    @Synchronized
    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        updateSurface = true
    }


    fun setMediaPlayer(mediaPlayer: MediaPlayer) {
        this.mediaPlayer = mediaPlayer
    }


    private fun makeProgram() {
        val vertexShader = loadShader(
            GLES20.GL_VERTEX_SHADER,
            vertexShaderCode
        )
        val fragmentShader = loadShader(
            GLES20.GL_FRAGMENT_SHADER,
            fragmentShaderCode
        )

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram()

        // add the vertex shader to program
        GLES20.glAttachShader(mProgram, vertexShader)

        // add the fragment shader to program
        GLES20.glAttachShader(mProgram, fragmentShader)

        // creates OpenGL ES program executables
        GLES20.glLinkProgram(mProgram)

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram)
    }

    //set the rectangle coordinates and buffers to draw it
    private fun setRectangleCoords() {
        mRectangleCoords[0] = left
        mRectangleCoords[1] = bottom
        mRectangleCoords[2] = -0.0f
        mRectangleCoords[3] = left
        mRectangleCoords[4] = bottom + (top - bottom) / 5
        mRectangleCoords[5] = -0.0f
        mRectangleCoords[6] = right
        mRectangleCoords[7] = bottom
        mRectangleCoords[8] = -0.0f
        mRectangleCoords[9] = right
        mRectangleCoords[10] = bottom + (top - bottom) / 5
        mRectangleCoords[11] = -0.0f

        //buffers for rectangle
        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        val textureByteBuffer = ByteBuffer.allocateDirect(mRectangleTexCoords.size * 4)
        textureByteBuffer.order(ByteOrder.nativeOrder())

        // allocates the memory from the byte buffer
        mRectangleTextureCoordinates = textureByteBuffer.asFloatBuffer()

        // fill the vertexBuffer with the vertices
        mRectangleTextureCoordinates.put(mRectangleTexCoords)

        // set the cursor position to the beginning of the buffer
        mRectangleTextureCoordinates.position(0)

        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        val vertexByteBuffer = ByteBuffer.allocateDirect(mRectangleCoords.size * 4)
        vertexByteBuffer.order(ByteOrder.nativeOrder())

        // allocates the memory from the byte buffer
        mRectangleVertexBuffer = vertexByteBuffer.asFloatBuffer()

        // fill the vertexBuffer with the vertices
        mRectangleVertexBuffer.put(mRectangleCoords)

        // set the cursor position to the beginning of the buffer
        mRectangleVertexBuffer.position(0)
    }

    /**
     * inits sphere coordinates and buffers to draw this sphere
     */
    private fun initSphereCoords() {
        val phiStep = 2 * Math.PI / mPhiSteps
        val tettaStep = Math.PI / mTettaSteps

        var spherePointCounter = 0
        var texturePointCounter = 0


        var tetta = 0.0
        while (tetta <= (Math.PI - tettaStep) + tettaStep / 2) {
            var phi = 0.0
            while (phi <= 2 * Math.PI + phiStep / 2) {
                mSphereCoords[spherePointCounter++] = (mR * sin(tetta) * cos(phi)).toFloat()
                mSphereCoords[spherePointCounter++] = (mR * cos(tetta)).toFloat()
                mSphereCoords[spherePointCounter++] = (mR * sin(tetta) * sin(phi)).toFloat()

                sphereTextureCoordinateData[texturePointCounter++] = (phi / (2 * Math.PI)).toFloat()
                sphereTextureCoordinateData[texturePointCounter++] =
                    1f - (tetta / (Math.PI)).toFloat()

                mSphereCoords[spherePointCounter++] =
                    (mR * sin(tetta + tettaStep) * cos(phi)).toFloat()
                mSphereCoords[spherePointCounter++] = (mR * cos(tetta + tettaStep)).toFloat()
                mSphereCoords[spherePointCounter++] =
                    (mR * sin(tetta + tettaStep) * sin(phi)).toFloat()

                sphereTextureCoordinateData[texturePointCounter++] = (phi / (2 * Math.PI)).toFloat()
                sphereTextureCoordinateData[texturePointCounter++] =
                    1f - ((tetta + tettaStep) / (Math.PI)).toFloat()
                phi += phiStep
            }
            tetta += tettaStep
        }

        vertexCount = mSphereCoords.size / COORDS_PER_VERTEX
        vertexStride = COORDS_PER_VERTEX * 4 // 4 bytes per vertex

        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        val vertexByteBuffer = ByteBuffer.allocateDirect(mSphereCoords.size * 4)
        vertexByteBuffer.order(ByteOrder.nativeOrder())

        // allocates the memory from the byte buffer
        mVertexBuffer = vertexByteBuffer.asFloatBuffer()

        // fill the vertexBuffer with the vertices
        mVertexBuffer.put(mSphereCoords)

        // set the cursor position to the beginning of the buffer
        mVertexBuffer.position(0)


        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        val textureByteBuffer = ByteBuffer.allocateDirect(sphereTextureCoordinateData.size * 4)
        textureByteBuffer.order(ByteOrder.nativeOrder())

        // allocates the memory from the byte buffer
        mSphereTextureCoordinates = textureByteBuffer.asFloatBuffer()

        // fill the vertexBuffer with the vertices
        mSphereTextureCoordinates.put(sphereTextureCoordinateData)

        // set the cursor position to the beginning of the buffer
        mSphereTextureCoordinates.position(0)
    }

    private fun checkGlError(op: String) {
        var error: Int
        while ((GLES20.glGetError().also { error = it }) != GLES20.GL_NO_ERROR) {
            Log.e("error", "$op: glError $error")
            throw RuntimeException("$op: glError $error")
        }
    }

    companion object {
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65


        fun loadShader(type: Int, shaderCode: String?): Int {
            // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
            // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)

            val shader = GLES20.glCreateShader(type)

            // add the source code to the shader and compile it
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            val ErrorLog = GLES20.glGetShaderInfoLog(shader)


            val errorCode = GLES20.glGetError()


            return shader
        }
    }
}

