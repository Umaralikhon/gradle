/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner;

import java.io.File;

/**
 * A Gradle distribution installed in the filesystem, e.g. <code>new File(System.getProperty("user.home"), "./gradle/wrapper/dist/gradle-2.8-bin")</code>.
 */
public final class InstalledGradleDistribution implements GradleDistribution<File> {
    private final File gradleHome;

    public InstalledGradleDistribution(File gradleHome) {
        this.gradleHome = gradleHome;
    }

    public File getHandle() {
        return gradleHome;
    }
}
