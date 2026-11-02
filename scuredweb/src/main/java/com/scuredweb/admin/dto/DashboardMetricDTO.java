package com.scuredweb.admin.dto;

public class DashboardMetricDTO {
    private String label;
    private String value;
    private String delta;

    public DashboardMetricDTO() {
    }

    public DashboardMetricDTO(String label, String value, String delta) {
        this.label = label;
        this.value = value;
        this.delta = delta;
    }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getDelta() { return delta; }
    public void setDelta(String delta) { this.delta = delta; }
}


