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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;
import android.preference.PreferenceManager;

public class ManageWakeLock {
	private static PowerManager.WakeLock myWakeLock = null;
	private static PowerManager.WakeLock myPartialWakeLock = null;
	private static PowerManager myPM = null;
	
	public static synchronized void setPM(Context context) {
		if (myPM == null) {
			myPM = (PowerManager) context.getSystemService(Context.POWER_SERVICE);			
		}
	}

	public static synchronized void acquireFull(Context context) {
		setPM(context);
		
		if (myWakeLock != null) {
			//myWakeLock.release();
			Log.v("**Wakelock already held");
			return;
		}
		
		SharedPreferences myPrefs = PreferenceManager.getDefaultSharedPreferences(context);

		//ManageKeyguard.disableKeyguard(context);
		
		int flags;
		
		if (myPrefs.getBoolean(context.getString(R.string.pref_dimscreen_key), 
				Boolean.parseBoolean(context.getString(R.string.pref_dimscreen_default)))) {
			flags = PowerManager.SCREEN_DIM_WAKE_LOCK;			
		} else {
			flags = PowerManager.SCREEN_BRIGHT_WAKE_LOCK;
		}
		
		flags |= 
			PowerManager.ACQUIRE_CAUSES_WAKEUP; 
			//PowerManager.ON_AFTER_RELEASE;

		myWakeLock = myPM.newWakeLock(flags, Log.TAG);
		Log.v("**Wakelock acquired");
		myWakeLock.setReferenceCounted(false);
		myWakeLock.acquire();
				
		//Fetch wakelock/screen timeout from preferences
		int timeout = Integer.valueOf(
				myPrefs.getString(
						context.getString(R.string.pref_timeout_key),
						context.getString(R.string.pref_timeout_default)));

		//Set a receiver to remove all locks in "timeout" seconds
		ClearAllReceiver.setCancel(context, timeout);		
	}

	public static synchronized void acquirePartial(Context context) {
		setPM(context);
		
		if (myPartialWakeLock != null) {
			return;
		}
		myPartialWakeLock = myPM.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Log.TAG + ": partial");
		Log.v("**Wakelock (partial) acquired");
		myPartialWakeLock.setReferenceCounted(false);
		myPartialWakeLock.acquire();
	}

	public static synchronized void releaseFull() {
		if (myWakeLock != null) {
			Log.v("**Wakelock released");
			myWakeLock.release();
			myWakeLock = null;
		}
	}
	
	public static synchronized void releasePartial() {
		if (myPartialWakeLock != null) {
			Log.v("**Wakelock (partial) released");
			myPartialWakeLock.release();
			myPartialWakeLock = null;
		}
	}	

	public static synchronized void releaseAll() {		
		releaseFull();
		releasePartial();
	}
	
	// This is not supported by the API at this time :(
//	public static synchronized void goToSleep(Context context) {
//		setPM(context);
//		myPM.goToSleep(SystemClock.uptimeMillis() + 2000);
//		
//	}
}