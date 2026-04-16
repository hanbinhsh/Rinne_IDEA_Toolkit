package com.github.hanbinhsh.rinneideatoolkit.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiArrayInitializerMemberValue
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiVariable
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText

@Service(Service.Level.PROJECT)
class MapperTableAnalyzer(private val project: Project) {

    enum class MapperColumnAction(val id: String, val displayName: String) {
        SELECT("select", "select"),
        INSERT("insert", "insert"),
        UPDATE("update", "update"),
        WHERE("where", "where"),
        JOIN("join", "join"),
        GROUP_BY("group_by", "group by"),
        ORDER_BY("order_by", "order by"),
        HAVING("having", "having"),
    }

    fun collectTables(method: PsiMethod): Set<String> {
        return collectTableMappings(method)
            .mapTo(linkedSetOf()) { it.tableName }
    }

    fun collectTableMappings(method: PsiMethod): List<MapperTableMapping> {
        if (DumbService.isDumb(project)) {
            return emptyList()
        }
        if (!looksLikeMapperMethod(method)) {
            return emptyList()
        }

        val sqlTexts = linkedSetOf<String>()
        sqlTexts += extractAnnotationSql(method)
        sqlTexts += extractXmlSql(method)

        val tableColumns = linkedMapOf<String, MutableMap<String, MutableSet<MapperColumnAction>>>()
        sqlTexts.forEach { sql ->
            extractTableMappings(sql).forEach { mapping ->
                val columnActions = tableColumns.getOrPut(mapping.tableName) { linkedMapOf() }
                mapping.columnActions.forEach { (columnName, actions) ->
                    columnActions.getOrPut(columnName) { linkedSetOf() }.addAll(actions)
                }
            }
        }
        return tableColumns.entries.map { (tableName, columnActions) ->
            MapperTableMapping(
                tableName = tableName,
                columns = columnActions.keys,
                columnActions = columnActions.mapValues { (_, actions) -> actions.toSet() },
            )
        }
    }

    private fun looksLikeMapperMethod(method: PsiMethod): Boolean {
        val className = method.containingClass?.name.orEmpty()
        return className.contains("Mapper", ignoreCase = true) ||
            hasMyBatisSqlAnnotation(method)
    }

    private fun hasMyBatisSqlAnnotation(method: PsiMethod): Boolean =
        method.modifierList.annotations.any(::isMyBatisSqlAnnotation)

    private fun extractAnnotationSql(method: PsiMethod): Set<String> =
        method.modifierList.annotations
            .filter(::isMyBatisSqlAnnotation)
            .flatMapTo(linkedSetOf()) { annotation ->
                annotationSqlParts(annotation)
            }

    private fun isMyBatisSqlAnnotation(annotation: PsiAnnotation): Boolean {
        val qualifiedName = annotation.qualifiedName
        val shortName = qualifiedName?.substringAfterLast('.')
            ?: annotation.nameReferenceElement?.referenceName
            ?: annotation.text.substringAfter('@').substringBefore('(').substringAfterLast('.')
        return qualifiedName in MYBATIS_SQL_ANNOTATIONS || shortName in MYBATIS_SQL_ANNOTATION_SHORT_NAMES
    }

    private fun annotationSqlParts(annotation: PsiAnnotation): List<String> {
        val value = annotation.findDeclaredAttributeValue("value")
            ?: annotation.parameterList.attributes.singleOrNull()?.value
            ?: return emptyList()
        return memberValueStrings(value)
    }

    private fun memberValueStrings(value: PsiAnnotationMemberValue): List<String> = when (value) {
        is PsiLiteralExpression -> listOfNotNull(value.value as? String)
        is PsiReferenceExpression -> resolveConstantString(value)?.let(::listOf).orEmpty()
        is PsiArrayInitializerMemberValue ->
            value.initializers.flatMap { initializer ->
                initializer?.let(::memberValueStrings).orEmpty()
            }

        else -> emptyList()
    }

    private fun resolveConstantString(reference: PsiReferenceExpression): String? =
        (reference.resolve() as? PsiVariable)
            ?.computeConstantValue()
            ?.toString()

