package com.spendwise.categorization;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * E4-S3-T5's Required Test: an architecture test confirming no class outside {@code
 * com.spendwise.categorization} holds a reference to {@link MlClient} — the enforcement
 * mechanism behind CLAUDE.md's "FastAPI is called only from the Categorization module"
 * invariant. Admin (Epic 11) must reach retrain/evaluate strictly through {@link
 * CategorizationService}, never by injecting {@link MlClient} itself.
 */
class CategorizationBoundaryTest {

    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter().importPackages("com.spendwise");

    @Test
    void onlyCategorizationModuleMayDependOnMlClient() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackage("com.spendwise.categorization..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName(MlClient.class.getName());

        rule.check(IMPORTED_CLASSES);
    }
}
