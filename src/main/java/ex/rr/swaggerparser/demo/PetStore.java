package ex.rr.swaggerparser.demo;

import org.springframework.stereotype.Service;

/**
 * PetStore
 */
import ex.rr.swaggerparser.annotation.SwaggerClient;
import ex.rr.swaggerparser.annotation.Type;
import lombok.RequiredArgsConstructor;

// @SwaggerClient(type = Type.OPENAPI3, location = "https://petstore3.swagger.io/api/v3/openapi.json")
@SwaggerClient(type = Type.SWAGGER, location = "http://petstore.swagger.io/v2/swagger.json")
@RequiredArgsConstructor
@Service
public class PetStore {

  // private final PetStoreApiClient apiClient;

  // Pet getPet() {
  // return apiClient.getPetById("1", Map.of());
  // }

}
