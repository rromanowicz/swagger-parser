package ex.rr.swaggerparser.annotation.processor;

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
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

import ex.rr.swaggerparser.annotation.SwaggerClient;
import ex.rr.swaggerparser.annotation.processor.v2.SwaggerProcessor;
import ex.rr.swaggerparser.annotation.processor.v3.OpenApiV3Processor;

@SupportedAnnotationTypes("ex.rr.swaggerparser.annotation.SwaggerClient")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
public class SwaggerClientProcessor extends AbstractProcessor {
  protected Messager messager;
  protected Filer filer;
  protected SwaggerProcessor v2Processor;
  protected OpenApiV3Processor v3Processor;

  @Override
  public synchronized void init(ProcessingEnvironment pEnv) {
    super.init(pEnv);
    filer = pEnv.getFiler();
    messager = pEnv.getMessager();
    v2Processor = new SwaggerProcessor(pEnv);
    v3Processor = new OpenApiV3Processor(pEnv);
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element element : roundEnv.getElementsAnnotatedWith(SwaggerClient.class)) {
      if (element.getKind() != ElementKind.CLASS) {
        messager.printMessage(Diagnostic.Kind.ERROR, "Can only be applied to class.");
        return true;
      }
      switch (element.getAnnotation(SwaggerClient.class).type()) {
        case OPENAPI3 -> v3Processor.process(element);
        case SWAGGER -> v2Processor.process(element);
        default -> throw new UnsupportedOperationException();
      }
    }
    return true;
  }

}
