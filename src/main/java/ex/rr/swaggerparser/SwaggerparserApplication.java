package ex.rr.swaggerparser;

import ex.rr.swaggerparser.annotation.SwaggerClient;
import ex.rr.swaggerparser.annotation.Type;
import ex.rr.swaggerparser.generated.*;

// @SwaggerClient(type = Type.OPENAPI3, location = "https://petstore3.swagger.io/api/v3/openapi.json")
@SwaggerClient(type = Type.SWAGGER, location = "http://petstore.swagger.io/v2/swagger.json")
public class SwaggerparserApplication {

  public static void main(String[] args) {
    Pet pet = Pet.builder().category(Category.builder().build()).build();
  }

}
