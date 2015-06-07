package com.soreepeong.darknova.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.SeekBar;

import com.soreepeong.darknova.R;
import com.soreepeong.darknova.drawable.WaveProgressDrawable;

/**
 * @author Soreepeong
 */
public class ComponentTestActivity extends Activity {

	WaveProgressDrawable d = new WaveProgressDrawable();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.debug);
		((ImageView) findViewById(R.id.viewer)).setImageDrawable(d);
		((SeekBar) findViewById(R.id.seekbar)).setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				d.setProgressPercentage(progress);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {

			}
		});
	}

}
