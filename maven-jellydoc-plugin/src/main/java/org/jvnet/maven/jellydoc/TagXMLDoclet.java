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

import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.AnnotationDesc.ElementValuePair;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.Doclet;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.Tag;
import com.sun.xml.txw2.TXW;
import com.sun.xml.txw2.TypedXmlWriter;
import com.sun.xml.txw2.output.StreamSerializer;
import org.cyberneko.html.parsers.SAXParser;
import org.jvnet.maven.jellydoc.annotation.NoContent;
import org.jvnet.maven.jellydoc.annotation.Required;
import org.jvnet.maven.jellydoc.annotation.TagLibUri;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.beans.Introspector;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

/**
 * Main Doclet class to generate Tag Library ML.
 *
 * @author <a href="mailto:gopi@aztecsoft.com">Gopinath M.R.</a>
 * @author <a href="mailto:jstrachan@apache.org">James Strachan</a>
 * @author Rodney Waldhoff
 */

// #### somehow we need to handle taglib inheritence...

public class TagXMLDoclet extends Doclet {

    private String targetFileName = null;
    private String encodingFormat;

    public TagXMLDoclet (RootDoc root) throws Exception
    {
        readOptions(root);
        File targetFile = new File(targetFileName);
        targetFile.getParentFile().mkdirs();
        FileOutputStream writer = new FileOutputStream(targetFileName);
        Tags tw = TXW.create(Tags.class,new StreamSerializer(writer));

        javadocXML(root,tw);
        tw.commit();
    }

    /**
     * Generates the xml for the tag libraries
     */
    private void javadocXML(RootDoc root, Tags tw) throws SAXException {
        Set<PackageDoc> pkgs = new HashSet<>();
        for (ClassDoc c : root.specifiedClasses())
            pkgs.add(c.containingPackage());
        pkgs.addAll(Arrays.asList(root.specifiedPackages()));

        // Generate for packages.
        for (PackageDoc pkg : pkgs)
            packageXML(pkg,tw);
    }

    /**
     * Generates doc for a tag library
     */
    private void packageXML(PackageDoc packageDoc, Tags tw) throws SAXException {

        System.out.println( "processing package: " + packageDoc.name());

        ClassDoc[] classArray = packageDoc.ordinaryClasses();

        // lets see if we find a Tag
        boolean foundTag = false;
        for (ClassDoc classDoc : classArray) {
            if (isTag(classDoc)) {
                foundTag = true;
                break;
            }
        }
        if (!foundTag)
            return;

        Library library = tw.library();
        library.name(packageDoc.name());

        String name = packageDoc.name();
        int idx = name.lastIndexOf('.');
        if ( idx > 0 ) {
            name = name.substring(idx+1);
        }
        library.prefix(name);

        String uri = findUri(packageDoc.annotations());
        if(uri==null)
            uri = "jelly:" + name; // fallback

        library.uri(uri);

        // generate Doc element.
        docXML(packageDoc,library);

        // generate tags
        for (ClassDoc c : classArray) {
            if (isTag(c)) {
                tagXML(c,library.tag());
            }
        }
    }

    private String findUri(AnnotationDesc[] an) {
        for (AnnotationDesc a : an)
            if(a.annotationType().qualifiedName().equals(TagLibUri.class.getName()))
                for (ElementValuePair e : a.elementValues())
                    if(e.element().name().equals("value"))
                        return e.value().value().toString();
        return null;
    }

    private boolean has(ProgramElementDoc doc, Class<? extends Annotation> type) {
        for (AnnotationDesc a : doc.annotations())
            if(a.annotationType().qualifiedName().equals(type.getName()))
                return true;
        return false;
    }

    /**
     * @return true if this class is a Jelly Tag
     */
    private boolean isTag(ClassDoc classDoc) {
        ClassDoc[] interfaceArray = classDoc.interfaces();
        for (ClassDoc i : interfaceArray) {
            String name = i.qualifiedName();
            if ("org.apache.commons.jelly.Tag".equals(name)) {
                return true;
            }
        }
        ClassDoc base = classDoc.superclass();
        return base != null && isTag(base);
    }

    /**
     * Generates doc for a tag
     */
    private void tagXML(ClassDoc classDoc, org.jvnet.maven.jellydoc.Tag tag) throws SAXException {
        if (classDoc.isAbstract()) {
            return;
        }

        tag.className(classDoc.name());
        String name = classDoc.name();
        if ( name.endsWith( "Tag" ) ) {
            name = name.substring(0, name.length() - 3 );
        }
        name = Introspector.decapitalize(name);


        System.out.println( "processing tag: " + name);

        tag.name(name);
        if(has(classDoc,NoContent.class))
            tag.noContent(true);

        // generate "doc" sub-element
        docXML(classDoc,tag);

        // generate the attributes
        propertiesXML(classDoc,tag);
    }

    /**
     * Generates doc for a tag property
     */
    private void propertiesXML(ClassDoc classDoc, org.jvnet.maven.jellydoc.Tag tag) throws SAXException {
        MethodDoc[] methodArray = classDoc.methods();
        for (MethodDoc m : methodArray) {
            propertyXML(m,tag);
        }
        ClassDoc base = classDoc.superclass();
        if ( base != null ) {
            propertiesXML( base, tag);
        }
    }


