package com.maciej.wojtaczka.wsface.back;

import com.maciej.wojtaczka.wsface.dto.OutboundParcel;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;

@Slf4j
class GlobalListener {

    private final ReactiveKafkaConsumerTemplate<String, OutboundParcel<?>> kafkaConsumer;
    private final ListenersRegistry personalListeners;

    GlobalListener(ReactiveKafkaConsumerTemplate<String, OutboundParcel<?>> kafkaConsumer, ListenersRegistry personalListeners) {
        this.kafkaConsumer = kafkaConsumer;
        this.personalListeners = personalListeners;
    }

    void listen() {
        kafkaConsumer
                .receive()
                .map(ConsumerRecord::value)
				.onErrorContinue((throwable, o) -> log.error("Error during listening: " + throwable.getMessage()))
                .subscribe(this::push);
    }

    void push(OutboundParcel<?> parcel) {
        personalListeners.get(parcel.getRecipients())
				.forEach(emitter -> emitter.emit(parcel));
    }

}
