package com.simplr.mykitta2.feature.auth

import android.content.Context
import android.telephony.TelephonyManager
import com.simplr.mykitta2.domain.Country
import java.util.Locale

class AndroidCountryDetector(private val context: Context) : CountryDetector {
    override suspend fun detect(): Country? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val iso = tm?.networkCountryIso?.takeIf { it.isNotBlank() }
            ?: tm?.simCountryIso?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().country
        return Country.fromIso(iso)
    }
}
