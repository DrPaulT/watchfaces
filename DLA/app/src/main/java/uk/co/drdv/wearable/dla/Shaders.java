package uk.co.drdv.wearable.dla;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.FloatBuffer;

public class Shaders {

    // The same vertex shader is used in full colour and ambient mode.

    // Line endings are not needed for successful compilation but the
    // device driver will report a line number other than 1 if there is a
    // compile-time error.  Very handy!
    private static final String VERTEX_SHADER =
            // Pass in the modelview matrix as a constant.
            "uniform mat4 u_mvpMatrix;  \n"
                    // Pass in the position and texture coordinates per vertex.
                    + "attribute vec4 a_position;  \n"
                    + "attribute vec2 a_texCoord;  \n"
                    // Varyings are sent on to the fragment shader.
                    + "varying vec2 v_texCoord;  \n"

                    + "void main() {  \n"
                    // Transform the vertex coordinate into clip coordinates.
                    + "  gl_Position = u_mvpMatrix * a_position;  \n"
                    // Pass through the texture coordinate.
                    + "  v_texCoord = a_texCoord;  \n"
                    + "}  \n";

    // The fragment shader to use when the watch is active
    // (screen on, full brightness and colour).
    private static final String FULL_COLOUR_FRAGMENT_SHADER =
            // Medium precision is common on mobile devices.
            "precision mediump float;  \n"
                    // Pass in the texture map as a constant sampler.
                    + "uniform sampler2D s_texture;  \n"
                    // The constant draw radius.
                    + "uniform float u_size;  \n"
                    // The time in terms of the s (horizontal) texture coordinate.
                    // s == 0 is the left edge of the texture, s == 1 the right edge.
                    + "uniform float u_now;  \n"
                    // Whether to swap blue-left/red-right with red-left/blue-right.
                    + "uniform float u_swap_day_night;  \n"
                    // The texture coordinate passed in from the vertex shader.
                    + "varying vec2 v_texCoord;  \n"

                    + "void main(){  \n"
                    // Look up the colour in the texture for this fragment.
                    // Filtering of mipmaps occurs here, so we will get a
                    // blended colour.
                    + "  vec4 colour = texture2D(s_texture, v_texCoord);  \n"

                    // s in [0..1], the x-coordinate, t in [0..1] as the y-coordinate.
                    + "  float s = v_texCoord.s;  \n"
                    + "  float t = v_texCoord.t;  \n"

                    // If the texel (texture pixel) is black, and we are not drawing the
                    // "hand" line, then we straightaway send out a black pixel to the display.
                    // This immediately culls a lot of fragment processing.
                    + "  if (colour.r == 0.0 && colour.g == 0.0 && colour.b == 0.0) {  \n"
                    // If we are in the narrow stripe between blue and red sides, then
                    // output a white pixel of increasing intensity as the radius increases.
                    + "    if (s < u_now + 0.001 && s > u_now - 0.001) {  \n"
                    + "      float w = (0.001 - abs(s - u_now)) * 1000.0;  \n"
                    + "      colour = vec4(w, w, w, 1.0);  \n"
                    + "    } else {  \n"
                    + "      gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);  \n"
                    + "      return;  \n"
                    + "    }  \n"
                    + "  } else {  \n"

                    // If the texel is grey then we send out
                    // a grey pixel, for the numerals and timeline.  This brightens over
                    // time.
                    + "    if (colour.r == colour.g && colour.g == colour.b) {  \n"
                    + "      gl_FragColor = colour * clamp(u_size * 2.0, 0.0, 1.0);  \n"
                    + "      return;  \n"
                    + "    }  \n"

                    // Add a blend for coloured pixels near the watch hand.
                    + "    if (s < u_now + 0.004 && s > u_now - 0.004) {  \n"
                    + "      float w = (0.004 - abs(s - u_now)) * 250.0;  \n"
                    + "      colour = vec4(w, w, w, 1.0) + colour;  \n"
                    + "    }  \n"
                    + "  }  \n"

                    // If the radius from the centre is greater than u_size, then return black.
                    + "  float len = length(vec2((s - 0.5) * 2.0, (t - 0.5) * 2.0));"
                    // Adding a fudge factor based on the green colour channel makes
                    // different colours expand at different rates.
                    + "  if (len > u_size + colour.g * 0.3) {  \n"
                    + "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);  \n"
                    + "    return;  \n"
                    + "  }  \n"

                    // Create a glow colour depending on the distance from the radius.
                    + "  float glow = clamp((0.3 - (u_size - len)) * 2.0, 0.0, 0.6);  \n"

                    // Set up the colours for each side.  We start with blue on
                    // the left in the morning, and swap it to the right in the
                    // afternoon.  This is how you tell am/pm at a glance.
                    + "  if ((s > u_now + 0.001 && u_swap_day_night == 0.0)  \n"
                    + "    || (s < u_now - 0.001 && u_swap_day_night == 1.0)) {  \n"
                    + "    gl_FragColor = colour * vec4(1.0, 1.0, 0.0, 1.0) + vec4(glow, glow, glow, 1.0);  \n"
                    + "    return;  \n"
                    + "  } else {  \n"
                    + "    gl_FragColor = colour * vec4(0.0, 1.0, 1.0, 1.0) + vec4(glow, glow, glow, 1.0);  \n"
                    + "    return;  \n"
                    + "  }  \n"

                    // Default case, output a black pixel.  If the face.png bitmap has
                    // been prepared properly, we won't reach here.
                    + "  gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);  \n"
                    + "}  \n";

    // In low-power (ambient) mode, we must only output black or white pixels,
    // no grey shades.
    private static final String AMBIENT_FRAGMENT_SHADER =
            "precision mediump float;  \n"
                    + "uniform sampler2D s_texture;  \n"
                    + "uniform float u_now;  \n"
                    + "varying vec2 v_texCoord;  \n"

