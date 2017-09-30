package sg.com.temasys.skylink.sdk.sampleapp;

/**
 * Created by lavanyasudharsanam on 20/1/15.
 */

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Point;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.webrtc.SurfaceViewRenderer;

import java.util.Date;

import sg.com.temasys.skylink.sdk.listener.LifeCycleListener;
import sg.com.temasys.skylink.sdk.listener.MediaListener;
import sg.com.temasys.skylink.sdk.listener.OsListener;
import sg.com.temasys.skylink.sdk.listener.RemotePeerListener;
import sg.com.temasys.skylink.sdk.rtc.Errors;
import sg.com.temasys.skylink.sdk.rtc.SkylinkConfig;
import sg.com.temasys.skylink.sdk.rtc.SkylinkConnection;
import sg.com.temasys.skylink.sdk.rtc.SkylinkException;
import sg.com.temasys.skylink.sdk.rtc.UserInfo;
import sg.com.temasys.skylink.sdk.sampleapp.ConfigFragment.Config;

import static sg.com.temasys.skylink.sdk.sampleapp.Utils.getNumRemotePeers;
import static sg.com.temasys.skylink.sdk.sampleapp.Utils.permQReset;
import static sg.com.temasys.skylink.sdk.sampleapp.Utils.permQResume;

/**
 * This class is used to demonstrate the VideoCall between two clients in WebRTC
 */
