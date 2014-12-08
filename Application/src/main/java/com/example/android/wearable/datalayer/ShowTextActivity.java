package com.example.android.wearable.datalayer;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ShowTextActivity extends Activity {
    private ListView lv;
    ArrayList name;
    String PATH="";
    SimpleAdapter adapter;


    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_show_text);
        lv = (ListView) findViewById(R.id.lv);
        name = new ArrayList();

        if (Environment.getExternalStorageState().equals(
                Environment.MEDIA_MOUNTED)) {
            // File path = Environment.getExternalStorageDirectory();// 获得SD卡路径
            File path =  Environment.getExternalStoragePublicDirectory("SensorData");
            PATH = path.getAbsoluteFile().toString();
            Log.d("TAG",path.toString());
            File[] files = path.listFiles();// 读取
            getFileName(files);
        }
        adapter = new SimpleAdapter(this, name, R.layout.sd_list,
                new String[] { PATH }, new int[] { R.id.txt_tv });
        lv.setAdapter(adapter);

        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int index, long l) {
                String fileName = name.get(index).toString();
                String path = fileName.substring(fileName.lastIndexOf("{")+1,fileName.lastIndexOf("="));
                String fileName0 = fileName.substring(fileName.lastIndexOf("=")+1,fileName.lastIndexOf("}"));
                fileName = path+"/"+fileName0+".txt";
                Log.d("TAG",fileName);
                Intent intent = new Intent(ShowTextActivity.this,ShowTxt.class);
                intent.putExtra("FILENAME",fileName);
                startActivity(intent);

            }
        });
        ItemOnLongClick();

    }


    private void getFileName(File[] files) {
        if (files != null) {// 先判断目录是否为空，否则会报空指针
            for (File file : files) {
                if (file.isDirectory()) {
                    getFileName(file.listFiles());
                } else {
                    String fileName = file.getName();
                    if (fileName.endsWith(".txt")) {
                        HashMap map = new HashMap();
                        fileName=file.toString().substring(file.toString().lastIndexOf("SensorData/")+11,file.toString().lastIndexOf("."));
                        map.put(PATH, fileName);
                        name.add(map);
                    }
                }
            }
        }
    }



    private void ItemOnLongClick() {
        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, final View arg1,
                                           final int arg2, long arg3) {
                new AlertDialog.Builder(ShowTextActivity.this)
                        .setTitle("对Item进行操作")
                        .setItems(R.array.arrcontent,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        String[] PK = getResources()
                                                .getStringArray(
                                                        R.array.arrcontent);
                                        if (PK[which].equals("删除")) {
                                            // 删除文件
                                            deleteFile(arg2);
                                        }
                                        if (PK[which].equals("删除所有")) {
                                            // 按照这种方式做删除操作，这个if内的代码有bug，实际代码中按需操作
                                            Log.d("TAG","删除所有".toString());
                                            deleteAllFiles();
                                        }
                                    }
                                })
                        .setNegativeButton("取消",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        // TODO Auto-generated method stub

                                    }
                                }).show();
                return true;
            }
        });
    }

    private void deleteFile(final int index){
        String fileName = name.get(index).toString();
        String path = fileName.substring(fileName.lastIndexOf("{")+1,fileName.lastIndexOf("="));
        String fileName0 = fileName.substring(fileName.lastIndexOf("=")+1,fileName.lastIndexOf("}"));
        fileName = path+"/"+fileName0+".txt";

        final File file= new File(fileName);
        // 选择的item为删除文件
        new AlertDialog.Builder(ShowTextActivity.this)
                .setTitle("注意")
                .setMessage("确定删除文件？")
                .setPositiveButton("确定",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {
                                // TODO Auto-generated method stub
                                file.delete();
                                name.remove(index);
                                adapter = (SimpleAdapter) lv
                                        .getAdapter();
                                adapter.notifyDataSetChanged(); // 实现数据的实时刷新

                            }
                        })
                .setNegativeButton("取消",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {
                                // TODO Auto-generated method stub

                            }
                        }).show();
    }


    private void deleteAllFiles(){

        // 选择的item为删除文件
        new AlertDialog.Builder(ShowTextActivity.this)
                .setTitle("注意")
                .setMessage("确定删除文件？")
                .setPositiveButton("确定",
                        new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {
                                // TODO Auto-generated method stub
                                for (int i=0;i<name.size();i++){
                                    String fileName = name.get(i).toString();
                                    String path = fileName.substring(fileName.lastIndexOf("{")+1,fileName.lastIndexOf("="));
                                    String fileName0 = fileName.substring(fileName.lastIndexOf("=")+1,fileName.lastIndexOf("}"));
                                    fileName = path+"/"+fileName0+".txt";
                                    final File file= new File(fileName);
                                    file.delete();
                                }
                                name.clear();
                                adapter = (SimpleAdapter) lv
                                        .getAdapter();
                                adapter.notifyDataSetChanged(); // 实现数据的实时刷新
                            }
                        })
                .setNegativeButton("取消",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(
                                    DialogInterface dialog,
                                    int which) {
                                // TODO Auto-generated method stub

                            }
                        }).show();
    }

}

