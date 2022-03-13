package com.maciej.wojtaczka.wsface.back;

import com.maciej.wojtaczka.wsface.model.InboundParcel;
import com.maciej.wojtaczka.wsface.model.Message;
import com.maciej.wojtaczka.wsface.model.OutboundParcel;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.kafka.core.reactive.ReactiveKafkaProducerTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.SenderRecord;

import static com.maciej.wojtaczka.wsface.model.OutboundParcel.MessageStatus.FAILED;
import static com.maciej.wojtaczka.wsface.model.OutboundParcel.MessageStatus.SENT;
import static com.maciej.wojtaczka.wsface.model.OutboundParcel.Type.MESSAGE_STATUS;
import static com.maciej.wojtaczka.wsface.model.OutboundParcel.Type.PONG;

@Slf4j
@Component
public class InboundDispatcher {

	private final ReactiveKafkaProducerTemplate<String, Message> reactiveKafkaProducerTemplate;

	public InboundDispatcher(ReactiveKafkaProducerTemplate<String, Message> reactiveKafkaProducerTemplate) {
		this.reactiveKafkaProducerTemplate = reactiveKafkaProducerTemplate;
	}

	public Flux<OutboundParcel<?>> dispatch(Flux<InboundParcel> inbound) {

		return inbound.flatMap(this::dispatchParcel);
	}

	private Publisher<OutboundParcel<?>> dispatchParcel(InboundParcel parcel) {
		switch (parcel.getType()) {
			case MESSAGE:
				return forwardMessage(parcel);
			case PING:
				return Mono.just(OutboundParcel.builder().type(PONG).build());
			default:
				throw new RuntimeException("Not supported");
		}
	}

	private Mono<OutboundParcel<?>> forwardMessage(InboundParcel messageParcel) {
		Message message = messageParcel.getMessagePayload();
		return reactiveKafkaProducerTemplate.send(SenderRecord.create("message-received", null, null, null, message, message))
											.map(result -> {
												if (result.exception() == null) {
													return OutboundParcel.builder()
																		 .type(MESSAGE_STATUS)
																		 .payload(SENT)
																		 .build();
												} else {
													Message failedMessage = result.correlationMetadata();
													log.warn("Message failed from: {}, for conversation: {}",
															 failedMessage.getAuthorId(), failedMessage.getConversationId());
													return OutboundParcel.builder()
																		 .type(MESSAGE_STATUS)
																		 .payload(FAILED)
																		 .build();
												}
											});
	}
}
