package com.eastwood.tools.plugins.publish

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.LibraryVariant
import com.eastwood.tools.plugins.publish.extension.AarMavenPublishExtension
import com.eastwood.tools.plugins.publish.extension.Publication
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar

class AarMavenPublishPlugin implements Plugin<Project> {

    static Action<? super RepositoryHandler> globalRepositories

    Project project

    void apply(Project project) {

        AarMavenPublishExtension publishExtension = project.extensions.create('aarMavenPublish', AarMavenPublishExtension, project)

        if (project == project.rootProject) {
            project.afterEvaluate {
                globalRepositories = publishExtension.repositories
            }
            return
        }

        project.plugins.apply('maven-publish')
        this.project = project

        project.afterEvaluate {
            project.plugins.all {
                if (it instanceof LibraryPlugin) {

                    def publishing = project.extensions.getByName('publishing')
                    if (globalRepositories != null) {
                        publishing.repositories globalRepositories
                    }
                    if (publishExtension.repositories != null) {
                        publishing.repositories publishExtension.repositories
                    }

                    project.extensions.configure(LibraryExtension, new Action<LibraryExtension>() {
                        @Override
                        void execute(LibraryExtension libraryExtension) {
                            libraryExtension.libraryVariants.all { LibraryVariant libraryVariant ->
                                Publication publication = publishExtension.publications.findByName(libraryVariant.name)
                                if (publication != null) {
                                    createPublishTask(libraryVariant, publication)
                                    publishExtension.publications.remove(publication)
                                }
                            }

                            if (publishExtension.publications.size() > 0) {
                                String names = publishExtension.publications.getNames().join(',')
                                throw new GradleException("Library Variant with name '" + names + "' not exist.")
                            }
                        }
                    })
                }
            }
        }
    }

    void createPublishTask(LibraryVariant libraryVariant, Publication publication) {
        List<String> productFlavors = new ArrayList<>()
        libraryVariant.productFlavors.each {
            productFlavors.add(it.name)
        }

        File aarOutputFile
        def bundleAarTask = project.tasks.getByName('bundle' + libraryVariant.name.capitalize() + 'Aar')
        bundleAarTask.outputs.files.each {
            if(it.name.endsWith('.aar')) {
                aarOutputFile = it.canonicalFile
            }
        }
        if(aarOutputFile == null) {
            throw new GradleException("Failure to find aar output file.")
        }

        def bundleAarSourceTask = project.tasks.create('bundle' + libraryVariant.name.capitalize() + 'AarSource', Jar)
        bundleAarSourceTask.dependsOn bundleAarTask
        bundleAarSourceTask.archiveName = aarOutputFile.name.replace('.aar', '-source.jar')
        libraryVariant.sourceSets.each {
            List<String> sourcePaths = new ArrayList<>()
            it.javaDirectories.each {
                sourcePaths.add(it.absolutePath)
            }
            bundleAarSourceTask.from(sourcePaths)
        }
        File aarSourceOutputFile = bundleAarSourceTask.archivePath

        def publicationName = 'Aar[' + libraryVariant.name + ']'
        String publishTaskNamePrefix = "publish${publicationName}PublicationTo"
        project.tasks.whenTaskAdded {
            if (it.name.startsWith(publishTaskNamePrefix)) {
                it.dependsOn bundleAarTask, bundleAarSourceTask
            }
        }

        createPublishingPublication(libraryVariant, publication, aarOutputFile, aarSourceOutputFile)
    }

    void createPublishingPublication(LibraryVariant libraryVariant, Publication publication, File aarOutputFile, File aarSourceOutputFile) {
        def publishing = project.extensions.getByName('publishing')
        MavenPublication mavenPublication = publishing.publications.maybeCreate('Aar[' + libraryVariant.name + ']', MavenPublication)
        mavenPublication.groupId = publication.groupId
        mavenPublication.artifactId = publication.artifactId
        mavenPublication.version = publication.version
        mavenPublication.pom.packaging = 'aar'

        mavenPublication.artifact source: aarOutputFile
        mavenPublication.artifact source: aarSourceOutputFile, classifier: 'sources'

        Set<ResolvedDependency> resolvedDependencies = libraryVariant.compileConfiguration.resolvedConfiguration.firstLevelModuleDependencies
        if (resolvedDependencies.size() > 0) {
            mavenPublication.pom.withXml {
                def dependenciesNode = asNode().appendNode('dependencies')
                resolvedDependencies.each {
                    if (it.moduleVersion != 'unspecified') {
                        def dependencyNode = dependenciesNode.appendNode('dependency')
                        dependencyNode.appendNode('groupId', it.moduleGroup)
                        dependencyNode.appendNode('artifactId', it.moduleName)
                        dependencyNode.appendNode('version', it.moduleVersion)
                        dependencyNode.appendNode('scope', 'compile')
                    }
                }
            }
        }

    }

}