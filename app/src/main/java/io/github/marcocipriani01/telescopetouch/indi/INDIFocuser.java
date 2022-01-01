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

package io.github.marcocipriani01.telescopetouch.indi;

import static io.github.marcocipriani01.telescopetouch.TelescopeTouchApp.connectionManager;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import org.indilib.i4j.Constants;
import org.indilib.i4j.client.INDIDevice;
import org.indilib.i4j.client.INDINumberElement;
import org.indilib.i4j.client.INDINumberProperty;
import org.indilib.i4j.client.INDIProperty;
import org.indilib.i4j.client.INDIPropertyListener;
import org.indilib.i4j.client.INDISwitchElement;
import org.indilib.i4j.client.INDISwitchProperty;
import org.indilib.i4j.client.INDIValueException;
import org.indilib.i4j.properties.INDIStandardElement;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;

public class INDIFocuser implements INDIPropertyListener {

    private static final String TAG = TelescopeTouchApp.getTag(INDIFocuser.class);
    public final INDIDevice device;
    private final Handler uiHandler;
    private final Set<FocuserListener> listeners = new HashSet<>();
    public volatile INDISwitchProperty directionP = null;
    public volatile INDISwitchElement inwardDirectionE = null;
    public volatile INDISwitchElement outwardDirectionE = null;
    public volatile INDINumberProperty relPositionP = null;
    public volatile INDINumberElement relPositionE = null;
    public volatile INDINumberProperty absPositionP = null;
    public volatile INDINumberElement absPositionE = null;
    public volatile INDINumberProperty syncPositionP = null;
    public volatile INDINumberElement syncPositionE = null;
    public volatile INDINumberProperty speedP = null;
    public volatile INDINumberElement speedE = null;
    public volatile INDISwitchProperty abortP = null;
    public volatile INDISwitchElement abortE = null;

    public INDIFocuser(INDIDevice device, Handler uiHandler) {
        this.device = device;
        this.uiHandler = uiHandler;
    }

    public static boolean isFocuserProp(INDIProperty<?> property) {
        String name = property.getName();
        return name.startsWith("FOCUS_") || name.equals("ABS_FOCUS_POSITION") || name.equals("REL_FOCUS_POSITION");
    }

    public void addListener(FocuserListener listener) {
        synchronized (listeners) {
            this.listeners.add(listener);
        }
    }

    public void removeListener(FocuserListener listener) {
        synchronized (listeners) {
            this.listeners.remove(listener);
        }
    }

    public void abort() throws INDIValueException {
        if (!canAbort())
            throw new UnsupportedOperationException("Unsupported abort!");
        abortE.setDesiredValue(Constants.SwitchStatus.ON);
        connectionManager.updateProperties(abortP);
    }

    public void setAbsolutePosition(int val) throws INDIValueException {
        if (!hasAbsolutePosition())
            throw new UnsupportedOperationException("Unsupported absolute position!");
        absPositionE.setDesiredValue((double) val);
        connectionManager.updateProperties(absPositionP);
    }

    public void sync(int position) throws INDIValueException {
        if (!canSync())
            throw new UnsupportedOperationException("Unsupported sync!");
        syncPositionE.setDesiredValue((double) position);
        connectionManager.updateProperties(syncPositionP);
    }

    public void moveOutward(int steps) throws INDIValueException {
        if ((!hasDirection()) || (!hasRelativePosition()))
            throw new UnsupportedOperationException("Unsupported relative movement!");
        outwardDirectionE.setDesiredValue(Constants.SwitchStatus.ON);
        inwardDirectionE.setDesiredValue(Constants.SwitchStatus.OFF);
        relPositionE.setDesiredValue((double) steps);
        connectionManager.updateProperties(directionP, relPositionP);
    }

    public void moveInward(int steps) throws INDIValueException {
        if (!canMoveRelative())
            throw new UnsupportedOperationException("Unsupported relative movement!");
        inwardDirectionE.setDesiredValue(Constants.SwitchStatus.ON);
        outwardDirectionE.setDesiredValue(Constants.SwitchStatus.OFF);
        relPositionE.setDesiredValue((double) steps);
        connectionManager.updateProperties(directionP, relPositionP);
    }

    public void setSpeed(double val) throws INDIValueException {
        if (!hasSpeed())
            throw new UnsupportedOperationException("Unsupported speed!");
        speedE.setDesiredValue(val);
        connectionManager.updateProperties(speedP);
    }

    public boolean canMoveRelative() {
        return (directionP != null) && (inwardDirectionE != null) && (outwardDirectionE != null) &&
                (relPositionP != null) && (relPositionE != null);
    }

    public boolean hasDirection() {
        return (directionP != null) && (inwardDirectionE != null) && (outwardDirectionE != null);
    }

    public boolean hasRelativePosition() {
        return (relPositionP != null) && (relPositionE != null);
    }

    public boolean hasAbsolutePosition() {
        return (absPositionP != null) && (absPositionE != null);
    }

