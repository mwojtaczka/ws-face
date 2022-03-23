package com.maciej.wojtaczka.wsface.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

@Component
public class TestWsClient {

	private final ObjectMapper objectMapper;

	public TestWsClient(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public <T> Flux<T> send(Flux<?> inbound, String principal, TypeReference<T> responseType, int expectedCount) {

		BlockingQueue<T> queue = new LinkedBlockingQueue<>();
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add("principal", principal);
		WebSocketClient client = new ReactorNettyWebSocketClient();
		return client.execute(
							 URI.create("ws://localhost:8080/websocket"),
							 httpHeaders,
							 session -> handle(session, inbound, responseType, expectedCount, queue, x -> {}))
					 .thenMany(Flux.fromIterable(queue))
					 .log("Response2: ");
	}

	/**
	 * Keeps Flux alive and never completes
	 */
	public <T> Flux<T> send(Flux<?> inbound, String principal, TypeReference<T> responseType, Consumer<T> doOnNextResponse) {

		BlockingQueue<T> queue = new LinkedBlockingQueue<>();
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.add("principal", principal);
		WebSocketClient client = new ReactorNettyWebSocketClient();
		return client.execute(
							 URI.create("ws://localhost:8080/websocket"),
							 httpHeaders,
							 session -> handle(session, inbound, responseType, Integer.MAX_VALUE, queue, doOnNextResponse))
					 .thenMany(Flux.fromIterable(queue));
	}

	private <T> Mono<Void> handle(WebSocketSession session,
								  Flux<?> inbound,
								  TypeReference<T> responseType,
								  int expectedCount,
								  BlockingQueue<T> queue,
								  Consumer<T> doOnNextResponse) {

		Mono<Void> receive = session.receive()
									.map(WebSocketMessage::getPayloadAsText)
									.map(string -> parse(string, responseType))
									.take(expectedCount)
									.doOnNext(queue::add)
									.doOnNext(doOnNextResponse)
									.log("Response1")
									.then();

		Mono<Void> send = session.send(inbound.map(parcel -> toWsMessage(session, parcel)));

		return send.then(receive);
	}

	private <T> T parse(String string, TypeReference<T> responseType) {
		try {
			return objectMapper.readValue(string, responseType);
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	private WebSocketMessage toWsMessage(WebSocketSession session, Object i) {
		try {
			return session.textMessage(objectMapper.writeValueAsString(i));
		} catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}
}
