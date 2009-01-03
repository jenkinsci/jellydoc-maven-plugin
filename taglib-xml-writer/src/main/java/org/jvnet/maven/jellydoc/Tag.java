package org.jvnet.maven.jellydoc;

import com.sun.xml.txw2.annotation.XmlAttribute;
import com.sun.xml.txw2.annotation.XmlElement;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Tag extends Item {
    @XmlAttribute
    Tag className(String value);

    @XmlAttribute
    Tag name(String value);

    @XmlAttribute("no-content")
    Tag noContent(boolean value);

    @XmlElement
    Attribute attribute();
}
