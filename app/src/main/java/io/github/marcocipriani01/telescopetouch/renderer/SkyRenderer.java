/*
 * Copyright 2021 Marco Cipriani (@marcocipriani01)
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.marcocipriani01.telescopetouch.renderer;

import android.content.res.Resources;
import android.opengl.GLSurfaceView;
import android.opengl.GLU;
import android.util.Log;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import io.github.marcocipriani01.telescopetouch.astronomy.GeocentricCoordinates;
import io.github.marcocipriani01.telescopetouch.maths.Matrix4x4;
import io.github.marcocipriani01.telescopetouch.maths.Vector3;
import io.github.marcocipriani01.telescopetouch.renderer.util.GLBuffer;
import io.github.marcocipriani01.telescopetouch.renderer.util.SkyRegionMap;
import io.github.marcocipriani01.telescopetouch.renderer.util.TextureManager;
import io.github.marcocipriani01.telescopetouch.renderer.util.UpdateClosure;

public class SkyRenderer implements GLSurfaceView.Renderer {

    protected final TextureManager textureManager;
    private final RenderState renderState = new RenderState();
    private final Set<UpdateClosure> updateClosures = new TreeSet<>();
    /**
     * All managers - we need to reload all of these when we recreate the surface.
     */
    private final Set<RendererObjectManager> managers = new TreeSet<>();
    /**
     * A list of managers which need to be reloaded before the next frame is rendered.  This may
     * be because they haven't ever been loaded yet, or because their objects have changed since
     * the last frame.
     */
    private final ArrayList<ManagerReloadData> managersToReload = new ArrayList<>();
    private final RendererObjectManager.UpdateListener updateListener =
            (rom) -> managersToReload.add(new ManagerReloadData(rom, false));
    private final SkyBox skyBox;
    private final OverlayManager overlayManager;
    /**
     * Maps an integer indicating render order to a list of objects at that level.  The managers
     * will be rendered in order, with the lowest number coming first.
     */
    private final TreeMap<Integer, Set<RendererObjectManager>> layersToManagersMap;
    private Matrix4x4 projectionMatrix;
    private Matrix4x4 viewMatrix;
    /**
     * Indicates whether the transformation matrix has changed since the last
     * time we started rendering
     */
    private boolean mustUpdateView = true;
    private boolean mustUpdateProjection = true;

    public SkyRenderer(Resources res) {
        renderState.setResources(res);
        layersToManagersMap = new TreeMap<>();
        textureManager = new TextureManager(res);
        // The skybox should go behind everything.
        skyBox = new SkyBox(Integer.MIN_VALUE, textureManager);
        skyBox.enable(false);
        addObjectManager(skyBox);
        // The overlays go on top of everything.
        overlayManager = new OverlayManager(Integer.MAX_VALUE, textureManager);
        addObjectManager(overlayManager);
        Log.d("SkyRenderer", "SkyRenderer::SkyRenderer()");
    }

    static void checkForErrors(GL10 gl) {
        int error = gl.glGetError();
        if (error != 0) {
            Log.e("SkyRenderer", "GL error: " + error);
            Log.e("SkyRenderer", GLU.gluErrorString(error));
        }
    }

    // Returns true if the buffers should be swapped, false otherwise.
    @Override
    public void onDrawFrame(GL10 gl) {
        // Initialize any of the unloaded managers.
        for (ManagerReloadData data : managersToReload) {
            data.manager.reload(gl, data.fullReload);
        }
        managersToReload.clear();

        maybeUpdateMatrices(gl);

        // Determine which sky regions should be rendered.
        renderState.setActiveSkyRegions(
                SkyRegionMap.getActiveRegions(
                        renderState.getLookDir(),
                        renderState.getRadiusOfView(),
                        (float) renderState.getScreenWidth() / renderState.getScreenHeight()));

        gl.glClear(GL10.GL_COLOR_BUFFER_BIT);

        for (Set<RendererObjectManager> managers : layersToManagersMap.values()) {
            for (RendererObjectManager rom : managers) {
                rom.draw(gl);
            }
        }
        checkForErrors(gl);

        // Queue updates for the next frame.
        for (UpdateClosure update : updateClosures) {
            update.run();
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d("SkyRenderer", "surfaceCreated");
        gl.glEnable(GL10.GL_DITHER);

        /*
         * Some one-time OpenGL initialization can be made here
         * probably based on features of this particular context
         */
        gl.glHint(GL10.GL_PERSPECTIVE_CORRECTION_HINT, GL10.GL_FASTEST);
        gl.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        gl.glEnable(GL10.GL_CULL_FACE);
        gl.glShadeModel(GL10.GL_SMOOTH);
        gl.glDisable(GL10.GL_DEPTH_TEST);

        // Release references to all of the old textures.
        textureManager.reset();

        String extensions = gl.glGetString(GL10.GL_EXTENSIONS);
        Log.i("SkyRenderer", "GL extensions: " + extensions);

        // Determine if the phone supports VBOs or not, and set this on the GLBuffer.
        // TODO(jpowell): There are two extension strings which seem applicable.
        // There is GL_OES_vertex_buffer_object and GL_ARB_vertex_buffer_object.
        // I can't find any documentation which explains the difference between
        // these two.  Most phones which support one seem to support both,
        // except for the Nexus One, which only supports ARB but doesn't seem
        // to benefit from using VBOs anyway.  I should figure out what the
        // difference is and use ARB too, if I can.
        boolean canUseVBO = extensions.contains("GL_OES_vertex_buffer_object");
        // VBO support on the Cliq and Behold is broken and say they can
        // use them when they can't.  Explicitly disable it for these devices.
        final String[] badModels = {
                "MB200",
                "MB220",
                "Behold",
        };
        for (String model : badModels) {
            if (android.os.Build.MODEL.contains(model)) {
                canUseVBO = false;
                break;
            }
        }
        Log.i("SkyRenderer", "Model: " + android.os.Build.MODEL);
        Log.i("SkyRenderer", canUseVBO ? "VBOs enabled" : "VBOs disabled");
        GLBuffer.setCanUseVBO(canUseVBO);

        // Reload all of the managers.
        for (RendererObjectManager rom : managers) {
            rom.reload(gl, true);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d("SkyRenderer", "Starting sizeChanged, size = (" + width + ", " + height + ")");

        renderState.setScreenSize(width, height);
        overlayManager.resize(gl, width, height);

        // Need to set the matrices.
        mustUpdateView = true;
        mustUpdateProjection = true;

        Log.d("SkyRenderer", "Changing viewport size");

        gl.glViewport(0, 0, width, height);

        Log.d("SkyRenderer", "Done with sizeChanged");
    }

    public void setRadiusOfView(float degrees) {
        // Log.d("SkyRenderer", "setRadiusOfView(" + degrees + ")");
        renderState.setRadiusOfView(degrees);
        mustUpdateProjection = true;
    }

    public void addUpdateClosure(UpdateClosure update) {
        updateClosures.add(update);
    }

    public void removeUpdateCallback(UpdateClosure update) {
        updateClosures.remove(update);
    }

    // Sets up from the perspective of the viewer.
    // ie, the zenith in celestial coordinates.
    public void setViewerUpDirection(GeocentricCoordinates up) {
        overlayManager.setViewerUpDirection(up);
    }

    public void addObjectManager(RendererObjectManager m) {
        m.setRenderState(renderState);
        m.setUpdateListener(updateListener);
        managers.add(m);

        // It needs to be reloaded before we try to draw it.
        managersToReload.add(new ManagerReloadData(m, true));

        // Add it to the appropriate layer.
        Set<RendererObjectManager> managers = layersToManagersMap.get(m.getLayer());
        if (managers == null) {
            managers = new TreeSet<>();
            layersToManagersMap.put(m.getLayer(), managers);
        }
        managers.add(m);
    }

    public void removeObjectManager(RendererObjectManager m) {
        managers.remove(m);
        Set<RendererObjectManager> managers = layersToManagersMap.get(m.getLayer());
        // managers shouldn't ever be null, so don't bother checking.  Let it crash if it is so we
        // know there's a bug.
        Objects.requireNonNull(managers).remove(m);
    }

    public void enableSkyGradient(GeocentricCoordinates sunPosition) {
        skyBox.setSunPosition(sunPosition);
        skyBox.enable(true);
    }

    public void disableSkyGradient() {
        skyBox.enable(false);
    }

    public void enableSearchOverlay(GeocentricCoordinates target, String targetName) {
        overlayManager.enableSearchOverlay(target, targetName);
    }

    public void disableSearchOverlay() {
        overlayManager.disableSearchOverlay();
    }

    public void setNightVisionMode(boolean enabled) {
        renderState.setNightVisionMode(enabled);
    }

    // Used to set the orientation of the text.  The angle parameter is the roll
    // of the phone.  This angle is rounded to the nearest multiple of 90 degrees
    // to keep the text readable.
    public void setTextAngle(float angleInRadians) {
        final float TWO_OVER_PI = 2.0f / (float) Math.PI;
        final float PI_OVER_TWO = (float) Math.PI / 2.0f;

        float newAngle = Math.round(angleInRadians * TWO_OVER_PI) * PI_OVER_TWO;

        renderState.setUpAngle(newAngle);
    }

    public void setViewOrientation(float dirX, float dirY, float dirZ,
                                   float upX, float upY, float upZ) {
        // Normalize the look direction
        float dirLen = (float) Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        float oneOverDirLen = 1.0f / dirLen;
        dirX *= oneOverDirLen;
        dirY *= oneOverDirLen;
        dirZ *= oneOverDirLen;

        // We need up to be perpendicular to the look direction, so we subtract
        // off the projection of the look direction onto the up vector
        float lookDotUp = dirX * upX + dirY * upY + dirZ * upZ;
        upX -= lookDotUp * dirX;
        upY -= lookDotUp * dirY;
        upZ -= lookDotUp * dirZ;

        // Normalize the up vector
        float upLen = (float) Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        float oneOverUpLen = 1.0f / upLen;
        upX *= oneOverUpLen;
        upY *= oneOverUpLen;
        upZ *= oneOverUpLen;

        renderState.setLookDir(new GeocentricCoordinates(dirX, dirY, dirZ));
        renderState.setUpDir(new GeocentricCoordinates(upX, upY, upZ));

        mustUpdateView = true;

        overlayManager.setViewOrientation(new GeocentricCoordinates(dirX, dirY, dirZ),
                new GeocentricCoordinates(upX, upY, upZ));
    }

    protected int getWidth() {
        return renderState.getScreenWidth();
    }

    protected int getHeight() {
        return renderState.getScreenHeight();
    }

    private void updateView(GL10 gl) {
        // Get a vector perpendicular to both, pointing to the right, by taking
        // lookDir cross up.
        Vector3 lookDir = renderState.getLookDir();
        Vector3 upDir = renderState.getUpDir();
        Vector3 right = Vector3.vectorProduct(lookDir, upDir);

        viewMatrix = Matrix4x4.createView(lookDir, upDir, right);

        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glLoadMatrixf(viewMatrix.getArray(), 0);
    }

    private void updatePerspective(GL10 gl) {
        projectionMatrix = Matrix4x4.createPerspectiveProjection(
                renderState.getScreenWidth(),
                renderState.getScreenHeight(),
                renderState.getRadiusOfView() * 3.141593f / 360.0f);

        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glLoadMatrixf(projectionMatrix.getArray(), 0);

        // Switch back to the model view matrix.
        gl.glMatrixMode(GL10.GL_MODELVIEW);
    }

    private void maybeUpdateMatrices(GL10 gl) {
        boolean updateTransform = mustUpdateView || mustUpdateProjection;
        if (mustUpdateView) {
            updateView(gl);
            mustUpdateView = false;
        }
        if (mustUpdateProjection) {
            updatePerspective(gl);
            mustUpdateProjection = false;
        }
        if (updateTransform) {
            // Device coordinates are a square from (-1, -1) to (1, 1).  Screen
            // coordinates are (0, 0) to (width, height).  Both coordinates
            // are useful in different circumstances, so we'll pre-compute
            // matrices to do the transformations from world coordinates
            // into each of these.
            Matrix4x4 transformToDevice = Matrix4x4.multiplyMM(projectionMatrix, viewMatrix);

            Matrix4x4 translate = Matrix4x4.createTranslation(1, 1, 0);
            Matrix4x4 scale = Matrix4x4.createScaling(renderState.getScreenWidth() * 0.5f,
                    renderState.getScreenHeight() * 0.5f, 1);

            Matrix4x4 transformToScreen =
                    Matrix4x4.multiplyMM(Matrix4x4.multiplyMM(scale, translate),
                            transformToDevice);

            renderState.setTransformationMatrices(transformToDevice, transformToScreen);
        }
    }

    // WARNING!  These factory methods are invoked from another thread and
    // therefore cannot do any OpenGL operations or any nontrivial nontrivial
    // initialization.
    //
    // TODO(jpowell): This would be much safer if the renderer controller
    // schedules creation of the objects in the queue.
    public PointObjectManager createPointManager(int layer) {
        return new PointObjectManager(layer, textureManager);
    }

    public PolyLineObjectManager createPolyLineManager(int layer) {
        return new PolyLineObjectManager(layer, textureManager);
    }

    public LabelObjectManager createLabelManager(int layer) {
        return new LabelObjectManager(layer, textureManager);
    }

    public ImageObjectManager createImageManager(int layer) {
        return new ImageObjectManager(layer, textureManager);
    }

    private static class ManagerReloadData {

        public final RendererObjectManager manager;
        public final boolean fullReload;

        ManagerReloadData(RendererObjectManager manager, boolean fullReload) {
            this.manager = manager;
            this.fullReload = fullReload;
        }
    }

    static class RenderState {

        private GeocentricCoordinates mCameraPos = new GeocentricCoordinates();
        private GeocentricCoordinates mLookDir = new GeocentricCoordinates(1, 0, 0);
        private GeocentricCoordinates mUpDir = new GeocentricCoordinates(0, 1, 0);
        private float mRadiusOfView = 45;  // in degrees
        private float mUpAngle = 0;
        private int mScreenWidth = 100;
        private int mScreenHeight = 100;
        private Matrix4x4 mTransformToDevice = Matrix4x4.createIdentity();
        private Matrix4x4 mTransformToScreen = Matrix4x4.createIdentity();
        private Resources mRes;
        private boolean mNightVisionMode = false;
        private SkyRegionMap.ActiveRegionData mActiveSkyRegionSet = null;

        public GeocentricCoordinates getCameraPos() {
            return mCameraPos;
        }

        public void setCameraPos(GeocentricCoordinates pos) {
            mCameraPos = pos.copy();
        }

        public GeocentricCoordinates getLookDir() {
            return mLookDir;
        }

        public void setLookDir(GeocentricCoordinates dir) {
            mLookDir = dir.copy();
        }

        public GeocentricCoordinates getUpDir() {
            return mUpDir;
        }

        public void setUpDir(GeocentricCoordinates dir) {
            mUpDir = dir.copy();
        }

        public float getRadiusOfView() {
            return mRadiusOfView;
        }

        public void setRadiusOfView(float radius) {
            mRadiusOfView = radius;
        }

        public float getUpAngle() {
            return mUpAngle;
        }

        public void setUpAngle(float angle) {
            mUpAngle = angle;
        }

        public int getScreenWidth() {
            return mScreenWidth;
        }

        public int getScreenHeight() {
            return mScreenHeight;
        }

        public Matrix4x4 getTransformToDeviceMatrix() {
            return mTransformToDevice;
        }

        public Matrix4x4 getTransformToScreenMatrix() {
            return mTransformToScreen;
        }

        public Resources getResources() {
            return mRes;
        }

        public void setResources(Resources res) {
            mRes = res;
        }

        public boolean getNightVisionMode() {
            return mNightVisionMode;
        }

        public void setNightVisionMode(boolean enabled) {
            mNightVisionMode = enabled;
        }

        public SkyRegionMap.ActiveRegionData getActiveSkyRegions() {
            return mActiveSkyRegionSet;
        }

        public void setActiveSkyRegions(SkyRegionMap.ActiveRegionData set) {
            mActiveSkyRegionSet = set;
        }

        public void setScreenSize(int width, int height) {
            mScreenWidth = width;
            mScreenHeight = height;
        }

        public void setTransformationMatrices(Matrix4x4 transformToDevice, Matrix4x4 transformToScreen) {
            mTransformToDevice = transformToDevice;
            mTransformToScreen = transformToScreen;
        }
    }
}