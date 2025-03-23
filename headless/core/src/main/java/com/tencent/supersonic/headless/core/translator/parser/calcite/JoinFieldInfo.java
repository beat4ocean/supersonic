package com.tencent.supersonic.headless.core.translator.parser.calcite;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Helper class to store join field information
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class JoinFieldInfo {
    String sourceModel;
    String sourceField;
    String targetModel;
    String targetField;
    String joinType;
}