    /**
     * Generates doc for a tag property
     */
    private void propertyXML(MethodDoc methodDoc, org.jvnet.maven.jellydoc.Tag tag) throws SAXException {
        if ( ! methodDoc.isPublic() || methodDoc.isStatic() ) {
            return;
        }
        String name = methodDoc.name();
        if ( ! name.startsWith( "set" ) ) {
            return;
        }
        Parameter[] parameterArray = methodDoc.parameters();
        if ( parameterArray == null || parameterArray.length != 1 ) {
            return;
        }
        Parameter parameter = parameterArray[0];

        name = name.substring(3);
        name = Introspector.decapitalize(name);

        if ( name.equals( "body") || name.equals( "context" ) || name.equals( "parent" ) ) {
            return;
        }

        Attribute a = tag.attribute();
        a.name(name);
        a.type(parameter.typeName());
        if(has(methodDoc, Required.class))
            a.use("required");

        // maybe do more semantics, like use custom tags to denote if its required, optional etc.

        // generate "doc" sub-element
        docXML(methodDoc,a);
    }

    /**
     * Generates doc for element "doc"
     */
    private void docXML(Doc doc, Item w) throws SAXException {
        TypedXmlWriter d = w.doc();
        // handle the "comment" part, including {@link} tags
        {
            for (Tag tag : doc.inlineTags()) {
                // if tags[i] is an @link tag
                if (tag instanceof SeeTag) {
                    String label = ((SeeTag) tag).label();
                    // if the label is null or empty, use the class#member part of the link
                    if (null == label || "".equals(label)) {
                        StringBuffer buf = new StringBuffer();
                        String className = ((SeeTag) tag).referencedClassName();
                        if ("".equals(className)) {
                            className = null;
                        }
                        String memberName = ((SeeTag) tag).referencedMemberName();
                        if ("".equals(memberName)) {
                            memberName = null;
                        }
                        if (null != className) {
                            buf.append(className);
                            if (null != memberName) {
                                buf.append(".");
                            }
                        }
                        if (null != memberName) {
                            buf.append(memberName);
                        }
                        label = buf.toString();
                    }
                    parseHTML(label,d);
                } else {
                    parseHTML(tag.text(),d);
                }
            }
        }
        // handle the "tags" part
        for (Tag tag : doc.tags())
            javadocTagXML(tag,w);
    }

    protected void parseHTML(String text, final TypedXmlWriter d) throws SAXException {
        SAXParser parser = new SAXParser();
        parser.setProperty(
            "http://cyberneko.org/html/properties/names/elems",
            "lower"
        );
        parser.setProperty(
            "http://cyberneko.org/html/properties/names/attrs",
            "lower"
        );
        parser.setContentHandler(
            new DefaultHandler() {
                private Stack<TypedXmlWriter> w = new Stack<>();
                { w.push(d); }
                @Override
                public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
                    if ( validDocElementName( localName ) ) {
                        w.push(w.peek()._element(localName,TypedXmlWriter.class));
                    }
                }
                @Override
                public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
                    if ( validDocElementName( localName ) ) {
                        w.pop();
                    }
                }
                @Override
                public void characters(char[] ch, int start, int length) throws SAXException {
                    w.peek()._pcdata(new String(ch,start,length));
                }
            }
        );
        try {
            parser.parse( new InputSource(new StringReader( text )) );
        }
        catch (IOException e) {
            System.err.println( "This should never happen!" + e );
        }
    }

    /**
     * @return true if the given name is a valid HTML markup element.
     */
    protected boolean validDocElementName(String name) {
        return ! name.equalsIgnoreCase( "html" ) && ! name.equalsIgnoreCase( "body" );
    }

    /**
     * Generates doc for all tag elements.
     */
    private void javadocTagXML(Tag tag, Item w) throws SAXException {
        String name = tag.name().substring(1) + "tag";
        if (! tag.text().equals(""))
            w._element(name,TypedXmlWriter.class)._pcdata(tag.text());
    }

    public static boolean start(RootDoc root) {
        try {
            new TagXMLDoclet(root);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
            return false;
        }
    }

    private void readOptions(RootDoc root)
    {
        for (String[] opt : root.options()) {
            if (opt[0].equals("-d")) {
                targetFileName = opt[1] + "/taglib.xml";
            }
            if (opt[0].equals("-encoding")) {
                encodingFormat = opt[1];
            }
        }
    }

    public static int optionLength(String option)
    {
        if(option.equals("-d"))
        {
            return 2;
        }
        if(option.equals("-encoding"))
        {
            return 2;
        }
        return 0;
    }

    public static boolean validOptions(String[][] options,
        DocErrorReporter reporter)
    {
        boolean foundEncodingOption = false;
        boolean foundDirOption = false;
        for (String[] opt : options) {
            if (opt[0].equals("-d")) {
                if (foundDirOption) {
                    reporter.printError("Only one -d option allowed.");
                    return false;
                } else {
                    foundDirOption = true;
                }
            }
            if (opt[0].equals("-encoding")) {
                if (foundEncodingOption) {
                    reporter.printError("Only one -encoding option allowed.");
                    return false;
                } else {
                    foundEncodingOption = true;
                }
            }
        }
        if (!foundDirOption)
        {
            reporter.printError("Usage: javadoc -d <directory> -doclet TagXMLDoclet ...");
            return false;
        }
        return true;
    }
}
