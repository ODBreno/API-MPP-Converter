package com.breno.mpp_converter.dto;

import java.util.Date;
import java.util.List;

public class TaskDTO {
    private Integer id;
    private String name;
    private Date startDate;
    private Date finishDate;
    private String duration;
    private Double percentageComplete;
    private List<Integer> predecessors;
    private Integer parentId;

    // Getters e Setters (essenciais para o Jackson converter para JSON)
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getFinishDate() {
        return finishDate;
    }

    public void setFinishDate(Date finishDate) {
        this.finishDate = finishDate;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Double getPercentageComplete() {
        return percentageComplete;
    }

    public void setPercentageComplete(Double percentageComplete) {
        this.percentageComplete = percentageComplete;
    }

    public List<Integer> getPredecessors() {
        return predecessors;
    }

    public void setPredecessors(List<Integer> predecessors) {
        this.predecessors = predecessors;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer parentId) {
        this.parentId = parentId;
    }
}