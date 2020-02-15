package cj.webrtc.entity;

import android.support.annotation.Nullable;

import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class WebRTCSingnalClient {

    private static WebRTCSingnalClient mInstance;
    private Socket mSocket;
    private String mRoomId;
    private OnSignalEventListener mOnSignalEventListener;

    public interface OnSignalEventListener {
        void onConnected();

        void onConnecting();

        void onDisconnected();

        void onUserJoined(String roomName, String userID);

        void onUserLeaved(String roomName, String userID);

        void onRemoteUserJoined(String roomName);

        void onRemoteUserLeaved(String roomName, String userID);

        void onRoomFull(String roomName, String userID);

        void onMessage(JSONObject message);
    }

    public OnSignalEventListener getmOnSignalEventListener() {
        return mOnSignalEventListener;
    }

    public void setmOnSignalEventListener(OnSignalEventListener mOnSignalEventListener) {
        this.mOnSignalEventListener = mOnSignalEventListener;
    }

    private WebRTCSingnalClient() {
    }

    public static WebRTCSingnalClient getInstance() {
        if (mInstance == null) {
            mInstance = new WebRTCSingnalClient();
        }
        return mInstance;
    }

    public void joinRoom(String url, String roomId) {
//        String url="https://www.cjhhh.ren:8666";
        try {
            mSocket = IO.socket(url);
            mSocket.connect();
            System.out.println("加入了房间");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onConnected();
                }
            }
        });

        mSocket.on(Socket.EVENT_CONNECTING, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onConnecting();
                }
            }
        });

        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onDisconnected();
                }
            }
        });

        mSocket.on("joined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomId = (String) args[0];
                String socketId = (String) args[1];

                //使用接口只是为了能在日志窗口显示
                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onUserJoined(roomId, socketId);
                }
            }
        });

        mSocket.on("otherjoined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomId = (String) args[0];
                String socketId = (String) args[1];

                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onRemoteUserJoined(roomId);
                }
            }
        });

        mSocket.on("full", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomId = (String) args[0];
                String socketId = (String) args[1];

                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onRoomFull(roomId, socketId);
                }
            }
        });

        mSocket.on("leaved", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomId = (String) args[0];
                String socketId = (String) args[1];

                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onUserLeaved(roomId, socketId);
                }
            }
        });

        mSocket.on("bye", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String roomId = (String) args[0];
                String socketId = (String) args[1];

                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onRemoteUserLeaved(roomId, socketId);
                }
            }
        });

        mSocket.on("messaged", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                //接收信息
                String roomName = (String) args[0];
                String socketId = (String) args[1];
                JSONObject msg = (JSONObject) args[2];

                if (mOnSignalEventListener != null) {
                    mOnSignalEventListener.onMessage(msg);
                }
            }
        });

        mRoomId = roomId;
        mSocket.emit("join", mRoomId);
    }

    public void sendMessage(JSONObject msg) {
        mSocket.emit("messages", mRoomId, msg);
    }

    public void leaveRoom() {
        mSocket.emit("leave", mRoomId);

        System.out.println("离开了房间");
    }

    public void closeSocket() {
        mSocket.disconnect();
        mSocket = null;
    }

}
