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
import android.text.Spannable;
import android.text.SpannableString;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import org.indilib.i4j.Constants;
import org.indilib.i4j.client.INDIProperty;
import org.indilib.i4j.client.INDITextElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.marcocipriani01.telescopetouch.R;
import io.github.marcocipriani01.telescopetouch.TelescopeTouchApp;

public class TextPropPref extends PropPref<INDITextElement> {

    public TextPropPref(Context context, INDIProperty<INDITextElement> prop) {
        super(context, prop);
    }

    /**
     * Create the summary rich-text string
     *
     * @return the summary
     */
    @Override
    protected Spannable createSummary() {
        List<INDITextElement> elements = prop.getElementsAsList();
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
            return new SpannableString(getContext().getString(R.string.no_indi_elements));
        }
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        if (!Objects.requireNonNull(getSummary()).toString().equals(context.getString(R.string.no_indi_elements))) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final List<INDITextElement> elements = prop.getElementsAsList();
            final ArrayList<EditText> editTextViews = new ArrayList<>(elements.size());
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            int padding = context.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            layoutParams.setMargins(padding, 0, padding, 0);

            for (INDITextElement element : elements) {
                TextView textView = new TextView(context);
                textView.setText(element.getLabel());
                textView.setPadding(padding, padding, padding, 0);
                layout.addView(textView, layoutParams);
                EditText editText = new EditText(context);
                editText.setText(element.getValueAsString().trim());
                editText.setPadding(padding, padding, padding, padding);
                editText.setEnabled(prop.getPermission() != Constants.PropertyPermissions.RO);
                editTextViews.add(editText);
                layout.addView(editText, layoutParams);
            }

            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(layout);
            builder.setView(scrollView).setTitle(prop.getLabel());

            if (prop.getPermission() != Constants.PropertyPermissions.RO) {
                builder.setPositiveButton(R.string.send_request, (dialog, id) -> {
                    try {
                        for (int i = 0; i < elements.size(); i++) {
                            elements.get(i).setDesiredValue(editTextViews.get(i).getText().toString());
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
            builder.setIcon(R.drawable.edit).show();
        }
    }
}