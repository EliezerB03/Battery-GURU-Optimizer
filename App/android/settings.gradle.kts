include(":guru")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        listOf("sesl-material-components-android", "sesl-androidx").forEach { repo ->
            maven {
                url = uri("https://maven.pkg.github.com/tribalfs/$repo")
                credentials {
                    username = System.getenv("GPR_USER")
                    password = System.getenv("GPR_TOKEN")
                }
            }
        }
    }
}