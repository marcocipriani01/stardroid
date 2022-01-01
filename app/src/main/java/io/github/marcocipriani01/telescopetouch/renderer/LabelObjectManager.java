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

import static io.github.marcocipriani01.telescopetouch.maths.MathsUtils.DEGREES_TO_RADIANS;

import android.graphics.Paint;
import android.graphics.Typeface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import javax.microedition.khronos.opengles.GL10;
import javax.microedition.khronos.opengles.GL11;

import io.github.marcocipriani01.telescopetouch.astronomy.GeocentricCoordinates;
import io.github.marcocipriani01.telescopetouch.maths.MathsUtils;
import io.github.marcocipriani01.telescopetouch.maths.Matrix4x4;
import io.github.marcocipriani01.telescopetouch.maths.Vector3;
import io.github.marcocipriani01.telescopetouch.renderer.util.GLBuffer;
import io.github.marcocipriani01.telescopetouch.renderer.util.LabelMaker;
import io.github.marcocipriani01.telescopetouch.renderer.util.SkyRegionMap;
import io.github.marcocipriani01.telescopetouch.renderer.util.TextureManager;
import io.github.marcocipriani01.telescopetouch.renderer.util.TextureReference;
import io.github.marcocipriani01.telescopetouch.source.TextSource;

/**
 * Manages rendering of text labels.
 *
 * @author James Powell
 */
public class LabelObjectManager extends RendererObjectManager {

    // Should we compute the regions for the labels? If false, we just put them in the catchall region.
    private static final boolean COMPUTE_REGIONS = true;
    private final SkyRegionMap<ArrayList<Label>> skyRegions = new SkyRegionMap<>();
    private final IntBuffer quadBuffer;
    private final Paint labelPaint;
    private LabelMaker labelMaker = null;
    private Label[] labels = new Label[0];
    // These are intermediate variables set in beginDrawing() and used in draw() to make the transformations more efficient
    private Vector3 labelOffset = new Vector3(0, 0, 0);
    private float dotProductThreshold;
    private TextureReference texture = null;

    public LabelObjectManager(int layer, TextureManager textureManager) {
        super(layer, textureManager);

        labelPaint = new Paint();
        labelPaint.setAntiAlias(true);
        labelPaint.setTypeface(Typeface.DEFAULT);

        ByteBuffer quadBuffer = ByteBuffer.allocateDirect(4 * 2 * 4);
        quadBuffer.order(ByteOrder.nativeOrder());
        this.quadBuffer = quadBuffer.asIntBuffer();
        this.quadBuffer.position(0);
        // A quad with size 1 on each size, so we just need to multiply
        // by the label's width and height to get it to the right size for each
        // label.
        float[] vertices = {
                -0.5f, -0.5f,   // lower left
                -0.5f, 0.5f,   // upper left
                0.5f, -0.5f,   // lower right
                0.5f, 0.5f};  // upper right
        for (float f : vertices) {
            this.quadBuffer.put(MathsUtils.floatToFixedPoint(f));
        }
        this.quadBuffer.position(0);

        // We want to initialize the labels of a sky region to an empty list.
        skyRegions.setRegionDataFactory(ArrayList::new);
    }

