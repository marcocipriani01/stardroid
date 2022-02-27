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

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;
import io.github.marcocipriani01.telescopetouch.renderer.RendererObjectManager.UpdateType;
import io.github.marcocipriani01.telescopetouch.source.AstronomicalSource;
import io.github.marcocipriani01.telescopetouch.source.ProtobufAstronomicalSource;
import io.github.marcocipriani01.telescopetouch.source.proto.SourceProto.AstronomicalSourceProto;
import io.github.marcocipriani01.telescopetouch.source.proto.SourceProto.AstronomicalSourcesProto;

/**
 * Implementation of the {@link Layer} interface which reads its data from
 * a file during the {@link Layer#initialize} method.
 *
 * @author Brent Bryan
 * @author John Taylor
 */
public abstract class AbstractFileBasedLayer extends AbstractLayer {

    private static final String TAG = TelescopeTouchApp.getTag(AbstractFileBasedLayer.class);
    private static final Executor BACKGROUND_EXECUTOR = Executors.newFixedThreadPool(1);
    private final AssetManager assetManager;
    private final String fileName;
    private final List<AstronomicalSource> fileSources = new ArrayList<>();

    public AbstractFileBasedLayer(AssetManager assetManager, Resources resources, String fileName) {
        super(resources, false);
        this.assetManager = assetManager;
        this.fileName = fileName;
    }

    @Override
    public synchronized void initialize() {
        BACKGROUND_EXECUTOR.execute(() -> {
            readSourceFile(fileName);
            AbstractFileBasedLayer.super.initialize();
        });
    }

    @Override
    protected void initializeAstroSources(List<AstronomicalSource> sources) {
        sources.addAll(fileSources);
    }

    private void readSourceFile(String sourceFilename) {
        Log.d(TAG, "Loading Proto File: " + sourceFilename + "...");
        try (InputStream in = assetManager.open(sourceFilename, AssetManager.ACCESS_BUFFER)) {
            AstronomicalSourcesProto sources = AstronomicalSourcesProto.parser().parseFrom(in);
            for (AstronomicalSourceProto proto : sources.getSourceList()) {
                fileSources.add(new ProtobufAstronomicalSource(proto, getResources()));
            }
            Log.d(TAG, "Found: " + fileSources.size() + " sources");
            Log.d(TAG, String.format("Finished Loading: %s | Found %s sources.\n", sourceFilename, fileSources.size()));
            refreshSources(EnumSet.of(UpdateType.Reset));
        } catch (IOException e) {
            Log.e(TAG, "Unable to open " + sourceFilename);
        }
    }
}