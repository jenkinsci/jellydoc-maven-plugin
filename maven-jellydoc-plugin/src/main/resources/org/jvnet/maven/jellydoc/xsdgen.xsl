<!-- generates XML Schema for schema-assisted editing -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:xsd="http://www.w3.org/2001/XMLSchema" version="1.0">

  <xsl:output method="xml" encoding="UTF-8" indent="yes"/>

  <xsl:template match="/">
    <xsl:apply-templates select="*"/>
  </xsl:template>

  <xsl:template match="tags">
    <xsl:apply-templates />
  </xsl:template>

  <xsl:template match="library">
    <xsd:schema targetNamespace="{@uri}" elementFormDefault="qualified">
      <xsl:apply-templates />
    </xsd:schema>
  </xsl:template>

  <xsl:template match="tag">
    <xsd:element name="{@name}">
      <xsl:apply-templates select="doc"/>
      <xsd:complexType mixed="true">
        <xsl:choose>
          <xsl:when test="@no-content">
            <!-- no content model -->
          </xsl:when>
          <xsl:otherwise>
            <xsd:sequence>
              <xsd:any processContents="lax" minOccurs="0" maxOccurs="unbounded"/>
            </xsd:sequence>
          </xsl:otherwise>
        </xsl:choose>
        <xsl:for-each select="attribute">
          <xsd:attribute name="{@name}">
            <xsl:if test="@use">
              <xsl:attribute name="use"><xsl:value-of select="@use" /></xsl:attribute>
            </xsl:if>
            <xsl:apply-templates select="doc"/>
          </xsd:attribute>
        </xsl:for-each>
      </xsd:complexType>
    </xsd:element>
  </xsl:template>

  <xsl:template match="doc">
    <xsd:annotation>
      <xsd:documentation>
        <xsl:copy-of select="*|text()" />
      </xsd:documentation>
    </xsd:annotation>
  </xsl:template>
</xsl:stylesheet>