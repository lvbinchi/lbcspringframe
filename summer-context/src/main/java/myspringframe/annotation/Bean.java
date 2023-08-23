package myspringframe.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Bean {
    String value() default "";

    String initMethod() default "";

    String destroyMethod() default "";
}
