package cvam.dignity.juganua.features.rpli

import android.content.Context
import android.content.Intent
import org.json.JSONArray
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Period
import java.util.*

/**
 * Logic handler for RPLI Premium calculations and data loading.
 * FIXED: Package name synchronized with the project structure.
 */
object RpliCalculatorLogic {

    fun formatInr(v: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }.format(v)

    fun shareRpliQuote(context: Context, res: CalculationResult) {
        val msg = """
            📊 *RPLI Premium Quote*
            ----------------------------------
            SA: ${formatInr(res.sumAssured)}
            Term: ${res.policyTerm} Yrs
            
            💰 *Premium* (${res.frequency.uppercase()})
            Payable: ${formatInr(res.finalPremium)}
            
            🎁 *Maturity*
            Amount: ${formatInr(res.finalMaturityAmount)}
            Profit: ${formatInr(res.netGain)}
            
            📈 *Returns*
            ROI: ${"%.1f".format(res.roi)}%
            ----------------------------------
        """.trimIndent()

        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, msg)
        }, "Share Quote"))
    }

    fun loadRpliTables(c: Context): Map<String, JSONArray> {
        val m = mutableMapOf<String, JSONArray>()
        listOf("monthly", "quarterly", "halfyearly", "yearly").forEach { f ->
            try {
                m[f] = JSONArray(c.assets.open("rpli_calculator/premium-table-gram-santosh-$f.json")
                    .bufferedReader().use { it.readText() })
            } catch (_: Exception) {}
        }
        return m
    }

    fun performRpliCalc(
        d: String, m: String, y: String,
        pt: Int, sa: Double, freq: String,
        tables: Map<String, JSONArray>
    ): CalculationResult {
        val dob = LocalDate.of(y.toInt(), m.toInt(), d.toInt())
        val age = Period.between(dob, LocalDate.now()).years + 1
        val matAge = age + pt
        val table = tables[freq] ?: throw Exception("Data not loaded")

        var rate = 0.0
        for (i in 0 until table.length()) {
            val row = table.getJSONObject(i)
            if (row.getInt("Entry Age") == age && row.has(matAge.toString())) {
                rate = row.getDouble(matAge.toString())
                break
            }
        }

        if (rate == 0.0) throw Exception("Age/Term combination not supported")

        val basePremiumPerPeriod = rate * (sa / 1000.0)

        val installmentsPerYear = when(freq) {
            "monthly" -> 12
            "quarterly" -> 4
            "halfyearly" -> 2
            "yearly" -> 1
            else -> 12
        }

        val monthlyRebate = (sa / 20000).toInt() * 1.0
        val rebatePerPeriod = monthlyRebate * (12.0 / installmentsPerYear)

        val finalPremium = basePremiumPerPeriod - rebatePerPeriod
        val totalPaid = finalPremium * (pt * installmentsPerYear)

        val totalBonus = (48.0 * (sa / 1000) * pt)
        val maturityAmount = sa + totalBonus
        val netGain = maturityAmount - totalPaid

        return CalculationResult(
            entryAge = age,
            maturityAge = matAge,
            policyTerm = pt,
            sumAssured = sa,
            basePremiumPerPeriod = basePremiumPerPeriod,
            rebatePerPeriod = rebatePerPeriod,
            finalPremium = finalPremium,
            totalBonus = totalBonus,
            finalMaturityAmount = maturityAmount,
            frequency = freq,
            totalInstallments = pt * installmentsPerYear,
            totalPremiumOverTerm = totalPaid,
            netGain = netGain,
            roi = if (totalPaid > 0) (netGain / totalPaid / pt) * 100 else 0.0
        )
    }
}