    private fun extractXmlSql(method: PsiMethod): Set<String> {
        val containingClass = method.containingClass ?: return emptySet()
        val namespace = containingClass.qualifiedName ?: return emptySet()
        val files = FilenameIndex.getAllFilesByExt(project, "xml", GlobalSearchScope.projectScope(project))
        if (files.isEmpty()) {
            return emptySet()
        }

        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        val result = linkedSetOf<String>()
        files.forEach { virtualFile ->
            val xmlFile = psiManager.findFile(virtualFile) as? XmlFile ?: return@forEach
            val rootTag = xmlFile.rootTag ?: return@forEach
            if (!rootTag.name.equals("mapper", ignoreCase = true)) {
                return@forEach
            }
            if (rootTag.getAttributeValue("namespace") != namespace) {
                return@forEach
            }

            val fragments = rootTag.findSubTags("sql")
                .mapNotNull { tag ->
                    tag.getAttributeValue("id")?.let { id -> id to tag }
                }
                .toMap(linkedMapOf())
            val statementTag = SQL_TAG_NAMES
                .asSequence()
                .flatMap { tagName -> rootTag.findSubTags(tagName).asSequence() }
                .firstOrNull { it.getAttributeValue("id") == method.name }
                ?: return@forEach

            val sql = collectSqlText(statementTag, fragments, namespace, mutableSetOf()).trim()
            if (sql.isNotBlank()) {
                result += sql
            }
        }
        return result
    }

    private fun collectSqlText(
        tag: XmlTag,
        fragments: Map<String, XmlTag>,
        namespace: String,
        visitedIncludes: MutableSet<String>,
    ): String {
        val builder = StringBuilder()
        xmlClausePrefix(tag.name)?.let { builder.append(it) }
        tag.value.children.forEach { child ->
            when (child) {
                is XmlText -> builder.append(' ').append(child.value)
                is XmlTag -> {
                    if (child.name.equals("include", ignoreCase = true)) {
                        val refId = child.getAttributeValue("refid") ?: return@forEach
                        val normalized = normalizeRefId(refId, namespace)
                        if (!visitedIncludes.add(normalized)) {
                            return@forEach
                        }
                        val included = fragments[normalized] ?: return@forEach
                        builder.append(' ').append(collectSqlText(included, fragments, namespace, visitedIncludes))
                        visitedIncludes.remove(normalized)
                    } else {
                        builder.append(' ').append(collectSqlText(child, fragments, namespace, visitedIncludes))
                    }
                }
            }
        }
        return builder.toString().replace(WHITESPACE_REGEX, " ").trim()
    }

    private fun xmlClausePrefix(tagName: String): String? = when (tagName.lowercase()) {
        "where" -> " WHERE "
        "set" -> " SET "
        else -> null
    }

    private fun normalizeRefId(refId: String, namespace: String): String =
        when {
            refId.startsWith("$namespace.") -> refId.removePrefix("$namespace.")
            '.' in refId -> refId.substringAfterLast('.')
            else -> refId
        }

