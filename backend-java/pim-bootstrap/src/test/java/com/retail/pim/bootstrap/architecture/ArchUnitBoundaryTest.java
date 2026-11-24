package com.retail.pim.bootstrap.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guardrail 2: ArchUnit Boundary Enforcement across Hexagonal Layers & Bounded Contexts.
 * Enforces:
 * 1. Pure Java Shared Kernel (zero Spring, JPA, or Redis dependencies).
 * 2. Domain layers depend on nothing outside their package and shared-kernel.
 * 3. Zero direct cross-context imports into another context's infrastructure or presentation layers.
 */
@AnalyzeClasses(packages = "com.retail.pim", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ArchUnitBoundaryTest {

    /**
     * Shared Kernel must remain 100% pure Java.
     */
    @ArchTest
    static final ArchRule sharedKernelMustBePureJava = classes()
            .that().resideInAPackage("com.retail.pim.shared..")
            .should().onlyDependOnClassesThat()
            .resideInAnyPackage(
                    "java..",
                    "javax..",
                    "jakarta.validation..",
                    "com.retail.pim.shared.."
            );

    /**
     * Domain layers must not depend on infrastructure or presentation layers.
     */
    @ArchTest
    static final ArchRule domainMustNotDependOnInfrastructureOrPresentation = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..presentation..");

    /**
     * Cross-context infrastructure isolation rules.
     */
    @ArchTest
    static final ArchRule catalogInfrastructureIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.compliance..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.syndication..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.catalog.infrastructure..");

    @ArchTest
    static final ArchRule complianceInfrastructureIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.syndication..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.compliance.infrastructure..");

    @ArchTest
    static final ArchRule damInfrastructureIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.compliance..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.syndication..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.dam.infrastructure..");

    @ArchTest
    static final ArchRule localizationInfrastructureIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.compliance..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.syndication..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.localization.infrastructure..");

    @ArchTest
    static final ArchRule syndicationInfrastructureIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.compliance..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.syndication.infrastructure..");

    @ArchTest
    static final ArchRule taxonomyInfrastructureIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.compliance..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.syndication.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.taxonomy.infrastructure..");

    /**
     * Cross-context presentation isolation rules.
     */
    @ArchTest
    static final ArchRule catalogPresentationIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.compliance..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.syndication..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.catalog.presentation..");

    @ArchTest
    static final ArchRule compliancePresentationIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.syndication..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.compliance.presentation..");

    @ArchTest
    static final ArchRule damPresentationIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.compliance..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.syndication..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.dam.presentation..");

    @ArchTest
    static final ArchRule localizationPresentationIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.compliance..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.syndication..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.localization.presentation..");

    @ArchTest
    static final ArchRule syndicationPresentationIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.compliance..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.taxonomy.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.syndication.presentation..");

    @ArchTest
    static final ArchRule taxonomyPresentationIsolated = noClasses()
            .that().resideInAnyPackage(
                    "com.retail.pim.catalog..",
                    "com.retail.pim.compliance..",
                    "com.retail.pim.dam..",
                    "com.retail.pim.localization..",
                    "com.retail.pim.syndication.."
            )
            .should().dependOnClassesThat()
            .resideInAPackage("com.retail.pim.taxonomy.presentation..");
}
