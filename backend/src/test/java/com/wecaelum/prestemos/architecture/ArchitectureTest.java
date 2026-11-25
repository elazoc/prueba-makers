package com.wecaelum.prestemos.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.wecaelum.prestemos", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPure = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "org.hibernate..", "tools.jackson..");

    @ArchTest
    static final ArchRule applicationUsesOnlyTransactionalFromSpring = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat(
                    resideInAPackage("org.springframework..").and(not(resideInAPackage("org.springframework.transaction.annotation"))))
            .orShould().dependOnClassesThat().resideInAnyPackage("jakarta..", "org.hibernate..", "tools.jackson..", "..infrastructure..");

    @ArchTest
    static final ArchRule securityReadsUsersThroughThePort = noClasses().that().resideInAPackage("..infrastructure.security..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.adapter.out.persistence..");
}
