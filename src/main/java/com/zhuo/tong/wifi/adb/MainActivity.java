package com.zhuo.tong.wifi.adb;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.zhuo.tong.utils.MyRootTool;
import com.zhuo.tong.utils.MyTestLog;
import com.zhuo.tong.utils.SPUtils;
import com.zhuo.tong.utils.net.MyNetworkCheck;
import com.zhuo.tong.utils.net.MyNetworkConnectChangedReceiver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    protected static final int ADB_NO_ENABLED = 0;
    protected static final int ADB_ENABLED = 1;
    protected static final int ADB_DISABLED = 2;
    protected static final int ADB_ERROR_PORT = -1;
    public TextView wifi_TextView;
    public TextView ip_TextView;
    public MyNetworkConnectChangedReceiver myNetworkConnectChangedReceiver;
    public IntentFilter filter;
    private ImageView wifiImageView;
    private TableRow wifiTableRow;
    private EditText adbEditText;
    protected TextView adb_TextView;
    protected ImageView adbImageView;
    protected TextView portTextView;
    MyNetworkConnectChangedListener listener;
    Handler handler;
    static class MyHandler extends Handler{
        WeakReference<Activity> mActivity;
        public MyHandler(Activity activity){
            mActivity = new WeakReference<Activity>(activity);
        }
        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = (MainActivity) mActivity.get();
            super.handleMessage(msg);
            switch (msg.what) {
                case ADB_NO_ENABLED:
                    activity.adb_TextView.setText("网络调试未开启");
                    activity.portTextView.setText("ip端口:");
                    activity.adbImageView.setBackgroundResource(R.mipmap.shut);
                    activity.adb = false;
                    break;
                case ADB_ENABLED:
                    activity.adb_TextView.setText("网络调试已开启");
                    activity.portTextView.setText("ip端口:"+activity.port);
                    activity.adbImageView.setBackgroundResource(R.mipmap.open);
                    activity.adb = true;
                    ConnectivityManager manager = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
                    NetworkInfo wifi = manager
                            .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                    if(wifi.isConnected()){
                        if(!activity.adb)
                            activity.adbEditText.setText("wifi已联通，打开网络调试可以不用usb数据线连接");
                        else {
                            if(activity.port == 5555)
                                activity.adbEditText.setText("请在主机终端中输入:adb connect " + MyNetworkCheck.getStringWifiIp(activity));
                            else
                                activity.adbEditText.setText("请在主机终端中输入:adb connect " + MyNetworkCheck.getStringWifiIp(activity) + ":" + activity.port);
                        }
                    }
                    break;
                case ADB_DISABLED:
                    activity.adb_TextView.setText("网络调试已关闭");
                    activity.portTextView.setText("ip端口:" + activity.port);
                    activity.adbImageView.setBackgroundResource(R.mipmap.shut);
                    activity.adb = false;
                    break;
                case ADB_ERROR_PORT:
                    activity.adb_TextView.setText("ip端口错误");
                    activity.portTextView.setText("ip端口:"+activity.errorPort);
                    activity.adbImageView.setBackgroundResource(R.mipmap.shut);
                    activity.adb = false;
                    break;
            }
        }
    };
    protected boolean adb;
    protected int port;
    protected int uport = 5555;//1024-49151
    protected String errorPort;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wifi_TextView = (TextView) findViewById(R.id.wifi_TextView);
        ip_TextView = (TextView) findViewById(R.id.ip_TextView);
        wifiImageView = (ImageView) findViewById(R.id.wifi_iv);
        wifiTableRow = (TableRow) findViewById(R.id.wifi_tr);
        adbEditText = (EditText) findViewById(R.id.adb_et);
        adb_TextView = (TextView) findViewById(R.id.adb_TextView);
        adbImageView = (ImageView) findViewById(R.id.adb_iv);
        portTextView = (TextView) findViewById(R.id.port_TextView);

        handler = new MyHandler(this);

        ((TableRow)portTextView.getParent()).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                rerefshAdbState();
            }
        });
        adbImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                adb();
            }
        });
        wifiImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean enabled = MyNetworkCheck.autoSetWifiEnabled(MainActivity.this);
