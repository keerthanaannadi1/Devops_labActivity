package com.elms.config;

import com.elms.leave.LeaveType;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "elms.leave")
public class LeaveQuotaProperties {

    private Map<LeaveType, Integer> quota = Map.of(
            LeaveType.CASUAL, 12,
            LeaveType.SICK, 10,
            LeaveType.EARNED, 15,
            LeaveType.COMP_OFF, 5);

    public Map<LeaveType, Integer> getQuota() {
        return quota;
    }

    public void setQuota(Map<LeaveType, Integer> quota) {
        this.quota = quota;
    }
}
