package org.checkerframework.checker.sql;

import org.checkerframework.common.basetype.BaseTypeChecker;

/**
 * A type-checker plug-in for the SQL type system qualifier that finds (and verifies the absence of)
 * SQl injection vulnerabilities.
 *
 * <p>It verifies that all executed SQL queries come from trusted sources and don't contains raw
 * user input.
 *
 * @checker_framework.manual #sql-checker SQL Checker
 */
public class SqlChecker extends BaseTypeChecker {}