    private fun extractTableMappings(sql: String): List<MapperTableMapping> {
        val tableEntries = extractTableEntries(sql)
        if (tableEntries.isEmpty()) {
            return emptyList()
        }

        val aliasToTable = linkedMapOf<String, String>()
        tableEntries.forEach { entry ->
            aliasToTable[entry.simpleName] = entry.tableName
            entry.alias?.let { aliasToTable[it] = entry.tableName }
        }

        val columnActionsByTable = linkedMapOf<String, MutableMap<String, MutableSet<MapperColumnAction>>>()
        tableEntries.forEach { entry ->
            columnActionsByTable.getOrPut(entry.tableName) { linkedMapOf() }
        }

        collectProjectionColumns(sql, aliasToTable, tableEntries.firstOrNull()?.tableName)
            .forEach { usage ->
                columnActionsByTable
                    .getOrPut(usage.tableName) { linkedMapOf() }
                    .getOrPut(usage.columnName) { linkedSetOf() }
                    .add(usage.action)
            }

        collectInsertColumns(sql).forEach { (tableName, column) ->
            columnActionsByTable
                .getOrPut(tableName) { linkedMapOf() }
                .getOrPut(column) { linkedSetOf() }
                .add(MapperColumnAction.INSERT)
        }

        collectUpdateColumns(sql).forEach { (tableName, column) ->
            columnActionsByTable
                .getOrPut(tableName) { linkedMapOf() }
                .getOrPut(column) { linkedSetOf() }
                .add(MapperColumnAction.UPDATE)
        }

        collectClauseColumns(sql, aliasToTable, tableEntries, WHERE_CLAUSE_REGEX, MapperColumnAction.WHERE)
            .forEach { usage ->
                columnActionsByTable
                    .getOrPut(usage.tableName) { linkedMapOf() }
                    .getOrPut(usage.columnName) { linkedSetOf() }
                    .add(usage.action)
            }

        collectClauseColumns(sql, aliasToTable, tableEntries, ON_CLAUSE_REGEX, MapperColumnAction.JOIN)
            .forEach { usage ->
                columnActionsByTable
                    .getOrPut(usage.tableName) { linkedMapOf() }
                    .getOrPut(usage.columnName) { linkedSetOf() }
                    .add(usage.action)
            }

        collectClauseColumns(sql, aliasToTable, tableEntries, GROUP_BY_CLAUSE_REGEX, MapperColumnAction.GROUP_BY)
            .forEach { usage ->
                columnActionsByTable
                    .getOrPut(usage.tableName) { linkedMapOf() }
                    .getOrPut(usage.columnName) { linkedSetOf() }
                    .add(usage.action)
            }

        collectClauseColumns(sql, aliasToTable, tableEntries, ORDER_BY_CLAUSE_REGEX, MapperColumnAction.ORDER_BY)
            .forEach { usage ->
                columnActionsByTable
                    .getOrPut(usage.tableName) { linkedMapOf() }
                    .getOrPut(usage.columnName) { linkedSetOf() }
                    .add(usage.action)
            }

        collectClauseColumns(sql, aliasToTable, tableEntries, HAVING_CLAUSE_REGEX, MapperColumnAction.HAVING)
            .forEach { usage ->
                columnActionsByTable
                    .getOrPut(usage.tableName) { linkedMapOf() }
                    .getOrPut(usage.columnName) { linkedSetOf() }
                    .add(usage.action)
            }

        QUALIFIED_COLUMN_REGEX.findAll(sql).forEach { match ->
            val owner = match.groupValues[1]
            val column = normalizeColumnName(match.groupValues[2]) ?: return@forEach
            val tableName = aliasToTable[owner] ?: return@forEach
            columnActionsByTable.getOrPut(tableName) { linkedMapOf() }.putIfAbsent(column, linkedSetOf())
        }

        return columnActionsByTable.entries.map { (tableName, columnActions) ->
            MapperTableMapping(
                tableName = tableName,
                columns = columnActions.keys,
                columnActions = columnActions.mapValues { (_, actions) -> actions.toSet() },
            )
        }
    }

    private fun extractTableEntries(sql: String): List<TableEntry> {
        val tables = linkedMapOf<String, TableEntry>()
        TABLE_PATTERNS.forEach { pattern ->
            pattern.findAll(sql).forEach { match ->
                val tableName = normalizeTableName(match.groupValues[1]) ?: return@forEach
                val alias = normalizeAlias(match.groups[2]?.value)
                tables.putIfAbsent(tableName, TableEntry(tableName, alias))
            }
        }
        return tables.values.toList()
    }

    private fun collectProjectionColumns(
        sql: String,
        aliasToTable: Map<String, String>,
        defaultTable: String?,
    ): Set<ColumnUsage> {
        val match = SELECT_CLAUSE_REGEX.find(sql) ?: return emptySet()
        return splitSqlList(match.groupValues[1])
            .mapNotNull { expression ->
                resolveColumnFromExpression(expression, aliasToTable, defaultTable)
                    ?.let { (tableName, columnName) ->
                        ColumnUsage(tableName, columnName, MapperColumnAction.SELECT)
                    }
            }
            .toCollection(linkedSetOf())
    }

