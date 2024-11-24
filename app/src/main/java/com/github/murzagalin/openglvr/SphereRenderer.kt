package com.github.murzagalin.openglvr

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.media.MediaPlayer
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin


class SphereRenderer(private var mediaPlayer: MediaPlayer) :
    GLSurfaceView.Renderer, OnFrameAvailableListener {

    private val vertexShaderCode =
        """
        attribute vec4 vPosition;
        attribute vec4 a_TexCoordinate;
        uniform mat4 u_Matrix;
        uniform mat4 uSTMatrix;
        varying vec2 v_TexCoordinate; 
        
        void main() {  
            gl_Position = u_Matrix * vPosition;  
            v_TexCoordinate = (uSTMatrix * a_TexCoordinate).xy;
        }
        """.trimIndent()

    private val fragmentShaderCode = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;uniform samplerExternalOES sTexture;
        varying vec2 v_TexCoordinate;
        
        void main() {  
            gl_FragColor =  texture2D(sTexture, v_TexCoordinate);
        }
        """.trimIndent()


    private lateinit var surface: SurfaceTexture
    private var updateSurface = false

    private var left = 0f
    private var right = 0f
    private var bottom = 0f
    private var top = 0f

    private var handleMatrix = 0

    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionViewMatrix = FloatArray(16)

    private val rotationMatrixX = FloatArray(16)
    private val rotationMatrixY = FloatArray(16)
    private val rotationMatrix = FloatArray(16)
    private val scrtch = FloatArray(16)
    private val STMatrix = FloatArray(16)


    // How many steps are on the sphere for angles tetta and phi (in spherical coordinates)
    private val tettaSteps = 10
    private val phiSteps = 18

    // radius in spherical coordinates
    private val r = 5f

    private val sphereCoords = FloatArray((phiSteps + 1) * (2 * (tettaSteps + 1) - 2) * 3)

    private val sphereTextureCoordinateData: FloatArray =
        FloatArray(((phiSteps + 1) * (2 * (tettaSteps + 1) - 2)) * 2)

    private var rectangleTexCoords: FloatArray = floatArrayOf(
        0f, 0f,
        0f, 1f,
        1f, 0f,
        1f, 1f
    )

    private var rectangleCoords: FloatArray = FloatArray(12)

    private var positionHandle = 0
    private var textureID = 0
    private var STMatrixHandle = 0

    private val COORDS_PER_VERTEX = 3

    private var vertexCount = 0
    private var vertexStride = COORDS_PER_VERTEX * 4 // 4 bytes per vertex

    private var program = 0

    private lateinit var vertexBuffer: FloatBuffer

    private lateinit var rectangleTextureCoordinates: FloatBuffer
    private lateinit var rectangleVertexBuffer: FloatBuffer


    /** Store our model data in a float buffer.  */
    private lateinit var sphereTextureCoordinates: FloatBuffer

    /** This will be used to pass in the texture.  */
    private var textureUniformHandle = 0

    /** This will be used to pass in model texture coordinate information.  */
    private var textureCoordinateHandle = 0

    /** Size of the texture coordinate data in elements.  */
    private val textureCoordinateDataSize = 2

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
        positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        handleMatrix = GLES20.glGetUniformLocation(program, "u_Matrix")

        textureUniformHandle = GLES20.glGetUniformLocation(program, "u_Texture")
        textureCoordinateHandle = GLES20.glGetAttribLocation(program, "a_TexCoordinate")

        STMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")

        GLES20.glEnableVertexAttribArray(textureCoordinateHandle)
        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(positionHandle)


        // Tell the texture uniform sampler to use this texture in the shader by binding to texture unit 0.
        GLES20.glUniform1i(textureUniformHandle, 0)

        sphereTextureCoordinates.position(0)
        GLES20.glVertexAttribPointer(
            textureCoordinateHandle,
            textureCoordinateDataSize,
            GLES20.GL_FLOAT,
            false,
            0,
            sphereTextureCoordinates
        )

        Matrix.setIdentityM(STMatrix, 0)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)

        textureID = textures[0]

        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_NEAREST.toFloat()
        )
        GLES20.glTexParameterf(
            GL_TEXTURE_EXTERNAL_OES,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )

        /*
        * Create the SurfaceTexture that will feed this textureID,
        * and pass it to the MediaPlayer
        */
        surface = SurfaceTexture(textureID)
        surface.setOnFrameAvailableListener(this)

        val surface = Surface(surface)
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
                surface.updateTexImage()
                surface.getTransformMatrix(STMatrix)
                updateSurface = false
            }
        }

        GLES20.glUniformMatrix4fv(STMatrixHandle, 1, false, STMatrix, 0)

        drawSphere()
        drawRectangle()
    }

    private fun drawSphere() {
        //sphere
        GLES20.glVertexAttribPointer(
            textureCoordinateHandle,
            textureCoordinateDataSize,
            GLES20.GL_FLOAT,
            false,
            0,
            sphereTextureCoordinates
        )

        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            vertexStride,
            vertexBuffer
        )

        Matrix.frustumM(projectionMatrix, 0, left, right, bottom, top, 1.2f, 5.0f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        Matrix.multiplyMM(projectionViewMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.setRotateM(rotationMatrixX, 0, angleY, -1.0f, 0.0f, 0f)
        Matrix.setRotateM(rotationMatrixY, 0, angleX, 0.0f, -1.0f, 0f)
        Matrix.multiplyMM(rotationMatrix, 0, rotationMatrixX, 0, rotationMatrixY, 0)
        Matrix.multiplyMM(scrtch, 0, projectionViewMatrix, 0, rotationMatrix, 0)

        GLES20.glUniformMatrix4fv(handleMatrix, 1, false, scrtch, 0)

        // Draw the sphere
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertexCount)
    }

    private fun drawRectangle() {
        //rectangle
        //projection matrixes for rectangle
        Matrix.orthoM(projectionMatrix, 0, left, right, bottom, top, 0.0f, 5.0f)
        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 0f, 0f, 0f, -1f, 0f, 1f, 0f)
        Matrix.multiplyMM(projectionViewMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

        GLES20.glVertexAttribPointer(
            textureCoordinateHandle,
            textureCoordinateDataSize,
            GLES20.GL_FLOAT,
            false,
            0,
            rectangleTextureCoordinates
        )

        GLES20.glUniformMatrix4fv(handleMatrix, 1, false, projectionViewMatrix, 0)

        GLES20.glVertexAttribPointer(
            positionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            vertexStride,
            rectangleVertexBuffer
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
        program = GLES20.glCreateProgram()

        // add the vertex shader to program
        GLES20.glAttachShader(program, vertexShader)

        // add the fragment shader to program
        GLES20.glAttachShader(program, fragmentShader)

        // creates OpenGL ES program executables
        GLES20.glLinkProgram(program)

        // Add program to OpenGL ES environment
        GLES20.glUseProgram(program)
    }

    //set the rectangle coordinates and buffers to draw it
    private fun setRectangleCoords() {
        rectangleCoords[0] = left
        rectangleCoords[1] = bottom
        rectangleCoords[2] = -0.0f
        rectangleCoords[3] = left
        rectangleCoords[4] = bottom + (top - bottom) / RECT_SCREEN_SPLIT
        rectangleCoords[5] = -0.0f
        rectangleCoords[6] = right
        rectangleCoords[7] = bottom
        rectangleCoords[8] = -0.0f
        rectangleCoords[9] = right
        rectangleCoords[10] = bottom + (top - bottom) / RECT_SCREEN_SPLIT
        rectangleCoords[11] = -0.0f

        //buffers for rectangle
        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        val textureByteBuffer = ByteBuffer.allocateDirect(rectangleTexCoords.size * 4)
        textureByteBuffer.order(ByteOrder.nativeOrder())

        // allocates the memory from the byte buffer
        rectangleTextureCoordinates = textureByteBuffer.asFloatBuffer()

        // fill the vertexBuffer with the vertices
        rectangleTextureCoordinates.put(rectangleTexCoords)

        // set the cursor position to the beginning of the buffer
        rectangleTextureCoordinates.position(0)

        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        val vertexByteBuffer = ByteBuffer.allocateDirect(rectangleCoords.size * 4)
        vertexByteBuffer.order(ByteOrder.nativeOrder())

        // allocates the memory from the byte buffer
        rectangleVertexBuffer = vertexByteBuffer.asFloatBuffer()

        // fill the vertexBuffer with the vertices
        rectangleVertexBuffer.put(rectangleCoords)

        // set the cursor position to the beginning of the buffer
        rectangleVertexBuffer.position(0)
    }

    /**
     * inits sphere coordinates and buffers to draw this sphere
     */
    private fun initSphereCoords() {
        val phiStep = 2 * Math.PI / phiSteps
        val tettaStep = Math.PI / tettaSteps

        var spherePointCounter = 0
        var texturePointCounter = 0


        var tetta = 0.0
        while (tetta <= (Math.PI - tettaStep) + tettaStep / 2) {
            var phi = 0.0
            while (phi <= 2 * Math.PI + phiStep / 2) {
                sphereCoords[spherePointCounter++] = (r * sin(tetta) * cos(phi)).toFloat()
                sphereCoords[spherePointCounter++] = (r * cos(tetta)).toFloat()
                sphereCoords[spherePointCounter++] = (r * sin(tetta) * sin(phi)).toFloat()

                sphereTextureCoordinateData[texturePointCounter++] = (phi / (2 * Math.PI)).toFloat()
                sphereTextureCoordinateData[texturePointCounter++] = 1f - (tetta / (Math.PI)).toFloat()

                sphereCoords[spherePointCounter++] = (r * sin(tetta + tettaStep) * cos(phi)).toFloat()
                sphereCoords[spherePointCounter++] = (r * cos(tetta + tettaStep)).toFloat()
                sphereCoords[spherePointCounter++] = (r * sin(tetta + tettaStep) * sin(phi)).toFloat()

                sphereTextureCoordinateData[texturePointCounter++] = (phi / (2 * Math.PI)).toFloat()
                sphereTextureCoordinateData[texturePointCounter++] = 1f - ((tetta + tettaStep) / (Math.PI)).toFloat()
                phi += phiStep
            }
            tetta += tettaStep
        }

        vertexCount = sphereCoords.size / COORDS_PER_VERTEX

        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        val vertexByteBuffer = ByteBuffer.allocateDirect(sphereCoords.size * 4)
        vertexByteBuffer.order(ByteOrder.nativeOrder())

        // allocates the memory from the byte buffer
        vertexBuffer = vertexByteBuffer.asFloatBuffer()

        // fill the vertexBuffer with the vertices
        vertexBuffer.put(sphereCoords)

        // set the cursor position to the beginning of the buffer
        vertexBuffer.position(0)


        // a float has 4 bytes so we allocate for each coordinate 4 bytes
        val textureByteBuffer = ByteBuffer.allocateDirect(sphereTextureCoordinateData.size * 4)
        textureByteBuffer.order(ByteOrder.nativeOrder())

        // allocates the memory from the byte buffer
        sphereTextureCoordinates = textureByteBuffer.asFloatBuffer()

        // fill the vertexBuffer with the vertices
        sphereTextureCoordinates.put(sphereTextureCoordinateData)

        // set the cursor position to the beginning of the buffer
        sphereTextureCoordinates.position(0)
    }

    companion object {
        private const val GL_TEXTURE_EXTERNAL_OES = 0x8D65

        const val RECT_VIEW_SCREEN_PERCENTAGE = 20
        const val RECT_SCREEN_SPLIT = 100 / RECT_VIEW_SCREEN_PERCENTAGE


        fun loadShader(type: Int, shaderCode: String?): Int {
            // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
            // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
            val shader = GLES20.glCreateShader(type)

            // add the source code to the shader and compile it
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            val errorLog = GLES20.glGetShaderInfoLog(shader)


            val errorCode = GLES20.glGetError()


            return shader
        }
    }
}

