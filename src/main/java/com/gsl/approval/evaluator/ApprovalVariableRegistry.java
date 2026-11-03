package com.gsl.approval.evaluator;

import com.gsl.approval.model.ApApprovalVariable;
import com.gsl.approval.model.ApprovalCondition;
import com.gsl.approval.model.ApprovalDataType;
import com.gsl.approval.model.ApprovalEntityType;
import com.gsl.approval.model.ApprovalVariable;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The rule variables the framework can evaluate for each entity. Adding an entity's variables is one enum plus one line here. Entities with
 * no registered variables can still resolve to their default rule, but any rule that names a variable fails with
 * {@code UNKNOWN_VARIABLE} instead of being guessed. UDF variables ({@code UDF...}) are tenant-defined, so their type comes from the condition.
 */
public final class ApprovalVariableRegistry {

    private static final String UDF_PREFIX = "UDF";

    private static final Map<ApprovalEntityType, Map<String, ApprovalVariable>> BY_ENTITY = Map.of(
            ApprovalEntityType.AP, index(ApApprovalVariable.values()));

    private ApprovalVariableRegistry() {}

    /** The variable a condition refers to for {@code entity}, or empty when the framework has no extractor for it. */
    public static Optional<ApprovalVariable> find(ApprovalEntityType entity, ApprovalCondition condition) {
        String code = condition.getVarCode();
        ApprovalVariable known = BY_ENTITY.getOrDefault(entity, Map.of()).get(code);
        if (known != null) {
            return Optional.of(known);
        }
        if (code != null && code.toUpperCase().startsWith(UDF_PREFIX) && condition.getDataType() != null) {
            return Optional.of(udf(code, condition));
        }
        return Optional.empty();
    }

    private static ApprovalVariable udf(String code, ApprovalCondition condition) {
        ApprovalDataType type = ApprovalDataType.fromWire(condition.getDataType());
        String basis = condition.getBasis() == null ? "H" : condition.getBasis();
        return new ApprovalVariable() {
            @Override
            public String varCode() {
                return code;
            }

            @Override
            public String basis() {
                return basis;
            }

            @Override
            public ApprovalDataType dataType() {
                return type;
            }
        };
    }

    private static Map<String, ApprovalVariable> index(ApprovalVariable[] variables) {
        return Arrays.stream(variables).collect(Collectors.toUnmodifiableMap(ApprovalVariable::varCode, Function.identity()));
    }
}
