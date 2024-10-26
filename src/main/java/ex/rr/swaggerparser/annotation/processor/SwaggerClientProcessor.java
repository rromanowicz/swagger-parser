package ex.rr.swaggerparser.annotation.processor;

import static java.util.Objects.nonNull;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import ex.rr.swaggerparser.annotation.SwaggerClient;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.parser.SwaggerParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

@SupportedAnnotationTypes("ex.rr.swaggerparser.annotation.SwaggerClient")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class SwaggerClientProcessor extends AbstractProcessor {
  private Messager messager;
  private Filer filer;
  // private Elements elements;
  // private Map markedClasses;

  @Override
  public synchronized void init(ProcessingEnvironment pEnv) {
    super.init(pEnv);
    filer = pEnv.getFiler();
    messager = pEnv.getMessager();
    // elements = pEnv.getElementUtils();
    // markedClasses = new HashMap<>();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(SwaggerClient.class)) {
      if (element.getKind() != ElementKind.CLASS) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Can be applied to class.");
        return true;
      }

      messager.printMessage(Diagnostic.Kind.NOTE, "Testing");

      switch (element.getAnnotation(SwaggerClient.class).type()) {
        case OPENAPI3 -> processOpenApi(element);
        case SWAGGER -> processSwagger(element);
        default -> throw new UnsupportedOperationException();
      }

      // TypeElement typeElement = (TypeElement) element;
      // markedClasses.put(typeElement.getSimpleName().toString(), element);
    }

    return true;
  }

  private void processSwagger(Element element) {
    final String location = element.getAnnotation(SwaggerClient.class).location();
    Swagger swagger = new SwaggerParser().read(location);
    Map<String, Model> definitions = swagger.getDefinitions();

    definitions.forEach((k, v) -> {
      TypeSpec def = generateDefinition(k, v);
      saveClassDefinitionToFile(element, def);
    });
  }

  private void processOpenApi(Element element) {
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
    }

  }

  private void saveClassDefinitionToFile(Element element, TypeSpec definition) {
    try {
      PackageElement packageElement = super.processingEnv.getElementUtils().getPackageOf(element);
      String packageName = packageElement.getQualifiedName().toString() + ".generated";

      JavaFile javaFile = JavaFile.builder(packageName, definition).build();
      javaFile.writeTo(filer);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
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
