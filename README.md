
# 碩久 NB-IoT 方案展示範例程式 - 『』

## 操作流程

1. NB-IoT 轉換器每間隔 1 分鐘會與基地台建立連線。
2. NB-IoT 轉換器向碩久雲平台送出『連線』訊息。
3. 碩久雲平台透過 MQTT 協定，將『連線』訊息傳送給控制端，也就是此範例程式。
4. 控制端收到『連線』訊息後，透過 MQTT 協定對 NB-IoT 轉換器送出 Modbus 指令。
5. NB-IoT 轉換器將 Modbus 指令，送至其銜接的溫濕度計，並得到 Modbus 回應數值。
6. NB-IoT 轉換器再將溫濕度計的 Modbus 回應數值，上傳至碩久雲平台。
7. 碩久雲平台透過 MQTT 協定，將 Mdobus 回應數值回傳給控制端，藉此呈現溫濕度數值。
8. 當 NB-IoT 轉換器在 20 秒內，沒有收到來自控制端的指令，會向碩久雲平台送出『離線』訊息。
9. NB-IoT 轉換器與基地台斷線，休眠 1 分鐘後再重複上述流程。

![流程圖](https://github.com/maxlong-tw/nbiot-demo-client-java/raw/master/workflow.png)

## 編譯需求

1. 安裝 JDK 8 以上版本 https://www.oracle.com/technetwork/java/javase/downloads/index.html#JDK8

2. 安裝 gradle 編譯軟體 https://gradle.org/install/#manually

## 編譯與執行

1. 取得程式碼：

        git clone https://github.com/maxlong-tw/nbiot-demo-client-java.git
        
2. 切換至程式碼目錄：

        cd nbiot-demo-client-java
        
3. 編譯程式碼並打包：

        gradle distZip
        
4. 將程式碼目錄中的 build/distributions/nbiot-demo-client-java.zip 解壓縮至任一目錄。
5. 執行解壓縮目錄中的 bin/nbiot-demo-client-java.bat。
6. 若要離開程式，請按下 Ctrl + C
