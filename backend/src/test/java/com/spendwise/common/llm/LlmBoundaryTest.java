package com.spendwise.common.llm;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * E8-S1-T1's Required Test — enforces CLAUDE.md's "no vendor has been selected... do not
 * hardcode any LLM SDK into business logic" by construction rather than by name-matching a vendor
 * package that has no classes on the classpath yet: no class outside {@code
 * com.spendwise.common.llm.provider} may depend on classes in that package. This blocks
 * Recommendations/Chatbot from injecting {@code StubLlmClient} directly today — they must go
 * through the {@link LlmClient} interface + {@link LlmConfig}'s bean — and transparently extends
 * to block any future vendor-SDK-backed implementation from leaking outside the same package.
 * Mirrors {@code CategorizationBoundaryTest}'s exact shape.
 */
class LlmBoundaryTest {

    private static final JavaClasses IMPORTED_CLASSES = new ClassFileImporter().importPackages("com.spendwise");

    @Test
    void onlyLlmConfigMayDependOnLlmProviderClasses() {
        ArchRule rule = noClasses()
                .that()
                .resideOutsideOfPackages("com.spendwise.common.llm.provider..", "com.spendwise.common.llm")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.spendwise.common.llm.provider..");

        rule.check(IMPORTED_CLASSES);
    }
}