    private fun collectInsertColumns(sql: String): Set<Pair<String, String>> {
        val result = linkedSetOf<Pair<String, String>>()
        INSERT_COLUMNS_REGEX.findAll(sql).forEach { match ->
            val tableName = normalizeTableName(match.groupValues[1]) ?: return@forEach
            splitSqlList(match.groupValues[2]).forEach { token ->
                normalizeColumnName(token)?.let { column ->
                    result += tableName to column
                }
            }
        }
        return result
    }

    private fun collectUpdateColumns(sql: String): Set<Pair<String, String>> {
        val result = linkedSetOf<Pair<String, String>>()
        UPDATE_SET_REGEX.findAll(sql).forEach { match ->
            val tableName = normalizeTableName(match.groupValues[1]) ?: return@forEach
            splitSqlList(match.groupValues[2]).forEach { assignment ->
                normalizeColumnName(assignment.substringBefore('='))?.let { column ->
                    result += tableName to column
                }
            }
        }
        return result
    }

    private fun collectClauseColumns(
        sql: String,
        aliasToTable: Map<String, String>,
        tableEntries: List<TableEntry>,
        regex: Regex,
        action: MapperColumnAction,
        groupIndex: Int = 1,
    ): Set<ColumnUsage> {
        val result = linkedSetOf<ColumnUsage>()
        regex.findAll(sql).forEach { match ->
            val clauseText = sanitizeSqlClause(match.groupValues[groupIndex])
            collectColumnsFromClause(clauseText, aliasToTable, tableEntries)
                .forEach { (tableName, columnName) ->
                    result += ColumnUsage(tableName, columnName, action)
                }
        }
        return result
    }

    private fun collectColumnsFromClause(
        clauseText: String,
        aliasToTable: Map<String, String>,
        tableEntries: List<TableEntry>,
    ): Set<Pair<String, String>> {
        val result = linkedSetOf<Pair<String, String>>()
        QUALIFIED_COLUMN_REGEX.findAll(clauseText).forEach { qualified ->
            val tableName = aliasToTable[qualified.groupValues[1]] ?: return@forEach
            val column = normalizeColumnName(qualified.groupValues[2]) ?: return@forEach
            result += tableName to column
        }

        val defaultTable = tableEntries.singleOrNull()?.tableName ?: return result
        PLAIN_IDENTIFIER_REGEX.findAll(clauseText).forEach { identifier ->
            val token = identifier.groupValues[1]
            if (isFunctionCallToken(clauseText, identifier.range.last + 1)) {
                return@forEach
            }
            if (!shouldTreatAsColumnToken(token, aliasToTable)) {
                return@forEach
            }
            normalizeColumnName(token)?.let { column ->
                result += defaultTable to column
            }
        }
        return result
    }

    private fun sanitizeSqlClause(clause: String): String =
        clause
            .replace(PLACEHOLDER_REGEX, " ")
            .replace(STRING_LITERAL_REGEX, " ")
            .replace(NUMERIC_LITERAL_REGEX, " ")

    private fun resolveColumnFromExpression(
        expression: String,
        aliasToTable: Map<String, String>,
        defaultTable: String?,
    ): Pair<String, String>? {
        val withoutAlias = expression
            .replace(AS_ALIAS_REGEX, "")
            .trim()
        if (withoutAlias == "*" || withoutAlias.endsWith(".*")) {
            return null
        }

        QUALIFIED_COLUMN_REGEX.findAll(withoutAlias)
            .lastOrNull()
            ?.let { qualified ->
                val tableName = aliasToTable[qualified.groupValues[1]] ?: return@let null
                val column = normalizeColumnName(qualified.groupValues[2]) ?: return@let null
                return tableName to column
            }

        if (withoutAlias.contains('(') || withoutAlias.contains(')')) {
            return null
        }

        val plain = normalizeColumnName(withoutAlias) ?: return null
        val tableName = defaultTable ?: return null
        return tableName to plain
    }

