package com.nettoolkit.pro.models;

/**
 * یک ردیف اطلاعاتی ساده: برچسب + مقدار (+ رنگ وضعیت اختیاری)
 */
public class InfoItem {

    public enum Status { NONE, GOOD, WARNING, BAD }

    private final String label;
    private String value;
    private Status status;

    public InfoItem(String label, String value) {
        this(label, value, Status.NONE);
    }

    public InfoItem(String label, String value, Status status) {
        this.label = label;
        this.value = value;
        this.status = status;
    }

    public String getLabel() { return label; }
    public String getValue() { return value; }
    public Status getStatus() { return status; }

    public void setValue(String value) { this.value = value; }
    public void setStatus(Status status) { this.status = status; }
}
