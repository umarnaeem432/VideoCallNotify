package com.example.videocallnotify.FCM;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.videocallnotify.R;
import com.example.videocallnotify.VideoActivity;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class MyFirebaseMessagingService extends FirebaseMessagingService{


    @Override
    public void onNewToken(String s) {
//        Token: d9p_8IVfuBQ:APA91bGwfWWLikn0Ir4IdBhoGZGwfAS1pZ7HbqCEhsBoWzpipkX393F2jVGw4_TUQwZzA_5Dm8Cnrz0JeYcZ-T_XsOb5q5N7zwovgnhqdMYMrBCtGT-QjHcjaxzIKLfYgwKO2KvVsAL5
        String token = FirebaseInstanceId.getInstance().getToken();
        Log.d("Firebase Token", token);
        SharedPreferences sharedPreferences = getApplicationContext().getSharedPreferences(getString(R.string.FCM_PREF), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();

        editor.putString(getString(R.string.FCM_TOKEN), token);

        editor.commit();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {

        Map<String, String> data = remoteMessage.getData();

        String title = data.get("title");
        String message = data.get("text");
        String roomName = data.get("roomName");


        Log.i("Notification", "{title: " + title + "\nmessage:" + message + "\nroomName:" + roomName + "}");

        Intent intent = new Intent(this, VideoActivity.class);
        intent.putExtra("roomName", roomName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
//        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
//        String channelId = "Default";
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
//                .setSmallIcon(R.mipmap.ic_launcher)
//                .setContentTitle(title)
//                .setContentText(message).setAutoCancel(true).setContentIntent(pendingIntent);
//
//        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationChannel channel = new NotificationChannel(channelId, "Default channel", NotificationManager.IMPORTANCE_DEFAULT);
//            manager.createNotificationChannel(channel);
//        }
//        manager.notify(0, builder.build());

    }
}
