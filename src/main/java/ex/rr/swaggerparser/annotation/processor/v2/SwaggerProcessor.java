package ex.rr.swaggerparser.annotation.processor.v2;

import static java.util.Objects.nonNull;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;

import ex.rr.swaggerparser.annotation.SwaggerClient;
import ex.rr.swaggerparser.annotation.processor.AbstractSwaggerProcessor;
import io.swagger.models.Model;
import io.swagger.models.Swagger;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.BooleanProperty;
import io.swagger.models.properties.DateProperty;
import io.swagger.models.properties.DateTimeProperty;
import io.swagger.models.properties.DecimalProperty;
import io.swagger.models.properties.FloatProperty;
import io.swagger.models.properties.IntegerProperty;
import io.swagger.models.properties.LongProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import io.swagger.models.properties.StringProperty;
import io.swagger.models.properties.UUIDProperty;
import io.swagger.parser.SwaggerParser;
import lombok.Builder;
import lombok.Data;

/**
 * SwaggerProcessor
 */
public class SwaggerProcessor extends AbstractSwaggerProcessor {

  private Element element;
  private String parentName;

  public SwaggerProcessor(ProcessingEnvironment processingEnvironment) {
    super();
    super.init(processingEnvironment);
  }

  @Override
  public void process(Element element) {
    this.element = element;
    final String location = element.getAnnotation(SwaggerClient.class).location();
    Swagger swagger = new SwaggerParser().read(location);
    Map<String, Model> definitions = swagger.getDefinitions();

    definitions.forEach((k, v) -> {
      TypeSpec def = generateModelDefinitions(k, v);
      saveClassDefinitionToFile(element, def);
    });
  }

  private TypeSpec generateModelDefinitions(String name, Model model) {
    this.parentName = name;
    var def = TypeSpec.classBuilder(name)
        .addModifiers(Modifier.PUBLIC)
        .addSuperinterface(ClassName.get(Serializable.class))
        .addAnnotation(Data.class)
        .addAnnotation(Builder.class);
    model.getProperties().forEach((k, v) -> def.addField(resolveType(v, k), k, Modifier.PRIVATE));

    return def.build();
  }

  private TypeName resolveType(Property property, String name) {
    return switch (property) {

      case ArrayProperty p -> {
        if (nonNull(p.getUniqueItems()) && p.getUniqueItems()) {
          yield ParameterizedTypeName.get(ClassName.get("java.util", "Set"),
              resolveType(p.getItems(), name));
        } else {
          yield ParameterizedTypeName.get(ClassName.get("java.util", "List"),
              resolveType(p.getItems(), name));
        }
      }
      case RefProperty p -> ClassName.get("", resolveReferenceClassName(p));
      case DateTimeProperty p -> TypeName.get(LocalDateTime.class);

      case BooleanProperty p -> TypeName.get(Boolean.class);
      case DateProperty p -> TypeName.get(LocalDate.class);
      case FloatProperty p -> TypeName.FLOAT;
      case DecimalProperty p -> TypeName.DOUBLE;
      case IntegerProperty p -> TypeName.get(this.getClass());
      case LongProperty p -> TypeName.LONG;
      case MapProperty p -> TypeName.get(Map.class); // TODO: fix types
      case UUIDProperty p -> TypeName.get(UUID.class);
      case StringProperty p -> {
        if (CollectionUtils.isNotEmpty(p.getEnum())) {
          yield generateEnumDefinition(name, p.getEnum());
        } else {
          yield TypeName.get(String.class);
        }
      }
      default -> {
        String format = String.format("%s | %s | %s", property.getName(), property.getFormat(), property.getType());
        messager.printError(format);
        System.out.println(format);
        yield TypeName.get(String.class);
      }
    };
  }

  private String resolveReferenceClassName(RefProperty property) {
    String[] arr = property.get$ref().split("/");
    return arr[arr.length - 1];
  }

  private ClassName generateEnumDefinition(String name, Collection<String> values) {
    String enumName = String.format("%s%s", StringUtils.capitalize(parentName), StringUtils.capitalize(name));
    TypeSpec.Builder enumDef = TypeSpec.enumBuilder(enumName)
        .addModifiers(Modifier.PUBLIC);
    values.forEach(v -> enumDef.addEnumConstant(v));
    saveClassDefinitionToFile(this.element, enumDef.build());
    return ClassName.get("", enumName);
  }

}