public class VideoCallFragment extends Fragment
        implements LifeCycleListener, MediaListener, OsListener, RemotePeerListener {

    private String ROOM_NAME;
    private String MY_USER_NAME;

    //set height width for self-video when in call
    public static final int WIDTH = 350;
    public static final int HEIGHT = 350;
    private static final String TAG = VideoCallFragment.class.getCanonicalName();
    private static final String ARG_SECTION_NUMBER = "section_number";
    // Constants for configuration change
    private static final String BUNDLE_CONNECTING = "connecting";
    private static final String BUNDLE_AUDIO_MUTED = "audioMuted";
    private static final String BUNDLE_VIDEO_MUTED = "videoMuted";

    private static SkylinkConnection skylinkConnection;
    // Indicates if camera should be toggled after returning to app.
    // Generally, it should match whether it was toggled when moving away from app.
    // For e.g., if camera was already off, then it would not be toggled when moving away from app,
    // So toggleCamera would be set to false at onPause(), and at onCreateView,
    // it would not be toggled.
    private static boolean toggleCamera;

    private LinearLayout linearLayout;
    private Button toggleAudioButton;
    private Button toggleVideoButton;
    private Button toggleCameraButton;
    private Button disconnectButton;
    private Button btnEnterRoom;
    private EditText etRoomName;
    private boolean connecting = false;
    private String roomName;
    private boolean audioMuted;
    private boolean videoMuted;
    private Activity parentActivity;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        ROOM_NAME = Config.ROOM_NAME_VIDEO;
        MY_USER_NAME = Config.USER_NAME_VIDEO;

        View rootView = inflater.inflate(R.layout.fragment_video_call, container, false);
        linearLayout = (LinearLayout) rootView.findViewById(R.id.ll_video_call);
        btnEnterRoom = (Button) rootView.findViewById(R.id.btn_enter_room);
        etRoomName = (EditText) rootView.findViewById(R.id.et_room_name);
        etRoomName.setText(ROOM_NAME.toString());
        toggleAudioButton = (Button) rootView.findViewById(R.id.toggle_audio);
        toggleVideoButton = (Button) rootView.findViewById(R.id.toggle_video);
        toggleCameraButton = (Button) rootView.findViewById(R.id.toggle_camera);
        disconnectButton = (Button) rootView.findViewById(R.id.disconnect);

        // Check if it was an orientation change
        if (savedInstanceState != null) {
            // Resume previous permission request, if any.
            permQResume(getContext(), this, skylinkConnection);

            // Toggle camera back to previous state if required.
            if (toggleCamera) {
                if (getVideoView(null) != null) {
                    try {
                        skylinkConnection.toggleCamera();
                        toggleCamera = false;
                    } catch (SkylinkException e) {
                        Log.e(TAG, e.getMessage());
                    }
                }
            }

            connecting = savedInstanceState.getBoolean(BUNDLE_CONNECTING);
            // Set the appropriate UI if already connected.
            if (isConnected()) {
                // Set listeners to receive callbacks when events are triggered
                setListeners();
                audioMuted = savedInstanceState.getBoolean(BUNDLE_AUDIO_MUTED);
                videoMuted = savedInstanceState.getBoolean(BUNDLE_VIDEO_MUTED);
                // Set the appropriate UI if already connected.
                onConnectUIChange();
                addSelfView(getVideoView(null));
                addRemoteView();
            } else if (connecting) {
                // Set listeners to receive callbacks when events are triggered
                setListeners();
                onConnectingUIChange();
                addSelfView(getVideoView(null));
            } else {
                onDisconnectUIChange();
            }
        } else {
            // This is the start of this sample, reset permission request states.
            permQReset();

            // Set toggleCamera back to default state.
            toggleCamera = false;
        }

        // Set UI elements
        setAudioBtnLabel(false);
        setVideoBtnLabel(false);

        btnEnterRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectToRoom();
                onConnectingUIChange();
            }
        });

        toggleAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If audio is enabled, mute audio and if audio is enabled, mute it
                audioMuted = !audioMuted;
                skylinkConnection.muteLocalAudio(audioMuted);

                // Set UI and Toast.
                setAudioBtnLabel(true);
            }
        });

        toggleVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If video is enabled, mute video and if video is enabled, mute it
                videoMuted = !videoMuted;
                skylinkConnection.muteLocalVideo(videoMuted);

                // Set UI and Toast.
                setVideoBtnLabel(true);
            }
        });

        toggleCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String toast = "Toggled camera ";
                if (getVideoView(null) != null) {
                    try {
                        skylinkConnection.toggleCamera();
                        if (skylinkConnection.isCameraOn()) {
                            toast += "to restarted!";
                        } else {
                            toast += "to stopped!";
                        }
                    } catch (SkylinkException e) {
                        toast += "but failed as " + e.getMessage();
                    }
                } else {
                    toast += "but failed as local video is not available!";
                }
                //Toast.makeText(parentActivity, toast, Toast.LENGTH_SHORT).show();
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(parentActivity, "Clicked Disconnect!", Toast.LENGTH_SHORT).show();
                disconnectFromRoom();
                onDisconnectUIChange();
            }
        });

        return rootView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Allow volume to be controlled using volume keys
        parentActivity.setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((MainActivity) activity).onSectionAttached(
                getArguments().getInt(ARG_SECTION_NUMBER));
        parentActivity = getActivity();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Toggle camera back to previous state if required.
        if (toggleCamera) {
            if (getVideoView(null) != null) {
                try {
                    skylinkConnection.toggleCamera();
                    toggleCamera = false;
                } catch (SkylinkException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Stop local video source only if not changing orientation
        if (!parentActivity.isChangingConfigurations()) {
            if (getVideoView(null) != null) {
                // Stop local video source if it's on.
                try {
                    // Record if need to toggleCamera when resuming.
                    toggleCamera = skylinkConnection.toggleCamera(false);
                } catch (SkylinkException e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save states for fragment restart
        outState.putBoolean(BUNDLE_CONNECTING, connecting);
        outState.putBoolean(BUNDLE_AUDIO_MUTED, audioMuted);
        outState.putBoolean(BUNDLE_VIDEO_MUTED, videoMuted);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        disconnectFromRoom();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        Utils.onRequestPermissionsResultHandler(
                requestCode, permissions, grantResults, TAG, skylinkConnection);
    }

    /***
     * Skylink Helper methods
     */

    /**
     * Check if we are currently connected to the room.
     *
     * @return True if we are connected and false otherwise.
     */
    private boolean isConnected() {
        if (skylinkConnection != null) {
            return skylinkConnection.isConnected();
        }
        return false;
    }

    /**
     * Get room name from text field (or use default if not entered),
     * and connect to that room.
     * Initializes SkylinkConnection if not initialized.
     */
    private void connectToRoom() {
        roomName = etRoomName.getText().toString();

        String toast = "";
        // If roomName is not set through the UI, get the default roomName from Constants
        if (roomName.isEmpty()) {
            roomName = ROOM_NAME;
            etRoomName.setText(roomName);
            toast = "No room name provided, entering default video room \"" + roomName
                    + "\".";
        } else {
            toast = "Entering video room \"" + roomName + "\".";
        }
        Toast.makeText(parentActivity, toast, Toast.LENGTH_SHORT).show();

        // Initialize the skylink connection
        initializeSkylinkConnection();

        // Create the Skylink connection string.
        // In production, the connection string should be generated by an external entity
        // (such as a secure App server that has the Skylink App Key secret), and sent to the App.
        // This is to avoid keeping the App Key secret within the application, for better security.
        String skylinkConnectionString = Utils.getSkylinkConnectionString(
                roomName, new Date(), SkylinkConnection.DEFAULT_DURATION);

        // The skylinkConnectionString should not be logged in production,
        // as it contains potentially sensitive information like the Skylink App Key ID.

        boolean connectFailed;
        connectFailed = !skylinkConnection.connectToRoom(skylinkConnectionString, MY_USER_NAME);
        if (connectFailed) {
            Toast.makeText(parentActivity, "Unable to connect to room!", Toast.LENGTH_SHORT).show();
            return;
        }

        connecting = true;

        // Initialize and use the Audio router to switch between headphone and headset
        AudioRouter.startAudioRouting(parentActivity);
    }

    /**
     * Disconnect from room.
     */
    private void disconnectFromRoom() {
        // Close the room connection when this sample app is finished, so the streams can be closed.
        // I.e. already connecting/connected and not changing orientation.
        if (!parentActivity.isChangingConfigurations() && skylinkConnection != null
                && isConnected()) {
            if (skylinkConnection.disconnectFromRoom()) {
                connecting = false;
            }
            AudioRouter.stopAudioRouting(parentActivity.getApplicationContext());
        }
    }

    private SkylinkConfig getSkylinkConfig() {
        SkylinkConfig config = new SkylinkConfig();
        // AudioVideo config options can be:
        // NO_AUDIO_NO_VIDEO | AUDIO_ONLY | VIDEO_ONLY | AUDIO_AND_VIDEO
        config.setAudioVideoSendConfig(SkylinkConfig.AudioVideoConfig.AUDIO_AND_VIDEO);
        config.setAudioVideoReceiveConfig(SkylinkConfig.AudioVideoConfig.AUDIO_AND_VIDEO);
        config.setHasPeerMessaging(true);
        config.setHasFileTransfer(true);
        config.setMirrorLocalView(true);

        // Allow only 1 remote Peer to join.
        config.setMaxPeers(1); // Default is 4 remote Peers.

        // Set some common configs.
        Utils.skylinkConfigCommonOptions(config);
        return config;
    }

    private void initializeSkylinkConnection() {
        skylinkConnection = SkylinkConnection.getInstance();
        //the app_key and app_secret is obtained from the temasys developer console.
        skylinkConnection.init(Config.getAppKey(),
                getSkylinkConfig(), this.parentActivity.getApplicationContext());
        // Set listeners to receive callbacks when events are triggered
        setListeners();
    }

    /**
     * Set listeners to receive callbacks when events are triggered.
     * SkylinkConnection instance must not be null or listeners cannot be set.
     * Do not set before {@link SkylinkConnection#init} as that will remove all existing Listeners.
     *
     * @return false if listeners could not be set.
     */
    private boolean setListeners() {
        if (skylinkConnection != null) {
            skylinkConnection.setLifeCycleListener(this);
            skylinkConnection.setMediaListener(this);
            skylinkConnection.setOsListener(this);
            skylinkConnection.setRemotePeerListener(this);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Get peerId of a Peer using SkylinkConnection API.
     *
     * @param index 0 for self Peer, 1 onwards for remote Peer(s).
     * @return Desired peerId or null if not available.
     */
    private String getPeerId(int index) {
        if (skylinkConnection == null) {
            return null;
        }
        String[] peerIdList = skylinkConnection.getPeerIdList();
        // Ensure index does not exceed max index on peerIdList.
        if (index <= peerIdList.length - 1) {
            return peerIdList[index];
        } else {
            return null;
        }
    }

    /**
     * Get Video View of a given Peer using SkylinkConnection API.
     *
     * @param peerId null for self Peer.
     * @return Desired Video View or null if not present.
     */
    private SurfaceViewRenderer getVideoView(String peerId) {
        if (skylinkConnection == null) {
            return null;
        }
        return skylinkConnection.getVideoView(peerId);
    }

    //----------------------------------------------------------------------------------------------
    // UI helper methods
    //----------------------------------------------------------------------------------------------

    /**
     * Change certain UI elements once connected to room or when Peer(s) join or leave.
     */
    private void onConnectUIChange() {
        btnEnterRoom.setVisibility(View.GONE);
        etRoomName.setEnabled(false);
        toggleAudioButton.setVisibility(View.VISIBLE);
        toggleVideoButton.setVisibility(View.VISIBLE);
        toggleCameraButton.setVisibility(View.VISIBLE);
        disconnectButton.setVisibility(View.VISIBLE);
    }

    /**
     * Change certain UI elements when trying to connect to room.
     */
    private void onConnectingUIChange() {
        btnEnterRoom.setVisibility(View.GONE);
        etRoomName.setEnabled(false);
        toggleAudioButton.setVisibility(View.GONE);
        toggleVideoButton.setVisibility(View.GONE);
        toggleCameraButton.setVisibility(View.GONE);
        disconnectButton.setVisibility(View.VISIBLE);
    }

    /**
     * Change certain UI elements when disconnecting from room.
     */
    private void onDisconnectUIChange() {
        View self = linearLayout.findViewWithTag("self");
        if (self != null) {
            linearLayout.removeView(self);
        }

        View peer = linearLayout.findViewWithTag("peer");
        if (peer != null) {
            linearLayout.removeView(peer);
        }

        btnEnterRoom.setVisibility(View.VISIBLE);
        etRoomName.setEnabled(true);
        toggleAudioButton.setVisibility(View.GONE);
        toggleVideoButton.setVisibility(View.GONE);
        toggleCameraButton.setVisibility(View.GONE);
        disconnectButton.setVisibility(View.GONE);
    }

    /**
     * Add or update our self VideoView into the app.
     *
     * @param videoView
     */
    private void addSelfView(SurfaceViewRenderer videoView) {
        if (videoView != null) {
            // If previous self video exists,
            // Set new video to size of previous self video
            // And remove old self video.
            View self = linearLayout.findViewWithTag("self");
            if (self != null) {
                videoView.setLayoutParams(self.getLayoutParams());
                // Remove the old self video.
                linearLayout.removeView(self);
            }

            // Tag new video as self and add onClickListener.
            videoView.setTag("self");
            // Show room and self info, plus give option to
            // switch self view between different cameras (if any).
            videoView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (skylinkConnection != null) {
                        String name = Utils.getRoomPeerIdNick(skylinkConnection, ROOM_NAME,
                                skylinkConnection.getPeerId());
                        TextView selfTV = new TextView(getContext());
                        selfTV.setText(name);
                        selfTV.setTextIsSelectable(true);
                        AlertDialog.Builder selfDialogBuilder =
                                new AlertDialog.Builder(getContext());
                        selfDialogBuilder.setView(selfTV);
                        selfDialogBuilder.setPositiveButton("OK", null);
                        selfDialogBuilder.setNegativeButton("Switch Camera",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        skylinkConnection.switchCamera();
                                    }
                                });
                        selfDialogBuilder.show();
                    }
                }
            });

            // If peer video exists, remove it first.
            View peer = linearLayout.findViewWithTag("peer");
            if (peer != null) {
                linearLayout.removeView(peer);
            }

            // Show new video on screen
            // Remove video from previous parent, if any.
            Utils.removeViewFromParent(videoView);

            // And new self video.
            linearLayout.addView(videoView);

            // Return the peer video, if it was there before.
            if (peer != null) {
                linearLayout.addView(peer);
            }
        }
    }

    /**
     * Add or update remote Peer's VideoView into the app.
     */
    private void addRemoteView() {
        SurfaceViewRenderer videoView;
        String remotePeerId = getPeerId(1);
        // Proceed only if the first (& only) remote Peer has joined.
        if (remotePeerId == null) {
            return;
        } else {
            videoView = getVideoView(remotePeerId);
        }
        if (videoView == null) {
            return;
        }

        // Resize self view
        View self = linearLayout.findViewWithTag("self");
        if (self != null) {
            self.setLayoutParams(new ViewGroup.LayoutParams(WIDTH, HEIGHT));
            linearLayout.removeView(self);
            linearLayout.addView(self);
        }

        // Remove previous peer video if it exists
        View viewToRemove = linearLayout.findViewWithTag("peer");
        if (viewToRemove != null) {
            linearLayout.removeView(viewToRemove);
        }

        // Add new peer video
        videoView.setTag("peer");
        // Remove view from previous parent, if any.
        Utils.removeViewFromParent(videoView);
        // Add view to parent
        linearLayout.addView(videoView);
    }

    /**
     * Set the mute audio button label according to the current state of audio.
     *
     * @param doToast If true, Toast about setting audio to current state.
     */
    private void setAudioBtnLabel(boolean doToast) {
        if (audioMuted) {
            toggleAudioButton.setText(getString(R.string.enable_audio));
            if (doToast) {
                Toast.makeText(parentActivity, getString(R.string.muted_audio),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            toggleAudioButton.setText(getString(R.string.mute_audio));
            if (doToast) {
                Toast.makeText(parentActivity, getString(R.string.enabled_audio),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Set the mute video button label according to the current state of video.
     *
     * @param doToast If true, Toast about setting video to current state.
     */
    private void setVideoBtnLabel(boolean doToast) {
        if (videoMuted) {
            toggleVideoButton.setText(getString(R.string.enable_video));
            if (doToast) {
                Toast.makeText(parentActivity, getString(R.string.muted_video),
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            toggleVideoButton.setText(getString(R.string.mute_video));
            if (doToast) {
                Toast.makeText(parentActivity, getString(R.string.enabled_video),
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /***
     * Lifecycle Listener Callbacks -- triggered during events that happen during the SDK's
     * lifecycle
     */

    /**
     * Triggered when connection is successful
     *
     * @param isSuccessful
     * @param message
     */

    @Override
    public void onConnect(boolean isSuccessful, String message) {
        if (isSuccessful) {
            connecting = false;
            onConnectUIChange();
            String log = "Connected to room " + roomName + " (" + skylinkConnection.getRoomId() +
                    ") as " + skylinkConnection.getPeerId() + " (" + MY_USER_NAME + ").";
            Toast.makeText(parentActivity, log, Toast.LENGTH_LONG).show();
            Log.d(TAG, log);
        } else {
            connecting = false;
            String error = "Skylink failed to connect!\nReason : " + message;
            Log.d(TAG, error);
            Toast.makeText(parentActivity, error, Toast.LENGTH_LONG).show();
            onDisconnectUIChange();
        }
    }

    @Override
    public void onDisconnect(int errorCode, String message) {
        onDisconnectUIChange();
        connecting = false;
        String log = "";
        if (errorCode == Errors.DISCONNECT_FROM_ROOM) {
            log += "We have successfully disconnected from the room.";
        } else if (errorCode == Errors.DISCONNECT_UNEXPECTED_ERROR) {
            log += "WARNING! We have been unexpectedly disconnected from the room!";
        }
        log += " Server message: " + message;

        //Toast.makeText(parentActivity, log, Toast.LENGTH_LONG).show();
        log = "[onDisconnect] " + log;
        Log.d(TAG, log);
    }

    @Override
    public void onLockRoomStatusChange(String remotePeerId, boolean lockStatus) {
        //Toast.makeText(parentActivity, "Peer " + remotePeerId +
               // " has changed Room locked status to " + lockStatus, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onReceiveLog(int infoCode, String message) {
        Utils.handleSkylinkReceiveLog(infoCode, message, parentActivity, TAG);
    }

    @Override
    public void onWarning(int errorCode, String message) {
        Utils.handleSkylinkWarning(errorCode, message, parentActivity, TAG);
    }

    /**
     * Media Listeners Callbacks - triggered when receiving changes to Media Stream from the
     * remote peer
     */

    /**
     * Triggered after the user's local media is captured.
     *
     * @param videoView
     */
    @Override
    public void onLocalMediaCapture(SurfaceViewRenderer videoView) {
        if (videoView == null) {
            return;
        }
        addSelfView(getVideoView(null));
    }

    @Override
    public void onVideoSizeChange(String peerId, Point size) {
        String peer = "Peer " + peerId;
        // If peerId is null, this call is for our local video.
        if (peerId == null) {
            peer = "We've";
        }
        Log.d(TAG, peer + " got video size changed to: " + size.toString() + ".");
    }

    @Override
    public void onRemotePeerMediaReceive(String remotePeerId, SurfaceViewRenderer videoView) {
        addRemoteView();
        String log = "Received new ";
        if (videoView != null) {
            log += "Video ";
        } else {
            log += "Audio ";
        }
        log += "from Peer " + Utils.getPeerIdNick(remotePeerId) + ".\r\n";

        UserInfo remotePeerUserInfo = skylinkConnection.getUserInfo(remotePeerId);
        log += "isAudioStereo:" + remotePeerUserInfo.isAudioStereo() + ".\r\n" +
                "video height:" + remotePeerUserInfo.getVideoHeight() + ".\r\n" +
                "video width:" + remotePeerUserInfo.getVideoHeight() + ".\r\n" +
                "video frameRate:" + remotePeerUserInfo.getVideoFps() + ".";
        //Toast.makeText(parentActivity, log, Toast.LENGTH_SHORT).show();
        Log.d(TAG, log);
    }

    @Override
    public void onRemotePeerAudioToggle(String remotePeerId, boolean isMuted) {
        String log = "Peer " + Utils.getPeerIdNick(remotePeerId) +
                " Audio mute status via:\r\nCallback: " + isMuted + ".";

        // It is also possible to get the mute status via the UserInfo.
        UserInfo userInfo = skylinkConnection.getUserInfo(remotePeerId);
        if (userInfo != null) {
            log += "\r\nUserInfo: " + userInfo.isAudioMuted() + ".";
        }
        Toast.makeText(parentActivity, log, Toast.LENGTH_SHORT).show();
        Log.d(TAG, log);
    }

    @Override
    public void onRemotePeerVideoToggle(String remotePeerId, boolean isMuted) {
        String log = "Peer " + Utils.getPeerIdNick(remotePeerId) +
                " Video mute status via:\r\nCallback: " + isMuted + ".";

        // It is also possible to get the mute status via the UserInfo.
        UserInfo userInfo = skylinkConnection.getUserInfo(remotePeerId);
        if (userInfo != null) {
            log += "\r\nUserInfo: " + userInfo.isVideoMuted() + ".";
        }
        Toast.makeText(parentActivity, log, Toast.LENGTH_SHORT).show();
        Log.d(TAG, log);
    }

    /**
     * OsListener Callbacks - triggered by Android OS related events.
     */
    @Override
    public void onPermissionRequired(
            final String[] permissions, final int requestCode, final int infoCode) {
        Utils.onPermissionRequiredHandler(permissions, requestCode, infoCode, TAG, getContext(), this, skylinkConnection);
    }

    @Override
    public void onPermissionGranted(String[] permissions, int requestCode, int infoCode) {
        Utils.onPermissionGrantedHandler(permissions, infoCode, TAG);
    }

    @Override
    public void onPermissionDenied(String[] permissions, int requestCode, int infoCode) {
        Utils.onPermissionDeniedHandler(infoCode, getContext(), TAG);
    }

    /**
     * Remote Peer Listener Callbacks - triggered during events that happen when data or connection
     * with remote peer changes
     */

    @Override
    public void onRemotePeerJoin(String remotePeerId, Object userData, boolean hasDataChannel) {
        String log = "Your Peer " + Utils.getPeerIdNick(remotePeerId) + " connected.";
        Toast.makeText(parentActivity, log, Toast.LENGTH_SHORT).show();
        Log.d(TAG, log);
    }

    @Override
    public void onRemotePeerLeave(String remotePeerId, String message, UserInfo userInfo) {
        Toast.makeText(parentActivity, "Your partner has left the room", Toast.LENGTH_SHORT).show();
        View peerView = linearLayout.findViewWithTag("peer");
        linearLayout.removeView(peerView);

        // Resize self view to better make use of screen.
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        SurfaceViewRenderer videoView = getVideoView(null);
        if (videoView != null) {
            videoView.setLayoutParams(params);
            addSelfView(videoView);
        }
        int numRemotePeers = getNumRemotePeers();
        String log = "Your partner " + Utils.getPeerIdNick(remotePeerId, userInfo) + " left: " +
                message + ". " + numRemotePeers + " remote partner(s) left in the room.";
        Toast.makeText(parentActivity, log, Toast.LENGTH_SHORT).show();
        Log.d(TAG, log);
    }

    @Override
    public void onRemotePeerConnectionRefreshed(String remotePeerId, Object userData, boolean hasDataChannel, boolean wasIceRestarted) {
        String peer = "Skylink Media Relay server";
        if (remotePeerId != null) {
            peer = "Peer " + Utils.getPeerIdNick(remotePeerId);
        }
        String log = "Your connection with " + peer + " has just been refreshed";
        if (wasIceRestarted) {
            log += ", with ICE restarted.";
        } else {
            log += ".\r\n";
        }

        UserInfo remotePeerUserInfo = skylinkConnection.getUserInfo(remotePeerId);
        log += "isAudioStereo:" + remotePeerUserInfo.isAudioStereo() + ".\r\n" +
                "video height:" + remotePeerUserInfo.getVideoHeight() + ".\r\n" +
                "video width:" + remotePeerUserInfo.getVideoHeight() + ".\r\n" +
                "video frameRate:" + remotePeerUserInfo.getVideoFps() + ".";
        //Toast.makeText(parentActivity, log, Toast.LENGTH_SHORT).show();
        Log.d(TAG, log);
    }

    @Override
    public void onRemotePeerUserDataReceive(String remotePeerId, Object userData) {
        Log.d(TAG, "onRemotePeerUserDataReceive " + remotePeerId);
    }

    @Override
    public void onOpenDataConnection(String peerId) {
        Log.d(TAG, "onOpenDataConnection");
    }
}
