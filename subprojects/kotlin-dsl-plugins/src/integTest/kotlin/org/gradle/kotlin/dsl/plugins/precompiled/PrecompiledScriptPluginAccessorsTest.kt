/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.plugins.precompiled

import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.PluginManager

import org.gradle.kotlin.dsl.fixtures.FoldersDsl
import org.gradle.kotlin.dsl.fixtures.bytecode.InternalName
import org.gradle.kotlin.dsl.fixtures.bytecode.RETURN
import org.gradle.kotlin.dsl.fixtures.bytecode.internalName
import org.gradle.kotlin.dsl.fixtures.bytecode.publicClass
import org.gradle.kotlin.dsl.fixtures.bytecode.publicDefaultConstructor
import org.gradle.kotlin.dsl.fixtures.bytecode.publicMethod
import org.gradle.kotlin.dsl.fixtures.normalisedPath
import org.gradle.kotlin.dsl.fixtures.pluginDescriptorEntryFor
import org.gradle.kotlin.dsl.support.zipTo

import org.gradle.test.fixtures.file.LeaksFileHandles

import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.File


class PrecompiledScriptPluginAccessorsTest : AbstractPrecompiledScriptPluginTest() {

    @Test
    fun `can use type-safe accessors with same name but different meaning in sibling plugins`() {

        withProjectRoot(newDir("external-plugins")) {
            withDefaultSettings()
            withKotlinDslPlugin()
            withFolders {
                "src/main/kotlin" {
                    "extensions" {
                        withFile("Extensions.kt", """
                            open class App { var name: String = "app" }
                            open class Lib { var name: String = "lib" }
                        """)
                    }
                    withFile("external-app.gradle.kts", """
                        extensions.create("external", App::class)
                    """)
                    withFile("external-lib.gradle.kts", """
                        extensions.create("external", Lib::class)
                    """)
                }
            }
            build("assemble")
        }

        val externalPlugins = existing("external-plugins/build/libs/external-plugins.jar")

        withFolders {
            "buildSrc" {
                withDefaultSettingsIn(relativePath)
                withKotlinDslPlugin().appendText("""
                    dependencies {
                        implementation(files("${externalPlugins.normalisedPath}"))
                    }
                """)

                withFile("src/main/kotlin/local-app.gradle.kts", """
                    plugins { `external-app` }
                    println("*using " + external.name + " from local-app in " + project.name + "*")
                """)

                withFile("src/main/kotlin/local-lib.gradle.kts", """
                    plugins { `external-lib` }
                    println("*using " + external.name + " from local-lib in " + project.name + "*")
                """)
            }
        }

        withDefaultSettings().appendText("""
            include("foo")
            include("bar")
        """)

        withFolders {
            "foo" {
                withFile("build.gradle.kts", """
                    plugins { `local-app` }
                """)
            }
            "bar" {
                withFile("build.gradle.kts", """
                    plugins { `local-lib` }
                """)
            }
        }

        assertThat(
            build("tasks").output,
            allOf(
                containsString("*using app from local-app in foo*"),
                containsString("*using lib from local-lib in bar*")
            )
        )
    }

    @Test
    fun `can use core plugin spec builders`() {

        givenPrecompiledKotlinScript("java-project.gradle.kts", """

            plugins {
                java
            }

        """)

        val (project, pluginManager) = projectAndPluginManagerMocks()

        instantiatePrecompiledScriptOf(
            project,
            "Java_project_gradle"
        )

        inOrder(pluginManager) {
            verify(pluginManager).apply("org.gradle.java")
            verifyNoMoreInteractions()
        }
    }