    public boolean canSync() {
        return (syncPositionP != null) && (syncPositionE != null);
    }

    public boolean hasSpeed() {
        return (speedP != null) && (speedE != null);
    }

    public boolean canAbort() {
        return (abortP != null) && (abortE != null);
    }

    @Override
    public void propertyChanged(INDIProperty<?> indiProperty) {
        if (indiProperty == absPositionP) {
            uiHandler.post(() -> {
                synchronized (listeners) {
                    for (FocuserListener listener : listeners) {
                        listener.onFocuserPositionChange((int) (double) absPositionE.getValue());
                    }
                }
            });
        } else if (indiProperty == speedP) {
            uiHandler.post(() -> {
                synchronized (listeners) {
                    for (FocuserListener listener : listeners) {
                        listener.onFocuserSpeedChange((int) (double) speedE.getValue());
                    }
                }
            });
        }
    }

    public synchronized void processNewProp(INDIProperty<?> property) {
        String name = property.getName(), devName = device.getName();
        Log.i(TAG, "New Property (" + name + ") added to focuser " + devName
                + ", elements: " + Arrays.toString(property.getElementNames()));
        switch (name) {
            case "ABS_FOCUS_POSITION":
                if ((absPositionE = (INDINumberElement) property.getElement(INDIStandardElement.FOCUS_ABSOLUTE_POSITION)) != null) {
                    property.addINDIPropertyListener(this);
                    absPositionP = (INDINumberProperty) property;
                }
                break;
            case "REL_FOCUS_POSITION":
                if ((relPositionE = (INDINumberElement) property.getElement(INDIStandardElement.FOCUS_RELATIVE_POSITION)) != null) {
                    relPositionP = (INDINumberProperty) property;
                }
                break;
            case "FOCUS_MOTION":
                if (((inwardDirectionE = (INDISwitchElement) property.getElement(INDIStandardElement.FOCUS_INWARD)) != null)
                        && ((outwardDirectionE = (INDISwitchElement) property.getElement(INDIStandardElement.FOCUS_OUTWARD)) != null)) {
                    directionP = (INDISwitchProperty) property;
                }
                break;
            case "FOCUS_ABORT_MOTION":
                if ((abortE = (INDISwitchElement) property.getElement(INDIStandardElement.ABORT)) != null) {
                    abortP = (INDISwitchProperty) property;
                }
                break;
            case "FOCUS_SPEED":
                if ((speedE = (INDINumberElement) property.getElement(INDIStandardElement.FOCUS_SPEED_VALUE)) != null) {
                    speedP = (INDINumberProperty) property;
                    property.addINDIPropertyListener(this);
                }
                break;
            case "FOCUS_SYNC":
                if ((syncPositionE = (INDINumberElement) property.getElement(INDIStandardElement.FOCUS_SYNC_VALUE)) != null) {
                    syncPositionP = (INDINumberProperty) property;
                }
                break;
            default:
                return;
        }
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (FocuserListener listener : listeners) {
                    listener.onFocuserFunctionsChange();
                }
            }
        });
    }

    public synchronized boolean removeProp(INDIProperty<?> property) {
        switch (property.getName()) {
            case "REL_FOCUS_POSITION":
                relPositionE = null;
                relPositionP = null;
                break;
            case "FOCUS_MOTION":
                inwardDirectionE = outwardDirectionE = null;
                directionP = null;
                break;
            case "FOCUS_ABORT_MOTION":
                abortE = null;
                abortP = null;
                break;
            case "ABS_FOCUS_POSITION":
                property.removeINDIPropertyListener(this);
                absPositionP = null;
                absPositionE = null;
                break;
            case "FOCUS_SPEED":
                property.removeINDIPropertyListener(this);
                speedP = null;
                speedE = null;
                break;
            case "FOCUS_SYNC":
                syncPositionP = null;
                syncPositionE = null;
                break;
            default:
                return false;
        }
        uiHandler.post(() -> {
            synchronized (listeners) {
                for (FocuserListener listener : listeners) {
                    listener.onFocuserFunctionsChange();
                }
            }
        });
        return (directionP == null) && (relPositionP == null) && (absPositionP == null) &&
                (syncPositionP == null) && (speedP == null) && (abortP == null);
    }

    public void terminate() {
        synchronized (listeners) {
            listeners.clear();
        }
        relPositionP = null;
        relPositionE = null;
        absPositionP = null;
        absPositionE = null;
        directionP = null;
        outwardDirectionE = inwardDirectionE = null;
        syncPositionP = null;
        syncPositionE = null;
        speedP = null;
        speedE = null;
        abortP = null;
        abortE = null;
    }

    @NonNull
    @Override
    public String toString() {
        return device.getName();
    }

    public interface FocuserListener {

        void onFocuserFunctionsChange();

        void onFocuserPositionChange(int position);

        void onFocuserSpeedChange(int value);
    }
}