/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/
package com.github.codegen;

import fr.inria.gforge.spoon.Spoon;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.compiler.CompilerMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.DefaultPlexusConfiguration;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import org.twdata.maven.mojoexecutor.PlexusConfigurationUtils;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Generate code from project source
 */
@Mojo(name = "codegen",
        defaultPhase = LifecyclePhase.PROCESS_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class CodeGenMojo extends AbstractMojo {

    /**
     * Location of the output.
     */
    @Parameter(defaultValue = "${project.build.directory}/codegen-classes", required = true)
    private File outputDirectory;

    /**
     * Location of the generated classes
     */
    @Parameter(defaultValue = "${project.build.directory}/generated-sources/codegen", required = true)
    private File generationDirectory;

    /**
     * Location of the code generation source.
     */
    @Parameter(defaultValue = "${project.basedir}/src/codegen/java", required = true)
    private File codegenSourceDirectory;

    @Parameter
    private List<Resource> codegenResources;

    /**
     * The source directories containing the sources to be compiled.
     */
    @Parameter(defaultValue = "${project.build.sourceDirectory}", readonly = true, required = true)
    private String sourceDirectory;

    @Parameter
    private CompilerMojo compilation;

    @Parameter
    private Spoon spoon;

    @Component
    private MavenProject mavenProject;

    @Component
    private MavenSession mavenSession;

    @Component
    private MojoExecution mojoExecution;

    @Component
    private BuildPluginManager pluginManager;

    @Parameter(defaultValue = "${plugin.artifacts}", required = true, readonly = true)
    private List<Artifact> pluginArtifacts;

    @Override
    public void execute() throws MojoExecutionException {
        copyResources();
        compile();
        process();
        removePath(mavenProject.getCompileSourceRoots(), sourceDirectory.toString());
        mavenProject.addCompileSourceRoot(generationDirectory.toString());
    }

    private void removePath( List<String> paths, String path )
    {
        if ( path != null )
        {
            path = path.trim();
            if ( path.length() > 0 )
            {
                File file = new File( path );
                if ( file.isAbsolute() )
                {
                    path = file.getAbsolutePath();
                }
                else
                {
                    path = new File( mavenProject.getBasedir(), path ).getAbsolutePath();
                }
                paths.remove( path );
            }
        }
    }


    private void process() throws MojoExecutionException {
        // Clone spoon configuration
        Xpp3Dom pluginConfiguration = new Xpp3Dom("configuration");
        Xpp3Dom spoonConfiguration = mojoExecution.getConfiguration().getChild("spoon");
        if (spoonConfiguration != null) {
            for (Xpp3Dom child : spoonConfiguration.getChildren()) {
                pluginConfiguration.addChild(child);
            }
        }

        for (int i = 0; i < pluginConfiguration.getChildCount(); ++i) {
            if ("srcFolder".equals(pluginConfiguration.getChild(i).getName())) {
                pluginConfiguration.removeChild(i--);
            }
        }

        Xpp3Dom srcFolder = new Xpp3Dom("srcFolder");
        srcFolder.setValue(this.sourceDirectory.toString());
        pluginConfiguration.addChild(srcFolder);

        Xpp3Dom outFolder = new Xpp3Dom("outFolder");
        outFolder.setValue(this.generationDirectory.toString());
        pluginConfiguration.addChild(outFolder);


        MavenSession spoonMavenSession = mavenSession.clone();
        MavenProject spoonMavenProject = mavenProject.clone();
        Set<Artifact> artifacts = mavenProject.getArtifacts();
        artifacts.add(getCodegenArtifact(mavenProject));
        mavenProject.setArtifacts(artifacts);
        spoonMavenSession.setCurrentProject(spoonMavenProject);

        Plugin spoonMavenPlugin = getSpoonMavenPlugin();
        String goal = MojoExecutor.goal("generate");
        MojoExecutor.ExecutionEnvironment environment = MojoExecutor.executionEnvironment(
                spoonMavenProject,
                spoonMavenSession,
                pluginManager
        );
        new File(spoonMavenProject.getBuild().getDirectory() + File.separator + "spoon-maven-plugin" + File.separator + "result-spoon.xml").delete();
        MojoExecutor.executeMojo(
                spoonMavenPlugin,
                goal,
                pluginConfiguration,
                environment
        );
    }

    private Artifact getCodegenArtifact(MavenProject mavenProject) {
        Artifact baseArtifact = mavenProject.getArtifact();
        Artifact artifact = new DefaultArtifact(baseArtifact.getGroupId(), baseArtifact.getArtifactId(), baseArtifact.getVersion(), "system", baseArtifact.getType(), "codegen", new ArtifactHandler() {
            public String getClassifier() {
                return null;
            }

            public String getDirectory() {
                return null;
            }

            public String getExtension() {
                return "jar";
            }

            public String getLanguage() {
                return "none";
            }

            public String getPackaging() {
                return "maven-plugin";
            }

            public boolean isAddedToClasspath() {
                return true;
            }

            public boolean isIncludesDependencies() {
                return false;
            }
        });
        artifact.setFile(outputDirectory);
        artifact.setResolved(true);
        return artifact;
    }

    private void compile() throws MojoExecutionException {
        // Clone compile configuration
        Xpp3Dom pluginConfiguration = new Xpp3Dom("configuration");
        Xpp3Dom compilationConfiguration = mojoExecution.getConfiguration().getChild("compilation");
        if (compilationConfiguration != null) {
            for (Xpp3Dom child : compilationConfiguration.getChildren()) {
                pluginConfiguration.addChild(child);
            }
        }
        Xpp3Dom outputDirectory = new Xpp3Dom("outputDirectory");
        outputDirectory.setValue(this.outputDirectory.toString());
        pluginConfiguration.addChild(outputDirectory);

        MavenSession compileMavenSession = mavenSession.clone();
        MavenProject compileMavenProject = mavenProject.clone();
        compileMavenProject.getCompileSourceRoots().clear();
        compileMavenProject.getCompileSourceRoots().add(codegenSourceDirectory.toString());
        compileMavenSession.setCurrentProject(compileMavenProject);

        Plugin mavenCompilerPlugin = getMavenCompilerPlugin();
        String goal = MojoExecutor.goal("compile");
        MojoExecutor.ExecutionEnvironment environment = MojoExecutor.executionEnvironment(
                compileMavenProject,
                compileMavenSession,
                pluginManager
        );
        MojoExecutor.executeMojo(
                mavenCompilerPlugin,
                goal,
                pluginConfiguration,
                environment

        );
    }

    private void copyResources() throws MojoExecutionException {
        if (codegenResources == null) {
            codegenResources = new LinkedList<Resource>();
            Resource resource = new Resource();
            resource.setDirectory("${project.basedir}/src/codegen/resources");
            codegenResources.add(resource);
        }
        PlexusConfiguration resourcesConfiguration = new DefaultPlexusConfiguration("resources");
        for (Resource resource : codegenResources) {
            resourcesConfiguration.addChild(configurationFromResource(resource));
        }
        PlexusConfiguration pluginConfiguration = new DefaultPlexusConfiguration("configuration");
        pluginConfiguration.addChild("outputDirectory", outputDirectory.toString());
        pluginConfiguration.addChild(resourcesConfiguration);
        MojoExecutor.executeMojo(
                getMavenResourcesPlugin(),
                MojoExecutor.goal("copy-resources"),
                PlexusConfigurationUtils.toXpp3Dom(pluginConfiguration),
                MojoExecutor.executionEnvironment(
                        mavenProject,
                        mavenSession,
                        pluginManager
                )
        );
    }

    private Plugin getSpoonMavenPlugin() throws MojoExecutionException {
        for (Artifact artifact : pluginArtifacts) {
            if (artifact.getGroupId().equals("fr.inria.gforge.spoon") && artifact.getArtifactId().equals("spoon-maven-plugin")) {
                return createPluginFromArtifact(artifact);
            }
        }
        throw new MojoExecutionException("Cant find resource plugin");
    }

    private Plugin getMavenCompilerPlugin() throws MojoExecutionException {
        for (Artifact artifact : pluginArtifacts) {
            if (artifact.getGroupId().equals("org.apache.maven.plugins") && artifact.getArtifactId().equals("maven-compiler-plugin")) {
                return createPluginFromArtifact(artifact);
            }
        }
        throw new MojoExecutionException("Cant find resource plugin");
    }

    private Plugin getMavenResourcesPlugin() throws MojoExecutionException {
        for (Artifact artifact : pluginArtifacts) {
            if (artifact.getGroupId().equals("org.apache.maven.plugins") && artifact.getArtifactId().equals("maven-resources-plugin")) {
                return createPluginFromArtifact(artifact);
            }
        }
        throw new MojoExecutionException("Cant find resource plugin");
    }

    private static Plugin createPluginFromArtifact(Artifact artifact) {
        Plugin plugin = new Plugin();
        plugin.setArtifactId(artifact.getArtifactId());
        plugin.setGroupId(artifact.getGroupId());
        plugin.setVersion(artifact.getVersion());
        return plugin;
    }

    private static PlexusConfiguration configurationFromResource(Resource resource) {
        PlexusConfiguration resourceConfiguration = new DefaultPlexusConfiguration("resource");
        resourceConfiguration.addChild("directory", resource.getDirectory());
        resourceConfiguration.addChild("targetPath", resource.getTargetPath());
        resourceConfiguration.addChild("filtering", resource.getFiltering());
        return resourceConfiguration;
    }
}
