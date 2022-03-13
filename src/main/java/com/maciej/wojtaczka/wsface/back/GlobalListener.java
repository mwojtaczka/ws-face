package com.maciej.wojtaczka.wsface.back;

import com.maciej.wojtaczka.wsface.model.OutboundParcel;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.reactive.ReactiveKafkaConsumerTemplate;

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
                .subscribe(this::push);
    }

    void push(OutboundParcel<?> parcel) {
        personalListeners.get(parcel.getReceiverId())
				.forEach(emitter -> emitter.emit(parcel));
    }

}
