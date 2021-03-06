/*
    This file is part of dgAlert Classic.

    dgAlert Classic is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    dgAlert Classic is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with dgAlert Classic.  If not, see <http://www.gnu.org/licenses/>.
*/
package com.blau.android.screenon.preferences;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Color;
import android.os.Parcelable;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.blau.android.screenon.DgAlertClassicUtils;
import com.blau.android.screenon.R;

public class CustomLEDColorListPreference extends ListPreference implements OnSeekBarChangeListener {
	private Context context;
	private static boolean dialogShowing;
	private SharedPreferences myPrefs = null;
	private String led_color;
	private String led_color_custom;
	private SeekBar redSeekBar;
	private SeekBar greenSeekBar;
	private SeekBar blueSeekBar;

	private TextView redTV;
	private TextView greenTV;
	private TextView blueTV;
	private ImageView previewIV;		
	NotificationManager myNM;
	
	public CustomLEDColorListPreference(Context c) {
	   super(c);
	   context = c;
	   myNM = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
   }

	public CustomLEDColorListPreference(Context c, AttributeSet attrs) {
	   super(c, attrs);
	   context = c;
	   myNM = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
   }
	
	@Override
   protected void onDialogClosed(boolean positiveResult) {
	   super.onDialogClosed(positiveResult);
	   
	   if (positiveResult) {	   
	   	getPrefs();
			if (context.getString(R.string.pref_custom_val).equals(led_color)) {
				showDialog();
			}
		}
	}

	private void getPrefs() {
		if (myPrefs == null) {
			myPrefs = PreferenceManager.getDefaultSharedPreferences(context);
		}
		
		led_color = myPrefs.getString(
				context.getString(R.string.pref_flashled_color_key),
		      context.getString(R.string.pref_flashled_default));

		led_color_custom = myPrefs.getString(
				context.getString(R.string.pref_flashled_color_custom_key),
				context.getString(R.string.pref_flashled_color_key));		
	}
	
	private void showDialog() {
		LayoutInflater inflater = (LayoutInflater) context
		      .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		int color = Color.parseColor(context.getString(R.string.pref_flashled_color_default));
		try {
			color = Color.parseColor(led_color_custom);
		} catch (IllegalArgumentException e) {
			// No need to do anything here
		}
		int red = Color.red(color);
		int green = Color.green(color);
		int blue = Color.blue(color);
		
		View v = inflater.inflate(R.layout.ledcolordialog, null);

		redSeekBar = (SeekBar) v.findViewById(R.id.RedSeekBar);
		greenSeekBar = (SeekBar) v.findViewById(R.id.GreenSeekBar);
		blueSeekBar = (SeekBar) v.findViewById(R.id.BlueSeekBar);

		redTV = (TextView) v.findViewById(R.id.RedTextView);
		greenTV = (TextView) v.findViewById(R.id.GreenTextView);
		blueTV = (TextView) v.findViewById(R.id.BlueTextView);
		
		previewIV = (ImageView) v.findViewById(R.id.PreviewImageView);
		
		redSeekBar.setProgress(red);
		greenSeekBar.setProgress(green);
		blueSeekBar.setProgress(blue);
		
		redSeekBar.setOnSeekBarChangeListener(this);
		greenSeekBar.setOnSeekBarChangeListener(this);
		blueSeekBar.setOnSeekBarChangeListener(this);
		
		updateSeekBarTextView(redSeekBar);
		updateSeekBarTextView(greenSeekBar);
		updateSeekBarTextView(blueSeekBar);
		
		updateColorImageView();
		
		new AlertDialog.Builder(context)
			.setIcon(android.R.drawable.ic_dialog_info)
			.setTitle(R.string.pref_flashled_color_title)
			.setView(v)
			.setOnCancelListener(new OnCancelListener() {
				public void onCancel(DialogInterface dialog) {
					dialogShowing = false;
				}
			})			
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int whichButton) {
		      	dialogShowing = false;
			    myNM.cancel(R.string.app_name);
		      }
			})
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
		      public void onClick(DialogInterface dialog, int whichButton) {
			      SharedPreferences.Editor settings = myPrefs.edit();
			      int red = redSeekBar.getProgress();
			      int green = greenSeekBar.getProgress();
			      int blue = blueSeekBar.getProgress();
			      int color = Color.rgb(red, green, blue);
			      
			      dialogShowing = false;
			      settings.putString(
			      		context.getString(R.string.pref_flashled_color_custom_key), 
			      		"#" + Integer.toHexString(color));
				      
			      Toast.makeText(context,
			      		R.string.pref_flashled_color_custom_set,
			            Toast.LENGTH_LONG).show();
			      settings.commit();
			      myNM.cancel(R.string.app_name);
		      }}).show();
		dialogShowing = true;
	}

	@Override
   protected void onRestoreInstanceState(Parcelable state) {
		super.onRestoreInstanceState(state);
	   if (dialogShowing) {
	   	getPrefs();
			showDialog();
	   }	   
   }

	@Override
   protected View onCreateDialogView() {
		dialogShowing = false;
	   return super.onCreateDialogView();
   }
	
	public void onProgressChanged(SeekBar seekbar, int progress, boolean fromTouch) {
		updateSeekBarTextView(seekbar, progress);
		updateColorImageView();
	}

	private void updateSeekBarTextView(SeekBar seekbar) {
		updateSeekBarTextView(seekbar, seekbar.getProgress());		
	}
	
	private void updateSeekBarTextView(SeekBar seekbar, int progress) {
		if (seekbar.equals(redSeekBar)) {
			redTV.setText(
					context.getString(R.string.pref_flashled_color_custom_dialog_red)
						+ " " + progress);
		} else if (seekbar.equals(greenSeekBar)) {
			greenTV.setText(
					context.getString(R.string.pref_flashled_color_custom_dialog_green)
						+ " " + progress);			
		} else if (seekbar.equals(blueSeekBar)) {
			blueTV.setText(
					context.getString(R.string.pref_flashled_color_custom_dialog_blue)
						+ " " + progress);
		}
	}
		
	private void updateColorImageView() {
		previewIV.setBackgroundColor(Color.rgb(
				redSeekBar.getProgress(),
				greenSeekBar.getProgress(),
				blueSeekBar.getProgress()));
		Notification notification = new Notification();//R.drawable.icon, "LED Test", System.currentTimeMillis());
		//Color.parseColor(flashLedCol);
		//Intent smsIntent = DgAlertClassicUtils.getSmsIntent();

		//notification.setLatestEventInfo(context, "LED Test", "LED Test", PendingIntent.getActivity(context, 0, smsIntent, 0));
		notification.flags |= Notification.FLAG_SHOW_LIGHTS;
		notification.ledOffMS = 0;
		notification.ledOnMS = 1;
		notification.ledARGB = Color.argb(255,
				(int)(redSeekBar.getProgress()),
				(int)(greenSeekBar.getProgress()),
				(int)(blueSeekBar.getProgress()));
		myNM.notify(R.string.app_name, notification);
	}

	public void onStartTrackingTouch(SeekBar seekbar) {
	}

	public void onStopTrackingTouch(SeekBar seekbar) {
	}
}