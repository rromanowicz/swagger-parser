package ex.rr.swaggerparser.annotation.processor.v3;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;

import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import ex.rr.swaggerparser.annotation.SwaggerClient;
import ex.rr.swaggerparser.annotation.processor.AbstractSwaggerProcessor;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;

/**
 * OpenApiV3Processor
 */
public class OpenApiV3Processor extends AbstractSwaggerProcessor {

  public OpenApiV3Processor(ProcessingEnvironment processingEnvironment) {
    super();
    super.init(processingEnvironment);
  }

  @Override
  public void process(Element element) {
    OpenAPI openApi;

    try {
      final String location = element.getAnnotation(SwaggerClient.class).location();
      ParseOptions parseOptions = new ParseOptions();
      parseOptions.setResolve(true); // implicit
      parseOptions.setResolveFully(true);
      openApi = new OpenAPIV3Parser().read(location, null, parseOptions);

      openApi.getComponents().getSchemas().forEach((k, v) -> {

        TypeSpec helloWorld = TypeSpec.classBuilder(k)
            .addModifiers(Modifier.PUBLIC)
            .addField(FieldSpec.builder(TypeName.get(String.class), "test", Modifier.PUBLIC).build())
            .build();

        saveClassDefinitionToFile(element, helloWorld);

        System.out.println(k);
      });
    } catch (Exception e) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Error fetching Swagger API Metadata.");
      throw e;
    }

  }

}
