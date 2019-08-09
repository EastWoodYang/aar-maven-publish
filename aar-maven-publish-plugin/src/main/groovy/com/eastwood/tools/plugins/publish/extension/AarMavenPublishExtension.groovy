package com.eastwood.tools.plugins.publish.extension

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler

class AarMavenPublishExtension {

    Project project

    Action<? super RepositoryHandler> repositories
    NamedDomainObjectContainer<Publication> publications


    AarMavenPublishExtension(Project project) {
        this.project = project
    }

    void repositories(Action<? super RepositoryHandler> repositories) {
        this.repositories = repositories
    }

    def publications(final Closure closure) {
        publications = project.container(Publication)
        publications.configure(closure)
    }

}