plugins { `kotlin-dsl` }

dependencies { implementation(libs.build.spotless) }

afterEvaluate {
    tasks.withType<JavaCompile>().configureEach {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
    }
}

gradlePlugin {
    plugins {
        register("spotless") {
            id = "foundation.e.bliss.spotless"
            implementationClass = "foundation.e.bliss.SpotlessPlugin"
        }
        register("githooks") {
            id = "foundation.e.bliss.githooks"
            implementationClass = "foundation.e.bliss.GitHooksPlugin"
        }
    }
}
