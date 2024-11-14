package ex.rr.swaggerparser.annotation.processor.v3;

import static java.util.Objects.nonNull;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.tools.Diagnostic;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import ex.rr.swaggerparser.annotation.SwaggerClient;
import ex.rr.swaggerparser.annotation.processor.AbstractSwaggerProcessor;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.DateSchema;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.media.UUIDSchema;
import io.swagger.v3.parser.core.models.ParseOptions;
import lombok.Builder;
import lombok.Data;

@SuppressWarnings({ "unchecked", "rawtypes" })
public class Oas3Processor extends AbstractSwaggerProcessor {

  private String parentName;

  public Oas3Processor(ProcessingEnvironment processingEnvironment) {
    super();
    super.init(processingEnvironment);
  }

  @Override
  public void process(Element element) {
    OpenAPI openApi;

    try {
      final String location = element.getAnnotation(SwaggerClient.class).location();
      format = element.getAnnotation(SwaggerClient.class).format();
      ParseOptions parseOptions = new ParseOptions();

      openApi = new OpenAPIParser().readLocation(location, null, parseOptions).getOpenAPI();

      openApi.getComponents().getSchemas().entrySet().stream()
          .forEach(entry -> generateModelDefinitions(entry.getKey(), entry.getValue()));

      super.persistDefinitions(element);
    } catch (Exception e) {
      messager.printMessage(Diagnostic.Kind.ERROR, "Error fetching Swagger API Metadata.");
      throw e;
    }
  }

  private TypeSpec generateModelDefinitions(String name, Schema schema) {
    this.parentName = name;

    var def = switch (format) {
      case POJO -> generatePOJODefinition(name, schema);
      case RECORD -> generateRecordDefinition(name, schema);
    };

    definitions.put(name, def);
    return def;
  }

  private TypeSpec generatePOJODefinition(String name, Schema<?> schema) {
    var pojo = TypeSpec.classBuilder(name)
        .addModifiers(Modifier.PUBLIC)
        .addSuperinterface(ClassName.get(Serializable.class))
        .addAnnotation(Data.class)
        .addAnnotation(Builder.class);

    if (nonNull(schema.getProperties()) && !schema.getProperties().isEmpty()) {
      Map<String, Schema> properties = schema.getProperties();
      properties.forEach((k, v) -> {
        var fieldName = (k.startsWith("@")) ? "at" + StringUtils.capitalize(k.replaceAll("@", "")) : k;
        var field = FieldSpec.builder(resolveType(k, v), fieldName.replaceAll("[^a-zA-Z0-9]", ""), Modifier.PRIVATE);
        var fieldAnnotation = getPropertyAnnotation(k, nonNull(v.getRequired()) && v.getRequired().contains(k));
        if (!fieldAnnotation.members().isEmpty()) {
          field.addAnnotation(fieldAnnotation);
        }
        pojo.addField(field.build());
      });
    }

    return pojo.build();
  }

  private TypeSpec generateRecordDefinition(String name, Schema schema) {
    var recordConstructor = MethodSpec.methodBuilder(name);
    var record = TypeSpec.recordBuilder(name)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        .addAnnotation(Builder.class);

    if (nonNull(schema.getProperties()) && !schema.getProperties().isEmpty()) {
      Map<String, Schema> properties = schema.getProperties();
      properties.forEach((k, v) -> {
        var fieldName = (k.startsWith("@")) ? "at" + StringUtils.capitalize(k.replaceAll("@", "")) : k;
        var field = ParameterSpec.builder(resolveType(k, v), fieldName.replaceAll("[^a-zA-Z0-9]", ""));
        var fieldAnnotation = getPropertyAnnotation(k, nonNull(v.getRequired()) && v.getRequired().contains(v));
        if (!fieldAnnotation.members().isEmpty()) {
          field.addAnnotation(fieldAnnotation);
        }
        recordConstructor.addParameter(field.build());
      });
      record.recordConstructor(recordConstructor.build());
    }
    return record.build();
  }

  private AnnotationSpec getPropertyAnnotation(String name, boolean required) {
    var cleanName = name.replaceAll("[^a-zA-Z0-9]", "");
    var fieldAnnotation = AnnotationSpec.builder(JsonProperty.class);
    if (!name.equals(cleanName)) {
      fieldAnnotation.addMember("value", "\"$L\"", name);
    }
    if (required) {
      fieldAnnotation.addMember("required", "$L", required);
    }
    return fieldAnnotation.build();
  }

  private TypeName resolveType(String name, Schema<?> schema) {
    if (nonNull(schema.get$ref())) {
      return ClassName.get("", resolveReferenceClassName(schema.get$ref()));
    }
    if (nonNull(schema.getEnum())) {
      return generateEnumDefinition(name, schema.getEnum());
    }

    return switch (schema) {
      case ArraySchema p -> {
        if (nonNull(p.getUniqueItems()) && p.getUniqueItems()) {
          yield ParameterizedTypeName.get(ClassName.get("java.util", "Set"),
              resolveType(name, p.getItems()));
        } else {
          yield ParameterizedTypeName.get(ClassName.get("java.util", "List"),
              resolveType(name, p.getItems()));
        }
      }
      case BooleanSchema p -> TypeName.get(Boolean.class);
      case StringSchema p -> TypeName.get(String.class);
      case IntegerSchema p -> switch (Optional.ofNullable(p.getFormat()).orElse("")) {
        case "int64" -> TypeName.get(Long.class);
        case "int32" -> TypeName.get(Integer.class);
        default -> TypeName.get(Integer.class);
      };
      case NumberSchema p -> switch (p.getType()) {
        case "float" -> TypeName.get(Float.class);
        case "double" -> TypeName.get(Double.class);
        default -> TypeName.get(Float.class);
      };
      case DateSchema p -> TypeName.get(LocalDate.class);
      case DateTimeSchema p -> TypeName.get(LocalDateTime.class);
      case UUIDSchema p -> TypeName.get(UUID.class);
      case MapSchema p -> TypeName.get(Map.class);
      case ObjectSchema p -> ClassName.get("", generateModelDefinitions(name, p).name());
      default -> TypeName.get(String.class);

    };
  }

  private String resolveReferenceClassName(String ref) {
    String[] arr = ref.split("/");
    return arr[arr.length - 1];
  }

  private <T> ClassName generateEnumDefinition(String name, Collection<T> values) {
    var fieldName = (name.startsWith("@")) ? "at" + StringUtils.capitalize(name.replaceAll("@", "")) : name;
    String enumName = String.format("%s%s", StringUtils.capitalize(parentName), StringUtils.capitalize(fieldName));
    TypeSpec.Builder enumDef = TypeSpec.enumBuilder(enumName)
        .addModifiers(Modifier.PUBLIC, Modifier.STATIC);
    values.forEach(v -> {
      String enumVal = String.valueOf(v).replaceAll("[^a-zA-Z0-9]", "");
      enumDef.addEnumConstant(enumVal);
    });
    enumDefinitions.put(enumName, enumDef.build());
    return ClassName.get("", enumName);
  }
}
