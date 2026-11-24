package com.gzgk.core;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces Modular Monolith package boundaries inside gzgk-core so domain packages
 * remain cleanly extractable without circular coupling.
 */
class ArchitectureBoundaryTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.gzgk.core");

    @Test
    void catalogMustNotDependOnHigherLayers() {
        noClasses().that().resideInAPackage("..catalog..")
                .should().dependOnClassesThat().resideInAnyPackage("..recommend..", "..sandbox..", "..grpc..", "..mq..")
                .check(CLASSES);
    }

    @Test
    void scorelineMustNotDependOnHigherLayers() {
        noClasses().that().resideInAPackage("..scoreline..")
                .should().dependOnClassesThat().resideInAnyPackage("..recommend..", "..sandbox..", "..grpc..", "..mq..")
                .check(CLASSES);
    }

    @Test
    void policyMustNotDependOnRecommendOrSandbox() {
        noClasses().that().resideInAPackage("..policy..")
                .should().dependOnClassesThat().resideInAnyPackage("..recommend..", "..sandbox..", "..grpc..")
                .check(CLASSES);
    }

    @Test
    void mqMustNotDependOnControllersOrGrpc() {
        noClasses().that().resideInAPackage("..mq..")
                .should().dependOnClassesThat().resideInAnyPackage("..grpc..", "..sandbox..", "..recommend..")
                .check(CLASSES);
    }
}
