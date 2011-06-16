/*
 * Copyright 2009, Kohsuke Kawaguchi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    @XmlElement
    Body body();
}