    @LeaksFileHandles("TDB")
    @Test
    fun `can use plugin spec builders for plugins in the implementation classpath`() {

        // given:
        val pluginId = "my.plugin"
        val pluginJar = jarForPlugin(pluginId, "MyPlugin")

        withPrecompiledScriptApplying(pluginId, pluginJar)
        compileKotlin()

        val (project, pluginManager) = projectAndPluginManagerMocks()

        instantiatePrecompiledScriptOf(
            project,
            "Plugin_gradle"
        )

        inOrder(pluginManager) {
            verify(pluginManager).apply(pluginId)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `plugin application errors are propagated`() {

        // given:
        val pluginId = "invalid.plugin"
        val pluginJar = jarWithInvalidPlugin(pluginId, "InvalidPlugin")

        withPrecompiledScriptApplying(pluginId, pluginJar)

        buildAndFail("classes").assertHasDescription(
            "An exception occurred applying plugin request [id: '$pluginId']"
        ).assertHasCause(
            "'InvalidPlugin' is neither a plugin or a rule source and cannot be applied."
        )
    }

    private
    fun withPrecompiledScriptApplying(pluginId: String, pluginJar: File) {

        withKotlinDslPlugin().appendText("""

            dependencies {
                implementation(files("${pluginJar.normalisedPath}"))
            }

        """)

        withPrecompiledKotlinScript("plugin.gradle.kts", """

            plugins { $pluginId }

        """)
    }

    @Test
    fun `can use plugin spec builders in multi-project builds with local and external plugins`() {

        withProjectRoot(newDir("external-plugins")) {
            withFolders {
                "external-foo" {
                    withKotlinDslPlugin()
                    withFile("src/main/kotlin/external-foo.gradle.kts", """
                        println("*external-foo applied*")
                    """)
                }
                "external-bar" {
                    withKotlinDslPlugin()
                    withFile("src/main/kotlin/external-bar.gradle.kts", """
                        println("*external-bar applied*")
                    """)
                }
                withDefaultSettingsIn(relativePath).appendText("""
                    include("external-foo", "external-bar")
                """)
            }
            build("assemble")
        }

        val externalFoo = existing("external-plugins/external-foo/build/libs/external-foo.jar")
        val externalBar = existing("external-plugins/external-bar/build/libs/external-bar.jar")

        withFolders {
            "buildSrc" {
                "local-foo" {
                    withFile("src/main/kotlin/local-foo.gradle.kts", """
                        plugins { `external-foo` }
                    """)
                    withKotlinDslPlugin().appendText("""
                        dependencies {
                            implementation(files("${externalFoo.normalisedPath}"))
                        }
                    """)
                }
                "local-bar" {
                    withFile("src/main/kotlin/local-bar.gradle.kts", """
                        plugins { `external-bar` }
                    """)
                    withKotlinDslPlugin().appendText("""
                        dependencies {
                            implementation(files("${externalBar.normalisedPath}"))
                        }
                    """)
                }
                withDefaultSettingsIn(relativePath).appendText("""
                    include("local-foo", "local-bar")
                """)
                withFile("build.gradle.kts", """
                    dependencies {
                        subprojects.forEach {
                            runtime(project(it.path))
                        }
                    }
                """)
            }
        }
        withBuildScript("""
            plugins {
                `local-foo`
                `local-bar`
            }
        """)

        assertThat(
            build("tasks").output,
            allOf(
                containsString("*external-foo applied*"),
                containsString("*external-bar applied*")
            )
        )
    }

    private
    fun FoldersDsl.withKotlinDslPlugin(): File =
        withKotlinDslPluginIn(relativePath)

    private
    val FoldersDsl.relativePath
        get() = root.relativeTo(projectRoot).path

    private
    fun jarWithInvalidPlugin(id: String, implClass: String): File =
        pluginJarWith(
            pluginDescriptorEntryFor(id, implClass),
            "$implClass.class" to publicClass(InternalName(implClass))
        )

    private
    fun jarForPlugin(id: String, implClass: String): File =
        pluginJarWith(
            pluginDescriptorEntryFor(id, implClass),
            "$implClass.class" to emptyPluginClassNamed(implClass)
        )

    private
    fun emptyPluginClassNamed(implClass: String): ByteArray =
        publicClass(InternalName(implClass), interfaces = listOf(Plugin::class.internalName)) {
            publicDefaultConstructor()
            publicMethod("apply", "(Ljava/lang/Object;)V") {
                RETURN()
            }
        }

    private
    fun pluginJarWith(vararg entries: Pair<String, ByteArray>): File =
        newFile("my.plugin.jar").also { file ->
            zipTo(file, entries.asSequence())
        }

    private
    fun projectAndPluginManagerMocks(): Pair<Project, PluginManager> {
        val pluginManager = mock<PluginManager>()
        val project = mock<Project> {
            on { getPluginManager() } doReturn pluginManager
            on { project } doAnswer { it.mock as Project }
        }
        return Pair(project, pluginManager)
    }
}
