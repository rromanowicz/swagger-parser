package ex.rr.swaggerparser.annotation.processor;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;

import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeSpec;

import ex.rr.swaggerparser.annotation.Format;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * AbstractSwaggerProcessor
 */
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor
public abstract class AbstractSwaggerProcessor {

  private ProcessingEnvironment processingEnv;
  protected Messager messager;

  protected Format format;
  protected Map<String, TypeSpec> definitions = new HashMap<>();
  protected Map<String, TypeSpec> enumDefinitions = new HashMap<>();

  public abstract void process(Element element);

  protected void init(ProcessingEnvironment processingEnvironment) {
    this.processingEnv = processingEnvironment;
    this.messager = processingEnv.getMessager();
  }

  protected void persistDefinitions(Element element) {
    var defs = Stream.concat(definitions.entrySet().stream().map(it -> it.getValue()),
        enumDefinitions.entrySet().stream().map(it -> it.getValue())).toList();
    switch (format) {
      case POJO -> defs.forEach(def -> saveClassDefinitionToFile(element, def));
      case RECORD -> {
        TypeSpec modelDef = TypeSpec.interfaceBuilder("Model")
            .addModifiers(Modifier.PUBLIC)
            .addTypes(defs)
            .build();
        saveClassDefinitionToFile(element, modelDef);
      }
    }
  }

  protected void saveClassDefinitionToFile(Element element, TypeSpec definition) {
    try {
      PackageElement packageElement = this.processingEnv.getElementUtils().getPackageOf(element);
      String packageName = packageElement.getQualifiedName().toString() + ".generated."
          + element.getSimpleName().toString().toLowerCase();

      JavaFile javaFile = JavaFile.builder(packageName, definition).build();
      javaFile.writeTo(this.processingEnv.getFiler());

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