    @Override
    public void reload(GL10 gl, boolean fullReload) {
        // We need to regenerate the texture.  If we're re-creating the surface
        // (fullReload=true), all resources were automatically released by OpenGL,
        // so we don't want to try to release it again.  Otherwise, we need to
        // release it to avoid a resource leak (mLabelMaker.shutdown takes
        // care of freeing the texture).
        //
        // TODO(jpowell): This whole reload interface is horrendous, and I should
        // make a better way of scheduling reloads.
        //
        // TODO(jpowell): LabelMaker and textures have gone through some changes
        // since they were originally created, and I feel like it might not make
        // sense for it to own the texture anymore.  I should see if I can just
        // let it create but not own it.
        if (!fullReload && labelMaker != null) {
            labelMaker.shutdown(gl);
        }

        labelMaker = new LabelMaker(true);
        texture = labelMaker.initialize(gl, labelPaint, labels,
                getRenderState().getResources(), textureManager());
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public synchronized void updateObjects(List<TextSource> labels, EnumSet<UpdateType> updateType) {
        if (updateType.contains(UpdateType.Reset)) {
            this.labels = new Label[labels.size()];
            synchronized (labels) {
                for (int i = 0; i < labels.size(); i++) {
                    this.labels[i] = new Label(labels.get(i));
                }
            }
            queueForReload();
        } else if (updateType.contains(UpdateType.UpdatePositions)) {
            if (labels.size() != this.labels.length) {
                logUpdateMismatch("LabelObjectManager", this.labels.length, labels.size(), updateType);
                return;
            }
            // Since we don't store the positions in any GPU memory, and do the
            // transformations manually, we can just update the positions stored
            // on the label objects.
            for (int i = 0; i < this.labels.length; i++) {
                GeocentricCoordinates pos = labels.get(i).getLocation();
                this.labels[i].x = (float) pos.x;
                this.labels[i].y = (float) pos.y;
                this.labels[i].z = (float) pos.z;
            }
        }

        // Put all of the labels in their sky regions.
        // TODO(jpowell): Get this from the label source itself once it supports this.
        skyRegions.clear();
        for (Label l : this.labels) {
            int region;
            if (COMPUTE_REGIONS) {
                region = SkyRegionMap.getObjectRegion(new GeocentricCoordinates(l.x, l.y, l.z));
            } else {
                region = SkyRegionMap.CATCHALL_REGION_ID;
            }
            skyRegions.getRegionData(region).add(l);
        }
    }

    @Override
    protected void drawInternal(GL10 gl) {
        gl.glTexEnvx(GL10.GL_TEXTURE_ENV, GL10.GL_TEXTURE_ENV_MODE, GL10.GL_MODULATE);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glActiveTexture(GL10.GL_TEXTURE0);
        texture.bind(gl);
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);

        beginDrawing(gl);
        // Draw the labels for the active sky regions.
        ArrayList<ArrayList<Label>> allActiveLabels = skyRegions.getDataForActiveRegions(getRenderState().getActiveSkyRegions());
        for (ArrayList<Label> labelsInRegion : allActiveLabels) {
            for (Label l : labelsInRegion) {
                drawLabel(gl, l);
            }
        }
        endDrawing(gl);
    }

    /**
     * Begin drawing labels. Sets the OpenGL state for rapid drawing.
     */
    public void beginDrawing(GL10 gl) {
        texture.bind(gl);
        gl.glShadeModel(GL10.GL_FLAT);
        gl.glEnable(GL10.GL_ALPHA_TEST);
        gl.glAlphaFunc(GL10.GL_GREATER, 0.5f);
        gl.glEnable(GL10.GL_TEXTURE_2D);

        // We're going to do the transformation on the CPU, so set the matrices
        // to the identity
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glOrthof(0, getRenderState().getScreenWidth(), 0, getRenderState().getScreenHeight(), -1, 1);

        GLBuffer.unbind((GL11) gl);
        gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
        gl.glEnableClientState(GL10.GL_TEXTURE_COORD_ARRAY);
        gl.glDisableClientState(GL10.GL_COLOR_ARRAY);

        SkyRenderer.RenderState rs = super.getRenderState();

        float viewWidth = rs.getScreenWidth();
        float viewHeight = rs.getScreenHeight();

        Matrix4x4 rotation = Matrix4x4.createRotation(rs.getUpAngle(), rs.getLookDir());
        labelOffset = Matrix4x4.multiplyMV(rotation, rs.getUpDir());

        // If a label isn't within the field of view angle from the target vector, it can't
        // be on the screen.  Compute the cosine of this angle so we can quickly identify these.
        // TODO(jpowell): I know I can make this tighter - do so.
        dotProductThreshold = (float) Math.cos(rs.getRadiusOfView() * DEGREES_TO_RADIANS * (1 + viewWidth / viewHeight) * 0.5f);
    }

