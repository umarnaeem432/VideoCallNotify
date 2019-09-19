package com.example.videocallnotify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.twilio.video.CameraCapturer;
import com.twilio.video.CameraCapturer.CameraSource;
import com.twilio.video.ConnectOptions;
import com.twilio.video.LocalAudioTrack;
import com.twilio.video.LocalParticipant;
import com.twilio.video.LocalVideoTrack;
import com.twilio.video.RemoteAudioTrack;
import com.twilio.video.RemoteAudioTrackPublication;
import com.twilio.video.RemoteDataTrack;
import com.twilio.video.RemoteDataTrackPublication;
import com.twilio.video.RemoteParticipant;
import com.twilio.video.RemoteVideoTrack;
import com.twilio.video.RemoteVideoTrackPublication;
import com.twilio.video.Room;
import com.twilio.video.TwilioException;
import com.twilio.video.Video;
import com.twilio.video.VideoTrack;
import com.twilio.video.VideoView;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class VideoActivity extends AppCompatActivity {

    /*
     * Android application UI elements
     */

    private VideoView primaryVideoView;
    private VideoView thumbnailVideoView;
    private LinearLayout callerImageLayout;
    private TextView statusTextView;
    private FloatingActionButton pickCallBtn, endCallBtn, rejectCallBtn;
    private Uri notification;
    private Ringtone r;

    // Twillio Objects
    private Room room;
    private LocalParticipant localParticipant;
    private CameraCapturer cameraCapturer;
    private LocalAudioTrack localAudioTrack;
    private LocalVideoTrack localVideoTrack;
    private AudioManager audioManager;
    private Vibrator vibrator;


    // Others
    private Intent intent; // to get the data from the FirebaseMessagingService and also to start the MainActivity when the call is ended or rejected.
    private boolean isSpeakerPhoneEnabled = true;
    private String remoteParticipantId ;
    private boolean rejected = false; // This will decide whether the call is ended or rejected.
    private String roomName; // Name of the room the receiver will connect to.

    private static final int CAMERA_MIC_PERMISSION_REQUEST_CODE = 1;
    private static final String LOCAL_AUDIO_TRACK_NAME = "mic";
    private static final String LOCAL_VIDEO_TRACK_NAME = "camera";
    private static final String ACCESS_TOKEN_SERVER = "http://192.168.10.6:3000/token/"; // this is the link to the server api that will return the twillio access token.
    private String accessToken; // Needed to connect to room
//    private final long vibrates[] = {100, 200, 300};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_video);
        notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        r = RingtoneManager.getRingtone(this, notification);
        r.play(); // play the ringtone when activity starts
        rejected = false;
        // Initialize all the member variables
        intent = getIntent();
        roomName = intent.getStringExtra("roomName");

        callerImageLayout =  findViewById(R.id.callersImageLayout);
        statusTextView = findViewById(R.id.statusTextView);
        pickCallBtn = findViewById(R.id.pickCall);
        endCallBtn = findViewById(R.id.endCall);
        rejectCallBtn = findViewById(R.id.rejectCall);
        primaryVideoView = findViewById(R.id.primaryVideoView);
        thumbnailVideoView = findViewById(R.id.thumbnailVideoView);
        primaryVideoView = findViewById(R.id.primaryVideoView);
        thumbnailVideoView = findViewById(R.id.thumbnailVideoView);


        // to use volume up and down btns.
        setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        audioManager.setSpeakerphoneOn(isSpeakerPhoneEnabled);


        if(!checkPermissionForCameraAndMicrophone()) {
            requestPermissionForCameraAndMicrophone();
        } else {
            Log.d("Status", "Its Working");
            createAudioVideoTracks();
        }


        /**
         * When user taps on the pickCallBtn, the app will send request for twillio access token.
         * After that the app will try to connect to the room.
         * If the connection is successful, the user connected event will generate and the caller will handle that event as needed.
         */
        pickCallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getAccessTokenFromServer();
                vibrator.cancel();
                r.stop();
            }
        });


        /**
         * When user taps on the endCallBtn, there are two situations,
         * 1: The call is successful and user just wants to end the call.
         * 2: User just wants to reject the call.
         * In case 1, we will disconnect from the room and the disconnect event will generate and the caller will know that the reciever has disconnected.
         * In case 2, we first need to connect to room and then disconnect, so that we can send the disconnect event to the caller to let him know that reciever has disconnected.
         */

        rejectCallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // if user wants to reject the call.
                vibrator.cancel();
                r.stop();
                rejected = true;
                if(rejected) {
                    // connect to room.
                    getAccessTokenFromServer();
                    return;
                }
            }
        });

        endCallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // if the call was successful and user just wants to end the call.

                if(room != null) {
                    room.disconnect();
                    if (localAudioTrack != null) {
                        localAudioTrack.release();
                        localAudioTrack = null;
                    }
                    if (localVideoTrack != null) {
                        localVideoTrack.release();
                        localVideoTrack = null;
                    }
                    endCall(); // will release all the resources.
                }

            }
        });
    }

    private void endCall() {

        VideoActivity.this.room = null;

        // Start the MainActivity when the user reject or end the call.
        intent = new Intent(VideoActivity.this, MainActivity.class);
        startActivity(intent);
    }

    /**
     * Checks for Mic and Camera permissions
     * @return true if Allowed and false otherwise.
     */

    private boolean checkPermissionForCameraAndMicrophone() {
        int resultCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        int resultMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        return resultCamera == PackageManager.PERMISSION_GRANTED &&
                resultMic == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Requests for Mic and Camera permissions.
     */
    private void requestPermissionForCameraAndMicrophone() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) ||
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.RECORD_AUDIO)) {
            Log.i("PERMISSIONS", "MIC AND CAMERA PERMISSIONS NOT GRANTED");
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_MIC_PERMISSION_REQUEST_CODE);
        }
    }

    /**
     * called when the activity is created after asking Camera and Mic permissions
     * Initializes the localAudio and localVideo tracks.
     * Also renders the localVideoTrack to the primaryView.
     */
    private void createAudioVideoTracks() {
        localAudioTrack = LocalAudioTrack.create(this, isSpeakerPhoneEnabled, LOCAL_AUDIO_TRACK_NAME);
        cameraCapturer = new CameraCapturer(this, CameraSource.FRONT_CAMERA);
        localVideoTrack = LocalVideoTrack.create(this,isSpeakerPhoneEnabled ,cameraCapturer, LOCAL_VIDEO_TRACK_NAME);
        primaryVideoView.setMirror(true);
        localVideoTrack.addRenderer(primaryVideoView);
    }


    /**
     * Makes a HTTP request to the TWILIO_ACCESS_TOKEN_SERVER.
     * On Success, it stores the return response(String accessToken) into the member variable of this class i.e this.accessToken.
     * Calls the connectToRoom() method when the accessToken has been set.
     */
    private void getAccessTokenFromServer() {

        String finalUrl = String.format("%s%s", ACCESS_TOKEN_SERVER, UUID.randomUUID().toString());
        Log.d("Server URL", finalUrl);


        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder().url(finalUrl).build();


        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
//                Toast.makeText(getApplicationContext(), "Something went wrong!", Toast.LENGTH_SHORT);
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if(response.isSuccessful()) {
                    accessToken = response.body().string();
                    Log.d("Twillio Token", accessToken);
                    if(localAudioTrack != null && localVideoTrack != null) {
                        connectToRoom(roomName, accessToken);
                    }
                }
            }
        });

    }

    /**
     * Connects to the room with audio and video tracks and roomName and accessToken passed to it.
     * @param roomName
     * @param token
     */
    public void connectToRoom(String roomName, String token) {

        if(localAudioTrack != null && localVideoTrack != null) {
            ConnectOptions connectOptions = new ConnectOptions.Builder(token)
                    .roomName(roomName)
                    .audioTracks(Collections.singletonList(localAudioTrack))
                    .videoTracks(Collections.singletonList(localVideoTrack))
                    .enableAutomaticSubscription(true)
                    .build();

            room = Video.connect(this, connectOptions, getRoomListenert());
        }

    }

    /**
     * Handler for room events like Reciver Connected to room.
     * Caller Conneced to room.
     * Disconnected from room, reconnected etc.
     * @return Room.Listener
     */
    private Room.Listener getRoomListenert() {
        String roomStatus = "Room Status";
        return new Room.Listener() {
            @Override
            public void onConnected(@NonNull Room room) {

                    statusTextView.setText("Connected to " + room.getName());

                    localParticipant = room.getLocalParticipant();


                    // If user wants to reject the call, the value of the 'reject' variable will be true.
                    if(rejected) {
                        if(room != null) {
                            room.disconnect(); // disconnect from the room.
                        }
                        endCall();
                        return;
                    }

                    RemoteParticipant remoteParticipant = room.getRemoteParticipants().get(0);
                    addRemoteParticipant(remoteParticipant);

                    callerImageLayout.setVisibility(View.GONE);
            }

            @Override
            public void onConnectFailure(@NonNull Room room, @NonNull TwilioException twilioException) {
                Log.i(roomStatus, "Failed to connect to room!");
            }

            @Override
            public void onReconnecting(@NonNull Room room, @NonNull TwilioException twilioException) {
                Log.i(roomStatus, "Reconnecting");
            }

            @Override
            public void onReconnected(@NonNull Room room) {
                Log.i(roomStatus, "Reconnected to Room!");
            }

            @Override
            public void onDisconnected(@NonNull Room room, @Nullable TwilioException twilioException) {
                Log.i(roomStatus, "Disconnected");
            }

            @Override
            public void onParticipantConnected(@NonNull Room room, @NonNull RemoteParticipant remoteParticipant) {
                Log.i(roomStatus, remoteParticipant.getIdentity() + "is Connected to Room");
            }

            @Override
            public void onParticipantDisconnected(@NonNull Room room, @NonNull RemoteParticipant remoteParticipant) {
                Log.i(roomStatus,remoteParticipant.getIdentity() + "has left the room");

                if (!remoteParticipant.getRemoteVideoTracks().isEmpty()) {
                    RemoteVideoTrackPublication remoteVideoTrackPublication =
                            remoteParticipant.getRemoteVideoTracks().get(0);

                    /*
                     * Remove video only if subscribed to participant track
                     */

                    if (remoteVideoTrackPublication.isTrackSubscribed()) {
                        removeParticipantVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
                    }
                }

                endCall();
            }

            @Override
            public void onRecordingStarted(@NonNull Room room) {

                Log.i(roomStatus, "Recording has started!");
            }

            @Override
            public void onRecordingStopped(@NonNull Room room) {
                Log.i(roomStatus, "Recording has stooped!");
            }
        };
    }

