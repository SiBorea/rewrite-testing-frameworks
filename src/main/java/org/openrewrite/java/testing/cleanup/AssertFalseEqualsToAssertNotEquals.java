/*
 * Copyright 2022 the original author or authors.
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

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.function.Supplier;

public class AssertFalseEqualsToAssertNotEquals extends Recipe {
    private static final MethodMatcher ASSERT_FALSE = new MethodMatcher(
            "org.junit.jupiter.api.Assertions assertFalse(..)");

    @Override
    public String getDisplayName() {
        return "Replace JUnit `assertFalse(a.equals(b))` to `assertNotEquals(a,b)`";
    }

    @Override
    public String getDescription() {
        return "Using `assertNotEquals(a,b)` is simpler and more clear.";
    }

    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesMethod<>(ASSERT_FALSE);
    }

    @Override
    protected JavaVisitor<ExecutionContext> getVisitor() {
        return new JavaVisitor<ExecutionContext>() {
            final Supplier<JavaParser> javaParser = () -> JavaParser.fromJavaVersion()
                    //language=java
                    .dependsOn("" +
                            "package org.junit.jupiter.api;" +
                            "import java.util.function.Supplier;" +
                            "public class Assertions {" +
                            "public static void assertNotEquals(Object expected,Object actual) {}" +
                            "public static void assertNotEquals(Object expected,Object actual, String message) {}" +
                            "public static void assertNotEquals(Object actual, Supplier<String> messageSupplier) {}" +
                            "}")
                    .build();

            @Override
            public J visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                J.MethodInvocation mi = (J.MethodInvocation) super.visitMethodInvocation(method, ctx);
                if (ASSERT_FALSE.matches(method) && isEquals(method.getArguments().get(0))) {
                    StringBuilder sb = new StringBuilder();
                    Object[] args;
                    if (mi.getSelect() == null) {
                        maybeRemoveImport("org.junit.jupiter.api.Assertions");
                        maybeAddImport("org.junit.jupiter.api.Assertions", "assertNotEquals");
                    } else {
                        sb.append("Assertions.");
                    }
                    sb.append("assertNotEquals(#{any(java.lang.Object)}, #{any(java.lang.Object)}");
                    if (mi.getArguments().size() == 2) {
                        sb.append(", #{any()}");
                    }
                    sb.append(")");

                    J.MethodInvocation methodInvocation = getMethodInvocation(method);
                    J.MethodInvocation s = (J.MethodInvocation)methodInvocation.getArguments().get(0);
                    args = method.getArguments().size() == 2 ? new Object[]{s.getSelect(), s.getArguments().get(0), mi.getArguments().get(1)} : new Object[]{s.getSelect(), s.getArguments().get(0)};
                    JavaTemplate t;
                    if (mi.getSelect() == null) {
                        t = JavaTemplate.builder(this::getCursor, sb.toString())
                                .staticImports("org.junit.jupiter.api.Assertions.assertNotEquals").javaParser(javaParser).build();
                    } else {
                        t = JavaTemplate.builder(this::getCursor, sb.toString())
                                .imports("org.junit.jupiter.api.Assertions").javaParser(javaParser).build();
                    }
                    return mi.withTemplate(t, mi.getCoordinates().replace(), args);
                }
                return mi;
            }

            private J.MethodInvocation getMethodInvocation(Expression expr){
                List<J> s = expr.getSideEffects();
                return  ((J.MethodInvocation) s.get(0));
            }

            private boolean isEquals(Expression expr) {
                List<J> s = expr.getSideEffects();

                if (s.isEmpty()){
                    return false;
                }

               J.MethodInvocation methodInvocation = getMethodInvocation(expr);

               return "equals".equals(methodInvocation.getName().getSimpleName());

            }
        };
    }
}
