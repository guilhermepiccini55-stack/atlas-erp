package com.atlas.erp.users.dto;

import com.atlas.erp.users.Role;
import java.util.List;

/**
 * Métricas da tela de usuários: KPIs com variação vs. mês anterior e sparkline (últimos 6 meses),
 * distribuição por papel e série mensal de usuários ativos.
 */
public record UserStatsResponse(
        Kpi totalUsers,
        Kpi activeUsers,
        Kpi newThisMonth,
        Kpi pendingInvites,
        List<RoleShare> byRole,
        List<MonthPoint> activity) {

    public record Kpi(long value, double changePercent, List<Long> sparkline) {
    }

    public record RoleShare(Role role, long count, double percent) {
    }

    /** month no formato yyyy-MM. */
    public record MonthPoint(String month, long value) {
    }
}
