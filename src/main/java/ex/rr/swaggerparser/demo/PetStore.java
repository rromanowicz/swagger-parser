package ex.rr.swaggerparser.demo;

import org.springframework.stereotype.Service;

import ex.rr.swaggerparser.annotation.Format;
/**
 * PetStore
 */
import ex.rr.swaggerparser.annotation.SwaggerClient;
import ex.rr.swaggerparser.annotation.Type;
// import ex.rr.swaggerparser.demo.generated.petstore.PetStoreApiClient;
import lombok.RequiredArgsConstructor;

// @SwaggerClient(type = Type.OAS3, format = Format.RECORD, location = "https://petstore3.swagger.io/api/v3/openapi.json")
@SwaggerClient(type = Type.OAS2, format = Format.RECORD, location = "http://petstore.swagger.io/v2/swagger.json")
@RequiredArgsConstructor
@Service
public class PetStore {

  // private final PetStoreApiClient apiClient;
}
