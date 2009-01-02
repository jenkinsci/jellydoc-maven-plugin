package org.jvnet.maven.jellydoc.annotation;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.TYPE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;
import java.lang.annotation.Inherited;

/**
 * Used on the {@code Tag} class to indicate that this tag
 * doesn't accept any nested content model.
 * 
 * @author Kohsuke Kawaguchi
 */
@Documented
@Retention(SOURCE)
@Target(TYPE)
@Inherited
public @interface NoContent {
}
