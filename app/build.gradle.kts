plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.monshop.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.monshop.app"
        minSdk = 26
        targetSdk = 34
        // Numéroté par la compilation : deux APK différents ne doivent jamais porter
        // le même numéro, sinon plus rien ne distingue les versions — ni pour
        // l'utilisateur, ni pour Android au moment d'installer par-dessus.
        val numeroCompilation = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionCode = numeroCompilation
        versionName = "1.0.$numeroCompilation"
    }

    // Clé de publication fournie par l'environnement (secrets GitHub Actions). Absente,
    // la compilation retombe sur la clé de debug : le dépôt reste compilable par
    // quiconque le clone, sans qu'aucune clé privée n'y soit jamais écrite.
    val clePublication = System.getenv("MOONSHOP_KEYSTORE_FILE")

    // Dit à voix haute ce qui se passerait sans bruit sinon : un APK de publication
    // signé par la clé de debug s'installe très bien, et rien à l'écran ne le
    // distingue — alors que n'importe qui pourrait en forger la mise à jour.
    if (clePublication == null) {
        logger.warn(
            "Moonshop : aucune clé de publication (MOONSHOP_KEYSTORE_FILE). " +
                "L'APK sera signé par la clé de debug, qui est publique : " +
                "utilisable pour essayer, jamais pour diffuser."
        )
    }

    signingConfigs {
        if (clePublication != null) {
            create("publication") {
                storeFile = file(clePublication)
                storePassword = System.getenv("MOONSHOP_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MOONSHOP_KEY_ALIAS")
                keyPassword = System.getenv("MOONSHOP_KEY_PASSWORD")
            }
        }
        getByName("debug") {
            // Clé fixe commitée dans le repo (app/debug.keystore), au lieu de la clé
            // aléatoire que chaque runner GitHub Actions générerait sinon à chaque build.
            // Sans ça, Android refuse d'installer une nouvelle version par-dessus
            // l'ancienne ("app not installed") car les signatures ne correspondent pas.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // Pas de rétrécissement : l'appli tient déjà en 15 Mo, et R8 n'apporterait
            // ici qu'un risque de casse silencieuse sur les accès par réflexion.
            isMinifyEnabled = false
            // Explicite plutôt que par défaut : c'est la ligne qui empêche qu'un APK
            // distribué laisse lire ses données privées par un simple câble USB.
            isDebuggable = false
            signingConfig = signingConfigs.getByName(
                if (clePublication != null) "publication" else "debug"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui:1.6.7")
    implementation("androidx.compose.material3:material3:1.2.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
    implementation("androidx.compose.ui:ui-tooling-preview:1.6.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
}
