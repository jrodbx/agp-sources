pluginManagement {
  repositories {
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
  }
  versionCatalogs {
    create("alpha") {
      from(files("gradle/alpha.versions.toml"))
    }
    create("stable") {
      from(files("gradle/stable.versions.toml"))
    }
  }
}