    private fun splitSqlList(clause: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var parenthesesDepth = 0
        var inSingleQuote = false
        var inDoubleQuote = false

        clause.forEach { char ->
            when (char) {
                '\'' -> {
                    if (!inDoubleQuote) {
                        inSingleQuote = !inSingleQuote
                    }
                    current.append(char)
                }

                '"' -> {
                    if (!inSingleQuote) {
                        inDoubleQuote = !inDoubleQuote
                    }
                    current.append(char)
                }

                '(' -> {
                    if (!inSingleQuote && !inDoubleQuote) {
                        parenthesesDepth++
                    }
                    current.append(char)
                }

                ')' -> {
                    if (!inSingleQuote && !inDoubleQuote && parenthesesDepth > 0) {
                        parenthesesDepth--
                    }
                    current.append(char)
                }

                ',' -> {
                    if (!inSingleQuote && !inDoubleQuote && parenthesesDepth == 0) {
                        current.toString().trim().takeIf { it.isNotBlank() }?.let(parts::add)
                        current.setLength(0)
                    } else {
                        current.append(char)
                    }
                }

                else -> current.append(char)
            }
        }

        current.toString().trim().takeIf { it.isNotBlank() }?.let(parts::add)
        return parts
    }

    private fun normalizeTableName(raw: String): String? {
        val cleaned = raw
            .trim()
            .trim(',', ';')
            .removePrefix("`")
            .removeSuffix("`")
            .removePrefix("\"")
            .removeSuffix("\"")
            .removePrefix("[")
            .removeSuffix("]")
            .trim()
        if (cleaned.isBlank()) {
            return null
        }
        return cleaned
            .split('.')
            .joinToString(".") { it.trim('`', '"', '[', ']') }
            .takeIf { TABLE_NAME_REGEX.matches(it) }
    }

    private fun normalizeColumnName(raw: String?): String? {
        val cleaned = raw
            ?.trim()
            ?.substringAfterLast('.')
            ?.substringBefore(' ')
            ?.trim(',', ';')
            ?.trim('`', '"', '[', ']')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        if (
            cleaned == "*" ||
            cleaned.contains('(') ||
            cleaned.contains(')') ||
            cleaned.startsWith("#{") ||
            cleaned.startsWith("\${")
        ) {
            return null
        }
        return cleaned.takeIf { COLUMN_NAME_REGEX.matches(it) }
    }

    private fun normalizeAlias(raw: String?): String? {
        val cleaned = raw
            ?.trim()
            ?.trim('`', '"', '[', ']')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return cleaned.takeUnless { RESERVED_ALIAS_WORDS.contains(it.lowercase()) }
    }

    private fun shouldTreatAsColumnToken(token: String, aliasToTable: Map<String, String>): Boolean {
        val normalized = token.trim('`', '"', '[', ']').lowercase()
        if (normalized.isBlank()) {
            return false
        }
        if (normalized in RESERVED_ALIAS_WORDS || normalized in RESERVED_COLUMN_WORDS) {
            return false
        }
        if (normalized in aliasToTable.keys.map { it.lowercase() }) {
            return false
        }
        return true
    }

