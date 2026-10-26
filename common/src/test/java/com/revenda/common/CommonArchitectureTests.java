package com.revenda.common;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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

    @ArchTest
    static final ArchRule commonKnowsNoService = noClasses()
            .that().resideInAPackage("com.revenda.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.revenda.vehicles..", "com.revenda.customers..", "com.revenda.sales..", "com.revenda.payments..")
            .because("o common é infraestrutura compartilhada, sem regra de negócio de nenhum serviço");
}
