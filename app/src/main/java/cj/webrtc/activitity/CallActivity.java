package cj.webrtc.activitity;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cj.webrtc.R;
import cj.webrtc.entity.WebRTCSingnalClient;

public class CallActivity extends AppCompatActivity {
    private static final int VIDEO_WIDTH = 1280;
    private static final int VIDEO_HEIGHT = 720;
    private static final int VIDEO_FPS = 30;

    public static final String VIDEO_TRACK_ID = "1";
    public static final String AUDIO_TRACK_ID = "2";

    private PeerConnection mPeerConnection;
    private PeerConnectionFactory mPeerConnectionFactory;

    private TextView mLogView;

    //OpenGL ES
    private EglBase mRootEglBase;
    //纹理渲染
    private SurfaceTextureHelper mSurfaceTextureHelper;

    //继承自 surface view
    private SurfaceViewRenderer mLocalSurfaceView;
    private SurfaceViewRenderer mRemoteSurfaceView;

    private VideoTrack mVideoTrack;
    private AudioTrack mAudioTrack;

    private VideoCapturer mVideoCapturer;

    private class SimpleSdpObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            System.out.println("SdpObserver onCreateSuccess");
        }

        @Override
        public void onSetSuccess() {
            System.out.println("SdpObserver onSetSuccess");
        }

        @Override
        public void onCreateFailure(String s) {
            System.out.println("SdpObserver onCreateFailure: " + s);
        }

        @Override
        public void onSetFailure(String s) {
            System.out.println("SdpObserver onSetFailure: " + s);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        mLogView = findViewById(R.id.logView);
        mRootEglBase = EglBase.create();  //配置文件要加上compileOptions, 不然会报错

        mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());

        mLocalSurfaceView = findViewById(R.id.localView);   //初始化surface view的时候先通过OpenGL计算
        mLocalSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
        mLocalSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL); //缩放按比例填充
        mLocalSurfaceView.setMirror(true);  //镜像翻转
        mLocalSurfaceView.setEnableHardwareScaler(false);   //不采用硬件缩放器

        mRemoteSurfaceView = findViewById(R.id.remoteView);
        mRemoteSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
        mRemoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        mRemoteSurfaceView.setMirror(true);
        mRemoteSurfaceView.setEnableHardwareScaler(true);
        mRemoteSurfaceView.setZOrderMediaOverlay(true);


        mPeerConnectionFactory = createPeerConnectionFactory(this);

        mVideoCapturer = createVideoCapturer();

        VideoSource videoSource = mPeerConnectionFactory.createVideoSource(false);
        //将videoSource注册为mVideoCapturer的观察者
        mVideoCapturer.initialize(mSurfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
        //从soure中获取track
        mVideoTrack = mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
        mVideoTrack.setEnabled(true);   //打开track
        mVideoTrack.addSink(mLocalSurfaceView);     //track有数据就往surface view添加数据(渲染数据)

        AudioSource audioSource = mPeerConnectionFactory.createAudioSource(new MediaConstraints());
        mAudioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
        mAudioTrack.setEnabled(true);


        //匿名内部类实现接口
        WebRTCSingnalClient.getInstance().setmOnSignalEventListener(new WebRTCSingnalClient.OnSignalEventListener() {
            @Override
            public void onConnected() {
                logcatOnUI("Signal Server Connected !");
            }

            @Override
            public void onConnecting() {
                logcatOnUI("Signal Server Connecting !");
            }

            @Override
            public void onDisconnected() {
                logcatOnUI("Signal Server Disconnected!");
            }

            @Override
            public void onUserJoined(String roomName, String userID) {
                logcatOnUI("local user joined!");

                if (mPeerConnection == null) {
                    mPeerConnection = createPeerConnection();
                }
            }

            @Override
            public void onUserLeaved(String roomName, String userID) {
                logcatOnUI("local user leaved!");

                WebRTCSingnalClient.getInstance().closeSocket();
                mPeerConnection.close();
                mPeerConnection = null;
            }

            @Override
            public void onRemoteUserJoined(String roomName) {
                logcatOnUI("Remote User Joined, room: " + roomName);

                if (mPeerConnection == null) {
                    mPeerConnection = createPeerConnection();
                }
                Call();
            }

            @Override
            public void onRemoteUserLeaved(String roomName, String userID) {
                logcatOnUI("Remote User Leaved, room: " + roomName + "uid:" + userID);

                mPeerConnection.close();
                mPeerConnection = null;
            }

            @Override
            public void onRoomFull(String roomName, String userID) {
                logcatOnUI("The Room is Full, room: " + roomName + "uid:" + userID);

                mLocalSurfaceView.release();
                mLocalSurfaceView = null;
                mRemoteSurfaceView.release();
                mRemoteSurfaceView = null;
                mVideoCapturer.dispose();
                mVideoCapturer = null;
                mSurfaceTextureHelper.dispose();
                mSurfaceTextureHelper = null;

                PeerConnectionFactory.stopInternalTracingCapture();
                PeerConnectionFactory.shutdownInternalTracer();

                mPeerConnectionFactory.dispose();
                mPeerConnectionFactory = null;

                finish();   //结束当前Activity
            }

            @Override
            public void onMessage(JSONObject message) {
                String type = null;

                try {
                    type = message.getString("type");
                    if ("offer".equals(type)) {
                        String desc = message.getString("sdp");
                        mPeerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.OFFER, desc));

                        MediaConstraints sdpMediaConstraints = new MediaConstraints();
                        mPeerConnection.createAnswer(new SimpleSdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {
                                mPeerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);

                                JSONObject message = new JSONObject();
                                try {
                                    message.put("type", "answer");
                                    message.put("sdp", sessionDescription.description);

                                    WebRTCSingnalClient.getInstance().sendMessage(message);

                                    logcatOnUI("receive offer and send answer");
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }, sdpMediaConstraints);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //设置远端video可见
                                mRemoteSurfaceView.setVisibility(View.VISIBLE);
                            }
                        });

                    } else if ("answer".equals(type)) {
                        String desc = message.getString("sdp");
                        mPeerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, desc));

                        logcatOnUI("receive answer");
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //设置远端video可见
                                mRemoteSurfaceView.setVisibility(View.VISIBLE);
                            }
                        });

                    } else if ("candidate".equals(type)) {
                        String id = message.getString("id");
                        Integer index = message.getInt("index");
                        String candidate = message.getString("candidate");
                        IceCandidate remoteIceCandidate = new IceCandidate(id, index, candidate);
                        mPeerConnection.addIceCandidate(remoteIceCandidate);

                        logcatOnUI("candidate: "+remoteIceCandidate.sdp);
                    }

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });


        String serverAddress = getIntent().getStringExtra("server");
        String roomId = getIntent().getStringExtra("room");

        WebRTCSingnalClient.getInstance().joinRoom(serverAddress, roomId);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mLocalSurfaceView.release();
        mRemoteSurfaceView.release();
        mVideoCapturer.dispose();
        mSurfaceTextureHelper.dispose();
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
        mPeerConnectionFactory.dispose();

        WebRTCSingnalClient.getInstance().leaveRoom();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //设置远端video不可见
                mRemoteSurfaceView.setVisibility(View.GONE);
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();

        try {
            mVideoCapturer.stopCapture();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        mVideoCapturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS);
    }

    private void Call() {
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection();
        }

        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        mPeerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                mPeerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);

                JSONObject message = new JSONObject();
                try {
                    message.put("type", "offer");
                    message.put("sdp", sessionDescription.description);

                    WebRTCSingnalClient.getInstance().sendMessage(message);

                    logcatOnUI("send offer");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, mediaConstraints);
    }

    private PeerConnection createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();

        //TURN服务器地址
        PeerConnection.IceServer temp = PeerConnection.IceServer.builder("turn:x.x.x.x:3478")
                .setUsername("cj").setPassword("webrtc").createIceServer();
        iceServers.add(temp);

        PeerConnection.RTCConfiguration rtcConfiguration = new PeerConnection.RTCConfiguration(iceServers);
        rtcConfiguration.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        rtcConfiguration.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        rtcConfiguration.enableDtlsSrtp = true;

        PeerConnection connection = mPeerConnectionFactory.createPeerConnection(rtcConfiguration, mPeerConnectionObserver);

        if (connection == null) {
            return null;
        }

        //将本地的音频轨和视频轨加入到连接中
        List<String> mediaStreamLables = Collections.singletonList("ARDAMS");
        connection.addTrack(mVideoTrack, mediaStreamLables);
        connection.addTrack(mAudioTrack, mediaStreamLables);
        return connection;
    }


    private PeerConnection.Observer mPeerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            System.out.println("onSignalingChange: " + signalingState);
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            System.out.println("onIceConnectionChange: " + iceConnectionState);
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            System.out.println("onIceConnectionReceivingChange: " + b);
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            System.out.println("onIceGatheringChange: " + iceGatheringState);
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            JSONObject message = new JSONObject();
            try {
                message.put("type", "candidate");
                message.put("index", iceCandidate.sdpMLineIndex);
                message.put("id", iceCandidate.sdpMid);
                message.put("candidate", iceCandidate.sdp);

                //这个所有的candidate发到Web端只有部分能收到, 所以开大招, 用服务器中转
                if(iceCandidate.sdp.contains("relay")) {
                    WebRTCSingnalClient.getInstance().sendMessage(message);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            for (int i = 0; i < iceCandidates.length; i++) {
                System.out.println("onIceCandidatesRemoved: " + iceCandidates[i]);
            }
            mPeerConnection.removeIceCandidates(iceCandidates);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            System.out.println("onAddStream" + mediaStream.videoTracks.size());
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            System.out.println("onRemoveStream");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            System.out.println("onDataChannel");
        }

        @Override
        public void onRenegotiationNeeded() {
            System.out.println("onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            MediaStreamTrack track = rtpReceiver.track();
            if (track instanceof VideoTrack) {
                VideoTrack remoteVideoTrack = (VideoTrack) track;
                remoteVideoTrack.setEnabled(true);
                remoteVideoTrack.addSink(mRemoteSurfaceView);   //渲染数据
            }
        }
    };

    private VideoCapturer createVideoCapturer() {
        if (Camera2Enumerator.isSupported(this)) {
            Camera2Enumerator camera2Enumerator = new Camera2Enumerator(this);
            String[] deviceNames = camera2Enumerator.getDeviceNames();
            for (String name : deviceNames) {
                if (camera2Enumerator.isFrontFacing(name)) { //mobilePhone支持前置摄像头
                    VideoCapturer videoCapturer = camera2Enumerator.createCapturer(name, null);
                    if (videoCapturer != null) {
                        return videoCapturer;
                    }
                }
            }
        } else {
            Camera1Enumerator camera1Enumerator = new Camera1Enumerator(true);
            String[] deviceNames = camera1Enumerator.getDeviceNames();
            for (String name : deviceNames) {
                if (!camera1Enumerator.isFrontFacing(name)) {
                    ////mobilePhone调用非前置摄像头
                    VideoCapturer videoCapturer = camera1Enumerator.createCapturer(name, null);
                    if (videoCapturer != null) {
                        return videoCapturer;
                    }
                }
            }
        }
        return null;
    }

    private PeerConnectionFactory createPeerConnectionFactory(Context context) {
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        //编码启用H264编码器(支持硬件加速), Vp8不支持硬件加速
        encoderFactory = new DefaultVideoEncoderFactory(mRootEglBase.getEglBaseContext(), false, true);
        decoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).setEnableInternalTracer(true).createInitializationOptions());

        PeerConnectionFactory.Builder builder = PeerConnectionFactory.builder().setVideoEncoderFactory(encoderFactory).setVideoDecoderFactory(decoderFactory);
        builder.setOptions(null);

        return builder.createPeerConnectionFactory();
    }

    private void logcatOnUI(String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String output = mLogView.getText() + "\n" + msg;
                mLogView.setText(output);
            }
        });
    }
}
