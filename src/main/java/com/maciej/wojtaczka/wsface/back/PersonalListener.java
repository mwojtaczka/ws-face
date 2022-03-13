package com.maciej.wojtaczka.wsface.back;

import lombok.Value;
import reactor.core.publisher.FluxSink;

import java.util.UUID;

@Value
public class PersonalListener<T> {

    FluxSink<T> emitter;
	UUID ownerID;

    public PersonalListener(UUID ownerId, FluxSink<T> emitter) {
		this.ownerID = ownerId;
        this.emitter = emitter;
    }

    public void emit(T msg) {
        emitter.next(msg);
    }
}
