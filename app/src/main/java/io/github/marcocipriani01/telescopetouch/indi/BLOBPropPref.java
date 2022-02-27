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

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import org.indilib.i4j.client.INDIBLOBElement;
import org.indilib.i4j.client.INDIProperty;

import java.util.List;
import java.util.Objects;

import io.github.marcocipriani01.telescopetouch.R;

public class BLOBPropPref extends PropPref<INDIBLOBElement> {

    public BLOBPropPref(Context context, INDIProperty<INDIBLOBElement> prop) {
        super(context, prop);
    }

    /**
     * Create the summary rich-text string
     *
     * @return the summary
     */
    @Override
    protected Spannable createSummary() {
        List<INDIBLOBElement> elements = prop.getElementsAsList();
        int count = elements.size();
        if (count > 0) {
            StringBuilder stringBuilder = new StringBuilder();
            int i;
            stringBuilder.append(elements.get(0).getLabel()).append(": ");
            for (i = 0; i < count - 1; i++) {
                stringBuilder.append(elements.get(i).getValueAsString()).append(", ")
                        .append(elements.get(i + 1).getLabel()).append(": ");
            }
            stringBuilder.append(elements.get(i).getValueAsString());
            return new SpannableString(stringBuilder.toString());
        } else {
            return new SpannableString(resources.getString(R.string.no_indi_elements));
        }
    }

    @Override
    protected void onClick() {
        Context context = getContext();
        if (!Objects.requireNonNull(getSummary()).toString().equals(resources.getString(R.string.no_indi_elements))) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            final List<INDIBLOBElement> elements = prop.getElementsAsList();
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            int padding = context.getResources().getDimensionPixelSize(R.dimen.padding_medium);
            layoutParams.setMargins(padding, 0, padding, 0);
            for (INDIBLOBElement element : elements) {
                TextView textView = new TextView(context);
                textView.setText(element.getLabel());
                textView.setPadding(padding, padding, padding, 0);
                layout.addView(textView, layoutParams);
                EditText editText = new EditText(context);
                editText.setText(element.getValueAsString());
                editText.setPadding(padding, padding, padding, padding);
                editText.setEnabled(false);
                layout.addView(editText, layoutParams);
            }
            TextView textView = new TextView(context);
            textView.setText(R.string.blob_open_viewer);
            textView.setPadding(padding, padding, padding, padding);
            layout.addView(textView, layoutParams);
            ScrollView scrollView = new ScrollView(context);
            scrollView.addView(layout);
            builder.setView(scrollView).setTitle(prop.getLabel())
                    .setNegativeButton(R.string.back_request, null)
                    .setIcon(R.drawable.edit).show();
        }
    }
}