//                wiifImageView.setBackgroundResource(R.mipmap.shut);
            }
        });
        wifiTableRow.setOnClickListener(new View.OnClickListener() {
            public final int TIME = 300;
            private long[] mHits = new long[2];
            @Override
            public void onClick(View v) {
                    System.arraycopy(mHits, 1, mHits, 0, mHits.length - 1);
                    mHits[mHits.length - 1] = SystemClock.uptimeMillis();
                    if (mHits[0] >= (SystemClock.uptimeMillis() - TIME)) {
                        MyNetworkCheck.openWIFI_Setting(MainActivity.this);
                    }else{
                        Toast.makeText(MainActivity.this, "双击跳转到系统网络设置",
                                Toast.LENGTH_SHORT).show();
                    }

            }
        });

//        adbEditText.setFocusable(false);
//        adbEditText.setFocusableInTouchMode(false);
        adbEditText.setCursorVisible(false);
//        adbEditText.clearFocus();
//        adbEditText.setInputType(InputType.TYPE_NULL);

        myNetworkConnectChangedReceiver = new MyNetworkConnectChangedReceiver();
        listener = new MyNetworkConnectChangedListener();
        filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
    }

    protected void rerefshAdbState() {
        MyRootTool.getRootProcess(new MyRootTool.MyHolder() {
            @Override
            public void success() {
                OutputStream outputStream = process.getOutputStream();
                InputStream inputStream = process.getInputStream();
                try {
                    outputStream.write("getprop service.adb.tcp.port\n".getBytes());
                            /*int r = -1;
                            MyTestLog.info("开始读端口");
                            while ((r=inputStream.read()) != -1){
                                MyTestLog.info("r:"+r);
                            }*/
                    String read = new BufferedReader(new InputStreamReader(inputStream)).readLine();
                    Message message = handler.obtainMessage();
                    if (!TextUtils.isEmpty(read)) {
                        try {
                            port = Integer.parseInt(read);
                            MyTestLog.info("端口:" + port);
                            if (port > 0) {
                                message.what = ADB_ENABLED;
                                MyTestLog.info("网络调试已开启");
                            } else {
                                message.what = ADB_DISABLED;
                                MyTestLog.info("网络调试已关闭");
                            }
                        } catch (NumberFormatException e) {
                            errorPort = read;
                            message.what = ADB_ERROR_PORT;
                            e.printStackTrace();
                        }

                    } else {
                        message.what = ADB_NO_ENABLED;
                        MyTestLog.info("端口为空");
                    }
                    handler.sendMessage(message);
                    outputStream.write("exit\n".getBytes());
                    int wait = process.waitFor();
                    MyTestLog.info("wait:" + wait);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    process.destroy();
                }
            }

            @Override
            public void fail() {
                MyTestLog.info("获取root失败");
                if (process != null)
                    process.destroy();
            }
        });
    }

    protected void adb() {
        if(adb){
            MyRootTool.execRoot("setprop service.adb.tcp.port -1; stop adbd; start adbd", new MyRootTool.MyHolder(){
                @Override
                public void success() {
                    super.success();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adbEditText.setText("网络调试关闭成功");
                        }
                    });

                    rerefshAdbState();
                }

                @Override
                public void fail() {
                    super.fail();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adbEditText.setText("网络调试关闭失败");
                        }
                    });
                }
            });
        }else {
            MyRootTool.execRoot("setprop service.adb.tcp.port "+ uport + "; stop adbd; start adbd", new MyRootTool.MyHolder(){
                @Override
                public void success() {
                    super.success();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adbEditText.setText("网络调试开启成功");
                        }
                    });

                    rerefshAdbState();
                }

                @Override
                public void fail() {
                    super.fail();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            adbEditText.setText("网络调试开启失败");
                        }
                    });

                }
            });
        }

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!MyNetworkCheck.isWifiEnabled(this)) {
            wifi_TextView.setText("wifi:未开启");
            wifiImageView.setBackgroundResource(R.mipmap.shut);

            SPUtils sp = SPUtils.getSp(this.getApplication());
            boolean autoWifiEnable = (boolean) sp.get("autoWifiEnable", false);
            if (autoWifiEnable) {
                boolean enabled = MyNetworkCheck.setWifiEnabled(this);
                if (enabled) {
                    wifi_TextView.setText("wifi:已开启");
                    wifiImageView.setBackgroundResource(R.mipmap.open);
                }
            }

        } else {
            wifi_TextView.setText("wifi:已开启");
            wifiImageView.setBackgroundResource(R.mipmap.open);
        }

        ip_TextView.setText( MyNetworkCheck.getStringWifiIp(this));

        myNetworkConnectChangedReceiver.setNetworkConnectChangedListener(listener);
        registerReceiver(myNetworkConnectChangedReceiver, filter);
        MyNetworkConnectChangedReceiver.dynamic = true;
        rerefshAdbState();

    }

    @Override
    protected void onStop() {
        super.onStop();
        /*PackageManager pm = getPackageManager();
        Intent intent = new Intent(WifiManager.WIFI_STATE_CHANGED_ACTION);
        List<ResolveInfo> resolveInfos = pm.queryBroadcastReceivers(intent, PackageManager.GET_RESOLVED_FILTER);
        for (ResolveInfo info :resolveInfos){
            MyTestLog.error(info.toString());
        }*/
        unregisterReceiver(myNetworkConnectChangedReceiver);
        MyNetworkConnectChangedReceiver.dynamic = false;
        myNetworkConnectChangedReceiver.setNetworkConnectChangedListener(null);
    }



    protected class MyNetworkConnectChangedListener implements MyNetworkConnectChangedReceiver.MyNetworkConnectChangedListener{

        @Override
        public void onWIFI_DISABLING() {
            MyTestLog.info("WIFI_DISABLING");
            wifi_TextView.setText("wifi:正在关闭");
        }

        @Override
        public void onWIFI_DISABLED() {
            MyTestLog.info("WIFI_DISABLED");
            wifi_TextView.setText("wifi:已关闭");
            wifiImageView.setBackgroundResource(R.mipmap.shut);
        }

        @Override
        public void onWIFI_ENABLING() {
            MyTestLog.info("WIFI_ENABLING");
            wifi_TextView.setText("wifi:正在开启");
        }

        @Override
        public void onWIFI_ENABLED() {
            MyTestLog.info("WIFI_ENABLED");
            wifi_TextView.setText("wifi:已开启");
            wifiImageView.setBackgroundResource(R.mipmap.open);
        }

        @Override
        public void onWIFI_UNKNOWN() {
            MyTestLog.info("WIFI_UNKNOWN");
            wifi_TextView.setText("wifi:未知");
        }

        @Override
        public void onCONNECTING() {
            MyTestLog.info("CONNECTING");
        }

        @Override
        public void onCONNECTED() {
            MyTestLog.info("CONNECTED");
            ip_TextView.setText(MyNetworkCheck.getStringWifiIp(MainActivity.this) +"(未确认)");
        }

        @Override
        public void onSUSPENDED() {
            MyTestLog.info("SUSPENDED");
        }

        @Override
        public void onDISCONNECTING() {
            MyTestLog.info("DISCONNECTING");
        }

        @Override
        public void onDISCONNECTED() {
            MyTestLog.info("DISCONNECTED");
            ip_TextView.setText(MyNetworkCheck.getStringWifiIp(MainActivity.this));
            adbEditText.setText("................");
        }

        @Override
        public void onUNKNOWN() {
            MyTestLog.info("UNKNOWN");
            ip_TextView.setText(MyNetworkCheck.getStringWifiIp(MainActivity.this));
        }

        @Override
        public void onCONNECTIVITY() {
            MyTestLog.info("CONNECTIVITY");
            ip_TextView.setText( MyNetworkCheck.getStringWifiIp(MainActivity.this));
            ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifi = manager
                    .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            if(wifi.isConnected()){
                if(!adb)
                    adbEditText.setText("wifi已联通，打开网络调试可以不用usb数据线连接");
                else {
                    if(port == 5555)
                        adbEditText.setText("请在主机终端中输入:adb connect " + ip_TextView.getText());
                    else
                        adbEditText.setText("请在主机终端中输入:adb connect " + ip_TextView.getText() + ":" + port);
                }
            }
        }
    }
}
