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

package io.github.marcocipriani01.telescopetouch.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.marcocipriani01.telescopetouch.astronomy.EquatorialCoordinates;
import io.github.marcocipriani01.telescopetouch.astronomy.GeocentricCoordinates;

/**
 * For representing constellations, constellation boundaries etc.
 */
public class LineSource extends AbstractSource implements Colorable {

    public final List<GeocentricCoordinates> vertices;
    public final List<EquatorialCoordinates> raDecs;
    public final float lineWidth;

    public LineSource(int color) {
        this(color, new ArrayList<>(), 1.5f);
    }

    public LineSource(int color, List<GeocentricCoordinates> vertices, float lineWidth) {
        super(color);
        this.vertices = vertices;
        this.raDecs = new ArrayList<>();
        this.lineWidth = lineWidth;
    }

    /**
     * Returns the width of the line to be drawn.
     */
    public float getLineWidth() {
        return lineWidth;
    }

    /**
     * Returns an ordered list of the vertices which should be used to draw a
     * polyline in the renderer.
     */
    public List<GeocentricCoordinates> getVertices() {
        return Collections.unmodifiableList((vertices != null) ? vertices : new ArrayList<>());
    }
}