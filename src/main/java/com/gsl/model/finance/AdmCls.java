package com.gsl.model.finance;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps to the {@code admcls} table — Sub-Ledger Classification master.
 *
 * <pre>
 * clscode | cmpcode | clsname           | clstype | ext
 * --------+---------+-------------------+---------+----
 *       1 |       1 | Customer          | C       | N   ← system-defined
 *       2 |       1 | Supplier          | S       | N
 *       3 |       1 | Transporter       | T       | N
 *       4 |       1 | Jobber            | J       | N
 *       5 |       1 | Agent             | A       | N
 *       6 |       1 | Others            | O       | N
 *       7 |       1 | Channel           | H       | N
 *      11 |       0 | TDS               | D       | N   ← user-defined
 *      12 |       0 | Staff             | E       | N
 * </pre>
 *
 * <ul>
 *   <li>{@code cmpcode = 1} → system-defined classification</li>
 *   <li>{@code cmpcode = 0} → user-defined classification</li>
 *   <li>{@code clstype = 'C'} → Customer type (drives Reference Site logic)</li>
 *   <li>{@code ext = 'N'} → active; {@code 'Y'} → inactive</li>
 * </ul>
 *
 * <p>Join to sub-ledger: {@code finsl.clscode = admcls.clscode}
 */
@Data
@NoArgsConstructor
public class AdmCls {

    /** Primary key — classification code. Matches finsl.clscode. */
    private long clscode;

    /**
     * Company code flag.
     * {@code 1} = system-defined, {@code 0} = user-defined.
     */
    private int cmpcode;

    /** Human-readable classification name (e.g. "Customer", "Supplier"). */
    private String clsname;

    /**
     * Classification type code.
     * {@code 'C'} = Customer, {@code 'S'} = Supplier, {@code 'T'} = Transporter,
     * {@code 'J'} = Jobber, {@code 'A'} = Agent, {@code 'O'} = Others,
     * {@code 'H'} = Channel, {@code 'D'} = TDS, {@code 'E'} = Staff.
     */
    private String clstype;

    /** Active flag. {@code 'N'} = active, {@code 'Y'} = inactive. */
    private String ext;

    /** Returns "System" for cmpcode=1, "User" for cmpcode=0. */
    public String cmpLabel() {
        return cmpcode == 1 ? "System" : "User";
    }

    /** Returns true if this is a customer-type classification (clstype = 'C'). */
    public boolean isCustomerType() {
        return "C".equalsIgnoreCase(clstype);
    }
}
