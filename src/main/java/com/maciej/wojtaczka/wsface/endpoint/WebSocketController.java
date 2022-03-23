package com.maciej.wojtaczka.wsface.endpoint;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.maciej.wojtaczka.wsface.back.InboundDispatcher;
import com.maciej.wojtaczka.wsface.back.ListenersRegistry;
import com.maciej.wojtaczka.wsface.back.PersonalListener;
import com.maciej.wojtaczka.wsface.dto.InboundParcel;
import com.maciej.wojtaczka.wsface.dto.OutboundParcel;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;

public class WebSocketController implements WebSocketHandler {

	private final ListenersRegistry listenersRegistry;
	private final InboundDispatcher inboundDispatcher;
	private final ObjectMapper objectMapper;

	public WebSocketController(ListenersRegistry listenersRegistry,
							   InboundDispatcher inboundDispatcher,
							   ObjectMapper objectMapper) {
		this.listenersRegistry = listenersRegistry;
		this.inboundDispatcher = inboundDispatcher;
		this.objectMapper = objectMapper;
	}

	@Override
	public Mono<Void> handle(WebSocketSession session) {
		String principal = Objects.requireNonNull(session.getHandshakeInfo().getHeaders().get("principal")).stream().findFirst()
								  .map(Object::toString)
								  .orElseThrow();
		Flux<InboundParcel<?>> inboundParcels = session.receive()
													   .map(WebSocketMessage::getPayloadAsText)
													   .map(this::toInboundParcel);

		Flux<OutboundParcel<?>> results = inboundDispatcher.dispatch(inboundParcels);

		Flux<OutboundParcel<?>> outboundParcels =
				Flux.create((FluxSink<OutboundParcel<?>> emitter) ->
									listenersRegistry.register(new PersonalListener<>(UUID.fromString(principal), emitter))
													 .subscribe())
					.doFinally(signalType -> listenersRegistry.unregister(UUID.fromString(principal)).subscribe());


		Flux<WebSocketMessage> toBeSent = Flux.merge(results, outboundParcels)
											  .map(outboundParcel -> session.textMessage(toJson(outboundParcel)));

		return session.send(toBeSent);
	}

	private String toJson(OutboundParcel<?> outboundParcel) {
		try {
			return objectMapper.writeValueAsString(outboundParcel);
		} catch (JsonProcessingException e) {
			throw new RuntimeException();
		}
	}

	private InboundParcel<?> toInboundParcel(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<InboundParcel<?>>() {
			});
		} catch (JsonProcessingException e) {
			throw new RuntimeException();
		}
	}

}
