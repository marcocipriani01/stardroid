/*
 * Copyright 2020 Marco Cipriani (@marcocipriani01) and the Sky Map Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.marcocipriani01.telescopetouch.layers;

import android.content.res.Resources;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.astronomy.GeocentricCoordinates;
import io.github.marcocipriani01.telescopetouch.astronomy.TimeUtils;
import io.github.marcocipriani01.telescopetouch.control.AstronomerModel;
import io.github.marcocipriani01.telescopetouch.renderer.RendererObjectManager.UpdateType;
import io.github.marcocipriani01.telescopetouch.source.AstronomicalSource;
import io.github.marcocipriani01.telescopetouch.source.LineSource;
import io.github.marcocipriani01.telescopetouch.source.Sources;
import io.github.marcocipriani01.telescopetouch.source.TextSource;
import io.github.marcocipriani01.telescopetouch.source.impl.LineSourceImpl;
import io.github.marcocipriani01.telescopetouch.source.impl.TextSourceImpl;

/**
 * Creates a mark at the zenith, nadir and cardinal point and a horizon.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
public class HorizonLayer extends AbstractLayer {

    public static final int DEPTH_ORDER = 40;
    public static final String PREFERENCE_ID = "source_provider.5";
    private final AstronomerModel model;

    public HorizonLayer(AstronomerModel model, Resources resources) {
        super(resources, true);
        this.model = model;
    }

    @Override
    protected void initializeAstroSources(ArrayList<AstronomicalSource> sources) {
        sources.add(new HorizonSource(model, getResources()));
    }

    @Override
    public int getLayerDepthOrder() {
        return DEPTH_ORDER;
    }

    @Override
    public String getPreferenceId() {
        return PREFERENCE_ID;
    }

    @Override
    protected int getLayerNameId() {
        return R.string.horizon;
    }

    /**
     * Implementation of {@link AstronomicalSource} for the horizon source.
     */
    static class HorizonSource extends AstronomicalSource {
        // Due to a bug in the G1 rendering code text and lines render in different
        // colors.
        private static final int LINE_COLOR = Color.argb(120, 86, 176, 245);
        private static final int LABEL_COLOR = Color.argb(120, 245, 176, 86);
        private static final long UPDATE_FREQ_MS = TimeUtils.MILLISECONDS_PER_SECOND;

        private final GeocentricCoordinates zenith = new GeocentricCoordinates();
        private final GeocentricCoordinates nadir = new GeocentricCoordinates();
        private final GeocentricCoordinates north = new GeocentricCoordinates();
        private final GeocentricCoordinates south = new GeocentricCoordinates();
        private final GeocentricCoordinates east = new GeocentricCoordinates();
        private final GeocentricCoordinates west = new GeocentricCoordinates();

        private final ArrayList<LineSource> lineSources = new ArrayList<>();
        private final ArrayList<TextSource> textSources = new ArrayList<>();
        private final AstronomerModel model;

        private long lastUpdateTimeMs = 0L;

        public HorizonSource(AstronomerModel model, Resources res) {
            this.model = model;

            List<GeocentricCoordinates> vertices = Arrays.asList(north, east, south, west, north);
            lineSources.add(new LineSourceImpl(LINE_COLOR, vertices, 1.5f));

            textSources.add(new TextSourceImpl(zenith, res.getString(R.string.zenith), LABEL_COLOR));
            textSources.add(new TextSourceImpl(nadir, res.getString(R.string.nadir), LABEL_COLOR));
            textSources.add(new TextSourceImpl(north, res.getString(R.string.north), LABEL_COLOR));
            textSources.add(new TextSourceImpl(south, res.getString(R.string.south), LABEL_COLOR));
            textSources.add(new TextSourceImpl(east, res.getString(R.string.east), LABEL_COLOR));
            textSources.add(new TextSourceImpl(west, res.getString(R.string.west), LABEL_COLOR));
        }

        private void updateCoords() {
            // Blog.d(this, "Updating Coords: " + (model.getTime().getTime() - lastUpdateTimeMs));

            this.lastUpdateTimeMs = model.getTime().getTimeInMillis();
            this.zenith.assign(model.getZenith());
            this.nadir.assign(model.getNadir());
            this.north.assign(model.getNorth());
            this.south.assign(model.getSouth());
            this.east.assign(model.getEast());
            this.west.assign(model.getWest());
        }

        @Override
        public Sources initialize() {
            updateCoords();
            return this;
        }

        @Override
        public EnumSet<UpdateType> update() {
            EnumSet<UpdateType> updateTypes = EnumSet.noneOf(UpdateType.class);
            // TODO(brent): Add distance here.
            if (Math.abs(model.getTime().getTimeInMillis() - lastUpdateTimeMs) > UPDATE_FREQ_MS) {
                updateCoords();
                updateTypes.add(UpdateType.UpdatePositions);
            }
            return updateTypes;
        }

        @Override
        public List<? extends TextSource> getLabels() {
            return textSources;
        }

        @Override
        public List<? extends LineSource> getLines() {
            return lineSources;
        }
    }
}