package org.apache.mesos.scheduler.recovery;

/**
 * This class encapsulates utility methods useful for the processing of RecoveryRequirements.
 */
public class RecoveryRequirementUtils {
    public static boolean isPermanent(RecoveryRequirement recoveryRequirement) {
        return isPermanent(recoveryRequirement.getRecoveryType());
    }

    public static boolean isTransient(RecoveryRequirement recoveryRequirement) {
        return isTransient(recoveryRequirement.getRecoveryType());
    }

    public static boolean isPermanent(RecoveryRequirement.RecoveryType recoveryType) {
        return isOfType(RecoveryRequirement.RecoveryType.PERMANENT, recoveryType);
    }

    public static boolean isTransient(RecoveryRequirement.RecoveryType recoveryType) {
        return isOfType(RecoveryRequirement.RecoveryType.TRANSIENT, recoveryType);
    }

    private static boolean isOfType(RecoveryRequirement.RecoveryType expectedRecoveryType,
                                    RecoveryRequirement.RecoveryType actualRecoveryType) {
        return expectedRecoveryType.equals(actualRecoveryType);
    }
}
