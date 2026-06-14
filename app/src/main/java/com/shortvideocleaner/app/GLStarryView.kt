package com.shortvideocleaner.app

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.AttributeSet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*
import kotlin.random.Random

class GLStarryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer = StarRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun pauseRendering() { renderMode = RENDERMODE_WHEN_DIRTY }
    fun resumeRendering() { renderMode = RENDERMODE_CONTINUOUSLY }

    private class StarRenderer : GLSurfaceView.Renderer {

        private val modelMatrix = FloatArray(16)
        private val viewMatrix = FloatArray(16)
        private val projMatrix = FloatArray(16)
        private val mvpMatrix = FloatArray(16)

        private var program = 0
        private var starCount = 0
        private var vertexBuffer: FloatBuffer? = null
        private var startTime = 0L
        private var w = 0f
        private var h = 0f

        // 顶点: 不旋转（用 modelMatrix 旋转），用 uTime 闪烁
        private val vertexShaderCode = """
            uniform mat4 uMVPMatrix;
            uniform float uTime;
            attribute vec4 aPosition;
            attribute float aSize;
            attribute float aBaseBright;
            attribute float aTwinkleSpeed;
            attribute float aTwinkleOff;
            attribute vec3 aColor;
            varying float vBright;
            varying vec3 vColor;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                gl_PointSize = aSize;
                float twinkle = sin(uTime * aTwinkleSpeed + aTwinkleOff) * 0.35 + 0.65;
                vBright = aBaseBright * twinkle;
                vColor = aColor;
            }
        """.trimIndent()

        private val fragmentShaderCode = """
            precision mediump float;
            varying float vBright;
            varying vec3 vColor;
            void main() {
                float d = length(gl_PointCoord - 0.5) * 2.0;
                float glow = smoothstep(1.0, 0.0, d) * 0.7;
                float core = exp(-d * d * 10.0) * 0.5;
                gl_FragColor = vec4(vColor, (glow + core) * vBright);
            }
        """.trimIndent()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0.015f, 0.015f, 0.06f, 1f)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE)
            program = createProgram(vertexShaderCode, fragmentShaderCode)
            startTime = System.currentTimeMillis()
            // 首帧全清
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        override fun onSurfaceChanged(gl: GL10?, wd: Int, ht: Int) {
            GLES20.glViewport(0, 0, wd, ht)
            w = wd.toFloat(); h = ht.toFloat()
            Matrix.orthoM(projMatrix, 0, 0f, w, h, 0f, -1f, 1f)
            Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f)
            generateStars(wd, ht)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        }

        override fun onDrawFrame(gl: GL10?) {
            if (starCount == 0 || program == 0) return

            val t = (System.currentTimeMillis() - startTime) * 0.001f

            // 拖尾效果：不 glClear，改为画半透明黑底让旧帧逐渐淡出
            GLES20.glClearColor(0.015f, 0.015f, 0.06f, 0.04f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            // 旋转轴心：屏幕底部中心，速度约 12°/s
            Matrix.setIdentityM(modelMatrix, 0)
            Matrix.translateM(modelMatrix, 0, w / 2f, h, 0f)
            Matrix.rotateM(modelMatrix, 0, t * 2f, 0f, 0f, 1f)
            Matrix.translateM(modelMatrix, 0, -w / 2f, -h, 0f)

            Matrix.multiplyMM(mvpMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvpMatrix, 0)

            GLES20.glUseProgram(program)
            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uMVPMatrix"), 1, false, mvpMatrix, 0)
            GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uTime"), t)

            // 顶点布局: x y size baseBright twinkleSpeed twinkleOff r g b = 9 floats
            val stride = 9 * 4
            val buf = vertexBuffer ?: return
            buf.position(0)

            val posLoc = GLES20.glGetAttribLocation(program, "aPosition")
            GLES20.glEnableVertexAttribArray(posLoc)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, stride, buf)
            buf.position(2)

            val sizeLoc = GLES20.glGetAttribLocation(program, "aSize")
            GLES20.glEnableVertexAttribArray(sizeLoc)
            GLES20.glVertexAttribPointer(sizeLoc, 1, GLES20.GL_FLOAT, false, stride, buf)
            buf.position(3)

            val brightLoc = GLES20.glGetAttribLocation(program, "aBaseBright")
            GLES20.glEnableVertexAttribArray(brightLoc)
            GLES20.glVertexAttribPointer(brightLoc, 1, GLES20.GL_FLOAT, false, stride, buf)
            buf.position(4)

            val tsLoc = GLES20.glGetAttribLocation(program, "aTwinkleSpeed")
            GLES20.glEnableVertexAttribArray(tsLoc)
            GLES20.glVertexAttribPointer(tsLoc, 1, GLES20.GL_FLOAT, false, stride, buf)
            buf.position(5)

            val toLoc = GLES20.glGetAttribLocation(program, "aTwinkleOff")
            GLES20.glEnableVertexAttribArray(toLoc)
            GLES20.glVertexAttribPointer(toLoc, 1, GLES20.GL_FLOAT, false, stride, buf)
            buf.position(6)

            val colorLoc = GLES20.glGetAttribLocation(program, "aColor")
            GLES20.glEnableVertexAttribArray(colorLoc)
            GLES20.glVertexAttribPointer(colorLoc, 3, GLES20.GL_FLOAT, false, stride, buf)

            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, starCount)

            GLES20.glDisableVertexAttribArray(posLoc)
            GLES20.glDisableVertexAttribArray(sizeLoc)
            GLES20.glDisableVertexAttribArray(brightLoc)
            GLES20.glDisableVertexAttribArray(tsLoc)
            GLES20.glDisableVertexAttribArray(toLoc)
            GLES20.glDisableVertexAttribArray(colorLoc)
        }

        private fun generateStars(wd: Int, ht: Int) {
            val count = 1000
            starCount = count
            val data = FloatArray(count * 9)
            val colors = listOf(
                floatArrayOf(1f, 0.92f, 0.85f),
                floatArrayOf(0.75f, 0.82f, 1f),
                floatArrayOf(1f, 0.78f, 0.9f),
                floatArrayOf(0.65f, 0.9f, 1f),
                floatArrayOf(1f, 0.88f, 0.7f),
                floatArrayOf(0.85f, 0.75f, 1f),
            )

            // 3 倍屏幕范围，旋转时不会露出空白
            val rangeW = wd * 3f
            val rangeH = ht * 3f
            val offX = -wd.toFloat()
            val offY = -ht.toFloat()

            for (i in 0 until count) {
                val idx = i * 9
                data[idx] = Random.nextFloat() * rangeW + offX
                data[idx + 1] = Random.nextFloat() * rangeH + offY
                data[idx + 2] = if (Random.nextFloat() < 0.07f)
                    Random.nextFloat() * 6f + 4f
                else
                    Random.nextFloat() * 2.5f + 1f
                data[idx + 3] = Random.nextFloat() * 0.4f + 0.35f
                data[idx + 4] = Random.nextFloat() * 3f + 0.5f   // twinkle speed
                data[idx + 5] = Random.nextFloat() * 6.28f         // twinkle offset
                val c = colors.random()
                data[idx + 6] = c[0]; data[idx + 7] = c[1]; data[idx + 8] = c[2]
            }

            vertexBuffer = ByteBuffer.allocateDirect(data.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .apply { put(data); position(0) }
        }

        private fun createProgram(vs: String, fs: String): Int {
            val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vs)
            val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fs)
            val prog = GLES20.glCreateProgram().also {
                GLES20.glAttachShader(it, vShader)
                GLES20.glAttachShader(it, fShader)
                GLES20.glLinkProgram(it)
            }
            GLES20.glDeleteShader(vShader)
            GLES20.glDeleteShader(fShader)
            return prog
        }

        private fun loadShader(type: Int, code: String): Int {
            return GLES20.glCreateShader(type).also { shader ->
                GLES20.glShaderSource(shader, code)
                GLES20.glCompileShader(shader)
            }
        }
    }
}
