package com.example.commons.infra.clients.webclient;

import com.example.commons.app.dto.ErrorDto;
import com.example.commons.domain.exceptions.EntityNotFoundException;
import com.example.commons.domain.exceptions.ServiceInvocationException;
import com.example.commons.domain.exceptions.UnexpectedException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Configuration
@RequiredArgsConstructor
public class WebClientConfig {

	private final ObjectMapper objectMapper;

	@Bean
	@LoadBalanced
	public WebClient.Builder webClientBuilder() {
		return WebClient.builder().filter(errorHandlingFilter());
	}

	private ExchangeFilterFunction errorHandlingFilter() {
		return ExchangeFilterFunction.ofResponseProcessor(response -> {
			if (!response.statusCode().isError()) {
				return Mono.just(response);
			}
			return response.bodyToMono(String.class).flatMap(body -> {
				try {
					ErrorDto errorDto = objectMapper.readValue(body, ErrorDto.class);
					if (response.statusCode().value() == 404) {
						return Mono.error(new EntityNotFoundException(errorDto.message()));
					}
					return Mono.error(new ServiceInvocationException(response.statusCode().value(), errorDto));
				} catch (JsonProcessingException e) {
					return Mono.error(new UnexpectedException(body));
				}
			});
		});
	}

}