                    + "void main(){  \n"
                    + "  vec4 colour = texture2D(s_texture, v_texCoord);  \n"

                    // The white stripe that forms the watch hand.
                    + "  float s = v_texCoord.s;  \n"
                    + "  if (s < u_now + 0.001 && s > u_now - 0.001) {  \n"
                    + "    gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);  \n"
                    + "    return;  \n"
                    + "  }  \n"

                    // Output any black pixels.
                    + "  if (colour.a == 0.0  \n"
                    + "    || (colour.r == 0.0 && colour.g == 0.0 && colour.b == 0.0)) {  \n"
                    + "    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);  \n"
                    + "    return;  \n"
                    + "  }  \n"

                    // Output existing white decals like the numerals and timeline.
                    + "  if (colour.r == colour.g && colour.g == colour.b && colour.r > 0.2) {  \n"
                    + "    gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);  \n"
                    + "    return;  \n"
                    + "  }  \n"

                    // Anything else is black.
                    + "  gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);  \n"
                    + "}";

    private int fullColour;
    private int positionFC;
    private int textureFC;
    private int textureArrayFC;
    private int uMVPMatrixFC;
    private int uSizeFC;
    private int uNowFC;
    private int uSwapDayNightFC;

    private int ambient;
    private int positionA;
    private int textureA;
    private int textureArrayA;
    private int uMVPMatrixA;
    private int uNowA;

    public Shaders() {
        createFullColourProgram();
        createAmbientProgram();
    }

    public void setFullColourParameters(float[] mvpMatrix, FloatBuffer vtBuffer,
                                        float size, float now, boolean swapDayNight) {
        GLES20.glUseProgram(fullColour);
        GLES20.glUniformMatrix4fv(uMVPMatrixFC, 1, false, mvpMatrix, 0);
        GLES20.glUniform1f(uSizeFC, size);
        GLES20.glUniform1f(uNowFC, now);
        if (swapDayNight) {
            GLES20.glUniform1f(uSwapDayNightFC, 1f);
        } else {
            GLES20.glUniform1f(uSwapDayNightFC, 0f);
        }
        vtBuffer.position(0);
        GLES20.glVertexAttribPointer(positionFC, 2, GLES20.GL_FLOAT, false, 16, vtBuffer);
        GLES20.glUniform1i(textureFC, 0);
        vtBuffer.position(2);
        GLES20.glVertexAttribPointer(textureArrayFC, 2, GLES20.GL_FLOAT, false, 16, vtBuffer);
    }

    public void setAmbientParameters(float[] mvpMatrix, FloatBuffer vtBuffer, float now) {
        GLES20.glUseProgram(ambient);
        GLES20.glUniformMatrix4fv(uMVPMatrixA, 1, false, mvpMatrix, 0);
        GLES20.glUniform1f(uNowA, now);
        vtBuffer.position(0);
        GLES20.glVertexAttribPointer(positionA, 2, GLES20.GL_FLOAT, false, 16, vtBuffer);
        GLES20.glUniform1i(textureA, 0);
        vtBuffer.position(2);
        GLES20.glVertexAttribPointer(textureArrayA, 2, GLES20.GL_FLOAT, false, 16, vtBuffer);
    }

    private void createFullColourProgram() {
        fullColour = createProgram(VERTEX_SHADER, FULL_COLOUR_FRAGMENT_SHADER);
        positionFC = GLES20.glGetAttribLocation(fullColour, "a_position");
        textureArrayFC = GLES20.glGetAttribLocation(fullColour, "a_texCoord");
        textureFC = GLES20.glGetUniformLocation(fullColour, "s_texture");
        uMVPMatrixFC = GLES20.glGetUniformLocation(fullColour, "u_mvpMatrix");
        uSizeFC = GLES20.glGetUniformLocation(fullColour, "u_size");
        uNowFC = GLES20.glGetUniformLocation(fullColour, "u_now");
        uSwapDayNightFC = GLES20.glGetUniformLocation(fullColour, "u_swap_day_night");
        GLES20.glEnableVertexAttribArray(positionFC);
        GLES20.glEnableVertexAttribArray(textureArrayFC);
        checkProgram(fullColour);
    }

    private void createAmbientProgram() {
        ambient = createProgram(VERTEX_SHADER, AMBIENT_FRAGMENT_SHADER);
        positionA = GLES20.glGetAttribLocation(ambient, "a_position");
        textureArrayA = GLES20.glGetAttribLocation(ambient, "a_texCoord");
        textureA = GLES20.glGetUniformLocation(ambient, "s_texture");
        uMVPMatrixA = GLES20.glGetUniformLocation(ambient, "u_mvpMatrix");
        uNowA = GLES20.glGetUniformLocation(ambient, "u_now");
        GLES20.glEnableVertexAttribArray(positionA);
        GLES20.glEnableVertexAttribArray(textureArrayA);
        checkProgram(ambient);
    }

    private int createProgram(String vertex, String fragment) {
        int vertexShader = createShader(GLES20.GL_VERTEX_SHADER, vertex);
        int fragmentShader = createShader(GLES20.GL_FRAGMENT_SHADER, fragment);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        checkProgram(program);
        return program;
    }

    private int createShader(int type, String sourceCode) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, sourceCode);
        GLES20.glCompileShader(shader);
        checkShader(shader);
        return shader;
    }

    private void checkShader(int shader) {
        Log.i("GL", "Shader info:" + shader + " " + GLES20.glGetShaderInfoLog(shader));
    }

    private void checkProgram(int program) {
        Log.i("GL", "Program info:" + program + " " + GLES20.glGetProgramInfoLog(program));
    }
}
