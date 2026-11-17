package com.example.druglikenesschecker.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PubChemCompound(
    val name: String,
    val cid: Long,
    val formula: String,
    val iupacName: String,
    val smiles: String,
    val imageUrl: String
)

object PubChemApi {

    suspend fun fetchCompoundByName(drugName: String): Result<PubChemCompound> = withContext(Dispatchers.IO) {
        try {
            val encodedName = URLEncoder.encode(drugName.trim(), "UTF-8")
            val urlString = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/$encodedName/property/CanonicalSMILES,MolecularFormula,IUPACName/JSON"

            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("PubChem compound '$drugName' not found (HTTP $responseCode)"))
            }

            val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(jsonString)

            val propertyTable = root.optJSONObject("PropertyTable")
                ?: return@withContext Result.failure(Exception("Invalid PubChem response structure"))
            val propertiesArray = propertyTable.optJSONArray("Properties")
                ?: return@withContext Result.failure(Exception("No compound properties found"))

            if (propertiesArray.length() == 0) {
                return@withContext Result.failure(Exception("No properties found for '$drugName'"))
            }

            val prop = propertiesArray.getJSONObject(0)

            val cid = prop.optLong("CID", 0L)
            val formula = prop.optString("MolecularFormula", "Unknown")
            val iupac = prop.optString("IUPACName", "N/A")

            val smiles = when {
                prop.has("ConnectivitySMILES") && prop.optString("ConnectivitySMILES").isNotBlank() ->
                    prop.optString("ConnectivitySMILES")
                prop.has("CanonicalSMILES") && prop.optString("CanonicalSMILES").isNotBlank() ->
                    prop.optString("CanonicalSMILES")
                else -> ""
            }

            if (smiles.isBlank()) {
                return@withContext Result.failure(Exception("SMILES structure not available for '$drugName'"))
            }

            val imageUrl = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/$encodedName/PNG"

            Result.success(
                PubChemCompound(
                    name = drugName.trim(),
                    cid = cid,
                    formula = formula,
                    iupacName = iupac,
                    smiles = smiles,
                    imageUrl = imageUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
