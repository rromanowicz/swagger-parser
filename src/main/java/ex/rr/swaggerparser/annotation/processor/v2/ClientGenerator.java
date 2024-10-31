package ex.rr.swaggerparser.annotation.processor.v2;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.palantir.javapoet.AnnotationSpec;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import ex.rr.swaggerparser.apiclient.ApiClient;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.Swagger;
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

    apiClient.addMethod(uriBuilderMethod());

    swagger.getPaths().forEach((pathName, path) -> {
      path.getOperationMap().forEach((operationType, operation) -> {
        switch (operationType) {
          case DELETE:
            break;
          case GET:
            apiClient.addMethod(MethodSpec.methodBuilder(operation.getOperationId()).addModifiers(Modifier.PUBLIC)
                .returns(resolveReturnType(operation.getResponses().values()))
                .addParameters(prepareParameters(operation))
                // .addParameter(ParameterSpec.builder(Map.class, "headers").build())
                .addParameter(ParameterSpec
                    .builder(ParameterizedTypeName.get(Map.class, String.class, String.class), "headers").build())
                .addStatement(
                    """
                        return apiClient.get(
                                          buildUri(\"$L\"),
                                          headers,
                                          new $T<$L>(){}
                                        ) """,
                    pathName,
                    TypeReference.class,
                    resolveReturnType(operation.getResponses().values()))

                .build());
            break;
          case HEAD:
            break;
          case OPTIONS:
            break;
          case PATCH:
            break;
          case POST:
            break;
          case PUT:
            break;
          default:
            break;
        }
      });
    });

    return apiClient.build();
  }

  private static Iterable<ParameterSpec> prepareParameters(Operation operation) {
    return operation.getParameters().stream().map(it -> ParameterSpec.builder(String.class, it.getName()).build())
        .toList();
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

  private static MethodSpec uriBuilderMethod() {
    return MethodSpec.methodBuilder("buildUri")
        .returns(URI.class)
        .addParameter(ParameterSpec.builder(String.class, "path").build())
        .addStatement("return $T.newInstance().uri($T.create(baseUrl)).path(path).build().toUri()",
            UriComponentsBuilder.class, URI.class)
        .build();
  }
}
