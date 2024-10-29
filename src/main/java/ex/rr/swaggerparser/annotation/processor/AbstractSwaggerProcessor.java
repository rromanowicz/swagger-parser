package ex.rr.swaggerparser.annotation.processor;

import java.io.IOException;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;

import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.TypeSpec;

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

  public abstract void process(Element element);

  protected void init(ProcessingEnvironment processingEnvironment) {
    this.processingEnv = processingEnvironment;
    this.messager = processingEnv.getMessager();
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
