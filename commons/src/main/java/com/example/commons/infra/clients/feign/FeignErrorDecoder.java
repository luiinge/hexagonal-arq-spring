package com.example.commons.infra.clients.feign;

import com.example.commons.app.dto.ErrorDto;
import com.example.commons.domain.exceptions.EntityNotFoundException;
import com.example.commons.domain.exceptions.ServiceInvocationException;
import com.example.commons.domain.exceptions.UnexpectedException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.io.InputStream;

@Component
@RequiredArgsConstructor
public class FeignErrorDecoder implements ErrorDecoder {

	private final ObjectMapper objectMapper;

	@Override
	public Exception decode(String methodKey, Response response) {
		try (InputStream body = response.body().asInputStream()) {
			ErrorDto errorDto = objectMapper.readValue(body, ErrorDto.class);
			return new ServiceInvocationException(response.status(), errorDto);
		} catch (IOException e) {
			return new Default().decode(methodKey, response);
		}

	}
}
