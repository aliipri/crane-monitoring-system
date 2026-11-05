package ru.crain.service;

import org.springframework.stereotype.Service;
import ru.crain.model.LoadProfile;
import ru.crain.model.VisualInspection;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class MinerDamageCalculator {

    public BigDecimal calculatePeriodDamage(LoadProfile profile, VisualInspection inspection) {
        double damage = damageForRange(profile, profile.getLoadLt40(), 0.30)
                + damageForRange(profile, profile.getLoad40To80(), 0.60)
                + damageForRange(profile, profile.getLoad80To100(), 0.90)
                + damageForRange(profile, profile.getLoadGt100(), 1.10);
        damage *= 1.0 + 0.35 * visualConditionIndex(inspection);
        return BigDecimal.valueOf(damage).setScale(4, RoundingMode.HALF_UP);
    }

    public BigDecimal accumulate(BigDecimal previousDamage, BigDecimal periodDamage) {
        return previousDamage.add(periodDamage).setScale(4, RoundingMode.HALF_UP);
    }

    private double damageForRange(LoadProfile profile, int percent, double loadCoefficient) {
        double cyclesInRange = profile.getCyclesTotal() * percent / 100.0;
        double allowableCycles = 8_000.0 / Math.pow(loadCoefficient, 3);
        return cyclesInRange / allowableCycles;
    }

    private double visualConditionIndex(VisualInspection inspection) {
        if (inspection == null) {
            return 0.0;
        }
        return 0.25 * clamp(inspection.getBrokenWiresCount() / 20.0)
                + 0.25 * clamp(inspection.getDiameterLossPercent().doubleValue() / 10.0)
                + 0.15 * clamp(inspection.getCorrosionScore() / 5.0)
                + 0.15 * clamp(inspection.getAbrasionScore() / 5.0)
                + 0.15 * clamp(inspection.getDeformationScore() / 5.0)
                + 0.05 * clamp(inspection.getLubricationScore() / 5.0);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
