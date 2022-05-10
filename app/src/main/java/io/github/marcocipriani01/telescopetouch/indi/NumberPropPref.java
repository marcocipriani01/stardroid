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

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.indilib.i4j.Constants;
import org.indilib.i4j.client.INDIElement;
import org.indilib.i4j.client.INDINumberElement;
import org.indilib.i4j.client.INDIProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;

public class NumberPropPref extends PropPref<INDINumberElement> {

    public NumberPropPref(Context context, INDIProperty<INDINumberElement> prop) {
        super(context, prop);
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    public static void setSliderValues(Slider slider, float min, float max, float step, float value) {
        float diff = max - min;
        if (step > diff) step = diff;
        float mod = diff % step;
        if (mod > .0001) max -= mod;
        mod = (value - min) % step;
        if (mod > .0001) value -= mod;
        if (value > max)
            value = max;
        else if (value < min)
            value = min;
        synchronized (slider) {
            slider.setValueFrom(min);
            slider.setValueTo(max);
            slider.setStepSize(step);
            slider.setValue(value);
        }
    }

    public static void setSliderValues(Slider slider, INDINumberElement element) {
        setSliderValues(slider, (float) element.getMin(),
                (float) element.getMax(), (float) element.getStep(),
                (float) (double) element.getValue());
    }

    /**
     * Create the summary rich-text string
     *
     * @return the summary
     */
    @Override
    protected Spannable createSummary() {
        List<INDINumberElement> elements = prop.getElementsAsList();
        int count = elements.size();
        if (count > 0) {
            StringBuilder stringBuilder = new StringBuilder();
            int i;
            stringBuilder.append(elements.get(0).getLabel()).append(": ");
            for (i = 0; i < count - 1; i++) {
                stringBuilder.append(elements.get(i).getValueAsString().trim()).append(", ")
                        .append(elements.get(i + 1).getLabel()).append(": ");
            }
            stringBuilder.append(elements.get(i).getValueAsString().trim());
            return new SpannableString(stringBuilder.toString());
        } else {
            return new SpannableString(resources.getString(R.string.no_indi_elements));
        }
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        if (!Objects.requireNonNull(getSummary()).toString().equals(resources.getString(R.string.no_indi_elements))) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context)
                    .setTitle(prop.getLabel()).setIcon(R.drawable.edit);
            final List<INDINumberElement> elements = prop.getElementsAsList();
            final ArrayList<EditText> editTextViews = new ArrayList<>(elements.size());
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            int padding = resources.getDimensionPixelSize(R.dimen.padding_medium);
            layoutParams.setMargins(padding, 0, padding, 0);
            boolean notReadOnly = prop.getPermission() != Constants.PropertyPermissions.RO;

            for (INDINumberElement element : elements) {
                TextInputLayout inputLayout = new TextInputLayout(context);
                inputLayout.setPadding(padding, padding, padding, 0);
                inputLayout.setHint(element.getLabel());
                TextInputEditText editText = new TextInputEditText(context);
                editText.setText(element.getValueAsString().trim());
                editText.setEnabled(notReadOnly);
                editText.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                editTextViews.add(editText);
                inputLayout.addView(editText);
                layout.addView(inputLayout, layoutParams);

                float min = (float) element.getMin(),
                        max = (float) element.getMax();
                if (notReadOnly && ((max - min) <= 2000f)) {
                    Slider slider = new Slider(context);
                    slider.setThumbStrokeColorResource(R.color.colorAccent);
                    ColorStateList colorStateList = ColorStateList.valueOf(resources.getColor(R.color.colorAccent));
                    slider.setHaloTintList(colorStateList);
                    slider.setTickTintList(colorStateList);
                    slider.setTrackActiveTintList(colorStateList);
                    slider.setThumbTintList(colorStateList);
                    slider.setTickVisible(false);
                    slider.setTrackHeight(resources.getDimensionPixelSize(R.dimen.slider_track_height));
                    slider.setPadding(padding * 2, 0, padding * 2, padding);
                    setSliderValues(slider, min, max, (float) element.getStep(), (float) (double) element.getValue());
                    slider.addOnChangeListener((slider1, value, fromUser) -> {
                        if (fromUser)
                            editText.setText(String.valueOf(value));
                    });
                    editText.addTextChangedListener(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            try {
                                float value = Float.parseFloat(s.toString());
                                if ((value >= min) && (value <= max)) slider.setValue(value);
                            } catch (NumberFormatException ignored) {
                            }
                        }
                    });
                    layout.addView(slider, layoutParams);
                }
            }

            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(layout);
            builder.setView(scrollView);

            if (notReadOnly) {
                builder.setPositiveButton(R.string.send_request, (dialog, id) -> {
                    try {
                        for (int i = 0; i < elements.size(); i++) {
                            INDIElement element = elements.get(i);
                            String s = editTextViews.get(i).getText().toString();
                            if (element.checkCorrectValue(s)) element.setDesiredValue(s);
                        }
                    } catch (Exception e) {
                        Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        TelescopeTouchApp.connectionManager.log(e);
                    }
                    connectionManager.updateProperties(prop);
                });
                builder.setNegativeButton(android.R.string.cancel, null);
            } else {
                builder.setNegativeButton(R.string.back_request, null);
            }
            builder.show();
        }
    }
}