    private fun isFunctionCallToken(text: String, startIndex: Int): Boolean {
        var index = startIndex
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }
        return index < text.length && text[index] == '('
    }

    private companion object {
        val MYBATIS_SQL_ANNOTATIONS = setOf(
            "org.apache.ibatis.annotations.Select",
            "org.apache.ibatis.annotations.Insert",
            "org.apache.ibatis.annotations.Update",
            "org.apache.ibatis.annotations.Delete",
        )
        val MYBATIS_SQL_ANNOTATION_SHORT_NAMES = setOf("Select", "Insert", "Update", "Delete")

        val SQL_TAG_NAMES = listOf("select", "insert", "update", "delete")

        val TABLE_PATTERNS = listOf(
            Regex("""\bfrom\s+([`"\[\]\w.]+)(?:\s+(?:as\s+)?([A-Za-z_][\w$]*))?""", RegexOption.IGNORE_CASE),
            Regex("""\bjoin\s+([`"\[\]\w.]+)(?:\s+(?:as\s+)?([A-Za-z_][\w$]*))?""", RegexOption.IGNORE_CASE),
            Regex("""\binsert\s+into\s+([`"\[\]\w.]+)(?:\s+(?:as\s+)?([A-Za-z_][\w$]*))?""", RegexOption.IGNORE_CASE),
            Regex("""\bupdate\s+([`"\[\]\w.]+)(?:\s+(?:as\s+)?([A-Za-z_][\w$]*))?""", RegexOption.IGNORE_CASE),
            Regex("""\bdelete\s+from\s+([`"\[\]\w.]+)(?:\s+(?:as\s+)?([A-Za-z_][\w$]*))?""", RegexOption.IGNORE_CASE),
        )

        val SELECT_CLAUSE_REGEX = Regex("""\bselect\b(.*?)\bfrom\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val INSERT_COLUMNS_REGEX = Regex(
            """\binsert\s+into\s+([`"\[\]\w.]+)\s*\((.*?)\)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val UPDATE_SET_REGEX = Regex(
            """\bupdate\s+([`"\[\]\w.]+)\b.*?\bset\b(.*?)(?:\bwhere\b|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val WHERE_CLAUSE_REGEX = Regex(
            """\bwhere\b(.*?)(?=\bgroup\s+by\b|\border\s+by\b|\bhaving\b|\blimit\b|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val ON_CLAUSE_REGEX = Regex(
            """\bon\b(.*?)(?=\bjoin\b|\bon\b|\bwhere\b|\bgroup\s+by\b|\border\s+by\b|\bhaving\b|\blimit\b|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val GROUP_BY_CLAUSE_REGEX = Regex(
            """\bgroup\s+by\b(.*?)(?=\bhaving\b|\border\s+by\b|\blimit\b|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val ORDER_BY_CLAUSE_REGEX = Regex(
            """\border\s+by\b(.*?)(?=\blimit\b|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val HAVING_CLAUSE_REGEX = Regex(
            """\bhaving\b(.*?)(?=\border\s+by\b|\blimit\b|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val AS_ALIAS_REGEX = Regex("""\s+as\s+[A-Za-z_][\w$]*$""", RegexOption.IGNORE_CASE)
        val QUALIFIED_COLUMN_REGEX = Regex("""\b([A-Za-z_][\w$]*)\.([A-Za-z_][\w$]*|\*)\b""")
        val PLAIN_IDENTIFIER_REGEX = Regex("""\b([A-Za-z_][\w$]*)\b""")
        val PLACEHOLDER_REGEX = Regex("""[#$]\{[^}]+}""")
        val STRING_LITERAL_REGEX = Regex("""'([^']|'')*'|"([^"]|"")*"""")
        val NUMERIC_LITERAL_REGEX = Regex("""\b\d+(\.\d+)?\b""")
        val TABLE_NAME_REGEX = Regex("""[A-Za-z_][\w$]*(\.[A-Za-z_][\w$]*)?""")
        val COLUMN_NAME_REGEX = Regex("""[A-Za-z_][\w$]*""")
        val WHITESPACE_REGEX = Regex("""\s+""")
        val RESERVED_ALIAS_WORDS = setOf(
            "where",
            "left",
            "right",
            "inner",
            "outer",
            "join",
            "on",
            "group",
            "order",
            "limit",
            "values",
            "set",
        )
        val RESERVED_COLUMN_WORDS = setOf(
            "and",
            "or",
            "not",
            "null",
            "is",
            "in",
            "exists",
            "like",
            "between",
            "true",
            "false",
            "case",
            "when",
            "then",
            "else",
            "end",
            "asc",
            "desc",
            "count",
            "sum",
            "avg",
            "min",
            "max",
            "distinct",
            "by",
        )
    }

    data class MapperTableMapping(
        val tableName: String,
        val columns: Set<String>,
        val columnActions: Map<String, Set<MapperColumnAction>> = emptyMap(),
    )

    private data class ColumnUsage(
        val tableName: String,
        val columnName: String,
        val action: MapperColumnAction,
    )

    private data class TableEntry(
        val tableName: String,
        val alias: String?,
    ) {
        val simpleName: String = tableName.substringAfterLast('.')
    }
}
