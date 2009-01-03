package org.jvnet.maven.jellydoc;

import com.sun.xml.txw2.annotation.XmlAttribute;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Attribute extends Item {
    @XmlAttribute
    Attribute name(String value);

    @XmlAttribute
    Attribute type(String value);

    @XmlAttribute
    Attribute use(String value);
}
