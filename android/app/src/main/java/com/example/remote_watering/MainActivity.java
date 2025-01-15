package com.example.remote_watering;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private TextView temperatureText, humidityText, soilmoistureText, temperatureThresholdText, SoilMoistureThresholdText;
    private Button manualWaterButton,manualColdButton;
    private Switch autoWaterSwitch,autoColdSwitch;
    private SeekBar temperatureSeekBar, SoilMoistureSeekBar;
    private WebView myWebView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private static final String ESP32_IP = "http://192.168.43.88";  // ESP32的IP地址
    private Handler handler = new Handler();
    private final int FETCH_INTERVAL = 5000;  // 每5秒请求一次数据

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        temperatureText = findViewById(R.id.temperatureText);
        humidityText = findViewById(R.id.humidityText);
        soilmoistureText = findViewById(R.id.SoilMoistureText);
        temperatureThresholdText = findViewById(R.id.temperatureThresholdText);
        SoilMoistureThresholdText = findViewById(R.id.SoilMoistureThresholdText);

        manualWaterButton = findViewById(R.id.manualWaterButton);
        manualColdButton = findViewById(R.id.manualColdButton);

        autoWaterSwitch = findViewById(R.id.autoWaterSwitch);
        autoColdSwitch = findViewById(R.id.autoColdSwitch);

        temperatureSeekBar = findViewById(R.id.temperatureSeekBar);
        SoilMoistureSeekBar = findViewById(R.id.SoilMoistureSeekBar);

        myWebView = findViewById(R.id.webview);
        myWebView.setWebViewClient(new WebViewClient()); // 在 WebView 内部加载 URL

        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true); // 启用 JavaScript
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true); // 适配网页宽度
        webSettings.setBuiltInZoomControls(false); // 禁用缩放
        webSettings.setDisplayZoomControls(false); // 不显示缩放按钮

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        // 加载要展示的网页
//        myWebView.loadUrl("http://192.168.1.101/mjpeg/1");
        myWebView.loadUrl("http://192.168.43.89/mjpeg/1");
        // 下拉刷新逻辑
        swipeRefreshLayout.setOnRefreshListener(() -> {
            myWebView.reload(); // 刷新 WebView
            swipeRefreshLayout.setRefreshing(false); // 停止刷新动画
            Toast.makeText(this, "刷新成功", Toast.LENGTH_SHORT).show();
        });

        manualWaterButton.setOnClickListener(view -> sendWateringRequest());
        manualColdButton.setOnClickListener(view -> sendColdingRequest());

        autoWaterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                sendAutoWateringRequest("on");
            } else {
                sendAutoWateringRequest("off");
            }
        });
        autoColdSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                sendAutoColdingRequest("on");
            } else {
                sendAutoColdingRequest("off");
            }
        });

        temperatureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 更新显示
                temperatureThresholdText.setText("温度阈值: " + progress + "°C");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 用户抬手时发送设定的阈值
                sendThreshold("temperature",seekBar.getProgress());
            }
        });

        SoilMoistureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 更新显示
                SoilMoistureThresholdText.setText("土壤湿度阈值: " + progress + "%");
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // 用户抬手时发送设定的阈值
                sendThreshold("soilMoisture",seekBar.getProgress());
            }
        });

        // 启动定时任务
        handler.post(fetchSensorDataRunnable);
    }

    // 请求ESP32进行手动浇水
    private void sendWateringRequest() {
        String request="/water?mode=manual";
        requestSendAndReaction(request);
    }
    // 请求ESP32进行手动降温
    private void sendColdingRequest() {
        String request="/cold?mode=manual";
        requestSendAndReaction(request);
    }

    // 设置自动浇水模式
    private void sendAutoWateringRequest(String mode) {
        String request="/water?mode=" + mode;
        requestSendAndReaction(request);
    }

    // 设置自动降温模式
    private void sendAutoColdingRequest(String mode) {
        String request="/cold?mode=" + mode;
        requestSendAndReaction(request);
    }

    // 定时请求温湿度数据的任务
    private final Runnable fetchSensorDataRunnable = new Runnable() {
        @Override
        public void run() {
            fetchSensorData();  // 执行请求传感器数据

            handler.postDelayed(this, FETCH_INTERVAL);  // 每隔FETCH_INTERVAL毫秒再次执行
        }
    };

    // 请求温湿度数据
    private void fetchSensorData() {
        new Thread(() -> {
            try {
                URL url = new URL(ESP32_IP + "/sensor");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                // 解析并显示数据
                parseAndDisplaySensorData(response.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    //发送阈值给esp332
    private void sendThreshold(String type, int value) {
        new Thread(() -> {
            try {
                // 构建请求 URL，发送数据类型和阈值
                URL url = new URL(ESP32_IP + "/setThreshold?type=" + type + "&value=" + value);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                // 发送请求并检查响应
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 如果成功，可以在 UI 线程中显示反馈
                    runOnUiThread(() -> Toast.makeText(this, type.equals("temperature")?"温度阈值已发送":"湿度阈值已发送", Toast.LENGTH_SHORT).show());
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "发送失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }


    // 解析并显示传感器数据
    private void parseAndDisplaySensorData(String jsonData) {
        runOnUiThread(() -> {
            try {
                // ESP32返回的数据是JSON格式，格式为{"temperature": xx, "humidity": xx, "soilmoisture": xx}
                JSONObject jsonObject = new JSONObject(jsonData);
                int temperature = jsonObject.getInt("temperature");
                int humidity = jsonObject.getInt("humidity");
                int soilmoisture =jsonObject.getInt("soilmoisture");

                temperatureText.setText("温度: " + temperature + "°C");
                humidityText.setText("湿度: " + humidity + "%");
                soilmoistureText.setText("土壤湿度: "+soilmoisture + "%");

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // 通用的发送get请求及显示响应信息
    void requestSendAndReaction(String request){
        new Thread(() -> {
            try {
                URL url = new URL(ESP32_IP + request);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                // 获取 HTTP 响应码
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 读取响应信息
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    // 在主线程显示提示信息
                    runOnUiThread(() -> Toast.makeText(this, "ESP32 响应: " + response.toString(), Toast.LENGTH_SHORT).show());
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(fetchSensorDataRunnable);  // 取消定时任务
    }
}


