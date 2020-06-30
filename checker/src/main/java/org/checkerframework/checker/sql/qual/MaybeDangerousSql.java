package org.checkerframework.checker.sql.qual;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.framework.qual.DefaultQualifierInHierarchy;
import org.checkerframework.framework.qual.SubtypeOf;

/**
 * Denotes a value that may contain dangerous data. It could come from any source, including user
 * controlled inputs, meaning it has the possibility of containing malicious SQL. A value with this
 * type should not executed on a SQL database.
 *
 * <p>This is the top qualifier of the SQL type system. This annotation is associated with the
 * {@link org.checkerframework.checker.sql.SqlChecker}.
 *
 * @see NotDangerousSql
 * @see org.checkerframework.checker.sql.SqlChecker
 * @checker_framework.manual #sql-checker SQL Checker
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
@DefaultQualifierInHierarchy
@SubtypeOf({})
public @interface MaybeDangerousSql {}
