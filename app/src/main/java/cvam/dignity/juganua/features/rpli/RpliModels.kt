package cvam.dignity.juganua.features.rpli

/**
 * Data class representing the comprehensive results of an RPLI calculation.
 */
data class CalculationResult(
    val entryAge: Int,
    val maturityAge: Int,
    val policyTerm: Int,
    val sumAssured: Double,
    val basePremiumPerPeriod: Double,
    val rebatePerPeriod: Double,
    val finalPremium: Double,
    val totalBonus: Double,
    val finalMaturityAmount: Double,
    val frequency: String,
    val totalInstallments: Int,
    val totalPremiumOverTerm: Double,
    val netGain: Double,
    val roi: Double
)