package com.example.jang.arduino;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Message;
import android.os.StrictMode;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.os.Handler;
import android.widget.Toast;


import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends ActionBarActivity implements SensorEventListener {

    // Debugging
    private static final String TAG = "Main";
    private static final boolean D = true;
    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    // Intent request code
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the BluetoothService
    private BluetoothService mBtService = null;
    private Handler mBTmonit_TimerHandler;
    private Runnable mBTmonit_Timer = null;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    private String lastMAC = null;
    private String lastTryMAC = null;
    // Layout Views
    private TextView mStatus_view;

    //*************************************************************************
    // Arduino Interface
    //*************************************************************************
    private TextView mMinCount;
    private TextView mSecCount;
    private int pushCount = 0;
    //*************************************************************************
    private long lastTime;
    private double speed;
    private double lastX;
    private double lastY;
    private double lastZ;
    private double x, y, z;
    private int pedo_count=0;
    private int pedo_max = 5;
    private int timeout = 5;

    private static final int SHAKE_THRESHOLD = 800;
    private static final int DATA_X = SensorManager.DATA_X;
    private static final int DATA_Y = SensorManager.DATA_Y;
    private static final int DATA_Z = SensorManager.DATA_Z;
    private SensorManager mSensorManager;
    private Sensor mSensor;


    // Intent request code


    // Layout
    private boolean pedo_flag = false;
    private boolean fsr_flag = true;
    private Button btn_Connect;
    private TextView txt_Result;
    private Button btn_Sign;

    private BluetoothService btService = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(D) Log.e(TAG, "+++ ON CREATE +++");
        // Set up the window layout
        setContentView(R.layout.activity_main);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();

        StrictMode.setThreadPolicy(policy);
    }
    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");
        if(mSensor!=null)
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_GAME);
        if (!mBluetoothAdapter.isEnabled()) {
            if(D) Log.d(TAG, "Bluetooth ON Request");
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        } else {
            if (mBtService == null) setupService();
        }
        if(mBTmonit_Timer == null){
            mBTmonit_Timer = new Runnable() {
                @Override
                public void run() {
                    if ((mBtService != null) &&
                            (mBtService.getState() == BluetoothService.STATE_LISTEN) &&
                            (lastMAC != "")) {
                        lastTryMAC = lastMAC;
                        if(D) Log.d(TAG, "Automatic Try-->" + lastMAC);
                        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(lastMAC);
                        mBtService.connect(device, true);
                        mBTmonit_TimerHandler.postDelayed(this, 30*1000);
                    }
                    else mBTmonit_TimerHandler.postDelayed(this, 5*1000);
                }
            };
            mBTmonit_TimerHandler = new Handler();
            mBTmonit_TimerHandler.postDelayed(mBTmonit_Timer, 2*1000);
        }

    }
    @Override
    public void onRestart() {
        super.onRestart();
        if(D) Log.e(TAG, "++ ON RESTART ++");
        if(mSensor!=null)
            mSensorManager.registerListener((SensorEventListener) this, mSensor, SensorManager.SENSOR_DELAY_GAME);
    }
    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "++ ON RESUME ++");
        if (mBtService != null) {
            if (mBtService.getState() == BluetoothService.STATE_NONE) {
                mBtService.start();
            }
        }
    }
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "++ ON PAUSE ++");
    }
    @Override
    public void onStop() {
        super.onStop();
        if(mSensorManager != null)
            mSensorManager.unregisterListener(this);
        if(D) Log.e(TAG, "++ ON STOP ++");

    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(D) Log.e(TAG, "++ ON DESTROY ++");
        if (mBtService != null) mBtService.stop();
        if(mBTmonit_Timer != null) mBTmonit_TimerHandler.removeCallbacks(mBTmonit_Timer);
    }

    private void setupService() {
        if(D) Log.d(TAG, "setupChat()");
        mStatus_view = (TextView) findViewById(R.id.status_view);

        //*************************************************************************
        // Arduino Interface
        //*************************************************************************
        mMinCount = (TextView) findViewById(R.id.min_print);
        mSecCount = (TextView) findViewById(R.id.sec_print);
        //*************************************************************************

        if(mBtService == null) {
            mBtService = new BluetoothService(this, mHandler);
        }
        configParaLoad();
    }

    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        if (mBtService.getState() != BluetoothService.STATE_CONNECTED) {
            Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        if (message.length() > 0) {
            byte[] send = message.getBytes();
            mBtService.write(send);

        }
    }
    //========================== Options Menu ==========================
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
            case R.id.secure_connect_scan:
                serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
        }
        return false;
    }
    //========================== 'BluetoothService' ?????? Message ==========================
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                        case BluetoothService.STATE_CONNECTED:
                            setStatus(getString(R.string.status_connected_to, mConnectedDeviceName));
                            if(lastMAC != lastTryMAC){
                                lastMAC = lastTryMAC;
                                configParaSave();
                            }
                            break;
                        case BluetoothService.STATE_CONNECTING:
                            setStatus(getString(R.string.status_connecting));
                            break;
                        case BluetoothService.STATE_LISTEN:
                        case BluetoothService.STATE_NONE:
                            setStatus(getString(R.string.status_not_connected));
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    break;
                case MESSAGE_READ:
                /*
                 * msg.arg1 = length
                */
                    synchronized (this) {
                        byte[] readBuf = (byte[]) msg.obj;
                        //*************************************************************************
                        // Arduino Interface (receive from Arduino)
                        //*************************************************************************
                        if( readBuf[0] == '*' &&
                                readBuf[1] == 'P' &&
                                readBuf[2] == '1' &&
                                readBuf[3] == '1' ){
                            if(fsr_flag) {
                                pushCount++;
                                if(pushCount!=0)mMinCount.setText("" + pushCount/60);
                                mSecCount.setText("" + pushCount%60);
                                if (pushCount > timeout) {
                                    makePostRequest();
                                    fsr_flag = false;
                                    pedo_flag =true;
                                    pushCount = 0;
                                    if(pushCount!=0)mMinCount.setText("" + pushCount/60);
                                    mSecCount.setText("" + pushCount%60);
                                }
                            }
                        }
                        //*************************************************************************
                    }
                    break;

                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),Toast.LENGTH_LONG).show();
                    break;
            }
        }
    };

    private final void setStatus(String status) {
        mStatus_view.setText(status);
    }
    //========================== Intent Request Return ==========================
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(D) Log.d(TAG, "onActivityResult " + requestCode + "," + resultCode);
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupService();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    if(D) Log.e(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
        }
    }
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        lastTryMAC = address;
        if(D) Log.d(TAG, "last try MAC-->" + lastTryMAC);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mBtService.connect(device, secure);
    }
    //========================== File Handler ==========================
    private boolean configParaSave(){
        int i;
        boolean success = false;
        File file = new File(this.getFilesDir(), "configPara.cfg");
        try {
            DataOutputStream stream = new DataOutputStream(new FileOutputStream(file));

            int len = 0;
            if(lastMAC != ""){
                byte [] byteMAC = lastMAC.getBytes();
                len = byteMAC.length;
                if(len > 31) len = 31;
                stream.writeByte((byte)len);	//string length
                for(i=0;i<len;i++){				//string (max 31)
                    stream.writeByte(byteMAC[i]);
                }
            }
            else stream.writeByte(0);

            if(len < 31){
                for(i=0;i<(31 - len);i++){
                    stream.writeByte(0);
                }
            }

            stream.flush();
            stream.close();
            success = true;
        } catch (IOException e) {
            e.printStackTrace();
            success = false;
        }
        if(D) Log.d(TAG, "configParaSave()-->" + success);
        return success;
    }
    private boolean configParaLoad(){
        boolean success = false;
        int i;

        File file = new File(this.getFilesDir(), "configPara.cfg");
        if(file.exists()){
            try {
                DataInputStream stream = new DataInputStream(new FileInputStream(file));

                byte[] byteMAC;
                byteMAC = new byte[32];
                for(i=0;i<32;i++){
                    byteMAC[i] = stream.readByte();
                }
                if((byteMAC[0] == 0) || (byteMAC[0] > 31)) lastMAC = "";
                else lastMAC = new String(byteMAC, 1, byteMAC[0]);

                stream.close();
                success = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(!success){
            lastMAC = "";
        }
        if(D) Log.d(TAG, "configParaLoad()-->" + success);
        if(D) Log.d(TAG, "last MAC-->" + lastMAC);
        return success;
    }

    //*************************************************************************
    // Arduino Interface (sending to Arduino)
    //*************************************************************************
    public void LedON(View view){
        sendMessage("*L11\r\n");
    }
    public void LedOFF(View view){
        sendMessage("*L10\r\n");
    }
    //*************************************************************************
    public void onSensorChanged(SensorEvent event){
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long currentTime = System.currentTimeMillis();
            long gabOfTime = (currentTime - lastTime);
            if (gabOfTime > 500) {
                lastTime = currentTime;
                x = event.values[SensorManager.DATA_X];
                y = event.values[SensorManager.DATA_Y];
                z = event.values[SensorManager.DATA_Z];

                speed = Math.abs(x + y + z - lastX - lastY - lastZ) / gabOfTime * 10000;

                if (speed > SHAKE_THRESHOLD) {
                    if(pedo_flag) {
                        pedo_count++;
                        TextView t1 = (TextView) findViewById(R.id.pedo_count);
                        String str = String.format("%d", pedo_count);
                        t1.setText(str);
                        if(pedo_count >= pedo_max){
                            makePostRequest();
                            fsr_flag = true;
                            pedo_flag = false;
                            pedo_count = 0;
                            str = String.format("%d", pedo_max - pedo_count);
                            t1.setText(str);
                        }

                    }
                }

                lastX = event.values[DATA_X];
                lastY = event.values[DATA_Y];
                lastZ = event.values[DATA_Z];
            }

        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    private void makePostRequest() {


        HttpClient httpClient = new DefaultHttpClient();
        // replace with your url
        String url = "";
        if(fsr_flag) {
            url = "http://needle-cushion.appspot.com/lock";
        }
        else{
            url = "http://needle-cushion.appspot.com/unlock";
        }
        HttpPost httpPost = new HttpPost(url);

        //Post Data
        List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
        nameValuePair.add(new BasicNameValuePair("username", "test_user"));
        nameValuePair.add(new BasicNameValuePair("password", "123456789"));


        //Encoding POST data
        try {
            httpPost.setEntity(new UrlEncodedFormEntity(nameValuePair));
        } catch (UnsupportedEncodingException e) {
            // log exception
            e.printStackTrace();
        }

        //making POST request.
        try {
            HttpResponse response = httpClient.execute(httpPost);
            // write response to log
            Log.d("Http Post Response:", response.toString());
        } catch (ClientProtocolException e) {
            // Log exception
            e.printStackTrace();
        } catch (IOException e) {
            // Log exception
            e.printStackTrace();
        }

    }
    public void mOnClick(View v)
    {
        EditText e1 = (EditText)findViewById(R.id.min_set);
        int min = Integer.parseInt(e1.getText().toString());
        e1 = (EditText)findViewById(R.id.cnt_set);
        int sec = Integer.parseInt(e1.getText().toString());
        timeout = min*60 + sec ;
    }
}