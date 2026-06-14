package com.gsl.annotations;

import com.gsl.enums.AppOperation;
import com.gsl.enums.TestAuthor;
import com.gsl.pages.enums.topmenucomponent.MainModuleType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface FrameworkAnnotation {
    TestAuthor     value()     default TestAuthor.ARANI;
    MainModuleType module()    default MainModuleType.FINANCE;
    String         subModule() default "FinanceARVoucher";
    AppOperation   operation() default AppOperation.NONE;
}
