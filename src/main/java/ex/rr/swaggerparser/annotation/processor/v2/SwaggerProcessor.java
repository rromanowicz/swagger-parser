package ex.rr.swaggerparser.annotation.processor.v2;

import static java.util.Objects.nonNull;

import java.util.Map;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import ex.rr.swaggerparser.annotation.SwaggerClient;
import ex.rr.swaggerparser.annotation.processor.AbstractSwaggerProcessor;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.parser.SwaggerParser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

/**
 * SwaggerProcessor
 */
public class SwaggerProcessor extends AbstractSwaggerProcessor {

  public SwaggerProcessor(ProcessingEnvironment processingEnvironment) {
    super();
    super.init(processingEnvironment);
  }

  @Override
  public void process(Element element) {
    final String location = element.getAnnotation(SwaggerClient.class).location();
    Swagger swagger = new SwaggerParser().read(location);
    Map<String, Model> definitions = swagger.getDefinitions();

    definitions.forEach((k, v) -> {
      TypeSpec def = generateDefinition(k, v);
      saveClassDefinitionToFile(element, def);
    });
  }

  private TypeSpec generateDefinition(String name, Model model) {
    var def = TypeSpec.classBuilder(name)
        .addModifiers(Modifier.PUBLIC)
        .addAnnotation(NoArgsConstructor.class)
        .addAnnotation(AllArgsConstructor.class)
        .addAnnotation(Builder.class);
    model.getProperties().forEach((k, v) -> def.addField(resolveType(v), k, Modifier.PRIVATE));

    return def.build();
  }

  private TypeName resolveType(Property property) {

    return switch (property.getType()) {
      case "string" -> TypeName.get(String.class);
      case "boolean" -> TypeName.BOOLEAN;
      case "number" ->
        switch (property.getFormat()) {
          case "float" -> TypeName.FLOAT;
          case "double" -> TypeName.DOUBLE;
          default -> TypeName.FLOAT;
        };
      case "integer" ->
        switch (property.getFormat()) {
          case "int32" -> TypeName.get(Integer.class);
          case "int64" -> TypeName.get(Long.class);
          default -> TypeName.get(Integer.class);
        };
      case "array" -> {
        ArrayProperty arrayProperty = (ArrayProperty) property;
        if (nonNull(arrayProperty.getUniqueItems()) && arrayProperty.getUniqueItems()) {
          yield ParameterizedTypeName.get(ClassName.get("java.util", "Set"), resolveType(arrayProperty.getItems()));
        } else {
          yield ParameterizedTypeName.get(ClassName.get("java.util", "List"), resolveType(arrayProperty.getItems()));
        }
      }
      case "ref" -> ClassName.get("", resolveClassName((RefProperty) property));

      default -> {
        RefProperty prop = (RefProperty) property;
        System.out.println(
            String.format("%s | %s | %s | %s", prop.getName(), prop.getFormat(), property.getType(), prop.get$ref()));

        yield TypeName.get(String.class);
      }
    };
  }

  private String resolveClassName(RefProperty property) {
    String[] arr = property.get$ref().split("/");
    return arr[arr.length - 1];
  }

}
