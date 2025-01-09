#include <WiFi.h>
#include <WebServer.h>
#include <Adafruit_Sensor.h>
#include "DHT.h"

#define DHTTYPE DHT11   // DHT 11
#define DHTPIN 4        //// 温湿度传感器连接到GPIO4 
DHT dht(DHTPIN, DHTTYPE);

const int soilMoisturePin = 6;  // 土壤湿度传感器连接到GPIO6

// L298N 引脚定义
#define ENA 41      // 电机 1 的 PWM 引脚
#define IN1 40      // 电机 1 正转引脚
#define IN2 39      // 电机 1 反转引脚
#define IN3 38      // 电机 2 正转引脚
#define IN4 37      // 电机 2 反转引脚
#define ENB 36      // 电机 2 的 PWM 引脚

// Wi-Fi 连接信息
const char* ssid = "CU_uNQd";
const char* password = "yks4yeeb";

// ESP32的IP地址
IPAddress local_IP(192, 168, 1, 100);
IPAddress gateway(192, 168, 1, 1);
IPAddress subnet(255, 255, 255, 0);

// 创建Web服务器，监听端口80
WebServer server(80);
// 自动浇水模式标志
bool autoWateringEnabled = false;
// 自动冷却模式标志
bool autoColdingEnabled = false;
// 土壤湿度阈值
int soilMoistureThreshold = 50;
// 温度阈值
int temperatureThreshold = 30;
// 温度、湿度、土壤湿度全局变量
int temperature,humidity,soilmoisture;

// 初始化设置，上电调用
void setup() {
  Serial.begin(115200);
  // dht温湿度初始化
  dht.begin();
  // 初始化Wi-Fi
  WiFi.config(local_IP, gateway, subnet);
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(1000);
    Serial.println("Connecting to WiFi...");
  }
  Serial.println("Connected to WiFi");


  // 设置引脚为输出模式
  pinMode(ENA, OUTPUT);
  pinMode(IN1, OUTPUT);
  pinMode(IN2, OUTPUT);
  pinMode(ENB, OUTPUT);
  pinMode(IN3, OUTPUT);
  pinMode(IN4, OUTPUT);

  // 启动时所有电机停止
  stopMotor1();
  stopMotor2();

  // 配置服务器端点和handle函数
  server.on("/water", handleWaterRequest);
  server.on("/cold", handleColdRequest);
  server.on("/sensor", handleSensorData);
  server.on("/setThreshold",handleSetThreshold);
  
  // 启动服务器
  server.begin();
  Serial.println("HTTP server started");
}

void loop() {
  // 处理HTTP请求
  server.handleClient();

  // 检查自动浇水模式
  if (autoWateringEnabled) {
    if (soilmoisture < soilMoistureThreshold) {  // 根据湿度值进行阈值设定
      forwardMotor1(); // 开启水泵
    }
    else {
      stopMotor1(); // 关闭水泵
    }
  }
  else {
   stopMotor1(); // 关闭水泵
  }


  // 检查自动冷却模式
  if (autoColdingEnabled) {
    if (temperature > temperatureThreshold) {  // 根据湿度值进行阈值设定
      forwardMotor2();//开启风扇
    }
    else {
      stopMotor2();//关闭风扇
    }
  }
  else { 
    stopMotor2();//关闭风扇
  }
}

bool waterFlag=0;
// 处理手动和自动浇水请求
void handleWaterRequest() {
  String mode = server.arg("mode");

  if (mode == "manual") { // 当浇水请求为手动
    waterFlag = !waterFlag; //翻转当前的waterFlag
    if(waterFlag){
      forwardMotor1(); // 开启水泵
      server.send(200, "text/plain", "手动浇水开启");
    }
    else{
      stopMotor1(); // 关闭水泵
      server.send(200, "text/plain", "手动浇水关闭");
    }
    
  } 
  else if (mode == "on") {
    autoWateringEnabled = true;
    server.send(200, "text/plain", "自动浇水启用");
  } 
  else if (mode == "off") {
    autoWateringEnabled = false;
    server.send(200, "text/plain", "自动浇水停止");
  } 
  else {
    server.send(400, "text/plain", "Invalid mode");
  }
}

bool coldingFlag = 0;
// 处理手动和自动冷却请求
void handleColdRequest() {
  String mode = server.arg("mode");

  if (mode == "manual") {
    coldingFlag = ! coldingFlag; // 翻转标志
    if(coldingFlag){
      forwardMotor2();// 开启风扇
      server.send(200, "text/plain", "手动降温开启");
    }
    else{
      stopMotor2();// 关闭风扇
      server.send(200, "text/plain", "手动降温关闭");
    }
    
  } 
  else if (mode == "on") {
    autoColdingEnabled = true;
    server.send(200, "text/plain", "自动降温启用");
  } 
  else if (mode == "off") {
    autoColdingEnabled = false;
    server.send(200, "text/plain", "自动降温停止");
  } 
  else {
    server.send(400, "text/plain", "Invalid mode");
  }
}

// 返回土壤湿度和温湿度数据
void handleSensorData() {
  int m = 100-(analogRead(soilMoisturePin)-1100)/17;    // analogRead(soilMoisturePin)读取土壤湿度ADC测定2780-1170（空气中-完全浸泡），给定区间（2800，1100）
  int h = dht.readHumidity();// 读取湿度
  int t = dht.readTemperature();// 读取温度（摄氏）false
  // int f = dht.readTemperature(true);// 读取温度（华氏）
  
  // 检查是否有任何读取失败并提前退出（重试）
  if (isnan(h) || isnan(t)) {
    Serial.println(F("Failed to read from DHT sensor!"));
    return;
  }
  Serial.printf("Humidity: %d\n",h);
  Serial.printf("Temperature: %d\n",t);
  Serial.printf("Soil moisture: %d\n",m);

  // 打包json，格式为{"temperature": xx, "humidity": xx, "soilmoisture": xx}
  String jsonResponse = "{\"temperature\": " + String(t) + ", \"humidity\": "+String(h)+
                        ", \"soilmoisture\": " + String(m) + "}";

  server.send(200, "application/json", jsonResponse);

  // 更新至全局变量
  temperature=t;
  humidity=h;
  soilmoisture=m;
}

// 接收并更新自动控制中温度/土壤湿度的阈值
void handleSetThreshold() {
  String type = server.arg("type");  // 获取类型参数（soilMoisture 或 temperature）
  String value = server.arg("value");  // 获取阈值参数

  if (type == "soilMoisture") {
    soilMoistureThreshold = value.toInt();  // 更新土壤湿度阈值
  } else if (type == "temperature") {
    temperatureThreshold = value.toInt();  // 更新温度阈值
  }
  server.send(200, "text/plain", "Threshold updated");
}

// 电机动作
// 电机 1 前进
void forwardMotor1() {
  digitalWrite(IN1, HIGH);
  digitalWrite(IN2, LOW);
  digitalWrite(ENA, HIGH);
}

// 电机 1 停止
void stopMotor1() {
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, LOW);
  digitalWrite(ENA, LOW);
}

// 电机 2 前进
void forwardMotor2() {
  digitalWrite(IN3, HIGH);
  digitalWrite(IN4, LOW);
  digitalWrite(ENB, HIGH);
}

// 电机 2 停止
void stopMotor2() {
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, LOW);
  digitalWrite(ENB, LOW);
}
