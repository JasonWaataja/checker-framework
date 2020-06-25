package org.checkerframework.checker.sql.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.DefaultFor;
import org.checkerframework.framework.qual.LiteralKind;
import org.checkerframework.framework.qual.QualifierForLiterals;
import org.checkerframework.framework.qual.SubtypeOf;
import org.checkerframework.framework.qual.TypeUseLocation;

/**
 * Denotes a reference that represents safe and valid SQL, i.e. can be trusted.
 *
 * @checker_framework.manual #sql-checker SQL Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@SubtypeOf(MaybeNotSql.class)
@QualifierForLiterals(LiteralKind.STRING)
@DefaultFor(TypeUseLocation.LOWER_BOUND)
public @interface Sql {}
