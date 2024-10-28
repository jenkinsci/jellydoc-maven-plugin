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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.doxia.sink.SinkFactory;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.reporting.MavenMultiPageReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javadoc;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.codehaus.plexus.util.FileUtils;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.DocumentSource;
import org.dom4j.io.SAXReader;
import org.dom4j.tree.DefaultDocument;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;

/**
 * Generates jellydoc XML and other artifacts from there.
 *
 * @author Kohsuke Kawaguchi
 */
@Mojo(name = "jellydoc", requiresDependencyResolution = ResolutionScope.COMPILE)
@Execute(phase = LifecyclePhase.GENERATE_SOURCES)
public class JellydocMojo extends AbstractMojo implements MavenMultiPageReport {
    /**
     * The Maven Project Object
     */
    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    public MavenProject project;

    /**
     * The Maven session object.
     */
    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    public MavenSession session;

    /**
     * The plugin dependencies.
     */
    @Parameter(defaultValue = "${plugin.artifacts}", required = true, readonly = true)
    public List<Artifact> pluginArtifacts;

    /**
     * Version of this plugin.
     */
    @Parameter(defaultValue = "${plugin.version}", required = true, readonly = true)
    public String pluginVersion;

    /**
     * Factory for creating artifact objects
     */
    @Component
    public ArtifactFactory factory;

    /**
     * Used for resolving artifacts
     */
    @Component
    public RepositorySystem repositorySystem;

    @Component
    public MavenProjectHelper helper;

    private File outputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        Project p = new Project();

        DefaultLogger logger = new DefaultLogger();
        logger.setErrorPrintStream(System.err);
        logger.setOutputPrintStream(System.out);
        logger.setMessageOutputLevel(getLog().isDebugEnabled() ? Project.MSG_DEBUG : Project.MSG_INFO);
        p.addBuildListener(logger);

        Javadoc javadoc = new Javadoc();
        javadoc.setTaskName("jellydoc");
        javadoc.setProject(p);

        boolean foundDir = false;
        for (Object dir : project.getCompileSourceRoots()) {
            File dirFile = new File(dir.toString());
            // For pom projects the dir might not exist
            if (dirFile.exists()) {
                FileSet fs = new FileSet();
                fs.setProject(p);
                fs.setDir(dirFile);
                javadoc.addFileset(fs);
                foundDir = true;
            }
        }

        if (!foundDir) {
            return;
        }

        javadoc.setClasspath(makePath(p, project.getArtifacts()));

        Javadoc.DocletInfo d = javadoc.createDoclet();
        d.setProject(p);
        d.setName(TagXMLDoclet.class.getName());
        setParam(d, "-d", targetDir().getAbsolutePath());

        Path docletPath = makePath(p, pluginArtifacts);

        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());
        List<RemoteRepository> remoteRepositories = RepositoryUtils.toRepos(buildingRequest.getRemoteRepositories());

        Artifact artifact = factory.createArtifact(
                "io.jenkins.tools.maven", "jellydoc-maven-plugin", pluginVersion, null, "maven-plugin");
        ArtifactRequest request = new ArtifactRequest(RepositoryUtils.toArtifact(artifact), remoteRepositories, "");
        Artifact self;
        try {
            self = RepositoryUtils.toArtifact(repositorySystem
                    .resolveArtifact(buildingRequest.getRepositorySession(), request)
                    .getArtifact());
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve plugin from within itself", e);
        }
        docletPath.createPathElement().setLocation(self.getFile());
        d.setPath(docletPath);

        // debug support
        //        javadoc.createArg().setLine("-J-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:8000");

        javadoc.execute();

        generateSchema();
    }

    public void generateSchema() throws MojoExecutionException {
        try {
            getLog().info("Generating XML Schema");
            TransformerFactory tf = TransformerFactory.newInstance();
            Templates templates = tf.newTemplates(new StreamSource(
                    JellydocMojo.class.getResource("xsdgen.xsl").toExternalForm()));
            File source = new File(project.getBasedir(), "target/taglib.xml");
            for (Node node : new SAXReader().read(source).selectNodes("/tags/library")) {
                Element lib = (Element) node;
                String prefix = lib.attributeValue("prefix");

                File schema = new File(project.getBasedir(), "target/taglib-" + prefix + ".xsd");

                lib.getParent().remove(lib); // make it on its own
                DefaultDocument newDoc = new DefaultDocument();
                newDoc.setRootElement(lib);

                templates
                        .newTransformer()
                        .transform(new DocumentSource(newDoc), new StreamResult(new FileOutputStream(schema)));

                helper.attachArtifact(project, "xsd", "taglib-" + prefix, schema);
            }
        } catch (TransformerException | FileNotFoundException | DocumentException e) {
            throw new MojoExecutionException("Failed to generate schema", e);
        }
    }

    private Path makePath(Project p, Collection<Artifact> artifacts) {
        Path docletPath = new Path(p);
        for (Artifact artifact : artifacts) {
            docletPath.createPathElement().setLocation(artifact.getFile());
        }
        return docletPath;
    }

    private File targetDir() {
        return new File(project.getBasedir(), "target");
    }

    private void setParam(Javadoc.DocletInfo d, String name, String value) {
        Javadoc.DocletParam dp = d.createParam();
        dp.setName(name);
        dp.setValue(value);
    }

    //    private Path makePath(Project p, List list) {
    //        Path src = new Path(p);
    //        for (Object dir : list)
    //            src.createPathElement().setLocation(new File(dir.toString()));
    //        return src;
    //    }

    /**
     * Generate a report.
     *
     * @param sink The sink to use for the generation.
     * @param locale The desired locale in which to generate the report; could be null.
     * @throws MavenReportException if any error occurs
     * @deprecated use {@link #generate(Sink, SinkFactory, Locale)} instead.
     */
    @Deprecated
    public void generate(Sink sink, Locale locale) throws MavenReportException {
        generate(sink, null, locale);
    }

    /**
     * This method is called when the report generation is invoked by maven-site-plugin.
     *
     * @param sink The sink to use for the generation.
     * @param sinkFactory The sink factory to use for the generation; could be null.
     * @param locale The desired locale in which to generate the report; could be null.
     * @throws MavenReportException if any error occurs
     */
    @Override
    public void generate(Sink sink, SinkFactory sinkFactory, Locale locale) throws MavenReportException {
        try {
            execute();
            File libFile = new File(targetDir(), "taglib.xml");
            if (libFile.exists()) {
                new ReferenceRenderer(sink, libFile.toURI().toURL()).render();
                FileUtils.copyDirectory(targetDir(), new File(targetDir(), "site"), "taglib-*.xsd", null);
            }
        } catch (AbstractMojoExecutionException | DocumentException | IOException e) {
            throw new MavenReportException("Failed to generate report", e);
        }
    }

    @Override
    public String getOutputName() {
        return "jelly-taglib-ref";
    }

    @Override
    public String getName(Locale locale) {
        return "Jelly taglib reference";
    }

    @Override
    public String getCategoryName() {
        return CATEGORY_PROJECT_REPORTS;
    }

    @Override
    public String getDescription(Locale locale) {
        return "Jelly taglib reference";
    }

    @Override
    public void setReportOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    @Override
    public File getReportOutputDirectory() {
        return this.outputDirectory;
    }

    @Override
    public boolean isExternalReport() {
        return false;
    }

    @Override
    public boolean canGenerateReport() {
        // TODO: check if the current project has any source files
        return true;
    }
}
