package cj.webrtc.activitity;

import android.Manifest;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import cj.webrtc.R;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final EditText mServerAddr = findViewById(R.id.ServerAddress);
        final EditText mRoomId = findViewById(R.id.RoomId);

        findViewById(R.id.JoinBtn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String serverAddr = "x";  //服务器IP地址
                final String roomId = mRoomId.getText().toString();
                Intent i = new Intent(MainActivity.this, CallActivity.class);
                i.putExtra("server", serverAddr);
                i.putExtra("room", roomId);
                startActivity(i);
            }
        });

        String[] permissions = {
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.INTERNET
        };
        if (!EasyPermissions.hasPermissions(this, permissions)) {
            EasyPermissions.requestPermissions(this, "要想1V1视频, 请确保打开权限！", 0, permissions);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
