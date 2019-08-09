package com.eastwood.tools.plugins.publish.extension

class Publication {

    String name

    String groupId
    String artifactId
    String version

    Publication(String name) {
        this.name = name
    }

    void groupId(String groupId) {
        this.groupId = groupId
    }

    void artifactId(String artifactId) {
        this.artifactId = artifactId
    }

    void version(String version) {
        this.version = version
    }

}