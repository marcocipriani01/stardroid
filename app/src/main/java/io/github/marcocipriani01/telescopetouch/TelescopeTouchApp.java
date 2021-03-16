/*
 * Copyright 2020 Marco Cipriani (@marcocipriani01)
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

package io.github.marcocipriani01.telescopetouch;

import android.app.Application;

import io.github.marcocipriani01.telescopetouch.indi.ConnectionManager;

/**
 * The main application class.
 *
 * @author marcocipriani01
 */
public class TelescopeTouchApp extends Application {

    /**
     * Global connection manager.
     */
    public static final ConnectionManager connectionManager = new ConnectionManager();
    public static final NSDHelper nsdHelper = new NSDHelper();
    private ApplicationComponent component;

    /**
     * Returns the Tag for a class to be used in Android logging statements
     */
    public static String getTag(Class<?> clazz) {
        return ApplicationConstants.APP_NAME + "." + clazz.getSimpleName();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        component = DaggerApplicationComponent.builder()
                .applicationModule(new ApplicationModule(this)).build();
        connectionManager.init(this);
    }

    public ApplicationComponent getApplicationComponent() {
        return component;
    }
}