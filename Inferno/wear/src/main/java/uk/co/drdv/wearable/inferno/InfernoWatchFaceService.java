package uk.co.drdv.wearable.inferno;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.os.SystemClock;
import android.support.wearable.watchface.Gles2WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.TimeZone;

public class InfernoWatchFaceService extends Gles2WatchFaceService {

    @Override
    public Engine onCreateEngine() {
        return new InfernoEngine();
    }

    private class InfernoEngine extends Engine {

        private static final String TIME_ZONE = "time-zone";
        private static final int PARTICLES = 12 * 40 + 448 + 256;
        private static final int LINES = 12;

        private Time time = new Time();
        private boolean registeredTimeZoneReceiver;
        private Shaders shaders;
        private FloatBuffer vBuffer;
        private FloatBuffer lineVBuffer;
        private int[] textures = new int[1];
        private int textureWidth;
        private int textureHeight;
        private long startMillis;
        private int minute = -1;
        private float timer = 0;
        private boolean isSquare = true;

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
            setWatchFaceStyle(new WatchFaceStyle.Builder(InfernoWatchFaceService.this)
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
            time.setToNow();
            shaders = new Shaders();
            createHub();
            createHands();
            createDecagon();
        }

        @Override
        public void onGlSurfaceCreated(int width, int height) {
            super.onGlSurfaceCreated(width, height);
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            makeTexture();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (!inAmbientMode) {
                startMillis = SystemClock.elapsedRealtime();
                time.setToNow();
                createHands();
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
                createHands();
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
            createHands();
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
                drawAmbient();
            } else {
                drawFullColour();
            }
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            if (insets.isRound()) {
                isSquare = false;
            }
        }

        private void createHub() {
            vBuffer = ByteBuffer.allocateDirect(PARTICLES * 4 * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();

            for (int d = 0; d < 480; d++) {
                double r = Math.toRadians(d * 360.0 / 480.0);
                double x = 0.1 * Math.sin(r);
                double y = 0.1 * Math.cos(r);
                vBuffer.put((float) (x + Math.random() * 0.025 - 0.0125));
                vBuffer.put((float) (y + Math.random() * 0.025 - 0.0125));
                vBuffer.put((float) (Math.random())); // Time base.;
                vBuffer.put(1);
            }
        }

        private void createDecagon() {
            lineVBuffer = ByteBuffer.allocateDirect(LINES * 2 * 4)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer();
            for (int d = 0; d < 12; d++) {
                double r = Math.toRadians(d * 30 * 5);
                double radius = 0.97;
                if (isSquare && ((d * 30 * 5) % 90) != 0) {
                    radius = 1.1;
                }
                double x = radius * Math.sin(r);
                double y = radius * Math.cos(r);
                lineVBuffer.put((float) x);
                lineVBuffer.put((float) y);
            }
        }

        private void drawAmbient() {
            timer = 0.1f;
            shaders.setAmbientParameters(vBuffer, timer);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, PARTICLES);
        }

        private void drawFullColour() {
            shaders.setLineParameters(lineVBuffer);
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, LINES);

            long elapsed = SystemClock.elapsedRealtime() - startMillis;
            timer = elapsed / 1000f;
            shaders.setFullColourParameters(vBuffer, timer);
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, PARTICLES);
            invalidate();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            InfernoWatchFaceService.this.registerReceiver(timeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            InfernoWatchFaceService.this.unregisterReceiver(timeZoneReceiver);
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
                    getApplicationContext().getResources(), R.drawable.particle, options);
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

        private void createHands() {
            vBuffer.position(12 * 40 * 4);
            double minuteAngle = Math.toRadians(time.minute * 6 + time.second / 10.0);
            double hourAngle = Math.toRadians(time.hour * 30 + time.minute / 2.0);
            double minuteStartX = 0.1 * Math.sin(minuteAngle);
            double minuteStartY = 0.1 * Math.cos(minuteAngle);
            double minuteEndX = minuteStartX * 8;
            double minuteEndY = minuteStartY * 8;
            double hourStartX = 0.1 * Math.sin(hourAngle);
            double hourStartY = 0.1 * Math.cos(hourAngle);
            double hourEndX = hourStartX * 5;
            double hourEndY = hourStartY * 5;

            pointStrip(hourStartX, hourStartY, hourEndX, hourEndY, 256);
            pointStrip(minuteStartX, minuteStartY, minuteEndX, minuteEndY, 448);
        }

        private void pointStrip(double startX, double startY, double endX, double endY, int num) {
            for (int i = 0; i < num; i++) {
                double x = startX + (endX - startX) * i / num;
                double y = startY + (endY - startY) * i / num;
                vBuffer.put((float) (x + Math.random() * 0.025 - 0.0125));
                vBuffer.put((float) (y + Math.random() * 0.025 - 0.0125));
                vBuffer.put((float) (Math.random())); // Time base.
                vBuffer.put(1);
            }
        }
    }
}
