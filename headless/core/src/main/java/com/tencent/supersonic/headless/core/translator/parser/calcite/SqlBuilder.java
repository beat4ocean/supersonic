package com.tencent.supersonic.headless.core.translator.parser.calcite;

import com.google.common.collect.Sets;
import com.tencent.supersonic.common.calcite.Configuration;
import com.tencent.supersonic.common.pojo.enums.EngineType;
import com.tencent.supersonic.headless.api.pojo.*;
import com.tencent.supersonic.headless.api.pojo.enums.IdentifyType;
import com.tencent.supersonic.headless.api.pojo.response.DatabaseResp;
import com.tencent.supersonic.headless.api.pojo.response.DimSchemaResp;
import com.tencent.supersonic.headless.api.pojo.response.MetricSchemaResp;
import com.tencent.supersonic.headless.api.pojo.response.ModelResp;
import com.tencent.supersonic.headless.core.pojo.JoinRelation;
import com.tencent.supersonic.headless.core.pojo.Ontology;
import com.tencent.supersonic.headless.core.pojo.OntologyQuery;
import com.tencent.supersonic.headless.core.pojo.QueryStatement;
import com.tencent.supersonic.headless.core.translator.parser.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.calcite.sql.*;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.validate.SqlValidatorScope;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class SqlBuilder {

    private final S2CalciteSchema schema;
    private final SqlValidatorScope scope;

    public SqlBuilder(S2CalciteSchema schema) {
        this.schema = schema;
        this.scope = SchemaBuilder.getScope(schema);
    }

    public String buildOntologySql(QueryStatement queryStatement) throws Exception {
        OntologyQuery ontologyQuery = queryStatement.getOntologyQuery();
        Ontology ontology = queryStatement.getOntology();

        if (ontologyQuery.getLimit() == null) {
            ontologyQuery.setLimit(0L);
        }

        Set<ModelResp> dataModels = ontologyQuery.getModels();
        if (dataModels == null || dataModels.isEmpty()) {
            throw new Exception("data model not found");
        }

        TableView tableView;
        if (!CollectionUtils.isEmpty(ontology.getJoinRelations()) && dataModels.size() > 1) {
            Set<ModelResp> models = probeRelatedModels(dataModels, queryStatement.getOntology());
            for (ModelResp model : models) {
                log.info("probeRelatedModels: {}", model.getName());
            }
            tableView = render(ontologyQuery, models, scope, schema);
        } else {
            tableView = render(ontologyQuery, dataModels, scope, schema);
        }

        SqlNode parserNode = tableView.build();
        DatabaseResp database = queryStatement.getOntology().getDatabase();
        EngineType engineType = EngineType.fromString(database.getType());
        try {
            parserNode = optimizeParseNode(parserNode, engineType);
        } catch (Exception e) {
            // failure in optimization phase doesn't affect the query result,
            // just ignore it
            log.error("optimizeParseNode error", e);
        }
        return SemanticNode.getSql(parserNode, engineType);
    }

    private Set<ModelResp> probeRelatedModels(Set<ModelResp> dataModels, Ontology ontology) {
        List<JoinRelation> joinRelations = ontology.getJoinRelations();
        Graph<String, DefaultEdge> graph = buildGraph(joinRelations);
        DijkstraShortestPath<String, DefaultEdge> dijkstraAlg = new DijkstraShortestPath<>(graph);
        Set<String> queryModels =
                dataModels.stream().map(ModelResp::getName).collect(Collectors.toSet());
        GraphPath<String, DefaultEdge> selectedGraphPath = null;
        for (String fromModel : queryModels) {
            for (String toModel : queryModels) {
                if (!fromModel.equals(toModel)) {
                    GraphPath<String, DefaultEdge> path = dijkstraAlg.getPath(fromModel, toModel);
                    if (isGraphPathContainsAll(path, queryModels)) {
                        selectedGraphPath = path;
                        break;
                    }
                }
            }
        }
        if (selectedGraphPath == null) {
            return dataModels;
        }
        Set<String> modelNames = Sets.newHashSet();
        for (DefaultEdge edge : selectedGraphPath.getEdgeList()) {
            modelNames.add(selectedGraphPath.getGraph().getEdgeSource(edge));
            modelNames.add(selectedGraphPath.getGraph().getEdgeTarget(edge));
        }
        return modelNames.stream().map(m -> ontology.getModelMap().get(m))
                .collect(Collectors.toSet());
    }

    private boolean isGraphPathContainsAll(GraphPath<String, DefaultEdge> graphPath,
            Set<String> vertex) {
        Set<String> allVertex = Sets.newHashSet();
        for (DefaultEdge edge : graphPath.getEdgeList()) {
            allVertex.add(graphPath.getGraph().getEdgeSource(edge));
            allVertex.add(graphPath.getGraph().getEdgeTarget(edge));
        }
        Collection<String> intersect =
                org.apache.commons.collections.CollectionUtils.intersection(vertex, allVertex);

        return intersect.size() == vertex.size();
    }

    private Graph<String, DefaultEdge> buildGraph(List<JoinRelation> joinRelations) {
        Graph<String, DefaultEdge> directedGraph = new DefaultUndirectedGraph<>(DefaultEdge.class);
        for (JoinRelation joinRelation : joinRelations) {
            directedGraph.addVertex(joinRelation.getLeft());
            directedGraph.addVertex(joinRelation.getRight());
            directedGraph.addEdge(joinRelation.getLeft(), joinRelation.getRight());
        }
        return directedGraph;
    }

    private SqlNode optimizeParseNode(SqlNode parserNode, EngineType engineType)
            throws SqlParseException {
        if (Objects.isNull(schema.getRuntimeOptions())
                || Objects.isNull(schema.getRuntimeOptions().getEnableOptimize())
                || !schema.getRuntimeOptions().getEnableOptimize()) {
            return parserNode;
        }

        SqlNode optimizeNode = null;
        SqlNode sqlNode = SqlParser.create(SemanticNode.getSql(parserNode, engineType),
                Configuration.getParserConfig(engineType)).parseStmt();
        if (Objects.nonNull(sqlNode)) {
            optimizeNode = SemanticNode.optimize(scope, schema, sqlNode, engineType);
        }

        if (Objects.nonNull(optimizeNode)) {
            return optimizeNode;
        }

        return parserNode;
    }

    private TableView render(OntologyQuery ontologyQuery, Set<ModelResp> dataModels,
                             SqlValidatorScope scope, S2CalciteSchema schema) throws Exception {
        SqlNode left = null;
        TableView leftTable = null;
        TableView outerTable = new TableView();
        Map<String, SqlNode> outerSelect = new HashMap<>();
        Map<String, String> beforeModels = new HashMap<>();
        EngineType engineType = EngineType.fromString(schema.getOntology().getDatabase().getType());

        // Sort models based on join relationships to respect join order
        List<JoinRelation> joinRelations = schema.getJoinRelations();
        List<ModelResp> orderedModels = sortModelsByJoinRelations(dataModels, joinRelations);

        // Create a mapping from semantic names to table field info
        Map<String, Map<String, FieldInfo>> semanticFieldsInfo = buildSemanticFieldInfo(orderedModels);

        // Create a mapping for join relationships
        Map<String, Map<String, List<JoinFieldInfo>>> joinFieldsInfo = buildJoinFieldsInfo(joinRelations);

        // Track table priorities based on join types
        Map<String, Integer> tablePriorities = calculateTablePriorities(joinRelations);

        for (ModelResp dataModel : orderedModels) {
            final Set<DimSchemaResp> queryDimensions = ontologyQuery.getDimensionsByModel(dataModel.getName());
            final Set<MetricSchemaResp> queryMetrics = ontologyQuery.getMetricsByModel(dataModel.getName());

            List<String> primary = new ArrayList<>();
            for (Identify identify : dataModel.getIdentifiers()) {
                primary.add(identify.getBizName());
            }

            TableView tableView = renderOne(queryMetrics, queryDimensions, dataModel, scope, schema);
            log.info("tableView {}", StringUtils.normalizeSpace(tableView.getTable().toString()));
            String alias = dataModel.getName() + Constants.JOIN_TABLE_SURFIX;
            tableView.setAlias(alias);
            tableView.setPrimary(primary);
            tableView.setDataModel(dataModel);

            // Add current model to priority map if not present
            tablePriorities.putIfAbsent(dataModel.getName(), 0);

            // Process each field in the current table
            for (String field : tableView.getFields()) {
                // Find semantic name for this field
                String semanticName = findSemanticName(field, dataModel);

                // Determine if this is a join field
                if (joinFieldsInfo.containsKey(dataModel.getName()) && joinFieldsInfo.get(dataModel.getName()).containsKey(field)) {
                    processJoinField(field, dataModel.getName(), alias, orderedModels, joinFieldsInfo,
                            tablePriorities, outerSelect, scope, engineType);
                } else if (semanticFieldsInfo.containsKey(semanticName) && semanticFieldsInfo.get(semanticName).size() > 1 && leftTable != null) {
                    // Field has same semantics in multiple tables
                    processSemanticField(field, semanticName, dataModel.getName(), alias,
                            beforeModels, semanticFieldsInfo, tablePriorities, outerSelect, scope, engineType);
                } else {
                    // Field only in current table
                    outerSelect.put(field, SemanticNode.parse(alias + "." + field, scope, engineType));
                }
            }

            if (left == null) {
                left = SemanticNode.buildAs(tableView.getAlias(), getTable(tableView));
            } else {
                left = buildJoin(left, leftTable, tableView, beforeModels, dataModel, schema, scope);
            }
            leftTable = tableView;
            beforeModels.put(dataModel.getName(), leftTable.getAlias());
        }

        for (Map.Entry<String, SqlNode> entry : outerSelect.entrySet()) {
            outerTable.getSelect().add(entry.getValue());
        }

        outerTable.setTable(left);
        return outerTable;
    }

    /**
     * Build a mapping from semantic names to table field info
     */
    private Map<String, Map<String, FieldInfo>> buildSemanticFieldInfo(List<ModelResp> models) {
        Map<String, Map<String, FieldInfo>> semanticFieldMap = new HashMap<>();

        for (ModelResp model : models) {
            String modelName = model.getName();

            // Process dimensions
            for (Dimension dimension : model.getModelDetail().getDimensions()) {
                String fieldName = dimension.getFieldName();
                String semanticName = dimension.getName();
                if (StringUtils.isBlank(semanticName)) {
                    semanticName = fieldName;
                }
                semanticFieldMap.computeIfAbsent(semanticName, k -> new HashMap<>())
                        .put(modelName, new FieldInfo(modelName, fieldName, false, 0));
            }

            // Process measures
            for (Measure measure : model.getModelDetail().getMeasures()) {
                String fieldName = measure.getFieldName();
                String semanticName = measure.getName();
                if (StringUtils.isBlank(semanticName)) {
                    semanticName = fieldName;
                }
                semanticFieldMap.computeIfAbsent(semanticName, k -> new HashMap<>())
                        .put(modelName, new FieldInfo(modelName, fieldName, false, 0));
            }

            // Process identifiers
            for (Identify identify : model.getIdentifiers()) {
                String fieldName = identify.getFieldName();
                String semanticName = identify.getName();
                if (StringUtils.isBlank(semanticName)) {
                    semanticName = fieldName;
                }
                semanticFieldMap.computeIfAbsent(semanticName, k -> new HashMap<>())
                        .put(modelName, new FieldInfo(modelName, fieldName, true, 0));
            }
        }

        return semanticFieldMap;
    }

    /**
     * Build a mapping for join relationships
     */
    private Map<String, Map<String, List<JoinFieldInfo>>> buildJoinFieldsInfo(List<JoinRelation> joinRelations) {
        Map<String, Map<String, List<JoinFieldInfo>>> joinFieldsMap = new HashMap<>();

        if (CollectionUtils.isEmpty(joinRelations)) {
            return joinFieldsMap;
        }

        for (JoinRelation joinRelation : joinRelations) {
            if (CollectionUtils.isEmpty(joinRelation.getJoinCondition())) {
                continue;
            }

            String leftModel = joinRelation.getLeft();
            String rightModel = joinRelation.getRight();
            String joinType = joinRelation.getJoinType();

            for (Triple<String, String, String> condition : joinRelation.getJoinCondition()) {
                String leftField = condition.getLeft();
                String operator = condition.getMiddle();
                String rightField = condition.getRight();

                if (operator.equals("=")) {
                    // Add left -> right mapping
                    joinFieldsMap.computeIfAbsent(leftModel, k -> new HashMap<>())
                            .computeIfAbsent(leftField, k -> new ArrayList<>())
                            .add(new JoinFieldInfo(leftModel, leftField, rightModel, rightField, joinType));

                    // Add right -> left mapping
                    joinFieldsMap.computeIfAbsent(rightModel, k -> new HashMap<>())
                            .computeIfAbsent(rightField, k -> new ArrayList<>())
                            .add(new JoinFieldInfo(rightModel, rightField, leftModel, leftField, joinType));
                }
            }
        }

        return joinFieldsMap;
    }

    /**
     * Calculate table priorities based on join types
     */
    private Map<String, Integer> calculateTablePriorities(List<JoinRelation> joinRelations) {
        Map<String, Integer> tablePriorities = new HashMap<>();

        if (CollectionUtils.isEmpty(joinRelations)) {
            return tablePriorities;
        }

        for (JoinRelation joinRelation : joinRelations) {
            String leftModel = joinRelation.getLeft();
            String rightModel = joinRelation.getRight();
            String joinType = joinRelation.getJoinType();

            if ("left join".equalsIgnoreCase(joinType)) {
                // Left table has higher priority in LEFT JOIN
                tablePriorities.put(leftModel, tablePriorities.getOrDefault(leftModel, 0) + 10);
                tablePriorities.put(rightModel, tablePriorities.getOrDefault(rightModel, 0) + 5);
            } else if ("right join".equalsIgnoreCase(joinType)) {
                // Right table has higher priority in RIGHT JOIN
                tablePriorities.put(rightModel, tablePriorities.getOrDefault(rightModel, 0) + 10);
                tablePriorities.put(leftModel, tablePriorities.getOrDefault(leftModel, 0) + 5);
            } else if ("inner join".equalsIgnoreCase(joinType) || "full join".equalsIgnoreCase(joinType)) {
                // Left table has slightly higher priority in INNER/FULL JOIN
                tablePriorities.put(leftModel, tablePriorities.getOrDefault(leftModel, 0) + 7);
                tablePriorities.put(rightModel, tablePriorities.getOrDefault(rightModel, 0) + 6);
            } else {
                // Default case, equal priority
                tablePriorities.put(leftModel, tablePriorities.getOrDefault(leftModel, 0) + 5);
                tablePriorities.put(rightModel, tablePriorities.getOrDefault(rightModel, 0) + 5);
            }
        }

        return tablePriorities;
    }

    /**
     * Find semantic name for a field
     */
    private String findSemanticName(String field, ModelResp dataModel) {
        // Try to find in dimensions
        for (Dimension dimension : dataModel.getModelDetail().getDimensions()) {
            if (dimension.getFieldName().equalsIgnoreCase(field) ||
                    dimension.getBizName().equalsIgnoreCase(field)) {
                return dimension.getName();
            }
        }

        // Try to find in measures
        for (Measure measure : dataModel.getModelDetail().getMeasures()) {
            if (measure.getFieldName().equalsIgnoreCase(field) ||
                    measure.getBizName().equalsIgnoreCase(field)) {
                return measure.getName();
            }
        }

        // Try to find in identifiers // ok
        for (Identify identify : dataModel.getIdentifiers()) {
            if (identify.getFieldName().equalsIgnoreCase(field) ||
                    identify.getBizName().equalsIgnoreCase(field)) {
                return identify.getName();
            }
        }

        // If not found, use field name as backup
        return field;
    }

    /**
     * Process a field that is part of join conditions
     */
    private void processJoinField(String field, String modelName, String alias,
            List<ModelResp> dataModels, Map<String, Map<String, List<JoinFieldInfo>>> joinFieldsInfo,
            Map<String, Integer> tablePriorities, Map<String, SqlNode> outerSelect,
            SqlValidatorScope scope, EngineType engineType) throws Exception {

        List<FieldInfo> fieldPairs = new ArrayList<>();

        // Add the current field
        fieldPairs.add(new FieldInfo(modelName, field, false, tablePriorities.getOrDefault(modelName, 0)));
        //System.out.println("modelName:" + modelName);

        // Add fields from related models that have already been processed
        List<JoinFieldInfo> modelJoinsInfo = joinFieldsInfo.get(modelName).get(field);
        for (JoinFieldInfo joinInfo : modelJoinsInfo) {
            String sourceModel = joinInfo.sourceModel;
            String sourceField = joinInfo.sourceField;
            String targetModel = joinInfo.targetModel;
            String targetField = joinInfo.targetField;
            String joinType = joinInfo.joinType;

            for (ModelResp model : dataModels) {
                if (model.getName().equals(targetModel)) {
                    int priority = tablePriorities.getOrDefault(targetModel, 0);
                    // Adjust priority based on join type
                    if ("inner join".equalsIgnoreCase(joinType) && modelName.equals(sourceModel)) {
                        priority -= 5; // Left table in INNER JOIN gets higher priority
                    } else if ("left join".equalsIgnoreCase(joinType) && modelName.equals(sourceModel)) {
                        priority -= 5; // Left table in LEFT JOIN gets higher priority
                    } else if ("right join".equalsIgnoreCase(joinType) && modelName.equals(joinInfo.targetModel)) {
                        priority += 5; // Right table in RIGHT JOIN gets higher priority
                    }
                    // if target model and field not exists, add it
                    if (fieldPairs.stream().noneMatch(f -> f.modelName.equals(targetModel) && f.fieldName.equals(targetField))) {
                        fieldPairs.add(new FieldInfo(targetModel, targetField, false, priority));
                        //System.out.println("targetModel:" + targetModel);
                    }
                }
            }
        }

        // Sort by priority (higher first)
        fieldPairs.sort((a, b) -> Integer.compare(b.priority, a.priority));

        // Build COALESCE if we have multiple fields
        if (fieldPairs.size() > 1) {
            List<SqlNode> coalesceArgs = new ArrayList<>();

            for (FieldInfo pair : fieldPairs) {
                coalesceArgs.add(SemanticNode.parse(
                        pair.modelName + Constants.JOIN_TABLE_SURFIX + "." + pair.fieldName, scope, engineType));
            }

            SqlNode coalesceNode = SqlStdOperatorTable.COALESCE.createCall(
                    SqlParserPos.ZERO,
                    coalesceArgs.toArray(new SqlNode[0])
            );

            SqlNode fieldNode = SqlStdOperatorTable.AS.createCall(
                    SqlParserPos.ZERO,
                    coalesceNode,
                    new SqlIdentifier(field, SqlParserPos.ZERO)
            );

            outerSelect.put(field, fieldNode);
            log.info("processJoinField: {}", outerSelect);
        } else {
            // Just use current field
            outerSelect.put(field, SemanticNode.parse(alias + "." + field, scope, engineType));
        }
    }

    /**
     * Process a field that has same semantics in multiple tables
     */
    private void processSemanticField(String field, String semanticName, String modelName,
            String alias, Map<String, String> beforeModels,
            Map<String, Map<String, FieldInfo>> semanticFieldMap, Map<String, Integer> tablePriorities,
            Map<String, SqlNode> outerSelect, SqlValidatorScope scope, EngineType engineType) throws Exception {

        Map<String, FieldInfo> tableFieldMap = semanticFieldMap.get(semanticName);
        List<FieldInfo> fieldPairs = new ArrayList<>();

        // Add the current field
        fieldPairs.add(new FieldInfo(modelName, field, false, tablePriorities.getOrDefault(modelName, 0)));

        // Add fields from other models with same semantics that have already been processed
        for (Map.Entry<String, FieldInfo> entry : tableFieldMap.entrySet()) {
            String otherModel = entry.getKey();
            FieldInfo fieldInfo = entry.getValue();

            if (!otherModel.equals(modelName) && beforeModels.containsKey(otherModel)) {
                int priority = tablePriorities.getOrDefault(otherModel, 0);
                // // Adjust priority for primary keys
                // if (fieldInfo.isPrimary) {
                //     priority += 3;
                // }
                fieldPairs.add(new FieldInfo(otherModel, fieldInfo.fieldName, fieldInfo.isPrimary, priority));
            }
        }

        // Sort by priority (higher first)
        fieldPairs.sort((a, b) -> Integer.compare(b.priority, a.priority));

        // Build COALESCE if we have multiple fields
        if (fieldPairs.size() > 1) {
            List<SqlNode> coalesceArgs = new ArrayList<>();

            for (FieldInfo pair : fieldPairs) {
                coalesceArgs.add(SemanticNode.parse(
                        pair.modelName + Constants.JOIN_TABLE_SURFIX + "." + pair.fieldName, scope, engineType));
            }

            SqlNode coalesceNode = SqlStdOperatorTable.COALESCE.createCall(
                    SqlParserPos.ZERO,
                    coalesceArgs.toArray(new SqlNode[0])
            );

            SqlNode fieldNode = SqlStdOperatorTable.AS.createCall(
                    SqlParserPos.ZERO,
                    coalesceNode,
                    new SqlIdentifier(field, SqlParserPos.ZERO)
            );

            outerSelect.put(field, fieldNode);
        } else {
            // Just use current field
            outerSelect.put(field, SemanticNode.parse(alias + "." + field, scope, engineType));
        }
    }

    /**
     * Sort models based on join relationships to maintain proper join order
     */
    private List<ModelResp> sortModelsByJoinRelations(Set<ModelResp> dataModels, List<JoinRelation> joinRelations) {
        if (CollectionUtils.isEmpty(joinRelations) || dataModels.size() <= 1) {
            return new ArrayList<>(dataModels);
        }

        // Create a map of model names to their ModelResp objects
        Map<String, ModelResp> modelMap = dataModels.stream()
                .collect(Collectors.toMap(ModelResp::getName, m -> m));

        List<ModelResp> orderedModels = new ArrayList<>();
        Set<String> processedModels = new HashSet<>();

        Set<String> leftModels = joinRelations.stream()
                .map(JoinRelation::getLeft)
                .collect(Collectors.toSet());
        Set<String> rightModels = joinRelations.stream()
                .map(JoinRelation::getRight)
                .collect(Collectors.toSet());

        // Find potential starting models
        Set<String> startModels = new HashSet<>(leftModels);
        startModels.removeAll(rightModels);

        // If no clear left-most model, look for models with LEFT JOIN type as left
        if (startModels.isEmpty()) {
            for (JoinRelation relation : joinRelations) {
                if ("left join".equalsIgnoreCase(relation.getJoinType())) {
                    startModels.add(relation.getLeft());
                }
            }
        }

        // If still no starting model, just use the first available model
        String startModel = startModels.isEmpty() ?
                dataModels.iterator().next().getName() :
                startModels.iterator().next();

        // Add start model if it's in our dataset
        if (modelMap.containsKey(startModel)) {
            orderedModels.add(modelMap.get(startModel));
            processedModels.add(startModel);
        }

        // Build the rest of the order by following join relationships
        while (orderedModels.size() < dataModels.size()) {
            boolean addedModel = false;

            // First try to follow explicit join relationships
            for (JoinRelation relation : joinRelations) {
                String leftModel = relation.getLeft();
                String rightModel = relation.getRight();

                // If we've processed the left model but not the right, add the right
                if (processedModels.contains(leftModel) && !processedModels.contains(rightModel)
                        && modelMap.containsKey(rightModel)) {
                    orderedModels.add(modelMap.get(rightModel));
                    processedModels.add(rightModel);
                    addedModel = true;
                    break;
                }

                // For RIGHT JOIN, if we've processed the right but not the left, add the left
                if ("right join".equalsIgnoreCase(relation.getJoinType())
                        && processedModels.contains(rightModel) && !processedModels.contains(leftModel)
                        && modelMap.containsKey(leftModel)) {
                    orderedModels.add(modelMap.get(leftModel));
                    processedModels.add(leftModel);
                    addedModel = true;
                    break;
                }
            }

            // If we couldn't add a model based on relationships, add any remaining model
            if (!addedModel) {
                for (ModelResp model : dataModels) {
                    if (!processedModels.contains(model.getName())) {
                        orderedModels.add(model);
                        processedModels.add(model.getName());
                        break;
                    }
                }
            }

            // If we haven't added any models in this iteration, break to avoid infinite loop
            if (!addedModel && processedModels.size() == orderedModels.size()) {
                // Add any remaining models
                for (ModelResp model : dataModels) {
                    if (!processedModels.contains(model.getName())) {
                        orderedModels.add(model);
                        processedModels.add(model.getName());
                    }
                }
            }
        }

        return orderedModels;
    }

    private SqlNode getTable(TableView tableView) {
        return SemanticNode.getTable(tableView.getTable());
    }

    private SqlNode buildJoin(SqlNode leftNode, TableView leftTable, TableView rightTable,
            Map<String, String> before, ModelResp dataModel, S2CalciteSchema schema,
            SqlValidatorScope scope) throws Exception {
        EngineType engineType = EngineType.fromString(schema.getOntology().getDatabase().getType());
        SqlNode condition =
                getCondition(leftTable, rightTable, dataModel, schema, scope, engineType);
        SqlLiteral sqlLiteral = SemanticNode.getJoinSqlLiteral("");
        JoinRelation matchJoinRelation = getMatchJoinRelation(before, rightTable, schema);
        SqlNode joinRelationCondition;
        if (!org.apache.commons.collections.CollectionUtils
                .isEmpty(matchJoinRelation.getJoinCondition())) {
            sqlLiteral = SemanticNode.getJoinSqlLiteral(matchJoinRelation.getJoinType());
            joinRelationCondition = getCondition(matchJoinRelation, scope, engineType);
            condition = joinRelationCondition;
        }

        return new SqlJoin(SqlParserPos.ZERO, leftNode,
                SqlLiteral.createBoolean(false, SqlParserPos.ZERO), sqlLiteral,
                SemanticNode.buildAs(rightTable.getAlias(), getTable(rightTable)),
                SqlLiteral.createSymbol(JoinConditionType.ON, SqlParserPos.ZERO), condition);
    }

    private JoinRelation getMatchJoinRelation(Map<String, String> before, TableView tableView,
            S2CalciteSchema schema) {
        JoinRelation matchJoinRelation = JoinRelation.builder().build();
        if (!CollectionUtils.isEmpty(schema.getJoinRelations())) {
            for (JoinRelation joinRelation : schema.getJoinRelations()) {
                log.info("joinRelation: {}", joinRelation);
                log.info("before: {}", before);
                // Case 1: Current table is the right side of join relation
                if (joinRelation.getRight().equalsIgnoreCase(tableView.getDataModel().getName())
                        && before.containsKey(joinRelation.getLeft())) {
                    // This is the correct direction - use join conditions as is
                    matchJoinRelation.setJoinCondition(joinRelation.getJoinCondition().stream()
                            .map(r -> Triple.of(
                                    before.get(joinRelation.getLeft()) + "." + r.getLeft(),
                                    r.getMiddle(), tableView.getAlias() + "." + r.getRight()))
                            .collect(Collectors.toList()));
                    matchJoinRelation.setJoinType(joinRelation.getJoinType());
                    // Found correct join direction, prioritize this and exit early
                    // return matchJoinRelation;
                }
                // Case 2: Current table is the left side of join relation
                else if (joinRelation.getLeft().equalsIgnoreCase(tableView.getDataModel().getName())
                        && before.containsKey(joinRelation.getRight())) {
                    String joinType = joinRelation.getJoinType();
                    // // Need to flip join type if it's LEFT or RIGHT join
                    // if ("left join".equalsIgnoreCase(joinType)) {
                    //     joinType = "right join";
                    // } else if ("right join".equalsIgnoreCase(joinType)) {
                    //     joinType = "left join";
                    // }

                    List<Triple<String, String, String>> candidateJoinCon = joinRelation
                            .getJoinCondition().stream()
                            .map(r -> {
                                String operator = r.getMiddle();
                                operator = switch (operator.trim().toUpperCase()) {
                                    // case "<" -> ">";
                                    // case "<=" -> ">=";
                                    // case ">" -> "<";
                                    // case ">=" -> "<=";
                                    default -> operator;
                                };
                                return Triple.of(
                                        before.get(joinRelation.getRight()) + "." + r.getRight(),
                                        operator, tableView.getAlias() + "." + r.getLeft());
                            })
                            .collect(Collectors.toList());

                    // Only use this join relation if we don't already have a direct match
                    // or if this one has more conditions
                    if (matchJoinRelation.getJoinCondition() == null || candidateJoinCon
                            .size() > matchJoinRelation.getJoinCondition().size()) {
                        matchJoinRelation.setJoinCondition(candidateJoinCon);
                        matchJoinRelation.setJoinType(joinRelation.getJoinType());
                    }
                }
            }
        }
        return matchJoinRelation;
    }

    private SqlNode getCondition(JoinRelation joinRelation, SqlValidatorScope scope,
            EngineType engineType) throws Exception {
        SqlNode condition = null;
        for (Triple<String, String, String> con : joinRelation.getJoinCondition()) {
            List<SqlNode> ons = new ArrayList<>();
            ons.add(SemanticNode.parse(con.getLeft(), scope, engineType));
            ons.add(SemanticNode.parse(con.getRight(), scope, engineType));
            if (Objects.isNull(condition)) {
                condition = new SqlBasicCall(SemanticNode.getBinaryOperator(con.getMiddle()), ons,
                        SqlParserPos.ZERO, null);
                continue;
            }
            SqlNode addCondition = new SqlBasicCall(SemanticNode.getBinaryOperator(con.getMiddle()),
                    ons, SqlParserPos.ZERO, null);
            condition = new SqlBasicCall(SqlStdOperatorTable.AND,
                    new ArrayList<>(Arrays.asList(condition, addCondition)), SqlParserPos.ZERO,
                    null);
        }
        return condition;
    }

    private SqlNode getCondition(TableView left, TableView right, ModelResp dataModel,
            S2CalciteSchema schema, SqlValidatorScope scope, EngineType engineType)
            throws Exception {

        Set<String> selectLeft = SemanticNode.getSelect(left.getTable());
        Set<String> selectRight = SemanticNode.getSelect(right.getTable());
        selectLeft.retainAll(selectRight);
        SqlNode condition = null;
        for (String on : selectLeft) {
            if (!isDimension(on, dataModel, schema)) {
                continue;
            }
            if (isForeign(on, left.getDataModel().getIdentifiers())) {
                if (!isPrimary(on, right.getDataModel().getIdentifiers())) {
                    continue;
                }
            }
            if (isForeign(on, right.getDataModel().getIdentifiers())) {
                if (!isPrimary(on, left.getDataModel().getIdentifiers())) {
                    continue;
                }
            }
            List<SqlNode> ons = new ArrayList<>();
            ons.add(SemanticNode.parse(left.getAlias() + "." + on, scope, engineType));
            ons.add(SemanticNode.parse(right.getAlias() + "." + on, scope, engineType));
            if (condition == null) {
                condition =
                        new SqlBasicCall(SqlStdOperatorTable.EQUALS, ons, SqlParserPos.ZERO, null);
                continue;
            }
            SqlNode addCondition =
                    new SqlBasicCall(SqlStdOperatorTable.EQUALS, ons, SqlParserPos.ZERO, null);
            condition = new SqlBasicCall(SqlStdOperatorTable.AND,
                    new ArrayList<>(Arrays.asList(condition, addCondition)), SqlParserPos.ZERO,
                    null);
        }
        return condition;
    }

    public static TableView renderOne(Set<MetricSchemaResp> queryMetrics,
                                      Set<DimSchemaResp> queryDimensions, ModelResp dataModel, SqlValidatorScope scope,
                                      S2CalciteSchema schema) {
        TableView tableView = new TableView();
        EngineType engineType = EngineType.fromString(schema.getOntology().getDatabase().getType());
        Set<String> queryFields = tableView.getFields();
        if (Objects.nonNull(queryMetrics)) {
            queryMetrics.forEach(m -> queryFields.addAll(m.getFields()));
        }
        if (Objects.nonNull(queryDimensions)) {
            queryDimensions.forEach(d -> queryFields.addAll(d.getFields()));
        }

        try {
            for (String field : queryFields) {
                tableView.getSelect().add(SemanticNode.parse(field, scope, engineType));
            }
            tableView.setTable(DataModelNode.build(dataModel, scope));
        } catch (Exception e) {
            log.error("Failed to create sqlNode for data model {}", dataModel);
        }

        log.info("renderOne tableView: {}", tableView);
        return tableView;
    }

    private static boolean isDimension(String name, ModelResp dataModel, S2CalciteSchema schema) {
        Optional<Dimension> dimension = dataModel.getModelDetail().getDimensions().stream()
                .filter(d -> d.getName().equalsIgnoreCase(name)).findFirst();
        if (dimension.isPresent()) {
            return true;
        }
        Optional<Identify> identify = dataModel.getIdentifiers().stream()
                .filter(i -> i.getBizName().equalsIgnoreCase(name)).findFirst();
        if (identify.isPresent()) {
            return true;
        }
        if (schema.getDimensions().containsKey(dataModel.getName())) {
            Optional<DimSchemaResp> dataSourceDim = schema.getDimensions().get(dataModel.getName())
                    .stream().filter(d -> d.getName().equalsIgnoreCase(name)).findFirst();
            return dataSourceDim.isPresent();
        }
        return false;
    }

    private static boolean isForeign(String name, List<Identify> identifies) {
        Optional<Identify> identify =
                identifies.stream().filter(i -> i.getBizName().equalsIgnoreCase(name)).findFirst();
        if (identify.isPresent()) {
            return IdentifyType.foreign.equals(identify.get().getType());
        }
        return false;
    }

    private static boolean isPrimary(String name, List<Identify> identifies) {
        Optional<Identify> identify =
                identifies.stream().filter(i -> i.getBizName().equalsIgnoreCase(name)).findFirst();
        if (identify.isPresent()) {
            return IdentifyType.primary.equals(identify.get().getType());
        }
        return false;
    }
}
