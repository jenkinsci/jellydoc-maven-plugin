#!/bin/bash -e
# generate XSD from all the Jelly tags developed in Apache commons Jelly
# these taglib.xml files are generated from Maven1.
#
# To generate taglib.xml on Jelly, run "maven jellydoc:doclet" and
# "maven tags:doc". It needs to be built first with
# "maven jar:install" and "maven tags:build".
base=$PWD
mkdir $base/schemas > /dev/null 2>&1 || true
xsl=$base/src/main/resources/org/jvnet/maven/jellydoc/xsdgen.xsl

cd $1
for xml in target/taglib.xml jelly-tags/*/target/taglib.xml
do
  lib=$(basename $(dirname $(dirname $xml)))
  echo $lib
  echo $xml
  xmlstarlet tr $xsl $xml | xmlstarlet fo > $base/schemas/$lib.xsd
done
# this file created by core
mv $base/schemas/..xsd $base/schemas/core.xsd
rm jelly-schemas.zip || true
zip jelly-schemas.zip schemas/*.xsd
