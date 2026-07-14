include(":guru")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        val gprUser = System.getenv("GPR_USER")
        val gprToken = System.getenv("GPR_TOKEN")
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-androidx")
            credentials {
                username = gprUser
                password = gprToken
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/sesl-material-components-android")
            credentials {
                username = gprUser
                password = gprToken
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/tribalfs/oneui-design")
            credentials {
                username = gprUser
                password = gprToken
            }
        }
    }
}