package com.example.wallet;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.example.wallet", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest
    static final ArchRule controllers_do_not_access_persistence_directly = noClasses()
        .that().resideInAPackage("..api..")
        .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domain_does_not_depend_on_outer_layers = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("..api..", "..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule repositories_stay_in_infrastructure = classes()
        .that().haveSimpleNameEndingWith("Repository")
        .should().resideInAPackage("..infrastructure..");
}
