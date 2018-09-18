package org.checkerframework.checker.determinism.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * An expression of type {@code @Det} evaluates to the same value (with respect to .equals()) on all
 * executions. For collections, the iteration order is also the same on all executions. This is the
 * default qualifier in the hierarchy.
 *
 * @checker_framework.manual #determinism-checker Determinism Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf({OrderNonDet.class})
@DefaultQualifierInHierarchy
@DefaultFor({TypeUseLocation.EXCEPTION_PARAMETER, TypeUseLocation.LOWER_BOUND})
public @interface Det {}