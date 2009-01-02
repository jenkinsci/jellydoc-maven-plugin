package org.jvnet.maven.jellydoc.annotation;

import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.PACKAGE;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.SOURCE;
import java.lang.annotation.Target;

/**
 * Namespace URI of this jelly tag library.
 * 
 * @author Kohsuke Kawaguchi
 */
@Documented
@Retention(SOURCE)
@Target(PACKAGE)
public @interface TagLibUri {
    String value();
}
