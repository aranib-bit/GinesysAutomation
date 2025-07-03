package com.annotations;

import com.enums.TestAuthor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface FrameworkAnnotation {
    TestAuthor value() default TestAuthor.ARANI;
}
