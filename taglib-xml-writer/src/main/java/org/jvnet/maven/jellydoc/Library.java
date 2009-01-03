package org.jvnet.maven.jellydoc;

import com.sun.xml.txw2.annotation.XmlAttribute;
import com.sun.xml.txw2.annotation.XmlElement;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Library extends Item {
    @XmlAttribute
    Library name(String name);
    @XmlAttribute
    Library prefix(String name);
    @XmlAttribute
    Library uri(String name);

    @XmlElement
    Tag tag();
}
