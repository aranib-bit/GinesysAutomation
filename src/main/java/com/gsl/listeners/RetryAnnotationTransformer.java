package com.gsl.listeners;

import com.gsl.config.ConfigFactory;
import com.gsl.utils.Log;
import io.github.sskorol.core.DataProviderTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Extends {@link DataProviderTransformer} to combine {@code @DataSupplier} routing with
 * retry-analyzer injection in a single {@link org.testng.IAnnotationTransformer}.
 *
 * <p>TestNG only allows one annotation transformer. Previously, registering
 * {@code RetryAnnotationTransformer} (from testng.xml) blocked the ServiceLoader-registered
 * {@code DataProviderTransformer}, which caused {@code @DataSupplier} methods to be invisible
 * as data providers. Extending {@code DataProviderTransformer} ensures its {@code @DataSupplier}
 * rewriting logic runs via {@code super.transform()} before the retry logic is applied.
 *
 * <p>The {@code DataProviderTransformer} from ServiceLoader will still log
 * "AnnotationTransformer already set" — that warning is harmless because this subclass
 * already covers all its responsibilities.
 */
public class RetryAnnotationTransformer extends DataProviderTransformer {

    @Override
    public void transform(ITestAnnotation annotation,
                          Class testClass,
                          Constructor testConstructor,
                          Method testMethod) {
        super.transform(annotation, testClass, testConstructor, testMethod);
        if (ConfigFactory.getConfig().retryMaxCount() > 0) {
            annotation.setRetryAnalyzer(RetryAnalyzer.class);
            if (testMethod != null) {
                Log.debug("[RetryAnnotationTransformer] RetryAnalyzer wired to {}.{}",
                        testMethod.getDeclaringClass().getSimpleName(),
                        testMethod.getName());
            }
        }
    }
}
