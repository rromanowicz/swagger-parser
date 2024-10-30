package ex.rr.swaggerparser.apiclient;

import java.net.URI;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * ApiClient
 */
public interface ApiClient {

  <T> T get(URI uri, Map<String, String> headers, TypeReference<T> type);

  <T, B> T post(URI uri, B body, Map<String, String> headers, TypeReference<T> type);

}
