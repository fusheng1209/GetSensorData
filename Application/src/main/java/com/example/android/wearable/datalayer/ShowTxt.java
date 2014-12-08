package com.example.android.wearable.datalayer;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


public class ShowTxt extends Activity {

    //private ListView lv_txt;

    ProgressDialog progressDialog;
    TextView textView;
    public static final int UPDATE_TEXT = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_txt);
        textView = (TextView) findViewById(R.id.textView);
        textView.setMovementMethod(new ScrollingMovementMethod());

        Intent intent = getIntent();
        String fileName = intent.getStringExtra("FILENAME");
        getWindow().setTitle(fileName);
        ReadTxtFile(fileName);


    }
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case UPDATE_TEXT:
                    textView.append(msg.obj.toString());
                    break;
                default:
                    break;
            }
        }
    };

    //读取文本文件中的内容
    public void ReadTxtFile(String strFilePath) {
        String path = strFilePath;
        //打开文件
        final File file = new File(path);
        final Message message = new Message();

        //如果path是传递过来的参数，可以做一个非目录的判断
        if (file.isDirectory()) {
            Log.d("TAG", "The File doesn't not exist.");
        } else {
            progressDialog = ProgressDialog.show(ShowTxt.this, "加载中", "正在加载，请稍后......", true);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    InputStream inStream = null;
                    try {
                        inStream = new FileInputStream(file);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    if (inStream != null) {
                        InputStreamReader inputReader = new InputStreamReader(inStream);
                        BufferedReader buffReader = new BufferedReader(inputReader);
                        String line;
                        StringBuilder stringBuilder = new StringBuilder();
                        //分行读取
                        try {
                            while ((line = buffReader.readLine()) != null) {
                                stringBuilder.append(line);
                                stringBuilder.append("\n");
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                message.what = UPDATE_TEXT;
                                message.obj = stringBuilder;
                                handler.sendMessage(message);
                                buffReader.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            finally {
                                try {
                                    Thread.sleep(500);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                progressDialog.dismiss();
                            }
                        }
                    }
                }
            }).start();
        }


    }
}




