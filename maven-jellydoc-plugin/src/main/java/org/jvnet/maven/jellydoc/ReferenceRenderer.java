package org.jvnet.maven.jellydoc;

import org.apache.maven.reporting.AbstractMavenReportRenderer;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.codehaus.doxia.sink.Sink;

import java.net.URL;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 * Generates a Maven report from <tt>taglib.xml</tt>
 * @author Kohsuke Kawaguchi
 */
public class ReferenceRenderer extends AbstractMavenReportRenderer {
    private final Document taglibXml;

    public ReferenceRenderer(Sink sink, URL taglibXml) throws DocumentException {
        super(sink);
        this.taglibXml = new SAXReader().read(taglibXml);
    }

    public String getTitle() {
        return "Jelly Taglib references";
    }

    protected void renderBody() {
        List<Element> libraries = (List<Element>) taglibXml.getRootElement().elements("library");

        paragraph("The following Jelly tag libraries are defined in this project.");

        if(libraries.size()>1) {
            sink.list();
            for (Element library : libraries) {
                sink.listItem();
                sink.rawText(String.format("<a href='#%s'>%s</a>",
                        library.attributeValue("prefix"),
                        library.attributeValue("uri")
                        ));
                sink.listItem_();
            }
            sink.list_();
        }

        for( Element library : libraries) {
            String prefix = library.attributeValue("prefix");

            anchor(prefix);
            startSection(library.attributeValue("uri"));
            paragraphHtml("This tag library is <a href='taglib-"+prefix+".xsd'>also available as an XML Schema</a>");
            renderSummaryTable(library,prefix);

            for( Element tag : (List<Element>)library.elements("tag"))
                renderTagReference(prefix,tag);

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
        tableHeader(new String[]{"Tag Name","Description"});
        for( Element tag : (List<Element>)library.elements("tag")) {
            sink.tableRow();
            sink.tableCell();
            String name = tag.attributeValue("name");
            sink.rawText("<a href='#"+prefix+':'+ name+"'>"+name+"</a>");
            sink.tableCell_();
            docCell(tag);
            sink.tableRow_();
        }
        endTable();
    }

    private void renderTagReference(String taglibPrefix,Element tag) {
        String name = tag.attributeValue("name");
        anchor(taglibPrefix +':'+ name);
        startSection(name);
        doc(tag);

        if(hasVisibleAttributes(tag)) {
            startTable();
            tableHeader(new String[]{"Attribute Name","Type","Description"});
            for( Element att : (List<Element>)tag.elements("attribute"))
                renderAttribute(att);
            endTable();
        }
        endSection();
    }

    private boolean hasVisibleAttributes(Element tag) {
        for( Element att : (List<Element>)tag.elements("attribute")) {
            String name = att.attributeValue("name");
            if(!HIDDEN_ATTRIBUTES.contains(name))
                return true; 
        }
        return false;
    }

    private void anchor(String name) {
        sink.anchor(name);
        sink.anchor_();
    }

    private void renderAttribute(Element att) {
        String name = att.attributeValue("name");
        if(HIDDEN_ATTRIBUTES.contains(name))
            return; // defined in TagSupport.

        sink.tableRow();
        String suffix="";
        if(att.attributeValue("use","optional").equals("required"))
                suffix=" (required)";
        tableCell(name +suffix);
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
        return doc.asXML();
    }

    private static final Set<String> HIDDEN_ATTRIBUTES = new HashSet<String>(Arrays.asList("escapeText","trim"));
}
