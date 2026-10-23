package com.api.fespot.global.aop;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;
import org.springframework.data.repository.Repository; // stereotype.Repository 아님!
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueViolationWrapArchTest {

    private static final String BASE_PACKAGE = "com.api.fespot";
    private static final Set<String> INSERT_METHODS =
            Set.of("save", "saveAll", "saveAndFlush", "saveAllAndFlush");

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE);

    @Test
    void 유니크_엔티티를_저장하는_서비스_메서드에는_변환_어노테이션이_있어야_한다() {
        List<String> violations = new ArrayList<>();

        for (JavaClass clazz : CLASSES) {
            if (!clazz.isAnnotatedWith(Service.class) && !clazz.isMetaAnnotatedWith(Service.class)) {
                continue;
            }
            for (JavaMethod method : clazz.getMethods()) {
                Set<JavaModifier> mods = method.getModifiers();
                if (!mods.contains(JavaModifier.PUBLIC)
                        || mods.contains(JavaModifier.SYNTHETIC)
                        || mods.contains(JavaModifier.BRIDGE)) {
                    continue;
                }
                Set<String> entities = uniqueEntitiesInsertedFrom(method);
                if (!entities.isEmpty() && !method.isAnnotatedWith(WrapDataIntegrityViolation.class)) {
                    violations.add(method.getFullName() + " → " + entities);
                }
            }
        }

        assertThat(violations).as("@CreateUniqueColumn 누락").isEmpty();
    }

    // 프로젝트 내부 클래스로의 호출을 따라가며 유니크 엔티티 리포지토리의 save 계열 호출을 찾는다
    private Set<String> uniqueEntitiesInsertedFrom(JavaMethod entry) {
        Set<String> found = new TreeSet<>();
        Set<JavaMethod> visited = new HashSet<>();
        Deque<JavaMethod> stack = new ArrayDeque<>();
        stack.push(entry);

        while (!stack.isEmpty()) {
            JavaMethod current = stack.pop();
            if (!visited.add(current)) {
                continue;
            }
            for (JavaMethodCall call : current.getMethodCallsFromSelf()) {
                JavaClass owner = call.getTargetOwner();
                if (owner.isAssignableTo(Repository.class)) {
                    if (INSERT_METHODS.contains(call.getTarget().getName())) {
                        Class<?> entity = ResolvableType.forClass(owner.reflect())
                                .as(Repository.class)
                                .resolveGeneric(0);
                        if (entity != null && UniqueConstraintScanner.hasUnique(entity)) {
                            found.add(entity.getSimpleName());
                        }
                    }
                } else if (owner.getPackageName().startsWith(BASE_PACKAGE)) {
                    call.getTarget().resolveMember().ifPresent(stack::push);
                }
            }
        }
        return found;
    }
}
