# Inferno

#### A watchface for Android Wear using GL particles to simulate flames.

Another Gles2WatchFaceService watch face, this time the hands are rendered
with particles that start out bright yellow and decay to translucent red
as they float upwards.

When the watch wakes up we initialise a vertex array with (x, y)
coordinates for the hands and central ring of the watch face.  These have
a small random displacement to prevent everything looking too uniform, but
once initialised we do not have to update anything in this array while
the hands point to this time.  The GPU will take over the process of
animating the particles through shader programs that create displacement
and colour changes over time.

Each particle has a time base, a duration for the motion of that particle.  It
starts at its initial location and moves up linearly until its time period is
over, when it restarts again.

During this time, we start with a fully translucent particle, quickly fade it in
to 0.5 opacity, then slowly blend it back to fully translucent again.  The colour
goes from yellow to deep orange at the same time.

Since the GPU is doing all this, it should be quite battery efficient.  I've been
wearing it for several days now, and that seems to be the case.

As always, check Shaders.java for the GPU side of the animation.
