package cvam.dignity.juganua.features.postal.ippb_register

import java.util.*

/**
 * IPPB Digital Register Models
 */
data class RegistrationData(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val dob: String = "",
    val aadhaar: String = "",
    val mobile: String = "",
    val account: String = "",
    val cif: String = "",
    val transactionType: String = "New Account",
    val amount: String = "200",
    val timestamp: Long = System.currentTimeMillis()
)

enum class ScanTarget { NONE, NAME, DOB, AADHAAR, MOBILE, ACCOUNT, CIF, ALL }
enum class SortCriteria { DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC }