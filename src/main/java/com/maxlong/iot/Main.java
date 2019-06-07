package com.maxlong.iot;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 基於碩久雲平台的 MQTT 協定，展示如何與 NB-IoT 轉換器進行通訊，取得溫濕度計數值。
 * 
 * 1) NB-IoT 轉換器每間隔 1 分鐘會與基地台建立連線。
 * 2) NB-IoT 轉換器向碩久雲平台送出『連線』訊息。
 * 3）碩久雲平台透過 MQTT 協定，將『連線』訊息傳送給控制端，也就是此範例程式。
 * 4）控制端收到『連線』訊息後，透過 MQTT 協定對 NB-IoT 轉換器送出 Modbus 指令。
 * 5）NB-IoT 轉換器將 Modbus 指令，送至其銜接的溫濕度計，並得到 Modbus 回應數值。
 * 6）NB-IoT 轉換器再將溫濕度計的 Modbus 回應數值，上傳至碩久雲平台。
 * 7）碩久雲平台透過 MQTT 協定，將 Mdobus 回應數值回傳給控制端，藉此呈現溫濕度數值。
 * 8）當 NB-IoT 轉換器在 20 秒內，沒有收到來自控制端的指令，會向碩久雲平台送出『離線』訊息。
 * 9) NB-IoT 轉換器與基地台斷線，休眠 1 分鐘後再重複上述流程。
 * 
 * @author Jack
 *
 */

@Slf4j
public class Main {
	final ObjectMapper jackson = new ObjectMapper();
	
	String imei = "866425030013488"; // NB-IoT 轉換器 LTE 模組的 IMEI 編號，共 15 碼數字
	
	// 碩久的雲平台，支援 MQTT 物聯網協定
	String broker = "tcp://maxlong.ddns.net:1883";
	String username = "demo";
	String password = "maxlong";

	MqttClient mqtt;	
	
	// MQTT Topic - 接收 NB-IoT 轉換器的上線狀態
	String topicStatus = String.format("/maxlong/broker/imei/%s/status", imei);
	
	// MQTT Topic - 將資料傳送到 NB-IoT 轉換器上
	String topicTx = String.format("/maxlong/broker/imei/%s/tx", imei);
	
	// MQTT Topic - 接收來自 NB-IoT 轉換器的資料
	String topicRx = String.format("/maxlong/broker/imei/%s/rx", imei);
	
	ExecutorService executor = Executors.newCachedThreadPool();
	
	public Main() {		
	}
	
	void run() throws Exception {
		MqttConnectOptions opt = new MqttConnectOptions();		
		opt.setUserName(username);
		opt.setPassword(password.toCharArray());
		opt.setCleanSession(true);

		// 建立 MQTT 連線
		log.info("向碩久雲平台建立 MQTT 連線 - {}", broker);		
		mqtt = new MqttClient(broker, MqttClient.generateClientId(), new MemoryPersistence());
		mqtt.connect(opt);
		
		// 設定接收來自碩久雲平台的 MQTT 訊息
		mqtt.setCallback(new MqttCallback() {
			
			@Override
			public void messageArrived(String topic, MqttMessage message) throws Exception {
				executor.execute(() -> { // 收到來自雲平台的訊息
					onMessage(topic, message.getPayload());
				});
			}
			
			@Override
			public void deliveryComplete(IMqttDeliveryToken token) {
			}
			
			@Override
			public void connectionLost(Throwable cause) {
			}
		});

		// 訂閱接收 NB-IoT 轉換器的『狀態』訊息
		log.info("準備接收 NB-IoT 轉換器 MQTT 的『狀態』訊息 - {}", topicStatus);
		mqtt.subscribe(topicStatus); // wait for heartbeat
		
		// 訂閱接收 NB-IoT 轉換器的『資料』訊息
		log.info("準備接收 NB-IoT 轉換器 MQTT 的『回應』訊息 - {}", topicRx);
		mqtt.subscribe(topicRx); // wait for incoming data
	}
	
	// 處理來自雲平台的 MQTT 訊息
	void onMessage(String topic, byte[] payload) {
		try {		
			if (topicStatus.equals(topic)) { // 如果是 NB-IoT 轉換器的『狀態』訊息
				String json = new String(payload);
				Map<?, ?> msg = jackson.readValue(json, Map.class);			
				String type = (String) msg.get("type");			
				if ("heartbeat".equals(type)) { // 來自 NB-IoT 連線上來的狀態，內容為 {"type":"heartbeat","timestamp":"2019-06-06T22:33:09.449Z","rssi":-65,"from":"211.77.241.100:40893"}
					sendModbusReq(); // 收到『連線』訊息，開始送 Modbus 指令詢問溫濕度計狀態
					
				} else if ("disconnect".equals(type)) { // 來自 NB-IoT 的離線狀態，內容為 {"type":"disconnect","timestamp":"2019-06-06T22:35:42.112Z","from":"211.77.241.100:42432"}
					log.warn("{} 已經離線", imei);
					
				} else {
					log.error("未知的 MQTT 訊息 - {}", json);				
				}
				
			} else if (topicRx.equals(topic)) { // 來自 NB-IoT 的『資料』訊息
				recvModbusReply(payload);
			}
			
		} catch (Exception e) {
			log.error("無法正常處理 MQTT 訊息", e);
		}
	}
	
	// 向站點 2 的溫濕度計透過 Modbus/RTU 協定取得當前數值
	void sendModbusReq() {
		byte[] req = new byte[] {
				0x02,				// slave id = 2
				0x03,				// read holding register
				0x00, 0x00,			// address = 0
				0x00, 0x02,			// quantity = 2
				(byte) 0xc4, 0x38	// CRC16
		};
		
		log.info("送出 Modbus 指令 - {}", toString(req));
		
		try {		
			mqtt.publish(topicTx, req, 0, false); // 向 NB-IoT 轉換器送出 Modbus 指令
			
		} catch (Exception e) {
			log.error("無法送出 MQTT 訊息", e);
		}
	}
	
	// 接收來自站點 2 的溫濕度計的 Modbus/RTU 回應結果
	void recvModbusReply(byte[] reply) throws IOException {
		log.info("得到 Modbus 回應 - {}", toString(reply));
		
		ByteArrayInputStream bais = new ByteArrayInputStream(reply);
		DataInputStream dis = new DataInputStream(bais);
		int slaveId = dis.read();
		if (slaveId != 0x02) {
			return;
		}
		
		int function = dis.read();
		if (function != 0x03) {
			return;
		}
		
		int size = dis.read();
		if (size != 0x04) { // 2 words
			return;
		}
		
		// 轉換溫濕度值
		float temperature = dis.readShort() / 100.0f;
		float humidity = dis.readShort() / 100.0f;
		
		log.info("溫度: {} °C, 濕度: {} %", temperature, humidity);			
	}
	
	// byte array to hex string
	String toString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02X ", b));
		}
		return sb.toString();
	}
	
	// 程式開始進入點
	public static void main(String[] args) throws Exception {
		Main m = new Main();
		m.run();
	}
}
