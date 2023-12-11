/*
 * Copyright 2001-2005 The Apache Software Foundation.
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

import com.sun.source.doctree.BlockTagTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.util.DocTrees;
import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.output.StreamSerializer;
import java.beans.Introspector;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import org.cyberneko.html.parsers.SAXParser;
import org.jvnet.maven.jellydoc.annotation.NoContent;
import org.jvnet.maven.jellydoc.annotation.Required;
import org.jvnet.maven.jellydoc.annotation.TagLibUri;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Main Doclet class to generate Tag Library ML.
 *
 * @author <a href="mailto:gopi@aztecsoft.com">Gopinath M.R.</a>
 * @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
 * @author Rodney Waldhoff
 */

// #### somehow we need to handle taglib inheritence...

public class TagXMLDoclet implements Doclet {

    private DocTrees docTrees;
    private Reporter reporter;

    private String targetFileName = null;
    private String encodingFormat;

    private void main(DocletEnvironment root) throws Exception {
        File targetFile = new File(targetFileName);
        targetFile.getParentFile().mkdirs();
        FileOutputStream writer = new FileOutputStream(targetFileName);
        Tags tw = TXW.create(Tags.class, new StreamSerializer(writer));

        javadocXML(root, tw);
        tw.commit();
    }

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "TagXMLDoclet";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Set.of(new Option("-d", "target directory", "<dir>", 1) {
            @Override
            public boolean process(String opt, List<String> args) {
                targetFileName = args.get(0) + "/taglib.xml";
                return true;
            }
        });
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    /**
     * Generates the xml for the tag libraries
     */
    private void javadocXML(DocletEnvironment root, Tags tw) throws SAXException {
        docTrees = root.getDocTrees();

        // Generate for packages.
        for (PackageElement pkg : ElementFilter.packagesIn(root.getIncludedElements())) {
            packageXML(pkg, tw);
        }
    }

    /**
     * Generates doc for a tag library
     */
    private void packageXML(PackageElement packageDoc, Tags tw) throws SAXException {

        System.out.println("processing package: " + packageDoc.getQualifiedName());

        // lets see if we find a Tag
        boolean foundTag = false;
        for (TypeElement classDoc : ElementFilter.typesIn(packageDoc.getEnclosedElements())) {
            if (isTag(classDoc)) {
                foundTag = true;
                break;
            }
        }
        if (!foundTag) {
            return;
        }

        Library library = tw.library();
        library.name(packageDoc.getQualifiedName().toString());

        String name = packageDoc.getQualifiedName().toString();
        int idx = name.lastIndexOf('.');
        if (idx > 0) {
            name = name.substring(idx + 1);
        }
        library.prefix(name);

        String uri = findUri(packageDoc.getAnnotationMirrors());
        if (uri == null) {
            uri = "jelly:" + name; // fallback
        }

        library.uri(uri);

        // generate Doc element.
        docXML(packageDoc, library);

        // generate tags
        for (TypeElement c : ElementFilter.typesIn(packageDoc.getEnclosedElements())) {
            if (isTag(c) && !c.getModifiers().contains(Modifier.ABSTRACT)) {
                tagXML(c, library.tag());
            }
        }
    }

