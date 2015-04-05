# DLA

#### A watchface for Android Wear based on diffusion-limited aggregation.

This app extends Gles2WatchFaceService to implement a watchface with OpenGL ES 2.
I've been wearing it for a while now and it seems to be quite power-efficient.

The load on the CPU is very small as GPU fragment shaders do most of the processing.
And actually the GPU doesn't even get out of bed to render two triangles on the
watch display.  If you have developer options enabled, turn on "Debug GPU
profiling" to see that the animations easily maintain a 60Hz framerate.

If you haven't done anything with OpenGL before, it might be good to read the
[Android documentation]
(http://developer.android.com/training/graphics/opengl/index.html)
first.

There are only two classes needed to implement this watchface.  Check the
DlaWatchFaceService class for an engine implementation that sets up the
GL environment, loads a texture and organises the viewing matrix.

Shaders.java defines the shader programs loaded onto the GPU to render the face.
We start with an aggregate in the file face.png that is used as a texture map.
There is a fragment shader to animate the colours in the texture to give the
expanding shock wave that appears when switching from ambient to active mode.

A separate shader is used to create the monochrome image for ambient mode when
the watch powers down.
