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
package com.blau.android.screenon;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.preference.PreferenceManager;

public class ReminderReceiverService extends Service {
	public static final String ACTION_REMIND = "com.blau.android.screenon.ACTION_REMIND";
	public static final String ACTION_OTHER = "com.blau.android.screenon.ACTION_OTHER";

	private Context context;
   private ServiceHandler mServiceHandler;
	private Looper mServiceLooper;
	
	static final Object mStartingServiceSync = new Object();
	static PowerManager.WakeLock mStartingService;
	
	@Override
	public void onCreate() {
		Log.v("ReminderReceiverService: onCreate()");
		HandlerThread thread = new HandlerThread(Log.TAG, Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();
		context = getApplicationContext();
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);	
	}
	
   @Override
	public void onStart(Intent intent, int startId) {
   	Log.v("ReminderReceiverService: onStart()");
		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		msg.obj = intent;
		mServiceHandler.sendMessage(msg);
	}

	@Override
	public void onDestroy() {
		Log.v("ReminderReceiverService: onDestroy()");
		mServiceLooper.quit();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
   private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			Log.v("ReminderReceiverService: handleMessage()");
			
			int serviceId = msg.arg1;
			Intent intent = (Intent) msg.obj;
			String action = intent.getAction();
			// String dataType = intent.getType();

			Log.v("ReminderReceiverService: action = " + action);
			if (ACTION_REMIND.equals(action)) {
				Log.v("ReminderReceiverService: processReminder()");
				processReminder(intent);
			} else if (Intent.ACTION_DELETE.equals(action)) {
				Log.v("ReminderReceiverService: cancelReminder()");
				ReminderReceiver.cancelReminder(context);
			}

			// NOTE: We MUST not call stopSelf() directly, since we need to
			// make sure the wake lock acquired by AlertReceiver is released.
			finishStartingService(ReminderReceiverService.this, serviceId);
		}
	}
   
   private void processReminder(Intent intent) {
		int unreadSms = DgAlertClassicUtils.getUnreadMessagesCount(context);
		if (unreadSms > 0) {
			Bundle b = intent.getExtras();
			SmsMmsMessage message = new SmsMmsMessage(context, b);

			SharedPreferences myPrefs = PreferenceManager.getDefaultSharedPreferences(context);
			int repeat_times = Integer.parseInt(myPrefs.getString(
					context.getString(R.string.pref_notif_repeat_times_key), 
					context.getString(R.string.pref_notif_repeat_times_default)));

			// values of repeat_times as follows:
			// -1 repeat indefinitely
			// positive value is exact number of repeats
			if (message.getReminderCount() <= repeat_times || repeat_times == -1) {
				ManageNotification.show(context, message);
				ReminderReceiver.scheduleReminder(context, message);
				if (myPrefs.getBoolean(
						context.getString(R.string.pref_notif_repeat_screen_on_key),
						Boolean.parseBoolean(
								context.getString(R.string.pref_notif_repeat_screen_on_default)))) {
					ManageWakeLock.acquireFull(context);
				}
					
			}
		}

	}

   /**
	 * Start the service to process the current event notifications, acquiring
	 * the wake lock before returning to ensure that the service will run.
	 */
	public static void beginStartingService(Context context, Intent intent) {
		synchronized (mStartingServiceSync) {
			Log.v("ReminderReceiverService: beginStartingService()");
			if (mStartingService == null) {
				PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
				mStartingService = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				      Log.TAG);
				mStartingService.setReferenceCounted(false);
			}
			mStartingService.acquire();
			context.startService(intent);
		}
	}

	/**
	 * Called back by the service when it has finished processing notifications,
	 * releasing the wake lock if the service is now stopping.
	 */
	public static void finishStartingService(Service service, int startId) {
		synchronized (mStartingServiceSync) {
			Log.v("ReminderReceiverService: finishStartingService()");
			if (mStartingService != null) {
				if (service.stopSelfResult(startId)) {
					mStartingService.release();
				}
			}
		}
	}
}