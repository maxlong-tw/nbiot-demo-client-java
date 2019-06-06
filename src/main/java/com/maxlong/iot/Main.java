package com.maxlong.iot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
	String broker = "tcp://maxlong.ddns.net:1883";
	String username = "demo";
	String password = "maxlong";

	MqttClient mqtt;
	
	String imei = "866425030013488";
	String topicStatus = String.format("/maxlong/broker/imei/%s/status", imei);
	String topicTx = String.format("/maxlong/broker/imei/%s/tx", imei);
	String topicRx = String.format("/maxlong/broker/imei/%s/rx", imei);
	
	ExecutorService executor = Executors.newCachedThreadPool();
	
	public Main() {		
	}
	
	void run() throws Exception {
		MqttConnectOptions opt = new MqttConnectOptions();		
		opt.setUserName(username);
		opt.setPassword(password.toCharArray());
		opt.setCleanSession(true);

		log.info("Connect to MQTT broker - {}", broker);
		
		mqtt = new MqttClient(broker, MqttClient.generateClientId(), new MemoryPersistence());
		mqtt.connect(opt);
		
		mqtt.setCallback(new MqttCallback() {
			
			@Override
			public void messageArrived(String topic, MqttMessage message) throws Exception {
				executor.execute(() -> {
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

		log.info("Subscribe MQTT topic - {}", topicStatus);
		mqtt.subscribe(topicStatus); // wait for heartbeat
		
		log.info("Subscribe MQTT topic - {}", topicRx);
		mqtt.subscribe(topicRx); // wait for incoming data
	}
	
	void onMessage(String topic, byte[] payload) {
		if (topicStatus.equals(topic)) {
			sendModbusReq();
			
		} else if (topicRx.equals(topic)) {
			recvModbusReply(payload);
		}
	}
	
	void sendModbusReq() {
		byte[] req = new byte[] {
				0x02,				// slave id = 2
				0x03,				// read holding register
				0x00, 0x00,			// address = 0
				0x00, 0x02,			// quantity = 2
				(byte) 0xc4, 0x38	// CRC16
		};
		
		log.info("SEND - {}", toString(req));
		
		try {		
			mqtt.publish(topicTx, req, 0, false); // send command to device
			
		} catch (Exception e) {
			log.error("Failed to publish the MQTT message", e);
		}
	}
	
	void recvModbusReply(byte[] reply) {
		log.info("RECV - {}", toString(reply));
	}
	
	String toString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			sb.append(String.format("%02X ", b));
		}
		return sb.toString();
	}
	
	public static void main(String[] args) throws Exception {
		Main m = new Main();
		m.run();
	}
}
