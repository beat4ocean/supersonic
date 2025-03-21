package com.tencent.supersonic.headless.core.translator.parser.calcite;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.tencent.supersonic.headless.api.pojo.response.ModelResp;
import lombok.Data;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlNodeList;
import org.apache.calcite.sql.SqlSelect;
import org.apache.calcite.sql.parser.SqlParserPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class TableView {

    private Set<String> fields = Sets.newHashSet();
    private List<SqlNode> select = Lists.newArrayList();
    private SqlNodeList order;
    private SqlNode fetch;
    private SqlNode offset;
    private SqlNode table;
    private String alias;
    private List<String> primary;
    private ModelResp dataModel;
    
    // Static fields for coalesce functionality
    private static boolean needsCoalesce = false;
    private static List<Map<String, String>> joinInfoList = new ArrayList<>();

    public SqlNode build() {
        List<SqlNode> selectNodeList = new ArrayList<>();
        if (select.isEmpty()) {
            return new SqlSelect(SqlParserPos.ZERO, null,
                    new SqlNodeList(SqlNodeList.SINGLETON_STAR, SqlParserPos.ZERO), table, null,
                    null, null, null, null, order, offset, fetch, null);
        } else {
            selectNodeList.addAll(select);
            return new SqlSelect(SqlParserPos.ZERO, null,
                    new SqlNodeList(selectNodeList, SqlParserPos.ZERO), table, null, null, null,
                    null, null, order, offset, fetch, null);
        }
    }
    
    /**
     * Set whether coalesce is needed for this join
     * @param needs boolean indicating if coalesce is needed
     */
    public static void setNeedsCoalesce(boolean needs) {
        needsCoalesce = needs;
    }
    
    /**
     * Check if coalesce is needed for joins
     * @return true if coalesce is needed
     */
    public static boolean isNeedsCoalesce() {
        return needsCoalesce;
    }
    
    /**
     * Add join information for coalesce processing
     * @param joinInfo Map containing join information
     */
    public static void addJoinInfo(Map<String, String> joinInfo) {
        if (joinInfoList == null) {
            joinInfoList = new ArrayList<>();
        }
        joinInfoList.add(joinInfo);
    }
    
    /**
     * Get all join information
     * @return List of join information maps
     */
    public static List<Map<String, String>> getJoinInfoList() {
        return joinInfoList;
    }
}
