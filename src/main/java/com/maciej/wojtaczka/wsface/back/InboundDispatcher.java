package com.maciej.wojtaczka.wsface.back;

import com.maciej.wojtaczka.wsface.dto.InboundParcel;
import com.maciej.wojtaczka.wsface.dto.OutboundParcel;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderRecord;

import java.util.Map;

import static com.maciej.wojtaczka.wsface.dto.InboundParcel.Type.PING;
import static com.maciej.wojtaczka.wsface.dto.OutboundParcel.Status.FAILED;
import static com.maciej.wojtaczka.wsface.dto.OutboundParcel.Status.SENT;
import static com.maciej.wojtaczka.wsface.dto.OutboundParcel.Type.PONG;
import static com.maciej.wojtaczka.wsface.dto.OutboundParcel.Type.STATUS;

@Slf4j
public class InboundDispatcher {

	private final ReactiveKafkaProducerTemplate<String, Object> reactiveKafkaProducerTemplate;
	private final Map<InboundParcel.Type, String> parcelTypeToTopicMap;

	public InboundDispatcher(ReactiveKafkaProducerTemplate<String, Object> reactiveKafkaProducerTemplate,
							 Map<InboundParcel.Type, String> parcelTypeToTopicMap) {
		this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
		this.parcelTypeToTopicMap = parcelTypeToTopicMap;
	}

	public Flux<OutboundParcel<?>> dispatch(Flux<InboundParcel<?>> inbound) {

		return inbound.flatMap(this::dispatchParcel);
	}

	private Publisher<OutboundParcel<?>> dispatchParcel(InboundParcel<?> parcel) {
		if (PING.equals(parcel.getType())) {
			return Mono.just(OutboundParcel.builder().type(PONG).build());
		}
		if (parcel.getType() != null) {
			return forwardParcel(parcel);
		}
		return Mono.just(failure());
	}

	private Mono<OutboundParcel<?>> forwardParcel(InboundParcel<?> inboundParcel) {
		String topic = parcelTypeToTopicMap.get(inboundParcel.getType());
		if (topic == null) {
			return Mono.just(failure());
		}
		return reactiveKafkaProducerTemplate.send(SenderRecord.create(topic, null, null, null, inboundParcel.getPayload(), null))
											.map(result -> {
												if (result.exception() == null) {
													log.debug("Forwarded parcel {} to topic {}", inboundParcel, topic);
													return OutboundParcel.builder()
																		 .type(STATUS)
																		 .payload(SENT)
																		 .build();
												} else {
													log.warn("Forwarding parcel of type {} failed", inboundParcel.getType());
													return failure();
												}
											});
	}

	private OutboundParcel<Object> failure() {
		return OutboundParcel.builder()
							 .type(STATUS)
							 .payload(FAILED)
							 .build();
	}
}
