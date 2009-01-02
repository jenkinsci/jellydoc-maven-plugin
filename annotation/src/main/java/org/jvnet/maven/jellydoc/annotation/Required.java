package org.jvnet.maven.jellydoc.annotation;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.METHOD;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Used on the setter method on a <code>Tag</code> class to indicate that this attribute
 * is required.
 *
 * @author Kohsuke Kawaguchi
 */
@Documented
@Retention(SOURCE)
@Target(METHOD)
public @interface Required {
}