//    @SuppressLint("RestrictedApi")
//    private void moveLocalVideoToPrimaryView() {
//
//        if (thumbnailVideoView.getVisibility() == View.VISIBLE) {
//            thumbnailVideoView.setVisibility(View.GONE);
//            pickCallBtn.setVisibility(View.VISIBLE);
//            if (localVideoTrack != null) {
//                localVideoTrack.removeRenderer(thumbnailVideoView);
//                localVideoTrack.addRenderer(primaryVideoView);
//            }
//            primaryVideoView.setMirror(true);
//        }
//    }

    /**
     * called when the participant disconnected or the call is ended.
     * @param remoteVideoTrack
     */
    private void removeParticipantVideo(RemoteVideoTrack remoteVideoTrack) {
        remoteVideoTrack.removeRenderer(primaryVideoView);
    }

    /**
     * Initialize the remoteVideoTracks and call the addRemoteVideo() method to display the Callers video.
     * @param remoteParticipant
     */

    private void addRemoteParticipant(RemoteParticipant remoteParticipant) {
        remoteParticipantId = remoteParticipant.getIdentity();
        if(remoteParticipant.getRemoteVideoTracks().size() > 0) {
            RemoteVideoTrackPublication remoteVideoTrackPublication = remoteParticipant.getRemoteVideoTracks().get(0);

            if(remoteVideoTrackPublication.isTrackSubscribed()) {
                addRemoteVideo(remoteVideoTrackPublication.getRemoteVideoTrack());
            }
        }

        remoteParticipant.setListener(remoteParticipantListener());
    }
    /**
     * returns the RemoteParticipant event handler to handle events like caller disconnected and connected events.
     * @return
     */
    private RemoteParticipant.Listener remoteParticipantListener() {
        return new RemoteParticipant.Listener() {
            @Override
            public void onAudioTrackPublished(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onAudioTrackUnpublished(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onAudioTrackSubscribed(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteAudioTrackPublication remoteAudioTrackPublication, @NonNull RemoteAudioTrack remoteAudioTrack) {

            }

            @Override
            public void onAudioTrackSubscriptionFailed(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteAudioTrackPublication remoteAudioTrackPublication, @NonNull TwilioException twilioException) {

            }

            @Override
            public void onAudioTrackUnsubscribed(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteAudioTrackPublication remoteAudioTrackPublication, @NonNull RemoteAudioTrack remoteAudioTrack) {

            }

            @Override
            public void onVideoTrackPublished(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackUnpublished(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackSubscribed(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteVideoTrackPublication remoteVideoTrackPublication, @NonNull RemoteVideoTrack remoteVideoTrack) {
                addRemoteVideo(remoteVideoTrack);
            }

            @Override
            public void onVideoTrackSubscriptionFailed(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteVideoTrackPublication remoteVideoTrackPublication, @NonNull TwilioException twilioException) {

            }

            @Override
            public void onVideoTrackUnsubscribed(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteVideoTrackPublication remoteVideoTrackPublication, @NonNull RemoteVideoTrack remoteVideoTrack) {
                removeParticipantVideo(remoteVideoTrack);
            }

            @Override
            public void onDataTrackPublished(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteDataTrackPublication remoteDataTrackPublication) {

            }

            @Override
            public void onDataTrackUnpublished(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteDataTrackPublication remoteDataTrackPublication) {

            }

            @Override
            public void onDataTrackSubscribed(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteDataTrackPublication remoteDataTrackPublication, @NonNull RemoteDataTrack remoteDataTrack) {

            }

            @Override
            public void onDataTrackSubscriptionFailed(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteDataTrackPublication remoteDataTrackPublication, @NonNull TwilioException twilioException) {

            }

            @Override
            public void onDataTrackUnsubscribed(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteDataTrackPublication remoteDataTrackPublication, @NonNull RemoteDataTrack remoteDataTrack) {

            }

            @Override
            public void onAudioTrackEnabled(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onAudioTrackDisabled(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteAudioTrackPublication remoteAudioTrackPublication) {

            }

            @Override
            public void onVideoTrackEnabled(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }

            @Override
            public void onVideoTrackDisabled(@NonNull RemoteParticipant remoteParticipant, @NonNull RemoteVideoTrackPublication remoteVideoTrackPublication) {

            }
        };
    }


    /**
     * Called when the reciver is successfully connected to the room.
     * It will move the localVideo to the thumbnail VideoView by calling the moveLocalVideoToThumbnail().
     * Renders the video of the caller to the primaryVideoView.
     * @param remoteVideoTrack
     */
    private void addRemoteVideo(VideoTrack remoteVideoTrack) {
        moveLocalVideoToThumbnail();
        primaryVideoView.setMirror(false);
        remoteVideoTrack.addRenderer(primaryVideoView);
    }

    /**
     * Moves the video of the receiver to the thumbnail VideoView.
     * Hides the pickCallBtn and rejectCallBtn
     * Shows the endCallBtn
     */
    @SuppressLint("RestrictedApi")
    private void moveLocalVideoToThumbnail() {
        if(thumbnailVideoView.getVisibility() == View.GONE) {
            pickCallBtn.setVisibility(View.GONE);
            rejectCallBtn.setVisibility(View.GONE);
            endCallBtn.setVisibility(View.VISIBLE);
            thumbnailVideoView.setVisibility(View.VISIBLE);
            localVideoTrack.removeRenderer(primaryVideoView);
            localVideoTrack.addRenderer(thumbnailVideoView);
            thumbnailVideoView.setMirror(true);
        }
    }

    /**
     * called when we press a volume down key or volume up key to stop the ringtone
     * @param keyCode
     * @param event
     * @return
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            r.stop();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(localAudioTrack == null && checkPermissionForCameraAndMicrophone()) {
            localAudioTrack = LocalAudioTrack.create(this, isSpeakerPhoneEnabled, LOCAL_AUDIO_TRACK_NAME);
        }
        if(localVideoTrack == null && checkPermissionForCameraAndMicrophone()) {
            localVideoTrack = LocalVideoTrack.create(this,isSpeakerPhoneEnabled ,cameraCapturer, LOCAL_VIDEO_TRACK_NAME);
            localVideoTrack.addRenderer(primaryVideoView);
        }
    }

    @Override
    protected void onDestroy() {
        if(room != null) {
            room.disconnect();
        }
        // Release the audio track to free native memory resources
        if(localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if(localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }
        localParticipant = null;
        VideoActivity.this.room = null;
        super.onDestroy();
    }

    @Override
    protected void onPause() {

        // Release the audio track to free native memory resources
        if(localAudioTrack != null) {
            localAudioTrack.release();
            localAudioTrack = null;
        }
        if(localVideoTrack != null) {
            localVideoTrack.release();
            localVideoTrack = null;
        }
        super.onPause();
    }
}
