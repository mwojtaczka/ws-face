package com.maciej.wojtaczka.wsface.back;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maciej.wojtaczka.wsface.dto.InboundParcel;
import com.maciej.wojtaczka.wsface.dto.OutboundParcel;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.SenderOptions;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Configuration
public class BackConfig {

	private static String applicationInstanceName;

	BackConfig(@Value("${spring.application.name}") String appName) {
		applicationInstanceName = appName + "-" + UUID.randomUUID();
	}

	public static String applicationInstanceName() {
		return applicationInstanceName;
	}

	@Bean
	ReactiveKafkaProducerTemplate<String, Object> reactiveKafkaProducerTemplate(
			KafkaProperties properties) {
		Map<String, Object> props = properties
				.buildProducerProperties();

		props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

		return new ReactiveKafkaProducerTemplate<>(SenderOptions.create(props));
	}

	@Bean
	ReceiverOptions<String, OutboundParcel<?>> kafkaReceiverOptions(KafkaProperties kafkaProperties, ObjectMapper objectMapper) {
		ReceiverOptions<String, OutboundParcel<?>> basicReceiverOptions = ReceiverOptions.create(kafkaProperties.buildConsumerProperties());
		return basicReceiverOptions.subscription(Collections.singletonList(applicationInstanceName))
								   .consumerProperty(ConsumerConfig.GROUP_ID_CONFIG, applicationInstanceName)
								   .consumerProperty(JsonDeserializer.USE_TYPE_INFO_HEADERS, false)
								   .withValueDeserializer(new KafkaGenericDeserializer<>(objectMapper, new TypeReference<OutboundParcel<?>>() {
								   }));
	}

	@Bean
	ReactiveKafkaConsumerTemplate<String, OutboundParcel<?>> reactiveKafkaConsumerTemplate(
			ReceiverOptions<String, OutboundParcel<?>> kafkaReceiverOptions) {
		return new ReactiveKafkaConsumerTemplate<>(kafkaReceiverOptions);
	}

	@Bean
	ListenersRegistry listenersRegistry(ReactiveStringRedisTemplate redisTemplate) {
		return new ListenersRegistry(applicationInstanceName, redisTemplate);
	}

	@Bean
	GlobalListener globalListener(ReactiveKafkaConsumerTemplate<String, OutboundParcel<?>> kafkaConsumer,
								  ListenersRegistry listenersRegistry) {
		GlobalListener globalListener = new GlobalListener(kafkaConsumer, listenersRegistry);
		globalListener.listen();
		return globalListener;
	}

	@Bean
	InboundDispatcher inboundDispatcher(ReactiveKafkaProducerTemplate<String, Object> reactiveKafkaProducerTemplate) {
		Map<InboundParcel.Type, String> topics = Map.of(InboundParcel.Type.MESSAGE, "message-received",
														 InboundParcel.Type.MESSAGE_STATUS, "message-status-changed");
		return new InboundDispatcher(reactiveKafkaProducerTemplate, topics);
	}

	static class KafkaGenericDeserializer<T> implements Deserializer<T> {

		private final ObjectMapper mapper;
		private final TypeReference<T> typeReference;

		public KafkaGenericDeserializer(ObjectMapper mapper, TypeReference<T> typeReference) {
			this.mapper = mapper;
			this.typeReference = typeReference;
		}

		@Override
		public T deserialize(final String topic, final byte[] data) {
			if (data == null) {
				return null;
			}

			try {
				return mapper.readValue(data, typeReference);
			} catch (final IOException ex) {
				throw new SerializationException("Can't deserialize data [" + Arrays.toString(data) + "] from topic [" + topic + "]", ex);
			}
		}

		@Override
		public void close() {}

		@Override
		public void configure(final Map<String, ?> settings, final boolean isKey) {}
	}
}
