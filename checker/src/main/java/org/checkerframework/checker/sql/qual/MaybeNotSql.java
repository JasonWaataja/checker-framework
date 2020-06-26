package org.checkerframework.checker.sql.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.SubtypeOf;

/**
 * Denotes a possibly-dangerous value: at run time, the value might contain valid SQL or it might
 * contain unverified user input.
 *
 * <p>This is the top qualifier of the SQL type system. This annotation is associated with the
 * {@link org.checkerframework.checker.tainting.SqlChecker}.
 *
 * @see Sql
 * @see org.checkerframework.checker.tainting.SqlChecker
 * @checker_framework.manual #sql-checker SQL Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@DefaultQualifierInHierarchy
@SubtypeOf({})
public @interface MaybeNotSql {}
