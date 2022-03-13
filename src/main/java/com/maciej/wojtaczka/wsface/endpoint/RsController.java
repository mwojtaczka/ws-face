package com.maciej.wojtaczka.wsface.endpoint;

import com.maciej.wojtaczka.wsface.back.ListenersRegistry;
import com.maciej.wojtaczka.wsface.back.InboundDispatcher;
import com.maciej.wojtaczka.wsface.back.PersonalListener;
import com.maciej.wojtaczka.wsface.model.InboundParcel;
import com.maciej.wojtaczka.wsface.model.OutboundParcel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.annotation.ConnectMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.Objects;
import java.util.UUID;

@Controller
@Slf4j
class RsController {

	private final ListenersRegistry listenersRegistry;
	private final InboundDispatcher inboundDispatcher;

	public RsController(InboundDispatcher inboundDispatcher, ListenersRegistry listenersRegistry) {
		this.inboundDispatcher = inboundDispatcher;
		this.listenersRegistry = listenersRegistry;
	}

	@ConnectMapping
	void onConnect(RSocketRequester requester) {
		log.info("New connection");
		Objects.requireNonNull(requester.rsocket(), "rsocket connection should not be null")
			   .onClose()
			   .doOnError(error -> log.warn(requester.rsocketClient() + " Closed"))
			   .doFinally(consumer -> {
				   log.info(requester.rsocketClient() + " Disconnected");
			   })
			   .subscribe();
	}

	@MessageMapping("subscribe")
	Flux<OutboundParcel<?>> subscribe(Flux<InboundParcel> inboundParcels, @Header String principal) {
		Flux<OutboundParcel<?>> results = inboundDispatcher.dispatch(inboundParcels);

		Flux<OutboundParcel<?>> receiveMsgs =
				Flux.create((FluxSink<OutboundParcel<?>> emitter) ->
									listenersRegistry.register(new PersonalListener<>(UUID.fromString(principal), emitter))
													 .subscribe()
					)
					.doFinally(signalType -> listenersRegistry.unregister(UUID.fromString(principal)).subscribe());

		return Flux.concat(results, receiveMsgs);
	}

}
