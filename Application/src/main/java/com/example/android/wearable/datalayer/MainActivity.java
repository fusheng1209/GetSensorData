/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.wearable.datalayer;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataApi.DataItemResult;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageApi.SendMessageResult;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static android.util.FloatMath.cos;
import static android.util.FloatMath.sin;
import static android.util.FloatMath.sqrt;

/**
 * Receives its own events using a listener API designed for foreground activities. Updates a data
 * item every second while it is open. Also allows user to take a photo and send that as an asset to
 * the paired wearable.
 */
public class MainActivity extends Activity implements DataApi.DataListener,
        MessageApi.MessageListener, NodeApi.NodeListener, ConnectionCallbacks,
        OnConnectionFailedListener,SensorEventListener {

    private static final String TAG = "MainActivity";

    /** Request code for launching the Intent to resolve Google Play services errors. */
    private static final int REQUEST_RESOLVE_ERROR = 1000;

    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String STOP_ACTIVITY_PATH="/stop-activity";
    private static final String START_SENSOR_PATH="/start-sensor";
    private static final String STOP_SENSOR_PATH="/stop-sensor";
    private static final String COUNT_PATH = "/count";
    private static final String IMAGE_PATH = "/image";
    private static final String STRING_PATH="/string";
    private static final String IMAGE_KEY = "photo";
    private static final String COUNT_KEY = "count";
    private static final String STRING_KEY="string";

    private GoogleApiClient mGoogleApiClient;
    private boolean mResolvingError = false;
    private boolean mCameraSupported = false;

    private ListView mDataItemList;
    private Button mTakePhotoBtn;
    private Button mSendPhotoBtn;
    private ImageView mThumbView;
    private Bitmap mImageBitmap;
    private View mStartActivityBtn;

    private DataItemAdapter mDataItemListAdapter;
    private Handler mHandler;

    // Send DataItems.
    private ScheduledExecutorService mGeneratorExecutor;
    private ScheduledFuture<?> mDataItemGeneratorFuture;

    static final int REQUEST_IMAGE_CAPTURE = 1;
    float [] sensorData=null;

    private static final float EPSILON =0.00001f ;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer,mGyroscope;
    private float gravity[]=new float[3];
    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    private float timestamp;


    float[] accelerometerValues=new float[3];
    float[] magneticFieldValues=new float[3];
    float[] linear_acceleration=new float[3];
    float[] values=new float[3];
    float[] rotate=new float[9];
    float[] gyroscope=new float[3];

    //写SD卡
    boolean start_save=false;
    String filePath,fileName,fileName2;
    EditText userName,activeName;
    private FileOutputStream outStream,outStream2;

    private Button btn_start;
    private Button btn_startSensor;
    private Button btn_ShowTxt;
    private static float history_step=0,current_step=0, steps=0;
    private boolean isStart;
    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        mHandler = new Handler();
        LOGD(TAG, "onCreate");
        mCameraSupported = getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
        setContentView(R.layout.main_activity);
        setupViews();
        // Stores DataItems received by the local broadcaster or from the paired watch.
        mDataItemListAdapter = new DataItemAdapter(this, android.R.layout.simple_list_item_1);

        mDataItemList.setAdapter(mDataItemListAdapter);

        mGeneratorExecutor = new ScheduledThreadPoolExecutor(1);

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();

        //获取传感器数据
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);//加速度传感器
        mGyroscope    =   mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);//陀螺仪
        //获取编辑框对象
        userName=(EditText)findViewById(R.id.userName);
        activeName=(EditText)findViewById(R.id.actionName);
        btn_start=(Button)findViewById(R.id.btn_startSave);
        btn_startSensor=(Button)findViewById(R.id.btn_startSensor);
        btn_ShowTxt = (Button)findViewById(R.id.btn_showTxt);

        isStart=true;
        //监听按钮事件
        listenClick();
        //判断SD卡是否存在并可写
        if (android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED))
        {
            filePath=getSDPath();
        }
        else
        {
            Toast.makeText(this,"外部存储器不存在",Toast.LENGTH_LONG).show();
            finish();
        }

        //初始化用户名
        SharedPreferences perf=getSharedPreferences("data",MODE_PRIVATE);
        String uName=perf.getString("userName", "");
        String aName=perf.getString("actionName","");
        if (!TextUtils.isEmpty(uName))
            userName.setText(uName);
        if (!TextUtils.isEmpty(aName))
            activeName.setText(aName);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            mImageBitmap = (Bitmap) extras.get("data");
            mThumbView.setImageBitmap(mImageBitmap);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mResolvingError) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mDataItemGeneratorFuture = mGeneratorExecutor.scheduleWithFixedDelay(
                new DataItemGenerator(), 1, 200, TimeUnit.MILLISECONDS);//线程执行延迟时间
    }

    @Override
    public void onPause() {
        super.onPause();
    }
    @Override
    protected void onStop() {

        super.onStop();
    }

    @Override
    protected void onDestroy() {

        if (!mResolvingError) {
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        super.onDestroy();
        mDataItemGeneratorFuture.cancel(true /* mayInterruptIfRunning */);
        stopSensor();
        try {
            if (outStream!=null)
                outStream.close();
            if (outStream2!=null)
                outStream2.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 保存UI数据
        // save(userName.getText().toString());
        SharedPreferences.Editor editor=getSharedPreferences("data",MODE_PRIVATE).edit();
        editor.putString("userName",userName.getEditableText().toString());
        editor.putString("actionName",activeName.getEditableText().toString());
        editor.commit();

    }

    public void startSensor(){
        mSensorManager.registerListener(this, mAccelerometer,SensorManager.SENSOR_DELAY_UI);
        mSensorManager.registerListener(this, mGyroscope, SensorManager.SENSOR_DELAY_UI);

    }
    public void stopSensor(){
        mSensorManager.unregisterListener(this);

    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
    public void onSensorChanged(SensorEvent event) {//传感器数据更新

        //  通过getType方法获得当前传回数据的传感器类型

        switch (event.sensor.getType()) {

            case Sensor.TYPE_ACCELEROMETER:            //  处理加速度传感器传回的数据
                Fun_ACCELEROMETER(event);
                break;
            case Sensor.TYPE_GYROSCOPE:
                Fun_GYROSCOPE(event);
                break;
            default:
                break;
        }

    }
    void Fun_Magnetic(SensorEvent event){//获取方向函数

        if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
            accelerometerValues=event.values;
        }
        if(event.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD){
            magneticFieldValues=event.values;
        }

        SensorManager.getRotationMatrix(rotate, null, accelerometerValues, magneticFieldValues);
        SensorManager.getOrientation(rotate, values);
        //经过SensorManager.getOrientation(rotate, values);得到的values值为弧度
        //转换为角度
        values[0]=(float)Math.toDegrees(values[0]);
        values[1]=(float)Math.toDegrees(values[1]);
        values[2]=(float)Math.toDegrees(values[2]);


    }
    void Fun_GYROSCOPE(SensorEvent event){
        // 根据陀螺仪采样数据计算出此次时间间隔的偏移量后，它将与当前旋转向量相乘。

        if (timestamp != 0) {
            final float dT = (event.timestamp - timestamp) * NS2S;
            // 未规格化的旋转向量坐标值，。
            float axisX = event.values[0];
            float axisY = event.values[1];
            float axisZ = event.values[2];

            // 计算角速度
            float omegaMagnitude = sqrt(axisX*axisX + axisY*axisY + axisZ*axisZ);

            // 如果旋转向量偏移值足够大，可以获得坐标值，则规格化旋转向量
            // (也就是说，EPSILON 为计算偏移量的起步值。小于该值的偏移视为误差，不予计算。)
            if (omegaMagnitude > EPSILON) {
                axisX /= omegaMagnitude;
                axisY /= omegaMagnitude;
                axisZ /= omegaMagnitude;
            }

            // 为了得到此次取样间隔的旋转偏移量，需要把围绕坐标轴旋转的角速度与时间间隔合并表示。
            // 在转换为旋转矩阵之前，我们要把围绕坐标轴旋转的角度表示为四元组。
            float thetaOverTwo = omegaMagnitude * dT / 2.0f;
            float sinThetaOverTwo = sin(thetaOverTwo);
            float cosThetaOverTwo = cos(thetaOverTwo);
            deltaRotationVector[0] = sinThetaOverTwo * axisX;
            deltaRotationVector[1] = sinThetaOverTwo * axisY;
            deltaRotationVector[2] = sinThetaOverTwo * axisZ;
            deltaRotationVector[3] = cosThetaOverTwo;
        }
        timestamp = event.timestamp;
        float[] deltaRotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(deltaRotationMatrix,deltaRotationVector);
        // 为了得到旋转后的向量，用户代码应该把我们计算出来的偏移量与当前向量叠加。
        // rotationCurrent = rotationCurrent * deltaRotationMatrix;



        gyroscope[0]=(float)(Math.round(event.values[0]+deltaRotationVector[0]*1000))/1000;
        gyroscope[1]=(float)(Math.round(event.values[1]+deltaRotationVector[1]*1000))/1000;
        gyroscope[2]=(float)(Math.round(event.values[2]+deltaRotationVector[2]*1000))/1000;

     /*   gyroscope[0]=(float)(Math.round(event.values[0]*1000))/1000;
        gyroscope[1]=(float)(Math.round(event.values[1]*1000))/1000;
        gyroscope[2]=(float)(Math.round(event.values[2]*1000))/1000;*/


    }

    void Fun_ACCELEROMETER(SensorEvent event){//加速度处理函数
        // alpha is calculated as t / (t + dT)
        // with t, the low-pass filter's time-constant
        // and dT, the event delivery rate
        final float alpha = 0.8f;
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0];
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1];
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2];

        // 用高通滤波器剔除重力干扰
        linear_acceleration[0] = event.values[0] - gravity[0];
        linear_acceleration[1] = event.values[1] - gravity[1];
        linear_acceleration[2] = event.values[2] - gravity[2];

        linear_acceleration[0]=(float)(Math.round(linear_acceleration[0]*1000))/1000;
        linear_acceleration[1]=(float)(Math.round(linear_acceleration[1]*1000))/1000;
        linear_acceleration[2]=(float)(Math.round(linear_acceleration[2]*1000))/1000;
    }



    @Override //ConnectionCallbacks
    public void onConnected(Bundle connectionHint) {
        LOGD(TAG, "Google API Client was connected");
        mResolvingError = false;
        mStartActivityBtn.setEnabled(true);
        mSendPhotoBtn.setEnabled(mCameraSupported);
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        Wearable.MessageApi.addListener(mGoogleApiClient, this);
        Wearable.NodeApi.addListener(mGoogleApiClient, this);
    }

    @Override //ConnectionCallbacks
    public void onConnectionSuspended(int cause) {
        LOGD(TAG, "Connection to Google API client was suspended");
        mStartActivityBtn.setEnabled(false);
        mSendPhotoBtn.setEnabled(false);
    }

    @Override //OnConnectionFailedListener
    public void onConnectionFailed(ConnectionResult result) {
        if (mResolvingError) {
            // Already attempting to resolve an error.
            return;
        } else if (result.hasResolution()) {
            try {
                mResolvingError = true;
                result.startResolutionForResult(this, REQUEST_RESOLVE_ERROR);
            } catch (IntentSender.SendIntentException e) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            }
        } else {
            Log.e(TAG, "Connection to Google API client has failed");
            mResolvingError = false;
            mStartActivityBtn.setEnabled(false);
            mSendPhotoBtn.setEnabled(false);
            Wearable.DataApi.removeListener(mGoogleApiClient, this);
            Wearable.MessageApi.removeListener(mGoogleApiClient, this);
            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
        }
    }

    @Override //DataListener
    public void onDataChanged(DataEventBuffer dataEvents) {
        LOGD(TAG, "onDataChanged: " + dataEvents);
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                for (DataEvent event : events) {
                    if (event.getType() == DataEvent.TYPE_CHANGED) {
                        // mDataItemListAdapter.clear();
                        //  mDataItemListAdapter.notifyDataSetChanged();
                        // mDataItemListAdapter.add( new Event("Mobile DataItem Changed",event.getDataItem().toString()));
                    } else if (event.getType() == DataEvent.TYPE_DELETED) {
                        mDataItemListAdapter.add(
                                new Event("DataItem Deleted", event.getDataItem().toString()));
                    }
                }
            }
        });
    }

    @Override //MessageListener
    public void onMessageReceived(final MessageEvent messageEvent) {
        LOGD(TAG, "onMessageReceived() A message from watch was received:" + messageEvent
                .getRequestId() + " " + messageEvent.getPath());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mDataItemListAdapter.clear();
                //接收手表数据
                sensorData=parse(messageEvent.toString());
                String wearable_str="";
                for (int i=0;i<sensorData.length-2;i++)
                    wearable_str=wearable_str+sensorData[i]+",";
                wearable_str = wearable_str + "\n";
                current_step=sensorData[sensorData.length-2];
                if (isStart){
                    history_step = current_step;
                    isStart = false;
                }
                steps=current_step-history_step;

                //采集手机数据
                String mobile_str=linear_acceleration[0]+","+linear_acceleration[1]+","+linear_acceleration[2]+","+
                        gyroscope[0]+","+gyroscope[1]+","+gyroscope[2]+"\n";

                if(start_save)//写传感器数据到SD卡
                {
                    //手表线性加速度  陀螺仪角速度
                    try {
                        outStream.write(wearable_str.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //线性加速度
                    try {
                        outStream2.write(mobile_str.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                String display_string="手机传感器：\n"+mobile_str+"\n手表传感器:\n"
                        +wearable_str+"\n步数="+ steps +"\n心率="+sensorData[sensorData.length-1]+"\n";
                mDataItemListAdapter.add(new Event("Sensor Data From Mobile and Watch",display_string));
            }
        });

    }
    //写数据到SD卡
    public String getSDPath(){
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED); //判断sd卡是否存在
        if (sdCardExist)
        {
            sdDir = Environment.getExternalStorageDirectory();//获取根目录
        }
        return sdDir.getAbsolutePath();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("USER_NAME",userName.getEditableText().toString().trim());
        outState.putString("ACTION_NAME",activeName.getEditableText().toString().trim());
    }

    public void listenClick()
    {

        btn_start.setEnabled(false);
        btn_startSensor.setEnabled(true);

        //保存数据
        btn_start.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {

                if (userName.getText().toString().equals("")) {
                    Toast t = Toast.makeText(MainActivity.this, "用户名为空，请重新输入！", Toast.LENGTH_SHORT);
                    t.setGravity(Gravity.TOP, 0, 150);
                    t.show();
                } else
                if (activeName.getText().toString().equals("")) {
                    Toast t = Toast.makeText(MainActivity.this, "动作类型为空，请重新输入！", Toast.LENGTH_SHORT);
                    t.setGravity(Gravity.TOP, 0, 150);
                    t.show();
                }else
                {
                    if (btn_start.getText().toString().equals("开始保存")) {
                        start_save = true;
                        btn_start.setText("停止保存");

                       // Time t = new Time(); // or Time t=new Time("GMT+8"); 加上Time Zone资料。
                      //  t.setToNow(); // 取得系统时间。
                        //获取时间
                        Calendar c = Calendar.getInstance();
                        String str_time = c.get(Calendar.YEAR) + "_" + c.get(Calendar.MONTH)+1+ "_" +  c.get(Calendar.DAY_OF_MONTH) + "_" +
                                c.get(Calendar.HOUR_OF_DAY) + "_" + c.get(Calendar.MINUTE) + "_" + c.get(Calendar.SECOND) ;


                        fileName = filePath + "/SensorData/" + userName.getEditableText().toString().trim() +
                                "/Wearable/Wearable_"+activeName.getEditableText().toString().trim() +"_"+ str_time + ".txt";
                        fileName2 = filePath + "/SensorData/" + userName.getEditableText().toString().trim() +
                                "/Mobile/Mobile_" + activeName.getEditableText().toString().trim() +"_"+str_time + ".txt";
                        try {
                            //手机数据
                            File saveFile = new File(fileName);
                            if (!saveFile.exists()) {
                                File dir = new File(saveFile.getParent());
                                dir.mkdirs();
                                if (!saveFile.createNewFile()) {
                                    System.out.println("File already exists");
                                }
                            }
                            outStream = new FileOutputStream(saveFile);
                            //手表数据
                            File saveFile2 = new File(fileName2);
                            if (!saveFile2.exists()) {
                                File dir = new File(saveFile2.getParent());
                                dir.mkdirs();
                                if (!saveFile.createNewFile()) {
                                    Log.d(TAG,"File already exists");
                                }
                            }
                            outStream2 = new FileOutputStream(saveFile2);

                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        // startSensor();


                    } else if (btn_start.getText().toString().equals("停止保存")) {
                        try {
                            String str="步数：" + steps + "\n";
                            outStream.write(str.getBytes());
                            outStream.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        // stopSensor();
                        btn_start.setText("开始保存");
                        start_save = false;
                    }
                }
            }
        });

        //采集数据
        btn_startSensor.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btn_startSensor.getText().toString().equals("开始采集")){
                    btn_startSensor.setText("停止采集");
                    startSensor();
                    btn_start.setEnabled(true);
                    //如果开始保存数据，则发START_SENSOR_PATH="/start-sensor";开始sensor 采集
                    Wearable.MessageApi.sendMessage(
                            mGoogleApiClient, "NODE", START_SENSOR_PATH, new byte[0]);

                }else
                    //停止采集数据之前先停止保存数据。设置开始保持数据按钮不可用。
                    if (btn_startSensor.getText().toString().equals("停止采集")){

                        history_step=current_step;
                        btn_startSensor.setText("开始采集");
                        stopSensor();
                        if(btn_start.getText().toString().equals("开始保存")){
                            btn_start.setEnabled(false);
                        }
                        else if (btn_start.getText().toString().equals("停止保存")){
                            try {
                                String str="步数：" + steps + "\n";
                                outStream.write(str.getBytes());
                                outStream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            start_save=false;
                            btn_start.setText("开始保存");
                            btn_start.setEnabled(false);
                        }

                        //如果停止保存数据，则发STOP_SENSOR_PATH="/stop-sensor";停止sensor 采集
                        Wearable.MessageApi.sendMessage(
                                mGoogleApiClient, "NODE", STOP_SENSOR_PATH, new byte[0]);

                    }
            }
        });

        //查看数据
        btn_ShowTxt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent showIntent = new Intent(MainActivity.this,ShowTextActivity.class);
                startActivity(showIntent);
            }
        });


    }

    public  float[] parse(String m_str) {
        String str = m_str.substring(m_str.indexOf(",")+1, m_str.lastIndexOf(","));
        String []str1 = str.split(";");
        List<String> list = new ArrayList<String>();

        for(String str2:str1) {
            String []str3 = str2.split(",");
            for(String s:str3) {
                list.add(s);
            }
        }

        float[] mFloat = new float[list.size()];

        for(int i=0;i<list.size();i++) {
            mFloat[i] = Float.parseFloat(list.get(i));
        }

        return mFloat;

    }
    @Override //NodeListener
    public void onPeerConnected(final Node peer) {
        LOGD(TAG, "onPeerConnected: " + peer);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mDataItemListAdapter.add(new Event("Connected", peer.toString()));
            }
        });

    }

    @Override //NodeListener
    public void onPeerDisconnected(final Node peer) {
        LOGD(TAG, "onPeerDisconnected: " + peer);
        mHandler.post(new Runnable() {
            @Override
            public void run() {

                mDataItemListAdapter.add(new Event("Disconnected", peer.toString()));
            }
        });
    }

    /**
     * A View Adapter for presenting the Event objects in a list
     */
    private static class DataItemAdapter extends ArrayAdapter<Event> {

        private final Context mContext;

        public DataItemAdapter(Context context, int unusedResource) {
            super(context, unusedResource);
            mContext = context;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                holder = new ViewHolder();
                LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                convertView = inflater.inflate(android.R.layout.two_line_list_item, null);
                convertView.setTag(holder);
                holder.text1 = (TextView) convertView.findViewById(android.R.id.text1);
                holder.text2 = (TextView) convertView.findViewById(android.R.id.text2);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            Event event = getItem(position);
            holder.text1.setText(event.title);
            holder.text2.setText(event.text);
            return convertView;
        }

        private class ViewHolder {

            TextView text1;
            TextView text2;
        }
    }

    private class Event {

        String title;
        String text;

        public Event(String title, String text) {
            this.title = title;
            this.text = text;
        }
    }

    private Collection<String> getNodes() {
        HashSet<String> results = new HashSet<String>();
        NodeApi.GetConnectedNodesResult nodes =
                Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();

        for (Node node : nodes.getNodes()) {
            results.add(node.getId());
        }

        return results;
    }

    private void sendStartActivityMessage(String node) {
        Wearable.MessageApi.sendMessage(
                mGoogleApiClient, node, START_ACTIVITY_PATH, new byte[0]).setResultCallback(
                new ResultCallback<SendMessageResult>() {
                    @Override
                    public void onResult(SendMessageResult sendMessageResult) {
                        if (!sendMessageResult.getStatus().isSuccess()) {
                            Log.e(TAG, "Failed to send message with status code: "
                                    + sendMessageResult.getStatus().getStatusCode());
                        }
                    }
                }
        );
    }

    private class StartWearableActivityTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... args) {
            Collection<String> nodes = getNodes();
            for (String node : nodes) {
                sendStartActivityMessage(node);
            }
            return null;
        }
    }

    /** Sends an RPC to start a fullscreen Activity on the wearable. */
    public void onStartWearableActivityClick(View view) {
        LOGD(TAG, "Generating RPC");

        // Trigger an AsyncTask that will query for a list of connected nodes and send a
        // "start-activity" message to each connected node.
        new StartWearableActivityTask().execute();
    }

    /** Generates a DataItem based on an incrementing count. */
    private class DataItemGenerator implements Runnable {

        private int count = 0;

        @Override
        public void run() {
            //不断发送int数据
            if(btn_startSensor.getText().toString().equals("停止采集")){
                PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(COUNT_PATH);
                putDataMapRequest.getDataMap().putInt(COUNT_KEY, count++);
                PutDataRequest request = putDataMapRequest.asPutDataRequest();

                LOGD(TAG, "Generating DataItem: " + request);
                if (!mGoogleApiClient.isConnected()) {
                    return;
                }
                Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                        .setResultCallback(new ResultCallback<DataItemResult>() {
                            @Override
                            public void onResult(DataItemResult dataItemResult) {
                                if (!dataItemResult.getStatus().isSuccess()) {
                                    Log.e(TAG, "ERROR: failed to putDataItem, status code: "
                                            + dataItemResult.getStatus().getStatusCode());
                                }
                            }
                        });
            }

        }
    }

    /**
     * Dispatches an {@link android.content.Intent} to take a photo. Result will be returned back
     * in onActivityResult().
     */
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    /**
     * Builds an {@link com.google.android.gms.wearable.Asset} from a bitmap. The image that we get
     * back from the camera in "data" is a thumbnail size. Typically, your image should not exceed
     * 320x320 and if you want to have zoom and parallax effect in your app, limit the size of your
     * image to 640x400. Resize your image before transferring to your wearable device.
     */
    private static Asset toAsset(Bitmap bitmap) {
        ByteArrayOutputStream byteStream = null;
        try {
            byteStream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
            return Asset.createFromBytes(byteStream.toByteArray());
        } finally {
            if (null != byteStream) {
                try {
                    byteStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Sends the asset that was created form the photo we took by adding it to the Data Item store.
     */
    private void sendPhoto(Asset asset) {
        PutDataMapRequest dataMap = PutDataMapRequest.create(IMAGE_PATH);
        dataMap.getDataMap().putAsset(IMAGE_KEY, asset);
        dataMap.getDataMap().putLong("time", new Date().getTime());
        PutDataRequest request = dataMap.asPutDataRequest();
        Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                .setResultCallback(new ResultCallback<DataItemResult>() {
                    @Override
                    public void onResult(DataItemResult dataItemResult) {
                        LOGD(TAG, "Sending image was successful: " + dataItemResult.getStatus()
                                .isSuccess());
                    }
                });

    }

    public void onTakePhotoClick(View view) {
        dispatchTakePictureIntent();
    }

    public void onSendPhotoClick(View view) {
        if (null != mImageBitmap && mGoogleApiClient.isConnected()) {
            sendPhoto(toAsset(mImageBitmap));
        }
    }

    /**
     * Sets up UI components and their callback handlers.
     */
    private void setupViews() {
        mTakePhotoBtn = (Button) findViewById(R.id.takePhoto);
        mSendPhotoBtn = (Button) findViewById(R.id.sendPhoto);

        // Shows the image received from the handset
        mThumbView = (ImageView) findViewById(R.id.imageView);
        mDataItemList = (ListView) findViewById(R.id.data_item_list);

        mStartActivityBtn = findViewById(R.id.start_wearable_activity);
    }

    /**
     * As simple wrapper around Log.d
     */
    private static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }
}
