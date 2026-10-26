package com.revenda.sales;

import com.revenda.common.test.ServiceArchitectureRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

@AnalyzeClasses(packagesOf = SalesApplication.class, importOptions = ImportOption.DoNotIncludeTests.class)
class SalesArchitectureTests {

    @ArchTest
    static final ArchTests rules = ArchTests.in(ServiceArchitectureRules.class);
}
