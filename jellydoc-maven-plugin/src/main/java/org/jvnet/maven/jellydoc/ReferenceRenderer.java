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

import java.io.StringWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.java.textilej.parser.MarkupParser;
import net.java.textilej.parser.builder.HtmlDocumentBuilder;
import net.java.textilej.parser.markup.confluence.ConfluenceDialect;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.reporting.AbstractMavenReportRenderer;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

/**
 * Generates a Maven report from {@code taglib.xml}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ReferenceRenderer extends AbstractMavenReportRenderer {
    private final Document taglibXml;
    private static final Comparator<Element> SORT_BY_NAME = Comparator.comparing(o -> o.attributeValue("name"));

    public ReferenceRenderer(Sink sink, URL taglibXml) throws DocumentException {
        super(sink);
        this.taglibXml = new SAXReader().read(taglibXml);
    }

    @Override
    public String getTitle() {
        return "Jelly Taglib references";
    }

    @Override
    protected void renderBody() {
        List<Element> libraries = sortByName(taglibXml.getRootElement().elements("library"));

        paragraph("The following Jelly tag libraries are defined in this project.");

        if (libraries.size() > 1) {
            startTable();
            tableHeader(new String[] {"Namespace URI", "Description"});
            for (Element library : libraries) {
                sink.tableRow();
                sink.tableCell();
                sink.rawText(String.format(
                        "<a href='#%s'>%s</a>", library.attributeValue("prefix"), library.attributeValue("uri")));
                sink.tableCell_();
                docCell(library);
                sink.tableRow_();
            }
            endTable();
        }

        for (Element library : libraries) {
            String prefix = library.attributeValue("prefix");

            anchor(prefix);
            startSection(library.attributeValue("uri"));
            doc(library);
            paragraphHtml(
                    "This tag library is <a href='taglib-" + prefix + ".xsd'>also available as an XML Schema</a>");
            renderSummaryTable(library, prefix);

            for (Element tag : sortByName(library.elements("tag"))) {
                renderTagReference(prefix, tag);
            }

            endSection();
        }
    }

    private void paragraphHtml(String rawText) {
        sink.paragraph();
        sink.rawText(rawText);
        sink.paragraph_();
    }

    private void renderSummaryTable(Element library, String prefix) {
        startTable();
        tableHeader(new String[] {"Tag Name", "Description"});

        List<Element> tags = sortByName(library.elements("tag"));

        for (Element tag : tags) {
            sink.tableRow();
            sink.tableCell();
            String name = tag.attributeValue("name");
            sink.rawText("<a href='#" + prefix + ".3A" + name + "'>" + name + "</a>");
            sink.tableCell_();
            docCell(tag);
            sink.tableRow_();
        }
        endTable();
    }

    private List<Element> sortByName(List<Element> list) {
        List<Element> tags = new ArrayList<>(list);
        tags.sort(SORT_BY_NAME);
        return tags;
    }

    /**
     * Generates a documentation for one tag.
     */
    private void renderTagReference(String taglibPrefix, Element tag) {
        String name = tag.attributeValue("name");
        anchor(taglibPrefix + ':' + name);
        startSection(name);
        doc(tag);

        if (hasVisibleAttributes(tag)) {
            startTable();
            tableHeader(new String[] {"Attribute Name", "Type", "Description"});
            for (Element att : sortByName(tag.elements("attribute"))) {
                renderAttribute(att);
            }
            endTable();
        }
        // renders description of the body
        if (tag.attributeValue("no-content", "false").equals("true")) {
            paragraph("This tag does not accept any child elements/text.");
        } else {
            Element body = tag.element("body");
            if (body != null) {
                startSection("body");
                doc(body);
                endSection();
            }
        }
        endSection();
    }

    private boolean hasVisibleAttributes(Element tag) {
        for (Element att : tag.elements("attribute")) {
            String name = att.attributeValue("name");
            if (!HIDDEN_ATTRIBUTES.contains(name)) {
                return true;
            }
        }
        return false;
    }

    private void anchor(String name) {
        sink.anchor(name);
        sink.anchor_();
    }

    /**
     * Generates a documentation for one attribute.
     */
    private void renderAttribute(Element att) {
        String name = att.attributeValue("name");
        if (HIDDEN_ATTRIBUTES.contains(name)) {
            return; // defined in TagSupport.
        }

        sink.tableRow();
        String suffix = "";
        if (att.attributeValue("use", "optional").equals("required")) {
            suffix += " (required)";
        }
        if (att.attributeValue("deprecated", "false").equals("true")) {
            suffix += " (deprecated)";
        }
        tableCell(name + suffix);
        tableCell(att.attributeValue("type"));
        docCell(att);
        sink.tableRow_();
    }

    /**
     * Renders the &lt;doc> tag as raw text.
     */
    private void doc(Element tag) {
        sink.rawText(docXml(tag));
    }

    /**
     * Renders the &lt;doc> tag as table cell.
     */
    private void docCell(Element tag) {
        sink.tableCell();
        sink.rawText(docXml(tag));
        sink.tableCell_();
    }

    private String docXml(Element parent) {
        Element doc = parent.element("doc");
        // remove all javadoc tags that don't belong.
        doc.content().removeAll(doc.elements("authortag"));
        String xml = doc.getText();

        StringWriter w = new StringWriter();
        MarkupParser parser = new MarkupParser(new ConfluenceDialect());
        HtmlDocumentBuilder builder = new HtmlDocumentBuilder(w) {
            @Override
            public void lineBreak() {
                // no line break since IDEs usually don't wrap text.
            }
        };

        builder.setEmitAsDocument(false);
        parser.setBuilder(builder);
        parser.parse(xml);
        return w.toString();
    }

    private static final Set<String> HIDDEN_ATTRIBUTES = new HashSet<>(Arrays.asList("escapeText", "trim"));
}
