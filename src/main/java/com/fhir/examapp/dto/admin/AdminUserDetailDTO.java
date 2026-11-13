package com.fhir.examapp.dto.admin;

import com.fhir.examapp.dto.DashboardDTO;
import com.fhir.examapp.dto.MaterialEvolutionPointDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminUserDetailDTO {
    private AdminUserDTO user;
    private AdminUserKpiDTO kpi;
    private DashboardDTO dashboard;
    private List<Map<String, Object>> attempts = new ArrayList<>();
    private List<MaterialEvolutionPointDTO> evolution = new ArrayList<>();
    private List<AdminFailedQuestionDTO> mostFailedQuestions = new ArrayList<>();
    private Map<String, Object> srsActivity;

    public AdminUserDetailDTO() {
    }

    public AdminUserDTO getUser() { return user; }
    public void setUser(AdminUserDTO user) { this.user = user; }

    public AdminUserKpiDTO getKpi() { return kpi; }
    public void setKpi(AdminUserKpiDTO kpi) { this.kpi = kpi; }

    public DashboardDTO getDashboard() { return dashboard; }
    public void setDashboard(DashboardDTO dashboard) { this.dashboard = dashboard; }

    public List<Map<String, Object>> getAttempts() { return attempts; }
    public void setAttempts(List<Map<String, Object>> attempts) { this.attempts = attempts; }

    public List<MaterialEvolutionPointDTO> getEvolution() { return evolution; }
    public void setEvolution(List<MaterialEvolutionPointDTO> evolution) { this.evolution = evolution; }

    public List<AdminFailedQuestionDTO> getMostFailedQuestions() { return mostFailedQuestions; }
    public void setMostFailedQuestions(List<AdminFailedQuestionDTO> mostFailedQuestions) { this.mostFailedQuestions = mostFailedQuestions; }

    public Map<String, Object> getSrsActivity() { return srsActivity; }
    public void setSrsActivity(Map<String, Object> srsActivity) { this.srsActivity = srsActivity; }
}
