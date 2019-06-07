
# 碩久 NB-IoT 方案展示範例程式

## 操作流程

1. NB-IoT 轉換器每間隔 1 分鐘會與基地台建立連線。
1. NB-IoT 轉換器向碩久雲平台送出『連線』訊息。
1. 碩久雲平台透過 MQTT 協定，將『連線』訊息傳送給控制端，也就是此範例程式。
1. 控制端收到『連線』訊息後，透過 MQTT 協定對 NB-IoT 轉換器送出 Modbus 指令。
1. NB-IoT 轉換器將 Modbus 指令，送至其銜接的溫濕度計，並得到 Modbus 回應數值。
1. NB-IoT 轉換器再將溫濕度計的 Modbus 回應數值，上傳至碩久雲平台。
1. 碩久雲平台透過 MQTT 協定，將 Mdobus 回應數值回傳給控制端，藉此呈現溫濕度數值。
1. 當 NB-IoT 轉換器在 20 秒內，沒有收到來自控制端的指令，會向碩久雲平台送出『離線』訊息。
1. NB-IoT 轉換器與基地台斷線，休眠 1 分鐘後再重複上述流程。

## 編譯與執行

1. 取得程式碼：

        git clone  https://github.com/maxlong-tw/nbiot-demo-client-java.git
        
2. 切換至程式碼目錄：

        cd nbiot-demo-client-java
        
3. 編譯程式碼並打包：

        gradle distZip
        
4. 將程式碼目錄中的 build/distributions/nbiot-demo-client-java.zip 解壓縮至任一目錄。
5. 執行解壓縮目錄中的 bin/nbiot-demo-client-java.bat。
6. 若要離開程式，請按下 Ctrl + C
