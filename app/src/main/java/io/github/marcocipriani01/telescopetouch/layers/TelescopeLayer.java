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

package io.github.marcocipriani01.telescopetouch.layers;

import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

import android.content.res.Resources;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.astronomy.GeocentricCoordinates;
import io.github.marcocipriani01.telescopetouch.renderer.RendererObjectManager;
import io.github.marcocipriani01.telescopetouch.source.AstronomicalSource;
import io.github.marcocipriani01.telescopetouch.source.ImageSource;
import io.github.marcocipriani01.telescopetouch.source.TextSource;

public class TelescopeLayer extends AbstractLayer {

    public static final int DEPTH_ORDER = 100;
    public static final String PREFERENCE_ID = "source_provider.7";
    private static final int LABEL_COLOR = 0xff6f00;
    private static final float SIZE_ON_MAP = 0.03f;

    public TelescopeLayer(Resources resources) {
        super(resources, true);
    }

    @Override
    protected void initializeAstroSources(List<AstronomicalSource> sources) {
        sources.add(new TelescopeSource());
    }

    public String getPreferenceId() {
        return PREFERENCE_ID;
    }

    @Override
    public int getLayerDepthOrder() {
        return DEPTH_ORDER;
    }

    @Override
    protected int getLayerNameId() {
        return R.string.telescope;
    }

    private class TelescopeSource extends AstronomicalSource {

        private final List<ImageSource> imageSources = Collections.synchronizedList(new ArrayList<>());
        private final List<TextSource> labelSources = Collections.synchronizedList(new ArrayList<>());
        private final GeocentricCoordinates coordinates = new GeocentricCoordinates();
        private long lastUpdateTimeMs = 0L;
        private TextSource textSource;
        private ImageSource imageSource;

        @Override
        public AstronomicalSource initialize() {
            Resources resources = getResources();
            coordinates.updateFromRaDec(connectionManager.telescopeCoordinates);
            imageSource = new ImageSource(coordinates, resources, R.drawable.telescope_crosshair, SIZE_ON_MAP);
            imageSources.add(imageSource);
            textSource = new TextSource(coordinates, getName(), LABEL_COLOR, 0.035f, 15);
            labelSources.add(textSource);
            return this;
        }

        @Override
        public EnumSet<RendererObjectManager.UpdateType> update() {
            EnumSet<RendererObjectManager.UpdateType> updates = EnumSet.noneOf(RendererObjectManager.UpdateType.class);
            long time = System.currentTimeMillis();
            if (Math.abs(time - lastUpdateTimeMs) > 200) {
                lastUpdateTimeMs = time;
                coordinates.updateFromRaDec(connectionManager.telescopeCoordinates);
                updates.add(RendererObjectManager.UpdateType.UpdatePositions);
                textSource.setText(getName());
                imageSource.resetUpVector();
            }
            return updates;
        }

        @Override
        public List<String> getNames() {
            return Collections.singletonList(getName());
        }

        private String getName() {
            return (connectionManager.telescopeName == null) ? getResources().getString(R.string.no_telescope) : connectionManager.telescopeName;
        }

        @Override
        public GeocentricCoordinates getSearchLocation() {
            return coordinates;
        }

        @Override
        public List<? extends ImageSource> getImages() {
            return imageSources;
        }

        @Override
        public List<? extends TextSource> getLabels() {
            return labelSources;
        }
    }
}