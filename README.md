
# NB-IoT 轉換器方案展示範例程式 - 控制端

## 操作流程 (workflow)

1. NB-IoT 轉換器每隔 1 分鐘會與基地台建立連線.
2. NB-IoT 轉換器向雲平台送出 heart beat 封包.
3. 雲平台使用 MQTT 協定, 將 heart beat 封包傳送給控制端, 為此範例程式. 
4. 控制端收到 heart beat 封包後, 再利用 MQTT 協定對 NB-IoT 轉換器送出 Modbus 指令. 
5. NB-IoT 轉換器將 Modbus 指令, 轉送至它連接的溫濕度計 (via RS-232, RS-485 or LoRa), 並得到 Modbus 回應結果. 
6. NB-IoT 轉換器再將溫濕度計的 Modbus 回應結果, 上傳至雲平台. 
7. 雲平台透過 MQTT 協定, 將 Mdobus 回應結果回傳給控制端, 最後呈現溫濕度數值. 
8. 若 NB-IoT 轉換器在 20 秒內, 沒有收到來自控制端的 Modbus 指令, 會向碩久雲平台送出 disconnect 封包. 
9. NB-IoT 轉換器與基地台斷線, 休眠 1 分鐘後再重複以上的流程. 

![流程圖](https://github.com/maxlong-tw/nbiot-demo-client-java/raw/master/workflow.png)

## compile 與 execute 前的準備

1. Java Development Kits 8 以上版本 https://www.oracle.com/technetwork/java/javase/downloads/index.html#JDK8

2. gradle 編程工具 https://gradle.org/install/#manually

3. 執行範例程式的主機, 需與 maxlong.ddns.net 的 TCP port 1883 建立連線, 請先檢查防火牆設定.

4. 插入 SIM card 至 NB-IoT 轉換器, 設定正確的 APN 名稱. (遠傳為 nbiot, 中華為 internet)

5. 將 NB-IoT 轉換器連接到 Modbus 溫溼度計 (via RS-232, RS-485 or LoRa), 在 NB-IoT 轉換器 Bridge 頁籤確認 port 對象.

## compile 與 execute

1. 取得程式碼:

        git clone https://github.com/maxlong-tw/nbiot-demo-client-java.git
        
2. 切換程式碼目錄:

        cd nbiot-demo-client-java
        
3. 更改程式碼 imei 變數的設定, 對應到 NB-IoT 轉換器的 IMEI:        
        
4. compile 並產生 zip 檔：

        gradle distZip
        
5. 將程式碼目錄 build/distributions/nbiot-demo-client-java.zip 解壓縮至任一目錄. 
6. 執行解壓縮目錄中的 bin/nbiot-demo-client-java.bat
7. 請按下 Ctrl + C 結束程式.
