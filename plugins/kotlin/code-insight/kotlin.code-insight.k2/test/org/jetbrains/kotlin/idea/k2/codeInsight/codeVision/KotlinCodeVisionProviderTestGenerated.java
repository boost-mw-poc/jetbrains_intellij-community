// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeInsight.codeVision;

import com.intellij.testFramework.TestDataPath;
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode;
import org.jetbrains.kotlin.idea.base.test.TestRoot;
import org.jetbrains.kotlin.idea.test.JUnit3RunnerWithInners;
import org.jetbrains.kotlin.idea.test.KotlinTestUtils;
import org.jetbrains.kotlin.test.TestMetadata;
import org.junit.runner.RunWith;
import org.jetbrains.kotlin.idea.codeInsight.codevision.AbstractKotlinCodeVisionProviderTest;

/**
 * This class is generated by {@link org.jetbrains.kotlin.testGenerator.generator.TestGenerator}.
 * DO NOT MODIFY MANUALLY.
 */
@SuppressWarnings("all")
@TestRoot("code-insight/kotlin.code-insight.k2")
@TestDataPath("$CONTENT_ROOT")
@RunWith(JUnit3RunnerWithInners.class)
@TestMetadata("../../idea/tests/testData/codeInsight/codeVision")
public class KotlinCodeVisionProviderTestGenerated extends AbstractKotlinCodeVisionProviderTest {
    @java.lang.Override
    @org.jetbrains.annotations.NotNull
    public final KotlinPluginMode getPluginMode() {
        return KotlinPluginMode.K2;
    }

    private void runTest(String testDataFilePath) throws Exception {
        KotlinTestUtils.runTest(this::doTest, this, testDataFilePath);
    }

    @TestMetadata("ClassFunctionOverrides.kt")
    public void testClassFunctionOverrides() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/ClassFunctionOverrides.kt");
    }

    @TestMetadata("ClassInheritors.kt")
    public void testClassInheritors() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/ClassInheritors.kt");
    }

    @TestMetadata("ClassMethodUsages.kt")
    public void testClassMethodUsages() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/ClassMethodUsages.kt");
    }

    @TestMetadata("ClassPropertiesOverrides.kt")
    public void testClassPropertiesOverrides() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/ClassPropertiesOverrides.kt");
    }

    @TestMetadata("ClassPropertyUsages.kt")
    public void testClassPropertyUsages() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/ClassPropertyUsages.kt");
    }

    @TestMetadata("ClassUsages.kt")
    public void testClassUsages() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/ClassUsages.kt");
    }

    @TestMetadata("EnumClassUsages.kt")
    public void testEnumClassUsages() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/EnumClassUsages.kt");
    }

    @TestMetadata("GlobalFunctionUsages.kt")
    public void testGlobalFunctionUsages() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/GlobalFunctionUsages.kt");
    }

    @TestMetadata("InterfaceAbstractMethodImplementation.kt")
    public void testInterfaceAbstractMethodImplementation() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/InterfaceAbstractMethodImplementation.kt");
    }

    @TestMetadata("InterfaceImplementations.kt")
    public void testInterfaceImplementations() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/InterfaceImplementations.kt");
    }

    @TestMetadata("InterfaceMethodUsages.kt")
    public void testInterfaceMethodUsages() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/InterfaceMethodUsages.kt");
    }

    @TestMetadata("InterfaceMethodsOverrides.kt")
    public void testInterfaceMethodsOverrides() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/InterfaceMethodsOverrides.kt");
    }

    @TestMetadata("InterfacePropertiesOverrides.kt")
    public void testInterfacePropertiesOverrides() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/InterfacePropertiesOverrides.kt");
    }

    @TestMetadata("InterfacePropertyUsages.kt")
    public void testInterfacePropertyUsages() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/InterfacePropertyUsages.kt");
    }

    @TestMetadata("InterfaceUsages.kt")
    public void testInterfaceUsages() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/InterfaceUsages.kt");
    }

    @TestMetadata("TooManyUsagesAndInheritors.kt")
    public void testTooManyUsagesAndInheritors() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/TooManyUsagesAndInheritors.kt");
    }

    @TestMetadata("UsagesAndInheritanceTogether.kt")
    public void testUsagesAndInheritanceTogether() throws Exception {
        runTest("../../idea/tests/testData/codeInsight/codeVision/UsagesAndInheritanceTogether.kt");
    }
}
