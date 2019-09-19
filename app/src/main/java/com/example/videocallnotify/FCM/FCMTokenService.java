package com.example.videocallnotify.FCM;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.videocallnotify.R;
import com.google.firebase.messaging.FirebaseMessagingService;

public class FCMTokenService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String s) {


        String token = s;
        Log.d("Firebase Token", token);
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences(getString(R.string.FCM_PREF), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(getString(R.string.FCM_TOKEN), token);

        editor.commit();
    }
}
