package com.dualdex.emulator

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.SurfaceHolder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class EmulatorSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), GLSurfaceView.Renderer {

    private companion object {
        const val TAG = "DualDexEmu"
    }

    private val inputManager = InputManager()
    private val pixelBuffer: ByteBuffer = ByteBuffer.allocateDirect(512 * 512 * 4).order(ByteOrder.nativeOrder())
    private val frameMetadata = IntArray(4) // width, height, pitch, pixelFormat
    private var textureId: Int = 0
    private var isTextureAllocated = false
    private var lastAllocatedWidth = 0
    private var lastAllocatedHeight = 0

    @Volatile
    private var speedMultiplier: Int = 1

    @Volatile
    private var currentFilter: ShaderFilter = ShaderFilter.NEAREST
    private var activeProgram: Int = 0

    // Shader programs map
    private val programMap = mutableMapOf<ShaderFilter, Int>()

    private val vertexShaderCode = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """.trimIndent()

    private val nearestFragmentShader = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        void main() {
            gl_FragColor = texture2D(uTexture, vTexCoord);
        }
    """.trimIndent()

    private val sharpBilinearFragmentShader = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uTextureSize;
        void main() {
            vec2 texel = vTexCoord * uTextureSize;
            vec2 texelFloor = floor(texel);
            vec2 texelFract = fract(texel);
            // Sharp clamping factor
            vec2 sharpFract = clamp((texelFract - 0.5) * 4.0 + 0.5, 0.0, 1.0);
            vec2 coord = (texelFloor + sharpFract) / uTextureSize;
            gl_FragColor = texture2D(uTexture, coord);
        }
    """.trimIndent()

    private val lcdGridFragmentShader = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uTextureSize;
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec2 grid = fract(vTexCoord * uTextureSize);
            float border = 1.0;
            if (grid.x < 0.08 || grid.x > 0.92 || grid.y < 0.08 || grid.y > 0.92) {
                border = 0.82;
            }
            gl_FragColor = vec4(color.rgb * border, color.a);
        }
    """.trimIndent()

    private val crtScanlineFragmentShader = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uTexture;
        uniform vec2 uTextureSize;
        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            float scanline = sin(vTexCoord.y * 3.14159265 * 320.0);
            scanline = 0.88 + 0.12 * scanline * scanline;
            gl_FragColor = vec4(color.rgb * scanline, color.a);
        }
    """.trimIndent()

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer
    @Volatile private var stretchToFit: Boolean = false
    private var surfaceWidth: Int = 1920
    private var surfaceHeight: Int = 1080

    @Volatile private var isEmulating = false
    private var emuThread: Thread? = null

    fun startEmulation() {
        if (isEmulating) return
        isEmulating = true
        emuThread = Thread({
            val targetFps = 59.7275
            val baseIntervalNs = (1_000_000_000.0 / targetFps).toLong() // 16,742,706 ns
            var nextDeadlineNs = System.nanoTime()

            FastForwardDiagnostics.refreshFromSystemProperty()
            FastForwardDiagnostics.onEmulationLoopStart()

            while (isEmulating) {
                val speed = speedMultiplier.coerceIn(1, 8)
                val targetIntervalNs = (baseIntervalNs / speed).coerceAtLeast(1_000_000L)
                FastForwardDiagnostics.onSpeedChanged(speed)
                FastForwardDiagnostics.setTargetInterval(targetIntervalNs)
                FastForwardDiagnostics.onLoopIteration()

                // A throw here previously killed the loop silently, leaving a permanently black
                // screen with audio still playing and no diagnostic anywhere.
                try {
                    LibretroCoreCoordinator.defaultInstance.stepFrame()
                } catch (t: Throwable) {
                    Log.e(TAG, "stepFrame() failed; emulation loop is aborting", t)
                    break
                }

                nextDeadlineNs += targetIntervalNs
                val now = System.nanoTime()
                val sleepNs = nextDeadlineNs - now

                if (sleepNs > 0) {
                    val sleepMs = sleepNs / 1_000_000L
                    val sleepRemNs = (sleepNs % 1_000_000L).toInt()
                    if (sleepMs > 0 || sleepRemNs > 200_000) {
                        try {
                            Thread.sleep(sleepMs, sleepRemNs)
                        } catch (e: InterruptedException) {
                            break
                        }
                    }
                    FastForwardDiagnostics.noteWakeLateness(System.nanoTime() - nextDeadlineNs)
                } else {
                    FastForwardDiagnostics.onLeftOverDeadline(-sleepNs)
                    if (sleepNs < -targetIntervalNs * 3) {
                        // Fell behind deadline by more than 3 frames (e.g. hitch or pause) -> reset deadline
                        FastForwardDiagnostics.onDeadlineReset(-sleepNs / targetIntervalNs)
                        nextDeadlineNs = now
                    }
                }

                FastForwardDiagnostics.tick()
            }
        }, "DualDexEmuLoop").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun stopEmulation() {
        isEmulating = false
        emuThread?.interrupt()
        try {
            emuThread?.join(300)
        } catch (e: InterruptedException) {
            // ignore
        }
        if (emuThread?.isAlive == true) {
            Log.w(TAG, "emulation thread still alive 300ms after stop; it may keep stepping the core")
        }
        emuThread = null
    }

    /**
     * Pauses only the emulation stepping loop, leaving the GL render thread and its EGL context
     * alive. Used to suspend the core across a ROM switch.
     *
     * This must NOT be confused with [onPause]: GLSurfaceView.onPause() tears the render thread
     * down permanently and only GLSurfaceView.onResume() brings it back. Calling it for a ROM
     * switch (which does not pause the Activity) left the top screen black forever while audio
     * and emulation kept running.
     */
    fun pauseEmulationLoop() {
        stopEmulation()
    }

    fun resumeEmulationLoop() {
        startEmulation()
    }

    override fun onPause() {
        stopEmulation()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        startEmulation()
    }

    init {
        setEGLContextClientVersion(2)
        setRenderer(this)
        renderMode = RENDERMODE_CONTINUOUSLY
        isFocusable = true
        isFocusableInTouchMode = true

        // A destroyed surface with a still-running emulation loop is a silent black screen, and
        // GLSurfaceView exposes no public hook for it.
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {}
            override fun surfaceChanged(h: SurfaceHolder, format: Int, w: Int, ht: Int) {}
            override fun surfaceDestroyed(h: SurfaceHolder) {
                Log.w(TAG, "GL surface DESTROYED (emulation loop keeps running; no visuals until recreated)")
            }
        })

        // Initial 3:2 aspect ratio scaling
        val aspectScaleX = 0.84375f
        val quadVertices = floatArrayOf(
            -aspectScaleX, -1.0f,
             aspectScaleX, -1.0f,
            -aspectScaleX,  1.0f,
             aspectScaleX,  1.0f
        )
        vertexBuffer = ByteBuffer.allocateDirect(quadVertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(quadVertices)
                position(0)
            }

        val texCoords = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(texCoords)
                position(0)
            }
    }

    fun setStretchToFit(stretch: Boolean) {
        stretchToFit = stretch
        queueEvent {
            updateVertices()
        }
    }

    fun isStretchToFit(): Boolean = stretchToFit

    private fun updateVertices() {
        val quadVertices = if (stretchToFit) {
            // Stretch edge-to-edge (no letterbox/pillarbox)
            floatArrayOf(
                -1.0f, -1.0f,
                 1.0f, -1.0f,
                -1.0f,  1.0f,
                 1.0f,  1.0f
            )
        } else {
            // Authentic 3:2 GBA aspect ratio (240x160 = 1.5)
            val gbaAspect = 1.5f
            val screenAspect = if (surfaceHeight > 0) surfaceWidth.toFloat() / surfaceHeight.toFloat() else (16f / 9f)
            val scaleX: Float
            val scaleY: Float
            if (screenAspect > gbaAspect) {
                scaleX = gbaAspect / screenAspect
                scaleY = 1.0f
            } else {
                scaleX = 1.0f
                scaleY = screenAspect / gbaAspect
            }
            floatArrayOf(
                -scaleX, -scaleY,
                 scaleX, -scaleY,
                -scaleX,  scaleY,
                 scaleX,  scaleY
            )
        }
        vertexBuffer.position(0)
        vertexBuffer.put(quadVertices)
        vertexBuffer.position(0)
    }

    fun setShaderFilter(filter: ShaderFilter) {
        currentFilter = filter
    }

    fun setSpeedMultiplier(multiplier: Int) {
        speedMultiplier = when {
            multiplier in 1..8 -> multiplier
            multiplier > 8 -> 8
            else -> 1
        }
    }

    fun getSpeedMultiplier(): Int = speedMultiplier

    fun toggleFastForward() {
        speedMultiplier = if (speedMultiplier == 1) 2 else if (speedMultiplier == 2) 4 else 1
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // Compile all filter shader programs
        programMap[ShaderFilter.NEAREST] = createProgram(vertexShaderCode, nearestFragmentShader)
        programMap[ShaderFilter.SHARP_BILINEAR] = createProgram(vertexShaderCode, sharpBilinearFragmentShader)
        programMap[ShaderFilter.LCD_GRID] = createProgram(vertexShaderCode, lcdGridFragmentShader)
        programMap[ShaderFilter.CRT_SCANLINE] = createProgram(vertexShaderCode, crtScanlineFragmentShader)

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        isTextureAllocated = false
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
        updateVertices()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Fetch latest frame pixels from native buffer (stepped at 59.7275 FPS on DualDexEmuLoop)
        pixelBuffer.position(0)
        val hasFrame = LibretroCoreCoordinator.defaultInstance.getVideoFrame(pixelBuffer, frameMetadata)

        if (hasFrame && frameMetadata[0] > 0 && frameMetadata[1] > 0) {
            val width = frameMetadata[0]
            val height = frameMetadata[1]
            val pixelFormat = frameMetadata[3] // 2 = RGB565, 1 = XRGB8888

            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            pixelBuffer.position(0)

            val glFormat = if (pixelFormat == 2) GLES20.GL_RGB else GLES20.GL_RGBA
            val glType = if (pixelFormat == 2) GLES20.GL_UNSIGNED_SHORT_5_6_5 else GLES20.GL_UNSIGNED_BYTE

            if (!isTextureAllocated || lastAllocatedWidth != width || lastAllocatedHeight != height) {
                // Initial texture allocation
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, glFormat,
                    width, height, 0,
                    glFormat, glType, pixelBuffer
                )
                isTextureAllocated = true
                lastAllocatedWidth = width
                lastAllocatedHeight = height
            } else {
                // High-performance zero-reallocation subimage update
                GLES20.glTexSubImage2D(
                    GLES20.GL_TEXTURE_2D, 0, 0, 0,
                    width, height, glFormat, glType, pixelBuffer
                )
            }
        }

        // Render with active shader filter
        activeProgram = programMap[currentFilter] ?: programMap[ShaderFilter.NEAREST] ?: return
        GLES20.glUseProgram(activeProgram)

        val posHandle = GLES20.glGetAttribLocation(activeProgram, "aPosition")
        val texCoordHandle = GLES20.glGetAttribLocation(activeProgram, "aTexCoord")
        val texHandle = GLES20.glGetUniformLocation(activeProgram, "uTexture")
        val sizeHandle = GLES20.glGetUniformLocation(activeProgram, "uTextureSize")

        if (sizeHandle != -1 && frameMetadata[0] > 0 && frameMetadata[1] > 0) {
            GLES20.glUniform2f(sizeHandle, frameMetadata[0].toFloat(), frameMetadata[1].toFloat())
        }

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(texHandle, 0)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (inputManager.onKeyDown(keyCode)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (inputManager.onKeyUp(keyCode)) return true
        return super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: android.view.MotionEvent): Boolean {
        if (inputManager.onGenericMotionEvent(event)) return true
        return super.onGenericMotionEvent(event)
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(
                TAG,
                "SHADER COMPILE FAILED (type=$type): ${GLES20.glGetShaderInfoLog(shader)}"
            )
        }
        return shader
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e(TAG, "PROGRAM LINK FAILED: ${GLES20.glGetProgramInfoLog(program)}")
        }
        return program
    }
}
