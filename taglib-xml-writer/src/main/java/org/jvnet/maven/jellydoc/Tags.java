package org.jvnet.maven.jellydoc;

import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.annotation.XmlElement;

/**
 * @author Kohsuke Kawaguchi
 */
@XmlElement("tags")
public interface Tags extends TypedXmlWriter {
    Library library();
}
