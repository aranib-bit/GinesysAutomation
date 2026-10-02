package com.gsl.config.converter;

import com.gsl.config.EnvResolver;
import com.gsl.utils.io.EnvScopedPath;
import org.aeonbits.owner.Converter;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Owner converter for the workbook paths in {@code exceldata.properties}: turns the relative path
 * ({@code Finance/ApVoucher.xlsx}) into the active environment's copy
 * ({@code env/<env>/data/Finance/ApVoucher.xlsx}), so every data supplier picks the right
 * environment without knowing about environments. Applied through
 * {@code @Config.ConverterClass} on each accessor of {@code ExcelDataConfig}.
 */
public final class EnvExcelPathConverter implements Converter<String> {

    private static final ConcurrentMap<String, String> CACHE = new ConcurrentHashMap<>();

    /** Returns the active environment's copy of {@code input}; blank input is passed through. */
    @Override
    public String convert(Method method, String input) {
        if (input == null || input.isBlank()) return input;
        String folder = EnvResolver.resourceFolder();
        return CACHE.computeIfAbsent(folder + "|" + input,
                k -> EnvScopedPath.resolve(folder, input.trim(), EnvExcelPathConverter::exists));
    }

    /** True when the file is under src/test/resources (IDE/Maven source tree) or on the classpath. */
    private static boolean exists(String resourcePath) {
        if (Files.isRegularFile(Paths.get(System.getProperty("user.dir"), "src", "test", "resources", resourcePath))) {
            return true;
        }
        return Thread.currentThread().getContextClassLoader().getResource(resourcePath) != null;
    }
}
