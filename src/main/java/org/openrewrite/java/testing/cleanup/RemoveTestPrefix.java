/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.cleanup;

import java.time.Duration;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.J.MethodDeclaration;
import org.openrewrite.java.tree.TypeUtils;

public class RemoveTestPrefix extends Recipe {

    @Override
    public String getDisplayName() {
        return "Remove `test` prefix from JUnit5 tests";
    }

    @Override
    public String getDescription() {
        return "Remove `test` from methods with `@Test`, `@ParameterizedTest`, `@RepeatedTest` or `@TestFactory`. They no longer have to prefix test to be usable by JUnit 5.";
    }

    @Override
    public Duration getEstimatedEffortPerOccurrence() {
      return Duration.ofMinutes(1);
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new RemoveTestPrefixVisitor();
    }

    private static class RemoveTestPrefixVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext executionContext) {
            J.MethodDeclaration m = super.visitMethodDeclaration(method, executionContext);

            String simpleName = method.getSimpleName();
            if (simpleName.startsWith("test")
                    && 4 < simpleName.length()
                    && Character.isAlphabetic(simpleName.charAt(4))
                    && Boolean.FALSE.equals(TypeUtils.isOverride(method.getMethodType()))
                    && hasJUnit5MethodAnnotation(m)) {
                String newMethodName = Character.toLowerCase(simpleName.charAt(4)) + simpleName.substring(5);
                JavaType.Method type = m.getMethodType();
                if (type != null) {
                    type = type.withName(newMethodName);
                }
                m = m.withName(m.getName()
                        .withSimpleName(newMethodName))
                        .withMethodType(type);
            }
            return m;
        }

        private static boolean hasJUnit5MethodAnnotation(MethodDeclaration method) {
            for (J.Annotation a : method.getLeadingAnnotations()) {
                if (TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.Test")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.RepeatedTest")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.params.ParameterizedTest")
                        || TypeUtils.isOfClassType(a.getType(), "org.junit.jupiter.api.TestFactory")) {
                    return true;
                }
            }
            return false;
        }
    }

}