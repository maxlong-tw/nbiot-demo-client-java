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
 * 基於 MQTT 協定, 展示如何控制端與 NB-IoT 轉換器進行通訊，取得溫濕度計數值。
 * 
 * @author Jack
 *
 */

@Slf4j
public class Main {
	final ObjectMapper jackson = new ObjectMapper();
	
	String imei = "866425030013488"; // NB-IoT 轉換器 LTE 模組的 IMEI 編號，共 15 碼數字
	
	// 碩久的雲平台, 支持 MQTT 物聯網協定
	String broker = "tcp://maxlong.ddns.net:1883";
	String username = "demo";
	String password = "maxlong"; // 展示與測試用帳號密碼

	MqttClient mqtt;	
	
	// 接收 NB-IoT 轉換器的上線狀態 (heart beat 或 disconnect 封包)
	String topicStatus = String.format("/maxlong/broker/imei/%s/status", imei);
	
	// 將指令透過雲平台轉送到 NB-IoT 轉換器
	String topicTx = String.format("/maxlong/broker/imei/%s/tx", imei);
	
	// 透過雲平台接收來自 NB-IoT 轉換器的指令結果
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
		log.info("向雲平台建立 MQTT 連線 - {}", broker);		
		mqtt = new MqttClient(
				broker,
				MqttClient.generateClientId(),
				new MemoryPersistence());
		mqtt.connect(opt);
		
		// 設定接收來自雲平台的 MQTT 訊息
		mqtt.setCallback(new MqttCallback() {
			
			@Override
			public void messageArrived(String topic, MqttMessage message) throws Exception {
				executor.execute(() -> { // 收到來自雲平台的訊息
					onMessage(topic, message.getPayload());
				});
			}
			
			@Override
			public void deliveryComplete(IMqttDeliveryToken token) {
				// 暫不處理
			}
			
			@Override
			public void connectionLost(Throwable cause) {
				// 暫不處理
			}
		});

		// 訂閱接收 NB-IoT 轉換器的上線狀態訊息
		log.info("開始接收 NB-IoT 轉換器 MQTT 的上線狀態訊息 - {}", topicStatus);
		mqtt.subscribe(topicStatus); // wait for heart beat
		
		// 訂閱接收 NB-IoT 轉換器的指令結果
		log.info("開始接收 NB-IoT 轉換器 MQTT 的回應訊息 - {}", topicRx);
		mqtt.subscribe(topicRx); // wait for incoming data
	}
	
	// 處理來自雲平台的 MQTT 訊息
	void onMessage(String topic, byte[] payload) {
		try {		
			if (topicStatus.equals(topic)) { // 如果是 NB-IoT 轉換器的『狀態』訊息
				String json = new String(payload);
				Map<?, ?> msg = jackson.readValue(json, Map.class);			
				String type = (String) msg.get("type");			
				if ("heartbeat".equals(type)) { // 來自 NB-IoT 轉換器連線上來的狀態，內容為 {"type":"heartbeat","timestamp":"2019-06-06T22:33:09.449Z","rssi":-65,"from":"211.77.241.100:40893"}
					sendModbusReq(); // 收到 heart beat 訊息，開始送 Modbus 指令詢問溫濕度計狀態
					
				} else if ("disconnect".equals(type)) { // 來自 NB-IoT 的離線狀態，內容為 {"type":"disconnect","timestamp":"2019-06-06T22:35:42.112Z","from":"211.77.241.100:42432"}
					log.warn("{} 已經離線", imei);
					
				} else {
					log.error("未知的 MQTT 訊息 - {}", json);				
				}
				
			} else if (topicRx.equals(topic)) { // 來自 NB-IoT 的回應訊息
				recvModbusReply(payload);
			}
			
		} catch (Exception e) {
			log.error("無法正常處理 MQTT 訊息", e);
		}
	}
	
	// 向 NB-IoT 轉換器銜接 device ID = 1 的溫濕度計送出 Modbus 指令
	void sendModbusReq() {
		byte[] req = new byte[] {
				0x01,				// device id = 1
				0x03,				// holding register
				0x00, 0x00,			// address = 0
				0x00, 0x02,			// count = 2
				(byte) 0xc4, 0x0B	// CRC
		};
		
		log.info("送出 Modbus 指令 - {}", toString(req));
		
		try {		
			mqtt.publish(topicTx, req, 0, false); // 向 NB-IoT 轉換器送出 Modbus 指令
			
		} catch (Exception e) {
			log.error("無法送出 MQTT 訊息", e);
		}
	}
	
	// 接收來自 NB-IoT 轉換器溫濕度計的結果
	void recvModbusReply(byte[] reply) throws IOException {
		log.info("得到 Modbus 回應 - {}", toString(reply));
		
		ByteArrayInputStream bais = new ByteArrayInputStream(reply);
		DataInputStream dis = new DataInputStream(bais);
		int slaveId = dis.read();
		if (slaveId != 0x01) { // device id = 1
			return;
		}
		
		int function = dis.read();
		if (function != 0x03) { // read holding registers
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
