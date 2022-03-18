package com.maciej.wojtaczka.wsface.config;

import com.maciej.wojtaczka.wsface.dto.OutboundParcel;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class TestConfig {

	@Bean
	KafkaTemplate<String, OutboundParcel<?>> kafkaTemplate(
			KafkaProperties properties) {
		DefaultKafkaProducerFactory<String, OutboundParcel<?>> producerFactory =
				new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(), new StringSerializer(), new JsonSerializer<>());

		return new KafkaTemplate<>(producerFactory);
	}
}
