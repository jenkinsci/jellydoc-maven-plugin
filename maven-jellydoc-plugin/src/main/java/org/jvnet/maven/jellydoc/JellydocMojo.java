package org.jvnet.maven.jellydoc;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.AbstractMojoExecutionException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Javadoc;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.codehaus.doxia.sink.Sink;
import org.codehaus.plexus.util.FileUtils;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.tree.DefaultDocument;
import org.dom4j.io.DocumentSource;
import org.dom4j.io.SAXReader;

import javax.xml.transform.Templates;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Generates jellydoc XML and other artifacts from there.
 *
 * @author Kohsuke Kawaguchi
 * @goal jellydoc
 * @phase generate-sources
 * @requiresDependencyResolution compile
 */
@SuppressWarnings({"unchecked"})
public class JellydocMojo extends AbstractMojo implements MavenReport {
    /**
     * The Maven Project Object
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    public MavenProject project;

    /**
     * The plugin dependencies.
     *
     * @parameter expression="${plugin.artifacts}"
     * @required
     * @readonly
     */
    public List<Artifact> pluginArtifacts;

    /**
     * Version of this plugin.
     *
     * @parameter expression="${plugin.version}"
     * @required
     * @readonly
     */
    public String pluginVersion;

    /**
     * Factory for creating artifact objects
     *
     * @component
     */
    public ArtifactFactory factory;

    /**
     * Used for resolving artifacts
     *
     * @component
     */
    public ArtifactResolver resolver;

    /**
     * The local repository where the artifacts are located.
     *
     * @parameter expression="${localRepository}"
     */
    public ArtifactRepository localRepository;

    /**
     * @component
     */
    public MavenProjectHelper helper;

    private File outputDirectory;

    public void execute() throws MojoExecutionException, MojoFailureException {
        Project p = new Project();

        DefaultLogger logger = new DefaultLogger();
        logger.setErrorPrintStream(System.err);
        logger.setOutputPrintStream(System.out);
        logger.setMessageOutputLevel( getLog().isDebugEnabled() ? Project.MSG_DEBUG : Project.MSG_INFO );
        p.addBuildListener(logger);

        Javadoc javadoc = new Javadoc();
        javadoc.setTaskName("jellydoc");
        javadoc.setProject(p);

        for (Object dir : project.getCompileSourceRoots()) {
            FileSet fs = new FileSet();
            fs.setProject(p);
            fs.setDir(new File(dir.toString()));
            javadoc.addFileset(fs);
        }
        javadoc.setClasspath(makePath(p,(Collection<Artifact>)project.getArtifacts()));

        Javadoc.DocletInfo d = javadoc.createDoclet();
        d.setProject(p);
        d.setName(TagXMLDoclet.class.getName());
        setParam(d, "-d", targetDir().getAbsolutePath());

        Path docletPath = makePath(p, pluginArtifacts);
        try {
            Artifact self = factory.createArtifact("org.jvnet.maven-jellydoc-plugin", "maven-jellydoc-plugin", pluginVersion, null, "maven-plugin");
            resolver.resolve(self,project.getPluginArtifactRepositories(),localRepository);
            docletPath.createPathElement().setLocation(self.getFile());
        } catch (AbstractArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to resolve plugin from within itself",e);
        }
        d.setPath(docletPath);

        // debug support
//        javadoc.createArg().setLine("-J-Xrunjdwp:transport=dt_socket,server=y,address=8000");

        javadoc.execute();

        generateSchema();
    }

    public void generateSchema() throws MojoExecutionException {
        try {
            getLog().info("Generating XML Schema");
            TransformerFactory tf = TransformerFactory.newInstance();
            Templates templates = tf.newTemplates(new StreamSource(JellydocMojo.class.getResource("xsdgen.xsl").toExternalForm()));
            File source = new File(project.getBasedir(), "target/taglib.xml");
            for(Element lib : (List<Element>)new SAXReader().read(source).selectNodes("/tags/library")) {
                String prefix = lib.attributeValue("prefix");

                File schema = new File(project.getBasedir(), "target/taglib-"+prefix+".xsd");

                lib.getParent().remove(lib); // make it on its own
                DefaultDocument newDoc = new DefaultDocument();
                newDoc.setRootElement(lib);

                templates.newTransformer().transform(
                    new DocumentSource(newDoc),
                    new StreamResult(new FileOutputStream(schema)));

                helper.attachArtifact(project,"xsd","taglib-"+prefix,schema);
            }
        } catch (TransformerException e) {
            throw new MojoExecutionException("Failed to generate schema",e);
        } catch (DocumentException e) {
            throw new MojoExecutionException("Failed to generate schema",e);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Failed to generate schema",e);
        }
    }

    private Path makePath(Project p, Collection<Artifact> artifacts) {
        Path docletPath = new Path(p);
        for (Artifact artifact : artifacts)
            docletPath.createPathElement().setLocation(artifact.getFile());
        return docletPath;
    }

    private File targetDir() {
        return new File(project.getBasedir(),"target");
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

    public void generate(Sink sink, Locale locale) throws MavenReportException {
        try {
            execute();
            new ReferenceRenderer(sink,new File(targetDir(),"taglib.xml").toURI().toURL()).render();
            FileUtils.copyDirectory(targetDir(),new File(targetDir(),"site"),"taglib-*.xsd",null);
        } catch (AbstractMojoExecutionException e) {
            throw new MavenReportException("Failed to generate report",e);
        } catch (MalformedURLException e) {
            throw new MavenReportException("Failed to generate report",e);
        } catch (DocumentException e) {
            throw new MavenReportException("Failed to generate report",e);
        } catch (IOException e) {
            throw new MavenReportException("Failed to generate report",e);
        }
    }

    public String getOutputName() {
        return "jelly-taglib-ref";
    }

    public String getName(Locale locale) {
        return "Jelly taglib reference";
    }

    public String getCategoryName() {
        return CATEGORY_PROJECT_REPORTS;
    }

    public String getDescription(Locale locale) {
        return "Jelly taglib reference";
    }

    public void setReportOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public File getReportOutputDirectory() {
        return this.outputDirectory;
    }

    public boolean isExternalReport() {
        return false;
    }

    public boolean canGenerateReport() {
        // TODO: check if the current project has any source files
        return true;
    }
}
