package ex.rr.swaggerparser.annotation.processor.v2;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import org.apache.commons.lang3.reflect.TypeUtils;
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

import ex.rr.swaggerparser.apiclient.ApiClient;
import io.swagger.models.ArrayModel;
import io.swagger.models.HttpMethod;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.FormParameter;
import io.swagger.models.parameters.HeaderParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.parameters.PathParameter;
import io.swagger.models.parameters.QueryParameter;
import io.swagger.models.properties.RefProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ClientGenerator
 */
public class ClientGenerator {

  public static TypeSpec generateClientDefiinition(Element element, Swagger swagger) {
    var apiClient = TypeSpec.classBuilder(element.getSimpleName().toString() + "ApiClient");
    apiClient.addModifiers(Modifier.PUBLIC);
    apiClient.addAnnotation(Slf4j.class);
    apiClient.addAnnotation(Component.class);
    apiClient.addAnnotation(RequiredArgsConstructor.class);

    apiClient.addField(FieldSpec.builder(TypeName.get(String.class), "baseUrl", Modifier.PRIVATE)
        .addAnnotation(AnnotationSpec.builder(Value.class).addMember("value",
            "\"$L{$L-baseUrl:$L$L}\"", "$", element.getSimpleName().toString().toLowerCase(),
            swagger.getHost(), swagger.getBasePath()).build())
        .build());

    apiClient.addField(FieldSpec.builder(ApiClient.class, "apiClient", Modifier.PRIVATE, Modifier.FINAL).build());

    swagger.getPaths().forEach((pathName, path) -> {
      path.getOperationMap().forEach((operationType, operation) -> {
        switch (operationType) {
          case GET, POST, PUT -> apiClient.addMethod(genDef(pathName, operationType, operation));
          case PATCH -> {
          }
          case DELETE -> {
          }
          default -> throw new UnsupportedOperationException();
        }
      });
    });

    return apiClient.build();
  }

  private static MethodSpec genDef(String pathName, HttpMethod method, Operation operation) {

    var methodSpec = MethodSpec.methodBuilder(operation.getOperationId()).addModifiers(Modifier.PUBLIC)
        .returns(resolveReturnType(operation.getResponses().values()));

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

    operation.getParameters()
        .forEach(param -> {
          switch (param) {
            case PathParameter p -> {
              var field = ParameterSpec.builder(ClassName.get(String.class), param.getName()).build();
              methodSpec.addParameter(field);
              paramMaps.addStatement("pathParams.put(\"$1L\", $1L)", param.getName());

            }
            case BodyParameter p -> {
              var field = ParameterSpec.builder(getParamClass(p), param.getName()).build();
              methodSpec.addParameter(field);
              methodBody.add(", $L", field.name());
            }
            case QueryParameter p -> {
            }
            case FormParameter p -> {
            }
            case HeaderParameter p -> {
            }
            default -> {
            }
          }
          ;
        });

    if (hasFormParams.test(operation.getParameters())) {
      methodBody.add(", formData");
      methodSpec.addParameter(ParameterSpec
          .builder(ParameterizedTypeName.get(Map.class, String.class, Object.class), "formData").build());
    }

    methodBody.addStatement(", headers, new $T<$L>(){})", TypeReference.class,
        resolveReturnType(operation.getResponses().values()));

    methodSpec.addParameter(ParameterSpec
        .builder(ParameterizedTypeName.get(Map.class, String.class, String.class), "headers").build());
    paramMaps.add("\n");
    methodSpec.addCode(paramMaps.build());
    methodSpec.addCode(methodBody.build());
    return methodSpec.build();
  }

  private static TypeName getParamClass(BodyParameter p) {

    return switch (p.getSchema()) {
      case ArrayModel m -> {
        yield ParameterizedTypeName.get(ClassName.get("java.util", "List"), ClassName.get("", resolveModelRef(m)));
      }
      default -> ClassName.get("", resolveModelRef(p.getSchema()));
    };
  }

  private static ClassName resolveReturnType(Collection<Response> responses) {
    return responses.stream()
        .filter(r -> Objects.nonNull(r.getResponseSchema()) && Objects.nonNull(r.getResponseSchema().getReference()))
        .findFirst()
        .map(r -> ClassName.get("", resolveReferenceClassName(r.getResponseSchema().getReference())))
        .orElse(ClassName.get(HttpStatus.class));
  }

  private static String resolveReferenceClassName(String reference) {
    String[] arr = reference.split("/");
    return arr[arr.length - 1];
  }

  private static String resolveModelRef(Model model) {
    var ref = switch (model) {
      case ArrayModel m -> ((RefProperty) m.getItems()).get$ref();
      default -> model.getReference();
    };
    String[] arr = ref.split("/");
    return arr[arr.length - 1];
  }

  private static Predicate<Collection<Parameter>> hasPathParams = it -> it.stream()
      .anyMatch(p -> TypeUtils.isInstance(p, PathParameter.class));

  private static Predicate<Collection<Parameter>> hasQueryParams = it -> it.stream()
      .anyMatch(p -> TypeUtils.isInstance(p, QueryParameter.class));

  private static Predicate<Collection<Parameter>> hasFormParams = it -> it.stream()
      .anyMatch(p -> TypeUtils.isInstance(p, FormParameter.class));
}
