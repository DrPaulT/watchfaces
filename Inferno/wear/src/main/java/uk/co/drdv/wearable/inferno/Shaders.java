package uk.co.drdv.wearable.inferno;

import android.opengl.GLES20;
import android.util.Log;

import java.nio.FloatBuffer;

public class Shaders {

    private static final String VERTEX_SHADER =
            "uniform float u_timer;  \n"
                    + "attribute vec4 a_position;  \n"
                    + "attribute float a_timeBase;  \n"
                    + "attribute float a_colour;  \n"
                    + "varying float v_blend;  \n"
                    + "varying float v_colour;  \n"
                    + "void main() {  \n"
                    + "  v_colour = a_colour;  \n"
                    + "  v_blend = fract(u_timer * a_timeBase);  \n"
                    + "  float offset = v_blend * a_timeBase;  \n"
                    + "  gl_Position = a_position + vec4(0.0, offset * 0.18, 0.0, 0.0);  \n"
                    + "  gl_PointSize = 10.0;  \n"
                    + "}  \n";

    private static final String FULL_COLOUR_FRAGMENT_SHADER =
            "precision mediump float;  \n"
                    + "uniform sampler2D s_texture;  \n"
                    + "varying float v_blend;  \n"
                    + "varying float v_colour;  \n"
                    + "void main() {  \n"
                    + "  vec4 colour = texture2D(s_texture, gl_PointCoord);  \n"
                    + "  float green = colour.g * (1.0 - v_blend);  \n"
                    + "  float alpha;  \n"
                    + "  if (v_blend <= 0.1) {  \n"
                    + "    alpha = v_blend * 0.5;  \n"
                    + "  } else {  \n"
                    + "    alpha = 0.5 - v_blend * 0.45;  \n"
                    + "  }  \n"
                    + "  if (v_colour > 0.0) {  \n"
                    + "    gl_FragColor = vec4(colour.r, green, green / 2.0, colour.a * alpha);  \n"
                    + "  } else {  \n"
                    + "    gl_FragColor = vec4(green / 2.0, green, colour.b, colour.a * alpha);  \n"
                    + "  }  \n"
                    + "}  \n";

    private static final String AMBIENT_FRAGMENT_SHADER =
            "precision mediump float;  \n"
                    + "uniform sampler2D s_texture;  \n"
                    + "void main() {  \n"
                    + "  gl_FragColor = texture2D(s_texture, gl_PointCoord);  \n"
                    + "}";

    private static final String LINE_VERTEX_SHADER =
            "attribute vec4 a_position;  \n"
                    + "void main() {  \n"
                    + "  gl_Position = a_position;  \n"
                    + "}  \n";

    private static final String LINE_FRAGMENT_SHADER =
            "precision mediump float;  \n"
                    + "void main() {  \n"
                    + "  gl_FragColor = vec4(0.3, 0.3, 0.4, 1.0);  \n"
                    + "}  \n";

    private int fullColour;
    private int uTimerFC;
    private int positionFC;
    private int timeBaseFC;
    private int colourFC;
    private int textureFC;

    private int ambient;
    private int uTimerA;
    private int positionA;
    private int timeBaseA;
    private int colourA;
    private int textureA;

    private int line;
    private int positionL;

    public Shaders() {
        createFullColourProgram();
        createAmbientProgram();
        createLineProgram();
    }

    public void setFullColourParameters(FloatBuffer vBuffer, float timer) {
        GLES20.glUseProgram(fullColour);
        GLES20.glUniform1f(uTimerFC, timer);
        vBuffer.position(0);
        GLES20.glVertexAttribPointer(positionFC, 2, GLES20.GL_FLOAT, false, 16, vBuffer);
        vBuffer.position(2);
        GLES20.glVertexAttribPointer(timeBaseFC, 1, GLES20.GL_FLOAT, false, 16, vBuffer);
        vBuffer.position(3);
        GLES20.glVertexAttribPointer(colourFC, 1, GLES20.GL_FLOAT, false, 16, vBuffer);
        GLES20.glUniform1i(textureFC, 0);
    }

    public void setAmbientParameters(FloatBuffer vBuffer, float timer) {
        GLES20.glUseProgram(ambient);
        GLES20.glUniform1f(uTimerA, timer);
        vBuffer.position(0);
        GLES20.glVertexAttribPointer(positionA, 2, GLES20.GL_FLOAT, false, 16, vBuffer);
        vBuffer.position(2);
        GLES20.glVertexAttribPointer(timeBaseA, 1, GLES20.GL_FLOAT, false, 16, vBuffer);
        vBuffer.position(3);
        GLES20.glVertexAttribPointer(colourA, 1, GLES20.GL_FLOAT, false, 16, vBuffer);
        GLES20.glUniform1i(textureA, 0);
    }

    public void setLineParameters(FloatBuffer vBuffer) {
        GLES20.glUseProgram(line);
        vBuffer.position(0);
        GLES20.glVertexAttribPointer(positionL, 2, GLES20.GL_FLOAT, false, 8, vBuffer);
    }

    private void createFullColourProgram() {
        fullColour = createProgram(VERTEX_SHADER, FULL_COLOUR_FRAGMENT_SHADER);
        uTimerFC = GLES20.glGetUniformLocation(fullColour, "u_timer");
        positionFC = GLES20.glGetAttribLocation(fullColour, "a_position");
        timeBaseFC = GLES20.glGetAttribLocation(fullColour, "a_timeBase");
        colourFC = GLES20.glGetAttribLocation(fullColour, "a_colour");
        textureFC = GLES20.glGetUniformLocation(fullColour, "s_texture");
        GLES20.glEnableVertexAttribArray(positionFC);
        GLES20.glEnableVertexAttribArray(timeBaseFC);
        GLES20.glEnableVertexAttribArray(colourFC);
        checkProgram(fullColour);
    }

    private void createAmbientProgram() {
        ambient = createProgram(VERTEX_SHADER, AMBIENT_FRAGMENT_SHADER);
        uTimerA = GLES20.glGetUniformLocation(ambient, "u_timer");
        positionA = GLES20.glGetAttribLocation(ambient, "a_position");
        timeBaseA = GLES20.glGetAttribLocation(ambient, "a_timeBase");
        colourA = GLES20.glGetAttribLocation(ambient, "a_colour");
        textureA = GLES20.glGetUniformLocation(ambient, "s_texture");
        GLES20.glEnableVertexAttribArray(positionA);
        GLES20.glEnableVertexAttribArray(timeBaseA);
        GLES20.glEnableVertexAttribArray(colourA);
        checkProgram(ambient);
    }

    private void createLineProgram() {
        line = createProgram(LINE_VERTEX_SHADER, LINE_FRAGMENT_SHADER);
        positionL = GLES20.glGetAttribLocation(line, "a_position");
        GLES20.glEnableVertexAttribArray(positionL);
        checkProgram(line);
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
