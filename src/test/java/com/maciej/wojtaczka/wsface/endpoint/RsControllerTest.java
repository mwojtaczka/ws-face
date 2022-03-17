package com.maciej.wojtaczka.wsface.endpoint;

import com.maciej.wojtaczka.wsface.back.BackConfig;
import com.maciej.wojtaczka.wsface.model.InboundParcel;
import com.maciej.wojtaczka.wsface.model.Message;
import com.maciej.wojtaczka.wsface.model.OutboundParcel;
import com.maciej.wojtaczka.wsface.utils.KafkaTestListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@EmbeddedKafka(partitions = 1, brokerProperties = { "listeners=PLAINTEXT://localhost:9092", "port=9092" })
@DirtiesContext
class RsControllerTest {

	@Autowired
	private RSocketRequester.Builder builder;

	@Autowired
	private KafkaTestListener kafkaTestListener;

	@Autowired
	private RedisOperations<String, String> redisOperations;

	@Autowired
	private KafkaTemplate<String, OutboundParcel<?>> kafkaTemplate;

	@Test
	void shouldForwardMessage() {
		UUID authorId = UUID.randomUUID();
		InboundParcel firstToSend = getInboundMessageParcel(authorId);
		InboundParcel secondToSend = getInboundMessageParcel(authorId);
		InboundParcel thirdToSend = getInboundMessageParcel(authorId);

		Flux<InboundParcel> toBeSent = Flux.just(firstToSend, secondToSend, thirdToSend);

		kafkaTestListener.listenToTopic("message-received", 3);

		var dataTypeRef = new ParameterizedTypeReference<OutboundParcel<OutboundParcel.MessageStatus>>() {
		};
		Flux<OutboundParcel<OutboundParcel.MessageStatus>> channel = getRSocketRequester().route("subscribe")
																						  .metadata(authorId.toString(), MimeTypeUtils.TEXT_PLAIN)
																						  .data(toBeSent)
																						  .retrieveFlux(dataTypeRef)
																						  .take(3);

		//verify each message successfully sent
		StepVerifier.create(channel)
					.expectNext(sentStatus(), sentStatus(), sentStatus())
					.verifyComplete();

		//verify messages forwarded to kafka
		List<Message> fromTopic = kafkaTestListener.receiveContentFromTopic("message-received", Message.class);
		assertThat(fromTopic).hasSize(3);
		assertThat(fromTopic.get(0)).isEqualTo(firstToSend.getMessagePayload());
		assertThat(fromTopic.get(1)).isEqualTo(secondToSend.getMessagePayload());
		assertThat(fromTopic.get(2)).isEqualTo(thirdToSend.getMessagePayload());
	}

	@Test
	void shouldRegisterUserListenerInRedis() throws InterruptedException {
		UUID receiverId = UUID.randomUUID();
		CountDownLatch latch = new CountDownLatch(1);

		getRSocketRequester().route("subscribe")
							 .metadata(receiverId.toString(), MimeTypeUtils.TEXT_PLAIN)
							 .data(Flux.just(getPingParcel(receiverId)))
							 .retrieveFlux(OutboundParcel.class)
							 .subscribe(x -> latch.countDown());

		//verify redis registration
		latch.await();
		String registeredInstance = redisOperations.opsForValue().get(receiverId.toString());
		assertThat(registeredInstance).isEqualTo(BackConfig.applicationInstanceName());
	}

	@Test
	void shouldUnregisterUserListenerInRedis_whenChannelCompleted() throws InterruptedException {
		UUID receiverId = UUID.randomUUID();
		var dataTypeRef = new ParameterizedTypeReference<OutboundParcel<Void>>() {
		};
		Flux<OutboundParcel<Void>> channel = getRSocketRequester().route("subscribe")
															.metadata(receiverId.toString(), MimeTypeUtils.TEXT_PLAIN)
															.data(Flux.just(getPingParcel(receiverId)))
															.retrieveFlux(dataTypeRef)
															.take(1);

		//verify pong response and completed channel
		StepVerifier.create(channel)
					.expectNext(pong())
					.verifyComplete();

		//verify redis empty entry
		Thread.sleep(100);
		String registeredInstance = redisOperations.opsForValue().get(receiverId.toString());
		assertThat(registeredInstance).isNull();
	}

	@Test
	void shouldPushMessages() {
		//given
		UUID receiverId = UUID.randomUUID();
		OutboundParcel<Message> first = getOutboundMessageParcel(receiverId);
		OutboundParcel<Message> second = getOutboundMessageParcel(receiverId);
		OutboundParcel<Message> third = getOutboundMessageParcel(receiverId);

		String topic = BackConfig.applicationInstanceName();
		var dataTypeRef = new ParameterizedTypeReference<OutboundParcel<Message>>() {
		};

		//when
		Flux<OutboundParcel<Message>> subscribe = getRSocketRequester().route("subscribe")
															  .metadata(receiverId.toString(), MimeTypeUtils.TEXT_PLAIN)
															  .data(Flux.just(getPingParcel(UUID.randomUUID())))
															  .retrieveFlux(dataTypeRef)
															  .take(4);
		CompletableFuture.runAsync(() -> {
			kafkaTemplate.send(topic, first);
			kafkaTemplate.send(topic, second);
			kafkaTemplate.send(topic, third);
		}, CompletableFuture.delayedExecutor(1000, TimeUnit.MILLISECONDS));

		//then
		StepVerifier.create(subscribe).thenAwait()
					.expectNextCount(1) //pong
					.expectNext(first, second, third)
					.verifyComplete();
	}

	private InboundParcel getInboundMessageParcel(UUID authorId) {
		Message first = Message.builder()
							   .authorId(authorId)
							   .content("Message 1")
							   .conversationId(UUID.randomUUID())
							   .build();
		return InboundParcel.builder()
							.type(InboundParcel.Type.MESSAGE)
							.receiverId(authorId)
							.messagePayload(first)
							.build();
	}

	private OutboundParcel<Message> getOutboundMessageParcel(UUID receiver) {
		Message first = Message.builder()
							   .authorId(UUID.randomUUID())
							   .content("Message 1")
							   .conversationId(UUID.randomUUID())
							   .build();
		return OutboundParcel.<Message>builder()
							 .type(OutboundParcel.Type.MESSAGE)
							 .recipients(Set.of(receiver))
							 .payload(first)
							 .build();
	}

	private InboundParcel getPingParcel(UUID authorId) {
		return InboundParcel.builder()
							.type(InboundParcel.Type.PING)
							.receiverId(authorId)
							.build();
	}

	private OutboundParcel<OutboundParcel.MessageStatus> sentStatus() {
		return OutboundParcel.<OutboundParcel.MessageStatus>builder()
							 .type(OutboundParcel.Type.MESSAGE_STATUS)
							 .payload(OutboundParcel.MessageStatus.SENT)
							 .build();
	}

	private OutboundParcel<Void> pong() {
		return OutboundParcel.<Void>builder()
							 .type(OutboundParcel.Type.PONG)
							 .build();
	}

	public RSocketRequester getRSocketRequester() {
		return builder
				.websocket(URI.create("ws://localhost:8080/rs"));
	}

}