    private String findUri(List<? extends AnnotationMirror> an) {
        for (AnnotationMirror a : an) {
            if (a.getAnnotationType().asElement() instanceof TypeElement
                    && ((TypeElement) a.getAnnotationType().asElement())
                            .getQualifiedName()
                            .toString()
                            .equals(TagLibUri.class.getName())) {
                for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e :
                        a.getElementValues().entrySet()) {
                    if (e.getKey().getSimpleName().toString().equals("value")) {
                        return e.getValue().toString();
                    }
                }
            }
        }
        return null;
    }

    private boolean has(Element doc, Class<? extends Annotation> type) {
        for (AnnotationMirror a : doc.getAnnotationMirrors()) {
            if (a.getAnnotationType().asElement() instanceof TypeElement
                    && ((TypeElement) a.getAnnotationType().asElement())
                            .getQualifiedName()
                            .toString()
                            .equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true if this class is a Jelly Tag
     */
    private boolean isTag(TypeElement classDoc) {
        List<? extends TypeMirror> interfaceArray = classDoc.getInterfaces();
        for (TypeMirror i : interfaceArray) {
            if ("org.apache.commons.jelly.Tag".equals(i.toString())) {
                return true;
            }
        }
        TypeMirror base = classDoc.getSuperclass();
        if (base instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) base;
            Element element = declaredType.asElement();
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                return isTag(typeElement);
            }
        }
        return false;
    }

    /**
     * Generates doc for a tag
     */
    private void tagXML(TypeElement classDoc, org.jvnet.maven.jellydoc.Tag tag) throws SAXException {
        String name = classDoc.getSimpleName().toString();
        tag.className(name);
        if (name.endsWith("Tag")) {
            name = name.substring(0, name.length() - 3);
        }
        name = Introspector.decapitalize(name);

        System.out.println("processing tag: " + name);

        tag.name(name);
        if (has(classDoc, NoContent.class)) {
            tag.noContent(true);
        }

        // generate "doc" sub-element
        docXML(classDoc, tag);

        // generate the attributes
        propertiesXML(classDoc, tag);
    }

    /**
     * Generates doc for a tag property
     */
    private void propertiesXML(TypeElement classDoc, org.jvnet.maven.jellydoc.Tag tag) throws SAXException {
        for (ExecutableElement m : ElementFilter.methodsIn(classDoc.getEnclosedElements())) {
            propertyXML(m, tag);
        }
        TypeMirror base = classDoc.getSuperclass();
        if (base instanceof DeclaredType) {
            DeclaredType declaredType = (DeclaredType) base;
            Element element = declaredType.asElement();
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                propertiesXML(typeElement, tag);
            }
        }
    }

    /**
     * Generates doc for a tag property
     */
    private void propertyXML(ExecutableElement methodDoc, org.jvnet.maven.jellydoc.Tag tag) throws SAXException {
        if (!methodDoc.getModifiers().contains(Modifier.PUBLIC)
                || methodDoc.getModifiers().contains(Modifier.STATIC)) {
            return;
        }
        String name = methodDoc.getSimpleName().toString();
        if (!name.startsWith("set")) {
            return;
        }
        List<? extends VariableElement> parameterArray = methodDoc.getParameters();
        if (parameterArray == null || parameterArray.size() != 1) {
            return;
        }
        VariableElement parameter = parameterArray.get(0);

        name = name.substring(3);
        name = Introspector.decapitalize(name);

        if (name.equals("body") || name.equals("context") || name.equals("parent")) {
            return;
        }

        Attribute a = tag.attribute();
        a.name(name);
        a.type(parameter.asType().toString());
        if (has(methodDoc, Required.class)) {
            a.use("required");
        }

        // maybe do more semantics, like use custom tags to denote if its required, optional etc.

        // generate "doc" sub-element
        docXML(methodDoc, a);
    }

    /**
     * Generates doc for element "doc"
     */
    private void docXML(Element doc, Item w) throws SAXException {
        TypedXmlWriter d = w.doc();
        DocCommentTree docCommentTree = docTrees.getDocCommentTree(doc);
        if (docCommentTree != null) {
            StringBuilder sb = new StringBuilder();
            // handle the "comment" part, including {@link} tags
            for (DocTree bodyTree : docCommentTree.getFullBody()) {
                // if tags[i] is an @link tag
                if (bodyTree instanceof LinkTree) {
                    LinkTree linkTree = (LinkTree) bodyTree;
                    List<? extends DocTree> label = linkTree.getLabel();
                    // if the label is null or empty, use the class#member part of the link
                    if (label == null || label.isEmpty()) {
                        ReferenceTree reference = linkTree.getReference();
                        sb.append(reference.toString());
                    } else {
                        for (DocTree labelElement : label) {
                            sb.append(labelElement.toString());
                        }
                    }
                } else if (bodyTree instanceof LiteralTree) {
                    sb.append(((LiteralTree) bodyTree).getBody().getBody());
                } else {
                    sb.append(bodyTree.toString());
                }
            }
            parseHTML(sb.toString(), d);

            // handle the "tags" part
            for (DocTree tag : docCommentTree.getBlockTags()) {
                if (tag instanceof BlockTagTree) {
                    javadocTagXML((BlockTagTree) tag, w);
                }
            }
        }
    }

    protected void parseHTML(String text, final TypedXmlWriter d) throws SAXException {
        SAXParser parser = new SAXParser();
        parser.setProperty("http://cyberneko.org/html/properties/names/elems", "lower");
        parser.setProperty("http://cyberneko.org/html/properties/names/attrs", "lower");
        parser.setContentHandler(new DefaultHandler() {
            private Stack<TypedXmlWriter> w = new Stack<>();

            {
                w.push(d);
            }

            @Override
            public void startElement(String namespaceURI, String localName, String qName, Attributes atts)
                    throws SAXException {
                if (validDocElementName(localName)) {
                    w.push(w.peek()._element(localName, TypedXmlWriter.class));
                }
            }

            @Override
            public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
                if (validDocElementName(localName)) {
                    w.pop();
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                w.peek()._pcdata(new String(ch, start, length));
            }
        });
        try {
            parser.parse(new InputSource(new StringReader(text)));
        } catch (IOException e) {
            System.err.println("This should never happen!" + e);
        }
    }

    /**
     * @return true if the given name is a valid HTML markup element.
     */
    protected boolean validDocElementName(String name) {
        return !name.equalsIgnoreCase("html") && !name.equalsIgnoreCase("body");
    }

    /**
     * Generates doc for all tag elements.
     */
    private void javadocTagXML(BlockTagTree tag, Item w) throws SAXException {
        String name = tag.getTagName() + "tag";
        String text = tag.toString().substring(tag.getTagName().length() + 2);
        if (!text.isEmpty()) {
            w._element(name, TypedXmlWriter.class)._pcdata(text);
        }
    }

    @Override
    public boolean run(DocletEnvironment root) {
        if (targetFileName == null) {
            reporter.print(Diagnostic.Kind.ERROR, "Usage: javadoc -d <directory> -doclet TagXMLDoclet ...");
            return false;
        }

        try {
            main(root);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return false;
        }
    }

    private abstract static class Option implements Doclet.Option {
        private final String[] names;
        private final String parameters;
        private final String description;
        private final int argCount;

        protected Option(String name, String description, String parameters, int argCount) {
            this.names = name.trim().split("\\s+");
            this.description = description;
            this.parameters = parameters;
            this.argCount = argCount;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Option.Kind getKind() {
            return Doclet.Option.Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return List.of(names);
        }

        @Override
        public String getParameters() {
            return parameters;
        }

        @Override
        public String toString() {
            return Arrays.toString(names);
        }

        @Override
        public int getArgumentCount() {
            return argCount;
        }
    }
}
