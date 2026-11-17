package com.example.druglikenesschecker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import biokt.DrugMLPredictor
import biokt.MLPrediction
import biokt.MolDescriptors
import biokt.MolecularDescriptors
import biokt.Molecule
import com.example.druglikenesschecker.data.api.PubChemApi
import com.example.druglikenesschecker.data.api.PubChemCompound
import com.example.druglikenesschecker.data.db.AppDatabase
import com.example.druglikenesschecker.data.db.DrugEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DrugAnalysisResult(
    val compound: PubChemCompound,
    val descriptors: MolecularDescriptors,
    val mlPredictions: Map<String, MLPrediction>
)

sealed class SearchUiState {
    object Idle : SearchUiState()
    object Loading : SearchUiState()
    data class Success(val data: DrugAnalysisResult) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class DrugCheckerViewModel(application: Application) : AndroidViewModel(application) {

    private val drugDao = AppDatabase.getDatabase(application).drugDao()

    val savedDrugs: LiveData<List<DrugEntity>> = drugDao.getAllDrugs()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    private val _saveStatus = MutableLiveData<String?>()
    val saveStatus: LiveData<String?> = _saveStatus

    private val _selectedForCompare = MutableStateFlow<List<DrugEntity>>(emptyList())
    val selectedForCompare: StateFlow<List<DrugEntity>> = _selectedForCompare

    fun searchDrug(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _uiState.value = SearchUiState.Error("Please enter or scan a drug name")
            return
        }

        _uiState.value = SearchUiState.Loading

        viewModelScope.launch {
            val pubChemResult = PubChemApi.fetchCompoundByName(trimmed)
            pubChemResult.fold(
                onSuccess = { compound ->
                    try {
                        val molecule = Molecule(compound.smiles, name = compound.name)
                        val descriptors = MolDescriptors.calculate(molecule)
                        val mlPredictions = DrugMLPredictor.fullAnalysis(molecule)

                        val result = DrugAnalysisResult(compound, descriptors, mlPredictions)
                        _uiState.value = SearchUiState.Success(result)
                    } catch (e: Exception) {
                        _uiState.value = SearchUiState.Error("bioKT calculation failed: ${e.message}")
                    }
                },
                onFailure = { error ->
                    _uiState.value = SearchUiState.Error(error.message ?: "Failed to fetch drug data from PubChem")
                }
            )
        }
    }

    fun saveCurrentDrug(result: DrugAnalysisResult) {
        viewModelScope.launch {
            try {
                val summaryList = result.mlPredictions.entries.map { (task, pred) ->
                    "$task: ${pred.label} (${(pred.confidence * 100).toInt()}%)"
                }.joinToString("; ")

                val entity = DrugEntity(
                    name = result.compound.name,
                    cid = result.compound.cid,
                    formula = result.compound.formula,
                    iupacName = result.compound.iupacName,
                    smiles = result.compound.smiles,
                    imageUrl = result.compound.imageUrl,
                    mw = result.descriptors.molecularWeight,
                    logP = result.descriptors.logP,
                    hbd = result.descriptors.hBondDonors,
                    hba = result.descriptors.hBondAcceptors,
                    tpsa = result.descriptors.topologicalPolarSurfaceArea,
                    lipinskiPasses = result.descriptors.lipinskiPasses,
                    veberPasses = result.descriptors.veberPasses,
                    oralBioavailability = result.descriptors.admet.oralBioavailability,
                    bbbPenetration = result.descriptors.admet.bbbPenetration,
                    hergInhibition = result.descriptors.admet.hergInhibition,
                    predictionsSummary = summaryList
                )

                drugDao.insertDrug(entity)
                _saveStatus.value = "Saved ${result.compound.name} to history!"
            } catch (e: Exception) {
                _saveStatus.value = "Failed to save: ${e.message}"
            }
        }
    }

    fun clearSaveStatus() {
        _saveStatus.value = null
    }

    fun deleteSavedDrug(drug: DrugEntity) {
        viewModelScope.launch {
            drugDao.deleteDrug(drug)
            removeFromCompare(drug)
        }
    }

    fun toggleCompareSelection(drug: DrugEntity) {
        val current = _selectedForCompare.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.id == drug.id }
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
        } else {
            if (current.size >= 2) {
                current.removeAt(0) // replace oldest if already 2 selected
            }
            current.add(drug)
        }
        _selectedForCompare.value = current
    }

    fun removeFromCompare(drug: DrugEntity) {
        val current = _selectedForCompare.value.toMutableList()
        current.removeAll { it.id == drug.id }
        _selectedForCompare.value = current
    }

    fun clearCompareSelection() {
        _selectedForCompare.value = emptyList()
    }
}
