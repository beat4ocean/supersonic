package com.tencent.supersonic.headless.api.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Identify {

    private String name;

    /** primary, foreign */
    private String type;

    private String bizName;

    private String fieldName;

    private Integer isCreateDimension = 0;

    public Identify(String name, String type, String bizName) {
        this.name = name;
        this.type = type;
        this.bizName = bizName;
    }

    public Identify(String name, String type, String bizName, Integer isCreateDimension) {
        this.name = name;
        this.type = type;
        this.bizName = bizName;
        this.isCreateDimension = isCreateDimension;
    }

    public String getFieldName() {
        return bizName;
    }
}