    /**
     * Ends the drawing and restores the OpenGL state.
     */
    public void endDrawing(GL10 gl) {
        gl.glDisable(GL10.GL_ALPHA_TEST);
        gl.glMatrixMode(GL10.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL10.GL_MODELVIEW);
        gl.glPopMatrix();
        gl.glDisable(GL10.GL_TEXTURE_2D);
        gl.glColor4x(MathsUtils.ONE, MathsUtils.ONE, MathsUtils.ONE, MathsUtils.ONE);
    }

    private void drawLabel(GL10 gl, Label label) {
        Vector3 lookDir = getRenderState().getLookDir();
        if (lookDir.x * label.x + lookDir.y * label.y + lookDir.z * label.z < dotProductThreshold)
            return;

        // Offset the label to be underneath the given position (so a label will
        // always appear underneath a star no matter how the phone is rotated)
        Vector3 v = new Vector3(
                label.x - labelOffset.x * label.offset,
                label.y - labelOffset.y * label.offset,
                label.z - labelOffset.z * label.offset);

        Vector3 screenPos = Matrix4x4.transformVector(getRenderState().getTransformToScreenMatrix(), v);

        // We want this to align consistently with the pixels on the screen, so we
        // snap to the nearest x/y coordinate, and add a magic offset of less than
        // half a pixel.  Without this, rounding error can cause the bottom and
        // top of a label to be one pixel off, which results in a noticeable
        // distortion in the text.
        final float MAGIC_OFFSET = 0.25f;
        screenPos.x = (int) screenPos.x + MAGIC_OFFSET;
        screenPos.y = (int) screenPos.y + MAGIC_OFFSET;

        gl.glPushMatrix();

        gl.glTranslatef((float) screenPos.x, (float) screenPos.y, 0);
        gl.glRotatef(180 / (float) Math.PI * getRenderState().getUpAngle(), 0, 0, -1);
        gl.glScalef(label.getWidthInPixels(), label.getHeightInPixels(), 1);

        gl.glVertexPointer(2, GL10.GL_FIXED, 0, quadBuffer);
        gl.glTexCoordPointer(2, GL10.GL_FIXED, 0, label.getTexCoords());
        if (getRenderState().getNightVisionMode()) {
            gl.glColor4x(MathsUtils.ONE, 0, 0, label.fixedA);
        } else {
            gl.glColor4x(label.fixedR, label.fixedG, label.fixedB, label.fixedA);
        }
        gl.glDrawArrays(GL10.GL_TRIANGLE_STRIP, 0, 4);
        gl.glPopMatrix();
    }

    /**
     * A private class which extends the LabelMaker's label data with an xyz position and rgba color values.
     * For the red-eye mode, it's easier to set the color in the texture to white and set the color when we render
     * the label than to have two textures, one with red labels and one without.
     */
    private static class Label extends LabelMaker.LabelData {

        public float x;
        public float y;
        public float z;
        // The distance this should be rendered underneath the specified position, in world coordinates.
        public float offset;
        // Fixed point color values
        public int fixedR;
        public int fixedG;
        public int fixedB;
        public int fixedA;

        public Label(TextSource ts) {
            super(ts.getText(), 0xffffffff, ts.getFontSize());
            if (ts.getText() == null || ts.getText().isEmpty()) {
                throw new RuntimeException("Bad Label: " + ts.getClass());
            }

            x = (float) ts.getLocation().x;
            y = (float) ts.getLocation().y;
            z = (float) ts.getLocation().z;

            offset = ts.getOffset();

            int rgb = ts.getColor();
            int a = 0xff;
            int r = (rgb >> 16) & 0xff;
            int g = (rgb >> 8) & 0xff;
            int b = rgb & 0xff;
            fixedA = MathsUtils.floatToFixedPoint(a / 255.0f);
            fixedB = MathsUtils.floatToFixedPoint(b / 255.0f);
            fixedG = MathsUtils.floatToFixedPoint(g / 255.0f);
            fixedR = MathsUtils.floatToFixedPoint(r / 255.0f);
        }
    }
}