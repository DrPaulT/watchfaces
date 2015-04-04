package uk.co.drdv.wearable.dla;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.support.wearable.watchface.Gles2WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.TimeZone;

public class DlaWatchFaceService extends Gles2WatchFaceService {

    @Override
    public Engine onCreateEngine() {
        return new DlaEngine();
    }

    private class DlaEngine extends Engine {

        private final long DURATION_MILLIS = 1000L;
        private final String TIME_ZONE = "time-zone";

        private Time time = new Time();
        private boolean registeredTimeZoneReceiver;
        private float[] modelviewMatrix = new float[16];
        private float[] projectionMatrix = new float[16];
        private float[] mvpMatrix = new float[16];
        private Shaders shaders;
        private FloatBuffer vtBuffer; // Vertex, texture interleaved.
        private int[] textures = new int[1];
        private int textureWidth;
        private int textureHeight;
        private long startMillis;
        private int minute = -1;
        private double azimuthRandom = Math.PI - 0.3;
        private double heightRandom = 0;
        // Nudge display slightly to prevent burn-in on Amoleds.
        private double xJitter = 0;
        private double yJitter = 0;
        private Interpolator interpolator = new AccelerateDecelerateInterpolator();

        private final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                time.clear(intent.getStringExtra(TIME_ZONE));
                time.setToNow();
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(DlaWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setStatusBarGravity(Gravity.CENTER | Gravity.TOP)
                    .setHotwordIndicatorGravity(Gravity.CENTER | Gravity.TOP)
                    .setShowSystemUiTime(false)
                    .build());
            startMillis = SystemClock.elapsedRealtime();
        }

        @Override
        public void onGlContextCreated() {
            super.onGlContextCreated();
            shaders = new Shaders();
            float[] vts = { // x, y, s, t.
                    -1, -1, 0, 1,
                    -1, 1, 0, 0,
                    1, -1, 1, 1,
                    1, 1, 1, 0
            };
            // AllocateDirect prevents the GC moving this memory.
            vtBuffer = ByteBuffer.allocateDirect(vts.length * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            vtBuffer.put(vts);
        }

        @Override
        public void onGlSurfaceCreated(int width, int height) {
            super.onGlSurfaceCreated(width, height);
            float aspectRatio = (float) width / height;
            float dist = 0.01f;
            Matrix.frustumM(projectionMatrix, 0,
                    -aspectRatio * dist, aspectRatio * dist, // Left, right.
                    -dist, dist, // Bottom, top.
                    dist, 100); // Near, far.
            makeTexture();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (!inAmbientMode) {
                xJitter = Math.random() * 0.05 - 0.025;
                yJitter = Math.random() * 0.05 - 0.025;
                azimuthRandom = Math.PI + Math.random() * 1.8 - 0.9;
                heightRandom = Math.random() * 0.5;
                startMillis = SystemClock.elapsedRealtime();
            }
            invalidate();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (visible) {
                registerReceiver();
                time.clear(TimeZone.getDefault().getID());
                time.setToNow();
                startMillis = SystemClock.elapsedRealtime();
                invalidate();
            } else {
                unregisterReceiver();
            }
        }

        @Override
        public void onTimeTick() { // Ambient mode refresh.
            super.onTimeTick();
            time.setToNow();
            int currentMinute = time.minute;
            if (minute != currentMinute) {
                minute = currentMinute;
                invalidate();
            }
        }

        @Override
        public void onDraw() {
            super.onDraw();
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            if (isInAmbientMode()) {
                initialiseAmbient();
            } else {
                initialiseFullColour();
            }
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        }

       private void initialiseAmbient() {
            float textureS = getTextureS();
            createModelviewMatrix(textureS, 1);
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelviewMatrix, 0);
            shaders.setAmbientParameters(mvpMatrix, vtBuffer, textureS);
        }

        private void initialiseFullColour() {
            float textureS = getTextureS();
            long elapsed = SystemClock.elapsedRealtime() - startMillis;
            if (elapsed < DURATION_MILLIS) {
                invalidate();
            }
            double delta = interpolator.getInterpolation(
                    Math.min((float) elapsed / DURATION_MILLIS, 1));
            createModelviewMatrix(textureS, delta);
            Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, modelviewMatrix, 0);
            shaders.setFullColourParameters(mvpMatrix, vtBuffer,
                    (float) (4.0 / 3 * delta), textureS, time.hour > 11);
        }

        private float getTextureS() {
            time.setToNow();
            float nowSeconds = (time.hour % 12) * 3600 + time.minute * 60 + time.second;
            return nowSeconds * 864f / textureWidth / 43200f + 80.5f / textureWidth;
        }

        private void createModelviewMatrix(float texS, double delta) {
            double x = (texS - 0.5) * 2;
            double y = x * x * x / 2.51;
            double distance = 1.4 - delta;
            double angle = azimuthRandom - (1 - delta);
            Matrix.setLookAtM(modelviewMatrix, 0,
                    (float) (x + distance * 0.5 * Math.sin(angle)), // Eye x.
                    (float) (y + distance * 0.5 * Math.cos(angle)), // Eye y.
                    (float) (0.25 + heightRandom - distance * 0.2), // Eye z.
                    (float) (x + xJitter), (float) (y + yJitter), 0, // Look-at.
                    0, 0, 1); // Up
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            DlaWatchFaceService.this.registerReceiver(timeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            DlaWatchFaceService.this.unregisterReceiver(timeZoneReceiver);
        }

        private void makeTexture() {
            int[] pixels = loadBitmapAsPixels();
            argbToAbgr(pixels);
            createGlTexture(pixels);
        }

        private int[] loadBitmapAsPixels() {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeResource(
                    getApplicationContext().getResources(), R.drawable.face, options);
            textureWidth = bitmap.getWidth();
            textureHeight = bitmap.getHeight();
            int[] pixels = new int[textureWidth * textureHeight];
            bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0,
                    bitmap.getWidth(), bitmap.getHeight());
            return pixels;
        }

        private void argbToAbgr(int[] pixels) {
            int length = pixels.length;
            for (int i = 0; i < length; i++) {
                int red = (pixels[i] >> 16) & 0xff;
                int green = (pixels[i] >> 8) & 0xff;
                int blue = pixels[i] & 0xff;
                int alpha = pixels[i] & 0xff000000;
                pixels[i] = alpha | (green << 8) | (red) | (blue << 16);
            }
        }

        private void createGlTexture(int[] pixels) {
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            IntBuffer intBuffer = IntBuffer.wrap(pixels);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0,
                    GLES20.GL_RGBA, textureWidth, textureHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, intBuffer);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
        }
    }
}
