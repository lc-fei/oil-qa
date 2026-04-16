package org.example.springboot.dto;

import lombok.Getter;

@Getter
public class GraphOptionItemResponse {

    private final String value;
    private final String label;
    private final String typeCode;
    private final String typeName;

    public GraphOptionItemResponse(String value, String label) {
        this(value, label, null, null);
    }

    public GraphOptionItemResponse(String value, String label, String typeCode, String typeName) {
        this.value = value;
        this.label = label;
        this.typeCode = typeCode;
        this.typeName = typeName;
    }
}
