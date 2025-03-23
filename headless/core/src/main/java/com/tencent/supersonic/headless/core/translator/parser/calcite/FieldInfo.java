package com.tencent.supersonic.headless.core.translator.parser.calcite;

import com.tencent.supersonic.headless.core.translator.parser.Constants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Helper class to store field information
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FieldInfo {
    String modelName;      // 模型名称
    String fieldName;      // 字段名称
    boolean isPrimary;     // 是否主键
    int priority;          // 优先级
}