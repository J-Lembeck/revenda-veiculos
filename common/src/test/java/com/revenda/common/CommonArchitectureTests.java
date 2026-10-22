package com.revenda.common;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.revenda.common", importOptions = ImportOption.DoNotIncludeTests.class)
class CommonArchitectureTests {

    @ArchTest
    static final ArchRule moneyAndErrorsArePlainJavaForTheDomain = classes()
            .that().resideInAnyPackage("com.revenda.common.money..", "com.revenda.common.error")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "lombok..", "com.revenda.common.money..", "com.revenda.common.error");
}
