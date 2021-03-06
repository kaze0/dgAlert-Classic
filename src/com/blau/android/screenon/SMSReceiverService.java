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
import android.telephony.gsm.SmsMessage;

public class SMSReceiverService extends Service {
	private static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
	private static final String ACTION_MMS_RECEIVED = "android.provider.Telephony.WAP_PUSH_RECEIVED";
	private static final String MMS_DATA_TYPE = "application/vnd.wap.mms-message";
   //private static final String ACTION_MESSAGE_SENT = "com.android.mms.transaction.MESSAGE_SENT";

	private Context context;
   private ServiceHandler mServiceHandler;
	private Looper mServiceLooper;
//	private int mResultCode;
	
	static final Object mStartingServiceSync = new Object();
	static PowerManager.WakeLock mStartingService;
	
	@Override
	public void onCreate() {
		Log.v("SMSReceiverService: onCreate()");
		HandlerThread thread = new HandlerThread(Log.TAG, Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();
		context = getApplicationContext();
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);	
	}
	
   @Override
	public void onStart(Intent intent, int startId) {
   	Log.v("SMSReceiverService: onStart()");
		
   	//mResultCode = intent.getIntExtra("result", 0);
		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		msg.obj = intent;
		mServiceHandler.sendMessage(msg);
	}

	@Override
	public void onDestroy() {
		Log.v("SMSReceiverService: onDestroy()");
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
			//TODO: Mike DG
			SharedPreferences myPrefs = PreferenceManager.getDefaultSharedPreferences(context);
			boolean screenOn = myPrefs.getBoolean(context
			      .getString(R.string.pref_screen_key), Boolean.valueOf(context
			      .getString(R.string.pref_screen_default)));
			
			Log.v("SMSReceiverService.handleMessage trying to turn screen on");
			//TODO: need to handle turning the screen off itself, because it wont turn on if we get
			//a quic second txt
			//put bug in google code
			if (screenOn)
			{
				ManageWakeLock.acquireFull(context);
			}
			
		

			Log.v("SMSReceiverService: handleMessage()");
			
			int serviceId = msg.arg1;
			Intent intent = (Intent) msg.obj;
			String action = intent.getAction();
			String dataType = intent.getType();

			if (ACTION_SMS_RECEIVED.equals(action)) {
				handleSmsReceived(intent);
			} else if (ACTION_MMS_RECEIVED.equals(action) && MMS_DATA_TYPE.equals(dataType)) {
				handleMmsReceived(intent);
//			} else if (ACTION_MESSAGE_SENT.equals(action)) {
//				handleMessageSent(intent);
			}

			// NOTE: We MUST not call stopSelf() directly, since we need to
			// make sure the wake lock acquired by AlertReceiver is released.
			finishStartingService(SMSReceiverService.this, serviceId);
		}
	}

	private void handleSmsReceived(Intent intent) {
		Log.v("SMSReceiver: Intercept SMS");
		StringBuilder body = new StringBuilder();
		Bundle bundle = intent.getExtras();
		if (bundle != null) {

			SharedPreferences myPrefs = PreferenceManager.getDefaultSharedPreferences(context);
			boolean onlyShowOnKeyguard = myPrefs.getBoolean(context
			      .getString(R.string.pref_onlyShowOnKeyguard_key), Boolean.valueOf(context
			      .getString(R.string.pref_onlyShowOnKeyguard_default)));

			SmsMessage[] messages = DgAlertClassicUtils.getMessagesFromIntent(intent);
			if (messages != null) {
				SmsMessage sms = messages[0];

				if (sms.getMessageClass() == SmsMessage.MessageClass.CLASS_0 || sms.isReplace()) {
					// TODO: can remove this
					// ManageWakeLock.releasePartial();
				} else {

					for (int i = 0; i < messages.length; i++) {
						body.append(messages[i].getMessageBody());
					}

					String address = messages[0].getOriginatingAddress();
					Log.v("sms address: " + address);
					Log.v("sms body: " + body);

					long timestamp = messages[0].getTimestampMillis();
					String message = body.toString();

					SmsMmsMessage smsMessage = new SmsMmsMessage(context, address, message, timestamp,
					      SmsMmsMessage.MESSAGE_TYPE_SMS);
					
					//ManageKeyguard.initialize(context);

					Log.v("^^^^^^Not in keyguard, only using notification");
					ManageNotification.show(context, smsMessage);
					ReminderReceiver.scheduleReminder(context, smsMessage);
				}
			}
		}
   }

	private void handleMmsReceived(Intent intent) {
		Log.v("MMS received!");
		SmsMmsMessage mmsMessage = null;
		int count = 0;
		int MMS_RETRY = 5;
		int MMS_RETRY_PAUSE = 2000;
		
		// Ok this is super hacky, but fixes the case where this code
		// runs before the system MMS transaction service (that stores
		// the MMS details in the database).  This should really be
		// a content listener that waits for a while then gives up...
		while (mmsMessage == null && count < MMS_RETRY) {
			mmsMessage = DgAlertClassicUtils.getMmsDetails(context);
			if (mmsMessage != null) {
				Log.v("MMS found in content provider");
				SharedPreferences myPrefs =
					PreferenceManager.getDefaultSharedPreferences(context);
				boolean onlyShowOnKeyguard = myPrefs.getBoolean(
						context.getString(R.string.pref_onlyShowOnKeyguard_key),
						Boolean.valueOf(
								context.getString(R.string.pref_onlyShowOnKeyguard_default)));
	
				Log.v("^^^^^^Not in keyguard, only using notification");
				ManageNotification.show(context, mmsMessage);
				ReminderReceiver.scheduleReminder(context, mmsMessage);
			} else {				
				Log.v("MMS not found, sleeping (count is " + count + ")");
				count++;
				try {
					Thread.sleep(MMS_RETRY_PAUSE);
				} catch (InterruptedException e) {
					//e.printStackTrace();
				}
			}
		}
	}

//	Unfortunately the system Mms app does not broadcast messages being sent to
// all receivers.
//	private void handleMessageSent(Intent intent) {
//		if (mResultCode != Activity.RESULT_OK && mResultCode != SmsManager.RESULT_ERROR_RADIO_OFF) {
//		}
//	}

   /**
	 * Start the service to process the current event notifications, acquiring
	 * the wake lock before returning to ensure that the service will run.
	 */
	public static void beginStartingService(Context context, Intent intent) {
		synchronized (mStartingServiceSync) {
			Log.v("SMSReceiverService: beginStartingService()");
			if (mStartingService == null) {
				PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
				//TODO: Mike DG
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
			Log.v("SMSReceiverService: finishStartingService()");
			if (mStartingService != null) {
				if (service.stopSelfResult(startId)) {
					mStartingService.release();
				}
			}
		}
	}
}