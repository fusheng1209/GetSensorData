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

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static android.util.FloatMath.cos;
import static android.util.FloatMath.sin;
import static android.util.FloatMath.sqrt;

/**
 * Listens to DataItems and Messages from the local node.
 */
public class DataLayerListenerService extends WearableListenerService implements SensorEventListener{

    private static final String TAG = "DataLayerListenerServic";

    private static final String START_ACTIVITY_PATH = "/start-activity";
    private static final String STOP_ACTIVITY_PATH="/stop-activity";
    private static final String START_SENSOR_PATH="/start-sensor";
    private static final String STOP_SENSOR_PATH="/stop-sensor";
    private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";
    public static final String COUNT_PATH = "/count";
    public static final String IMAGE_PATH = "/image";
    private static final String STRING_PATH="/string";
    public static final String IMAGE_KEY = "photo";
    private static final String COUNT_KEY = "count";
    private static final String STRING_KEY="string";

    private static final int MAX_LOG_TAG_LENGTH = 23;
    GoogleApiClient mGoogleApiClient;


    //Sensor
    private SensorManager mSensorManager;
    private List<Sensor> mSensorInfo;
    private Sensor mAccelerometer,mStepCount,mHeartRate,mGyroscope;

    private float gravity[]=new float[3];
    float[] accelerometerValues=new float[3];
    float[] linear_acceleration=new float[3];
    float[] rotation=new float[3];

    private static final float NS2S = 1.0f / 1000000000.0f;
    private final float[] deltaRotationVector = new float[4];
    private static final float EPSILON =0.00001f ;

    static float steps=0,heartRate=0;
    private float timestamp;


    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .build();
        mGoogleApiClient.connect();

        //get all sensor information
        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer= mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);//加速度传感器
        mStepCount=mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);//步数
        mHeartRate = mSensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);//心率
        mGyroscope=mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);//陀螺仪

       /* new Thread(new Runnable() {
            @Override
            public void run() {
                Intent intent=new Intent("com.getSensorData.broadcast.MY_BROADCAST");
                while(true){
                    intent.putExtra("StartHeartRate",true);
                    sendBroadcast(intent);
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    intent.putExtra("StartHeartRate",false);
                    sendBroadcast(intent);
                }

            }
        }).start();*/

    }

   /* public class MyBroadcastReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean start=false;
            start=intent.getBooleanExtra("StartHeartRate",start);
            if (start){
                mSensorManager.registerListener((SensorEventListener) this,mHeartRate,SensorManager.SENSOR_DELAY_UI);
            }else{
                mSensorManager.unregisterListener((SensorEventListener) this,mHeartRate);
            }

        }
    }*/
    @Override
    public void onDestroy() {
        super.onDestroy();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        switch (event.sensor.getType()) {

            case Sensor.TYPE_ACCELEROMETER:            //  处理加速度传感器传回的数据
                Fun_ACCELEROMETER(event);
                break;
            case Sensor.TYPE_STEP_COUNTER:          //获取步数
                 steps=event.values[0];
                break;
            case Sensor.TYPE_HEART_RATE:
                 heartRate = event.values[0];
            case Sensor.TYPE_GYROSCOPE:             //处理陀螺仪数据
                Fun_GYROSCOPE(event);
                break;
            default:
                break;
        }

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

        rotation[0]=(float)(Math.round(event.values[0]+deltaRotationVector[0]*1000))/1000;
        rotation[1]=(float)(Math.round(event.values[1]+deltaRotationVector[1]*1000))/1000;
        rotation[2]=(float)(Math.round(event.values[2]+deltaRotationVector[2]*1000))/1000;

     /*   rotation[0]=(float)(Math.round(event.values[0]*1000))/1000;
        rotation[1]=(float)(Math.round(event.values[1]*1000))/1000;
        rotation[2]=(float)(Math.round(event.values[2]*1000))/1000;*/
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
        linear_acceleration[0] = (float)(Math.round((event.values[0] - gravity[0])*1000))/1000;
        linear_acceleration[1] = (float)(Math.round((event.values[1] - gravity[1])*1000))/1000;
        linear_acceleration[2] = (float)(Math.round((event.values[2] - gravity[2])*1000))/1000;

    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        LOGD(TAG, "onDataChanged: " + dataEvents);
        final List<DataEvent> events = FreezableUtils.freezeIterable(dataEvents);
        dataEvents.close();
        if(!mGoogleApiClient.isConnected()) {
            ConnectionResult connectionResult = mGoogleApiClient
                    .blockingConnect(10, TimeUnit.SECONDS);
            if (!connectionResult.isSuccess()) {
                Log.e(TAG, "DataLayerListenerService failed to connect to GoogleApiClient.");
                return;
            }
        }

        // Loop through the events and send a message back to the node that created the data item.
        for (DataEvent event : events) {
            Uri uri = event.getDataItem().getUri();
            String path = uri.getPath();
            if (COUNT_PATH.equals(path)) {
                // Get the node id of the node that created the data item from the host portion of
                String str=linear_acceleration[0]+","+linear_acceleration[1]+","+linear_acceleration[2]+
                        ";"+rotation[0]+","+rotation[1]+","+rotation[2] + ";" +steps+ ";"+heartRate;

                Wearable.MessageApi.sendMessage(mGoogleApiClient,"nodeId",str,str.getBytes());
            }

        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        LOGD(TAG, "onMessageReceived: " + messageEvent);

        // Check to see if the message is to start an activity
        if (messageEvent.getPath().equals(START_ACTIVITY_PATH)) {
            Intent startIntent = new Intent(this, MainActivity.class);
            startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(startIntent);
        }
        if (messageEvent.getPath().equals(STOP_ACTIVITY_PATH)){

        }
        if (messageEvent.getPath().equals(STOP_SENSOR_PATH)){
            mSensorManager.unregisterListener(this);
        }
        if (messageEvent.getPath().equals(START_SENSOR_PATH)){
            mSensorManager.registerListener(this, mAccelerometer,SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener (this, mGyroscope, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this,mStepCount,SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(this,mHeartRate,SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onPeerConnected(Node peer) {
        LOGD(TAG, "onPeerConnected: " + peer);
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        LOGD(TAG, "onPeerDisconnected: " + peer);
    }

    public static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }
}
