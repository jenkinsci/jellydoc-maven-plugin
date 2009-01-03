package org.jvnet.maven.jellydoc;

import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.annotation.XmlElement;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Item extends TypedXmlWriter {
    @XmlElement
    TypedXmlWriter doc();

    @XmlElement
    void doc(String content);
}
