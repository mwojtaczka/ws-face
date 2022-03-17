package com.maciej.wojtaczka.wsface.back;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maciej.wojtaczka.wsface.model.Message;
import com.maciej.wojtaczka.wsface.model.OutboundParcel;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.codec.Decoder;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.messaging.rsocket.DefaultMetadataExtractor;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.util.pattern.PathPatternRouteMatcher;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.SenderOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Configuration
public class BackConfig {

	private static String applicationInstanceName;

	BackConfig(@Value("${spring.application.name}") String appName) {
		applicationInstanceName = appName + "-" + UUID.randomUUID().toString();
	}

	public static String applicationInstanceName() {
		return applicationInstanceName;
	}

	@Autowired
	public void rsocketMessageHandler(RSocketMessageHandler handler) {
		handler.setRouteMatcher(new PathPatternRouteMatcher());

		List<Decoder<?>> decoders = new ArrayList<>(handler.getDecoders());

		DefaultMetadataExtractor extractor = new DefaultMetadataExtractor(decoders);
		extractor.metadataToExtract(MimeTypeUtils.TEXT_PLAIN, String.class, "principal");
		handler.setMetadataExtractor(extractor);
	}

	@Bean
	ReactiveKafkaProducerTemplate<String, Message> reactiveKafkaProducerTemplate(
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
