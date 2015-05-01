/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.reporting.components

import org.gradle.api.Transformer
import org.gradle.internal.SystemProperties
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.fixtures.AvailableToolChains
import org.gradle.nativeplatform.fixtures.NativePlatformsTestFixture

class ComponentReportOutputFormatter implements Transformer<String, String> {

    @Override
    String transform(String original) {
         return original
                .replace("\n", SystemProperties.instance.lineSeparator)
                .replaceAll("(\\w+/)+\\w+") { it[0].replace('/', File.separator) }
    }
}
