package com.example.gyrotest;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    /*Wizets*/
    private TextView tv_roll, tv_pitch;

    /*Used for Accelometer & Gyroscoper*/
    private SensorManager mSensorManager = null;
    private UserSensorListner userSensorListner;
    private Sensor mGyroscopeSensor = null;
    private Sensor mAccelerometer = null;

    /*Sensor variables*/
    private float[] mGyroValues = new float[3];
    private float[] mAccValues = new float[3];
    private double mAccPitch, mAccRoll;

    /*for unsing complementary fliter*/
    private float a = 0.2f;
    private static final float NS2S = 1.0f/1000000000.0f;
    private double pitch = 0, roll = 0;
    private double timestamp;
    private double dt;
    private double temp;
    private boolean running;
    private boolean gyroRunning;
    private boolean accRunning;
    private WebView d3;

    /*check tilt*/
    private static final int TILT_CHECK_TIME = 100000000;
    private double checkTimeForTilt;

    String[] wordCloud = new String[]{ "Donut", "Eclair", "Froyo", "Gingerbread", "Honeycomb",
            "Ice Cream Sandwich", "Jelly Bean", "KitKat", "Lollipop", "Marshmallow"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        d3 = (WebView) findViewById(R.id.d3_cloud);

        tv_roll = (TextView)findViewById(R.id.tv_roll);
        tv_pitch = (TextView)findViewById(R.id.tv_pitch);

        mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        userSensorListner = new UserSensorListner();
        mGyroscopeSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mAccelerometer= mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        /* 실행 중이지 않을 때 -> 실행 */
        checkGyroAcc();

        WebSettings ws = d3.getSettings();
        ws.setJavaScriptEnabled(true);
        d3.loadUrl("file:///android_asset/d3.html");
        d3.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                StringBuffer sb = new StringBuffer();
                sb.append("wordCloud([");
                for (int i = 0; i < wordCloud.length; i++) {
                    sb.append("'").append(wordCloud[i]).append("'");
                    if (i < wordCloud.length - 1) {
                        sb.append(",");
                    }
                }
                sb.append("])");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    d3.evaluateJavascript(sb.toString(), null);
                } else {
                    d3.loadUrl("javascript:" + sb.toString());
                }
            }
        });
    }

    /**
     * 1차 상보필터 적용 메서드 */
    private void complementaty(double new_ts){

        /* 자이로랑 가속 해제 */
        gyroRunning = false;
        accRunning = false;

        /*센서 값 첫 출력시 dt(=timestamp - event.timestamp)에 오차가 생기므로 처음엔 break */
        if(timestamp == 0){
            timestamp = new_ts;
            return;
        }
        dt = (new_ts - timestamp) * NS2S; // ns->s 변환
        timestamp = new_ts;

        /* degree measure for accelerometer */
        mAccPitch = -Math.atan2(mAccValues[0], mAccValues[2]) * 180.0 / Math.PI; // Y 축 기준
        mAccRoll= Math.atan2(mAccValues[1], mAccValues[2]) * 180.0 / Math.PI; // X 축 기준

        /**
         * 1st complementary filter.
         *  mGyroValuess : 각속도 성분.
         *  mAccPitch : 가속도계를 통해 얻어낸 회전각.
         */
        temp = (1/a) * (mAccPitch - pitch) + mGyroValues[1];
        pitch = pitch + (temp*dt);

        temp = (1/a) * (mAccRoll - roll) + mGyroValues[0];
        roll = roll + (temp*dt);

        if(pitch > 40.0)
            pitch = 40.0;
        if(pitch < -40.0)
            pitch = -40.0;

        tv_roll.setText("roll : "+(int)roll);
        tv_pitch.setText("pitch : "+(int)pitch);

    }

    public class UserSensorListner implements SensorEventListener{

        @Override
        public void onSensorChanged(SensorEvent event) {
            switch (event.sensor.getType()){

                /** GYROSCOPE */
                case Sensor.TYPE_GYROSCOPE:

                    /*센서 값을 mGyroValues에 저장*/
                    mGyroValues = event.values;

                    if(!gyroRunning)
                        gyroRunning = true;

                    break;

                /** ACCELEROMETER */
                case Sensor.TYPE_ACCELEROMETER:

                    /*센서 값을 mAccValues에 저장*/
                    mAccValues = event.values;

                    if(!accRunning)
                        accRunning = true;

                    break;

            }

            /*두 센서 새로운 값을 받으면 상보필터 적용*/
            if(gyroRunning && accRunning){
                complementaty(event.timestamp);

                // 센서값 d3.html 에 전달
                if(d3 != null){
                    StringBuffer sb = new StringBuffer();
                    sb.append("update(");
                    sb.append((int)pitch);
                    sb.append(")");

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        d3.evaluateJavascript(sb.toString(), null);
                    } else {
                        d3.loadUrl("javascript:" + sb.toString());
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    }

    @Override
    protected void onPause(){
        super.onPause();
        mSensorManager.unregisterListener(userSensorListner);
    }

    @Override
    protected void onResume(){
        super.onResume();
        checkGyroAcc();
    }

    @Override
    protected void onStart(){
        super.onStart();
        checkGyroAcc();
    }

    private void checkGyroAcc(){
        if(!running){
            running = true;
            mSensorManager.registerListener(userSensorListner, mGyroscopeSensor, SensorManager.SENSOR_DELAY_UI);
            mSensorManager.registerListener(userSensorListner, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        else if(running)
        {
            running = false;
            mSensorManager.unregisterListener(userSensorListner);

        }
    }
}