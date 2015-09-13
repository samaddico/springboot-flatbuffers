package org.horiga.study.springboot.flatbuffers;

import com.google.flatbuffers.Table;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.Objects;

/**
 *
 */
@Slf4j
public class FlatBuffersHttpMessageConverter extends AbstractHttpMessageConverter<Table> {

	public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

	public static final MediaType X_FLATBUFFERS = new MediaType("application", "x-fb", DEFAULT_CHARSET);

	public static final String X_FLATBUFFERS_MESSAGE_ID = "X-FlatBuffers-MessageId";

	public static final int MAX_MESSAGE_BYTES = 1024 * 10;

	private int maxReadableBytes = MAX_MESSAGE_BYTES;

	protected final Map<String, FMessage> messageRepository;

	public FlatBuffersHttpMessageConverter(Map<String, FMessage> messageRepository) {
		super(X_FLATBUFFERS);
		this.messageRepository = messageRepository;
	}

	@Override
	protected boolean supports(Class<?> clazz) {
		return Table.class.isAssignableFrom(clazz);
	}

	@Override
	protected Table readInternal(Class<? extends Table> clazz, HttpInputMessage inputMessage)
			throws IOException, HttpMessageNotReadableException {

		final String messageId = inputMessage.getHeaders().getFirst(X_FLATBUFFERS_MESSAGE_ID);
		log.debug("Request.messageId: {}", messageId);
		if(Objects.isNull(messageId) ||
				!messageRepository.containsKey(messageId)) {
			throw new HttpMessageNotReadableException("Unknown message protocol identifier");
		}

		final InputStream stream = inputMessage.getBody();
		int available = stream.available();
		if( available > maxReadableBytes) {
			throw new HttpMessageNotReadableException("Message size too large. " + available + " bytes.");
		}

		byte[] readBytes = new byte[available];
		stream.read(readBytes);

		return messageRepository.get(messageId).build(ByteBuffer.wrap(readBytes));
	}

	@Override
	protected void writeInternal(Table message, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
		MediaType contentType = outputMessage.getHeaders().getContentType();
		log.debug("Response.contentType: {}", contentType);
		if (X_FLATBUFFERS.isCompatibleWith(contentType)) {
			FileCopyUtils.copy(message.getByteBuffer().array(), outputMessage.getBody());
			message.getByteBuffer();
		} else {
			log.info("This response is not FlatBuffers type.");
		}
	}

	private Charset getCharset(HttpHeaders headers) {
		if (headers == null || headers.getContentType() == null || headers.getContentType().getCharSet() == null) {
			return DEFAULT_CHARSET;
		}
		return headers.getContentType().getCharSet();
	}

	public void setMaxReadableBytes(int max) {
		this.maxReadableBytes = max;
	}

}
