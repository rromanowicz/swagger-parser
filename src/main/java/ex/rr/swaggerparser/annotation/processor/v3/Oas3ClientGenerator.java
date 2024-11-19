package ex.rr.swaggerparser.annotation.processor.v3;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import java.awt.List;

import org.apache.commons.lang3.reflect.TypeUtils;
import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import ex.rr.swaggerparser.annotation.Format;
import ex.rr.swaggerparser.annotation.SwaggerClient;
import ex.rr.swaggerparser.annotation.processor.AbstractClientGenerator;
import ex.rr.swaggerparser.apiclient.ApiClient;
import io.swagger.models.HttpMethod;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import io.swagger.v3.oas.models.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

public class Oas3ClientGenerator extends AbstractClientGenerator {

  private Predicate<Collection<Parameter>> hasPathParams = it -> nonNull(it) && it.stream()
      .anyMatch(p -> TypeUtils.isInstance(p, PathParameter.class));

  private Predicate<Collection<Parameter>> hasQueryParams = it -> nonNull(it) && it.stream()
      .anyMatch(p -> TypeUtils.isInstance(p, QueryParameter.class));

  public TypeSpec generateClientDefiinition(Element element, OpenAPI openApi) {
    format = element.getAnnotation(SwaggerClient.class).format();
    var apiClient = TypeSpec.classBuilder(element.getSimpleName().toString() + "ApiClient");
    apiClient.addModifiers(Modifier.PUBLIC);
    apiClient.addAnnotation(Slf4j.class);
    apiClient.addAnnotation(Component.class);
    apiClient.addAnnotation(RequiredArgsConstructor.class);

    apiClient.addJavadoc(CodeBlock.of(openApi.getInfo().getTitle()));
    apiClient.addJavadoc(CodeBlock.of(openApi.getInfo().getDescription()));

    IntStream.range(0, openApi.getServers().size()).forEach(idx -> {
      apiClient.addField(FieldSpec.builder(TypeName.get(String.class), "baseUrl", Modifier.PRIVATE)
          .addAnnotation(AnnotationSpec.builder(Value.class).addMember("value",
              "\"{$L$L-baseUrl:$L}\"", "$", element.getSimpleName().toString().toLowerCase(),
              openApi.getServers().get(idx).getUrl()).build())
          .build());
    });

    apiClient.addField(FieldSpec.builder(ApiClient.class, "apiClient", Modifier.PRIVATE, Modifier.FINAL).build());

    openApi.getPaths().forEach((pathName, path) -> {
      if (nonNull(path.getGet())) {
        apiClient.addMethod(genDef(pathName, HttpMethod.GET, path.getGet()));
      }
    });

    return apiClient.build();
  }

  private MethodSpec genDef(String pathName, HttpMethod method, Operation operation) {

    var methodSpec = MethodSpec.methodBuilder(operation.getOperationId()).addModifiers(Modifier.PUBLIC)
        .returns(resolveReturnType(operation.getResponses()));

    var methodBody = CodeBlock.builder()
        .add("return apiClient.$L(", method.name().toLowerCase());
    methodBody.add("""
        $T.newInstance().uri($T.create(baseUrl)).path(\"$L\")
        """,
        UriComponentsBuilder.class, URI.class, pathName);

    var paramMaps = CodeBlock.builder();

    if (hasQueryParams.test(operation.getParameters())) {
      paramMaps
          .addStatement("$T queryParams = new $T<>()",
              ParameterizedTypeName.get(MultiValueMap.class, String.class, String.class),
              LinkedMultiValueMap.class);
      methodBody.add(".queryParams(queryParams)");
    }
    methodBody.add(".build()");

    if (hasPathParams.test(operation.getParameters())) {
      paramMaps
          .addStatement("$T pathParams = new $T<>()",
              ParameterizedTypeName.get(Map.class, String.class, String.class),
              HashMap.class);
      methodBody.add(".expand(pathParams)");
    }
    methodBody.add(".toUri()");

    if (nonNull(operation.getParameters())) {
      operation.getParameters()
          .forEach(param -> {
            switch (param) {
              case PathParameter p -> {
                var field = ParameterSpec.builder(ClassName.get(String.class), param.getName()).build();
                methodSpec.addParameter(field);
                paramMaps.addStatement("pathParams.put(\"$1L\", $1L)", param.getName());
              }
              case QueryParameter p -> {
                var field = ParameterSpec.builder(ClassName.get(String.class), param.getName()).build();
                methodSpec.addParameter(field);
                paramMaps.addStatement("queryParams.add(\"$1L\", $1L)", param.getName());
              }
              case HeaderParameter p -> {
              }
              default -> {
              }
            }
            ;
          });
    }

    methodBody.addStatement(", headers, new $T<$L>(){})", TypeReference.class,
        resolveReturnType(operation.getResponses()));

    methodSpec.addParameter(ParameterSpec
        .builder(ParameterizedTypeName.get(Map.class, String.class, String.class), "headers").build());
    paramMaps.add("\n");
    methodSpec.addCode(paramMaps.build());
    methodSpec.addCode(methodBody.build());
    return methodSpec.build();
  }

  private TypeName resolveReturnType(ApiResponses responses) {
    return responses.entrySet().stream()
        .filter(entry -> nonNull(entry.getValue().getContent()))
        .findFirst()
        .map(it -> it.getValue().getContent().get("application/json").getSchema())
        .map(it -> resolveSchemaType(it))
        .orElse(ClassName.get(HttpStatus.class));
  }

  private TypeName resolveSchemaType(Schema<?> schema) { // TODO add other Schemas
    return switch (schema) {
      case ArraySchema s -> ParameterizedTypeName.get(ClassName.get("java.util", "List"),
          ClassName.get("", resolveReferenceClassName(s.getItems().get$ref())));
      case ObjectSchema s -> ClassName.get("", resolveReferenceClassName(s.get$ref()));
      default -> {
        if (nonNull(schema.get$ref())) {
          yield ClassName.get("", resolveReferenceClassName(schema.get$ref()));
        } else {
          System.out.println(schema);
        }
        yield TypeName.get(String.class);

      }
    };
  }

  private String resolveReferenceClassName(String reference) {
    if (isNull(reference)) {
      return "null";
    }
    String[] arr = reference.split("/");
    return String.format("%s%s", (format == Format.RECORD) ? "Model." : "", arr[arr.length - 1]);
  }
}
