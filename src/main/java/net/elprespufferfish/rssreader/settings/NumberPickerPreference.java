package net.elprespufferfish.rssreader.settings;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

import net.elprespufferfish.rssreader.R;

/**
 * Number picker dialog.
 *
 * @author elprespufferfish
 */
public class NumberPickerPreference extends DialogPreference {

    private NumberPicker picker;
    private int value;

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogLayoutResource(R.layout.number_picker);
    }

    public NumberPickerPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setDialogLayoutResource(R.layout.number_picker);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        picker = (NumberPicker) view.findViewById(R.id.number_picker);
        picker.setMinValue(1);
        picker.setMaxValue(31);
        picker.setValue(value);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (!positiveResult) {
            return;
        }

        picker.clearFocus();
        int newValue = picker.getValue();
        if (callChangeListener(newValue)) {
            setValue(newValue);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray typedArray, int index) {
        return typedArray.getInt(index, 1);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue){
        setValue(restorePersistedValue ? getPersistedInt(1) : (Integer) defaultValue);
    }

    private void setValue(int value) {
        this.value = value;
        persistInt(this.value);
    }

}

