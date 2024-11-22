package com.example.remote_watering;

import android.os.Bundle;
import android.os.Handler;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

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

    private static final String ESP32_IP = "http://192.168.1.100";  // ESP32的IP地址
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
                temperatureThresholdText.setText("温度阈值: " + progress + "°C");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        SoilMoistureSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                SoilMoistureThresholdText.setText("湿度阈值: " + progress + "%");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 启动定时任务
        handler.post(fetchSensorDataRunnable);
    }

    // 请求ESP32进行手动浇水
    private void sendWateringRequest() {
        new Thread(() -> {
            try {
                URL url = new URL(ESP32_IP + "/water?mode=manual");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() -> Toast.makeText(this, "Watering started", Toast.LENGTH_SHORT).show());
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 请求ESP32进行手动降温
    private void sendColdingRequest() {
        new Thread(() -> {
            try {
                URL url = new URL(ESP32_IP + "/cold?mode=manual");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() -> Toast.makeText(this, "Colding started", Toast.LENGTH_SHORT).show());
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 设置自动浇水模式
    private void sendAutoWateringRequest(String mode) {
        new Thread(() -> {
            try {
                URL url = new URL(ESP32_IP + "/water?mode=" + mode);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // 设置自动降温模式
    private void sendAutoColdingRequest(String mode) {
        new Thread(() -> {
            try {
                URL url = new URL(ESP32_IP + "/cold?mode=" + mode);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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

    // 解析并显示传感器数据
    private void parseAndDisplaySensorData(String jsonData) {
        runOnUiThread(() -> {
            try {
                // 假设ESP32返回的数据是JSON格式
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(fetchSensorDataRunnable);  // 取消定时任务
    }
}


