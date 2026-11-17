package com.example.druglikenesschecker

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.druglikenesschecker.data.db.DrugEntity
import com.example.druglikenesschecker.ui.DrugAnalysisResult
import com.example.druglikenesschecker.ui.DrugCheckerViewModel
import com.example.druglikenesschecker.ui.SearchUiState
import com.example.druglikenesschecker.ui.auth.AuthManager
import com.example.druglikenesschecker.ui.auth.UserProfile
import com.example.druglikenesschecker.ui.history.SavedDrugsAdapter
import com.example.druglikenesschecker.ui.scan.TextRecognitionHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: DrugCheckerViewModel by viewModels()
    private lateinit var adapter: SavedDrugsAdapter
    private var currentResult: DrugAnalysisResult? = null

    // UI References
    private lateinit var tvUserName: TextView
    private lateinit var ivUserAvatar: ImageView
    private lateinit var btnAuth: MaterialButton

    private lateinit var tabLayout: TabLayout
    private lateinit var layoutCheckerTab: View
    private lateinit var layoutSavedTab: View

    private lateinit var etDrugName: EditText
    private lateinit var btnSearch: MaterialButton
    private lateinit var btnScanBox: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    private lateinit var layoutResults: LinearLayout
    private lateinit var tvResName: TextView
    private lateinit var tvResFormula: TextView
    private lateinit var tvResIupac: TextView
    private lateinit var tvResSmiles: TextView
    private lateinit var ivStructure: ImageView

    private lateinit var tvMw: TextView
    private lateinit var tvLogP: TextView
    private lateinit var tvHbd: TextView
    private lateinit var tvHba: TextView
    private lateinit var tvTpsa: TextView
    private lateinit var chipLipinski: Chip
    private lateinit var chipVeber: Chip

    private lateinit var chipOralBio: Chip
    private lateinit var chipBbb: Chip
    private lateinit var chipHerg: Chip

    private lateinit var tvMlPredictions: TextView
    private lateinit var btnSaveDrug: MaterialButton

    private lateinit var rvSavedDrugs: RecyclerView
    private lateinit var cardCompareBar: MaterialCardView
    private lateinit var tvCompareStatus: TextView
    private lateinit var btnRunCompare: MaterialButton

    // Google Sign-In Launcher
    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            AuthManager.handleSignInResult(
                task,
                onSuccess = { profile ->
                    updateUserUi(profile)
                    Toast.makeText(this, "Signed in as ${profile.displayName}", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(this, "Sign in failed: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            )
        }

    // Camera Capture Launcher for Box Scan
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
            if (bitmap != null) {
                processScannedBitmap(bitmap)
            }
        }

    // Gallery Picker Launcher for Box Scan
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri != null) {
                processScannedUri(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()
        setupAuth()
        setupTabs()
        setupSearchAndScan()
        setupHistoryRecyclerView()
        observeViewModel()
    }

    private fun bindViews() {
        tvUserName = findViewById(R.id.tvUserName)
        ivUserAvatar = findViewById(R.id.ivUserAvatar)
        btnAuth = findViewById(R.id.btnAuth)

        tabLayout = findViewById(R.id.tabLayout)
        layoutCheckerTab = findViewById(R.id.layoutCheckerTab)
        layoutSavedTab = findViewById(R.id.layoutSavedTab)

        etDrugName = findViewById(R.id.etDrugName)
        btnSearch = findViewById(R.id.btnSearch)
        btnScanBox = findViewById(R.id.btnScanBox)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)

        layoutResults = findViewById(R.id.layoutResults)
        tvResName = findViewById(R.id.tvResName)
        tvResFormula = findViewById(R.id.tvResFormula)
        tvResIupac = findViewById(R.id.tvResIupac)
        tvResSmiles = findViewById(R.id.tvResSmiles)
        ivStructure = findViewById(R.id.ivStructure)

        tvMw = findViewById(R.id.tvMw)
        tvLogP = findViewById(R.id.tvLogP)
        tvHbd = findViewById(R.id.tvHbd)
        tvHba = findViewById(R.id.tvHba)
        tvTpsa = findViewById(R.id.tvTpsa)
        chipLipinski = findViewById(R.id.chipLipinski)
        chipVeber = findViewById(R.id.chipVeber)

        chipOralBio = findViewById(R.id.chipOralBio)
        chipBbb = findViewById(R.id.chipBbb)
        chipHerg = findViewById(R.id.chipHerg)

        tvMlPredictions = findViewById(R.id.tvMlPredictions)
        btnSaveDrug = findViewById(R.id.btnSaveDrug)

        rvSavedDrugs = findViewById(R.id.rvSavedDrugs)
        cardCompareBar = findViewById(R.id.cardCompareBar)
        tvCompareStatus = findViewById(R.id.tvCompareStatus)
        btnRunCompare = findViewById(R.id.btnRunCompare)
    }

    private fun setupAuth() {
        AuthManager.setGuestUser("Scientist Guest")
        val currentUser = AuthManager.getCurrentUser()
        updateUserUi(currentUser)

        btnAuth.setOnClickListener {
            val user = AuthManager.getCurrentUser()
            if (user != null && !user.isGuest) {
                AuthManager.signOut(this) {
                    AuthManager.setGuestUser("Scientist Guest")
                    updateUserUi(AuthManager.getCurrentUser())
                    Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
                }
            } else {
                val client = AuthManager.getGoogleSignInClient(this)
                googleSignInLauncher.launch(client.signInIntent)
            }
        }
    }

    private fun updateUserUi(profile: UserProfile?) {
        if (profile != null) {
            tvUserName.text = profile.displayName ?: "User"
            if (profile.photoUrl != null) {
                ivUserAvatar.load(profile.photoUrl) {
                    crossfade(true)
                }
            } else {
                ivUserAvatar.setImageResource(android.R.drawable.ic_menu_myplaces)
            }
            btnAuth.text = if (profile.isGuest) "Sign In" else "Sign Out"
        }
    }

    private fun setupTabs() {
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        layoutCheckerTab.visibility = View.VISIBLE
                        layoutSavedTab.visibility = View.GONE
                    }
                    1 -> {
                        layoutCheckerTab.visibility = View.GONE
                        layoutSavedTab.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupSearchAndScan() {
        btnSearch.setOnClickListener {
            val query = etDrugName.text.toString()
            viewModel.searchDrug(query)
        }

        btnScanBox.setOnClickListener {
            showScanOptionsDialog()
        }

        btnSaveDrug.setOnClickListener {
            currentResult?.let { result ->
                viewModel.saveCurrentDrug(result)
            }
        }

        btnRunCompare.setOnClickListener {
            val selected = viewModel.selectedForCompare.value
            if (selected.size == 2) {
                showComparisonDialog(selected[0], selected[1])
            }
        }
    }

    private fun showScanOptionsDialog() {
        val options = arrayOf("Take Photo with Camera", "Choose from Gallery")
        AlertDialog.Builder(this)
            .setTitle("Scan Medicine Box Text")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePictureLauncher.launch(null)
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun processScannedBitmap(bitmap: Bitmap) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val result = TextRecognitionHelper.recognizeTextFromBitmap(bitmap)
            progressBar.visibility = View.GONE
            result.fold(
                onSuccess = { lines -> showTextSelectionDialog(lines) },
                onFailure = { e -> Toast.makeText(this@MainActivity, "Text recognition failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            )
        }
    }

    private fun processScannedUri(uri: Uri) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val result = TextRecognitionHelper.recognizeTextFromUri(this@MainActivity, uri)
            progressBar.visibility = View.GONE
            result.fold(
                onSuccess = { lines -> showTextSelectionDialog(lines) },
                onFailure = { e -> Toast.makeText(this@MainActivity, "Text recognition failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            )
        }
    }

    private fun showTextSelectionDialog(lines: List<String>) {
        if (lines.isEmpty()) {
            Toast.makeText(this, "No text detected in box image", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_text_picker, null)
        val listView = dialogView.findViewById<ListView>(R.id.lvTextLines)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, lines)
        listView.adapter = adapter

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedText = lines[position]
            val cleanedName = extractCandidateDrugName(selectedText)
            etDrugName.setText(cleanedName)
            dialog.dismiss()
            viewModel.searchDrug(cleanedName)
        }

        dialog.show()
    }

    private fun extractCandidateDrugName(rawLine: String): String {
        // Strip common dosage noise like "500mg", "Tablet", "Capsules" to leave clean drug name
        return rawLine.replace(Regex("(?i)\\b(\\d+\\s*(mg|g|ml)|tablets?|capsules?|ip|usp|bp)\\b"), "")
            .trim()
            .ifEmpty { rawLine.trim() }
    }

    private fun setupHistoryRecyclerView() {
        adapter = SavedDrugsAdapter(
            onDeleteClick = { drug -> viewModel.deleteSavedDrug(drug) },
            onCompareCheckToggle = { drug -> viewModel.toggleCompareSelection(drug) },
            onItemClick = { drug ->
                etDrugName.setText(drug.name)
                tabLayout.getTabAt(0)?.select()
                viewModel.searchDrug(drug.name)
            }
        )
        rvSavedDrugs.layoutManager = LinearLayoutManager(this)
        rvSavedDrugs.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launchWhenStarted {
            viewModel.uiState.collect { state ->
                when (state) {
                    is SearchUiState.Idle -> {
                        progressBar.visibility = View.GONE
                        tvError.visibility = View.GONE
                        layoutResults.visibility = View.GONE
                    }
                    is SearchUiState.Loading -> {
                        progressBar.visibility = View.VISIBLE
                        tvError.visibility = View.GONE
                        layoutResults.visibility = View.GONE
                    }
                    is SearchUiState.Success -> {
                        progressBar.visibility = View.GONE
                        tvError.visibility = View.GONE
                        layoutResults.visibility = View.VISIBLE
                        currentResult = state.data
                        displayAnalysisResult(state.data)
                    }
                    is SearchUiState.Error -> {
                        progressBar.visibility = View.GONE
                        layoutResults.visibility = View.GONE
                        tvError.visibility = View.VISIBLE
                        tvError.text = state.message
                    }
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.selectedForCompare.collect { list ->
                val ids = list.map { it.id }.toSet()
                adapter.updateSelectedCompareIds(ids)

                if (list.size >= 2) {
                    cardCompareBar.visibility = View.VISIBLE
                    tvCompareStatus.text = "${list.size} Drugs Selected for Compare"
                } else if (list.size == 1) {
                    cardCompareBar.visibility = View.VISIBLE
                    tvCompareStatus.text = "1 Drug Selected (Select 1 more)"
                } else {
                    cardCompareBar.visibility = View.GONE
                }
            }
        }

        viewModel.savedDrugs.observe(this) { list ->
            adapter.submitList(list)
        }

        viewModel.saveStatus.observe(this) { msg ->
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                viewModel.clearSaveStatus()
            }
        }
    }

    private fun displayAnalysisResult(result: DrugAnalysisResult) {
        val c = result.compound
        val d = result.descriptors
        val ml = result.mlPredictions

        tvResName.text = c.name
        tvResFormula.text = "Formula: ${c.formula}"
        tvResIupac.text = "IUPAC: ${c.iupacName}"
        tvResSmiles.text = "SMILES: ${c.smiles}"

        ivStructure.load(c.imageUrl) {
            crossfade(true)
            placeholder(android.R.drawable.ic_menu_gallery)
            error(android.R.drawable.ic_dialog_alert)
        }

        tvMw.text = "${"%.2f".format(d.molecularWeight)} Da"
        tvLogP.text = "%.2f".format(d.logP)
        tvHbd.text = d.hBondDonors.toString()
        tvHba.text = d.hBondAcceptors.toString()
        tvTpsa.text = "${"%.2f".format(d.topologicalPolarSurfaceArea)} Å²"

        if (d.lipinskiPasses) {
            chipLipinski.text = "Lipinski: PASS ✓"
            chipLipinski.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
            chipLipinski.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            chipLipinski.text = "Lipinski: FAIL ✗"
            chipLipinski.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
            chipLipinski.setTextColor(Color.parseColor("#C62828"))
        }

        if (d.veberPasses) {
            chipVeber.text = "Veber: PASS ✓"
            chipVeber.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
            chipVeber.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            chipVeber.text = "Veber: FAIL ✗"
            chipVeber.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
            chipVeber.setTextColor(Color.parseColor("#C62828"))
        }

        // Coloured ADMET Chips
        when (d.admet.oralBioavailability) {
            "High" -> {
                chipOralBio.text = "Oral Bioavailability: High ✓"
                chipOralBio.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
                chipOralBio.setTextColor(Color.parseColor("#2E7D32"))
            }
            "Moderate" -> {
                chipOralBio.text = "Oral Bioavailability: Moderate"
                chipOralBio.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFF3E0"))
                chipOralBio.setTextColor(Color.parseColor("#E65100"))
            }
            else -> {
                chipOralBio.text = "Oral Bioavailability: Low ✗"
                chipOralBio.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
                chipOralBio.setTextColor(Color.parseColor("#C62828"))
            }
        }

        if (d.admet.bbbPenetration) {
            chipBbb.text = "BBB Penetration: Yes"
            chipBbb.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E1F5FE"))
            chipBbb.setTextColor(Color.parseColor("#0277BD"))
        } else {
            chipBbb.text = "BBB Penetration: No"
            chipBbb.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
            chipBbb.setTextColor(Color.parseColor("#616161"))
        }

        if (!d.admet.hergInhibition) {
            chipHerg.text = "hERG Risk: Low ✓"
            chipHerg.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
            chipHerg.setTextColor(Color.parseColor("#2E7D32"))
        } else {
            chipHerg.text = "hERG Risk: Cardiotoxicity Risk ⚠"
            chipHerg.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
            chipHerg.setTextColor(Color.parseColor("#C62828"))
        }

        // DrugMLPredictor List
        val sb = StringBuilder()
        ml.forEach { (task, pred) ->
            val labelFormatted = pred.label.replace("_", " ")
            val confFormatted = "${(pred.confidence * 100).toInt()}%"
            val taskFormatted = task.replaceFirstChar { it.uppercaseChar() }
            sb.append("• ").append(taskFormatted).append(": ")
                .append(labelFormatted).append(" (").append(confFormatted).append(")\n")
        }
        tvMlPredictions.text = sb.toString().trim()
    }

    private fun showComparisonDialog(drug1: DrugEntity, drug2: DrugEntity) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_drug_compare, null)

        val ivCompImg1 = dialogView.findViewById<ImageView>(R.id.ivCompImg1)
        val tvCompName1 = dialogView.findViewById<TextView>(R.id.tvCompName1)
        val tvCompFormula1 = dialogView.findViewById<TextView>(R.id.tvCompFormula1)
        val tvCompMw1 = dialogView.findViewById<TextView>(R.id.tvCompMw1)
        val tvCompLogP1 = dialogView.findViewById<TextView>(R.id.tvCompLogP1)
        val tvCompHbdHba1 = dialogView.findViewById<TextView>(R.id.tvCompHbdHba1)
        val tvCompTpsa1 = dialogView.findViewById<TextView>(R.id.tvCompTpsa1)
        val tvCompLipinski1 = dialogView.findViewById<TextView>(R.id.tvCompLipinski1)
        val tvCompVeber1 = dialogView.findViewById<TextView>(R.id.tvCompVeber1)
        val tvCompAdmet1 = dialogView.findViewById<TextView>(R.id.tvCompAdmet1)
        val tvCompMl1 = dialogView.findViewById<TextView>(R.id.tvCompMl1)

        val ivCompImg2 = dialogView.findViewById<ImageView>(R.id.ivCompImg2)
        val tvCompName2 = dialogView.findViewById<TextView>(R.id.tvCompName2)
        val tvCompFormula2 = dialogView.findViewById<TextView>(R.id.tvCompFormula2)
        val tvCompMw2 = dialogView.findViewById<TextView>(R.id.tvCompMw2)
        val tvCompLogP2 = dialogView.findViewById<TextView>(R.id.tvCompLogP2)
        val tvCompHbdHba2 = dialogView.findViewById<TextView>(R.id.tvCompHbdHba2)
        val tvCompTpsa2 = dialogView.findViewById<TextView>(R.id.tvCompTpsa2)
        val tvCompLipinski2 = dialogView.findViewById<TextView>(R.id.tvCompLipinski2)
        val tvCompVeber2 = dialogView.findViewById<TextView>(R.id.tvCompVeber2)
        val tvCompAdmet2 = dialogView.findViewById<TextView>(R.id.tvCompAdmet2)
        val tvCompMl2 = dialogView.findViewById<TextView>(R.id.tvCompMl2)

        val btnClose = dialogView.findViewById<MaterialButton>(R.id.btnCloseComp)

        // Populate Drug 1
        ivCompImg1.load(drug1.imageUrl)
        tvCompName1.text = drug1.name
        tvCompFormula1.text = drug1.formula
        tvCompMw1.text = "MW: ${"%.1f".format(drug1.mw)} Da"
        tvCompLogP1.text = "LogP: ${"%.2f".format(drug1.logP)}"
        tvCompHbdHba1.text = "HBD/HBA: ${drug1.hbd}/${drug1.hba}"
        tvCompTpsa1.text = "TPSA: ${"%.1f".format(drug1.tpsa)} Å²"
        tvCompLipinski1.text = "Lipinski: ${if (drug1.lipinskiPasses) "PASS ✓" else "FAIL ✗"}"
        tvCompVeber1.text = "Veber: ${if (drug1.veberPasses) "PASS ✓" else "FAIL ✗"}"
        tvCompAdmet1.text = "ADMET:\n• Oral Bio: ${drug1.oralBioavailability}\n• BBB: ${if (drug1.bbbPenetration) "Yes" else "No"}\n• hERG: ${if (drug1.hergInhibition) "Risk" else "Low"}"
        tvCompMl1.text = "ML Predictions:\n${drug1.predictionsSummary.replace("; ", "\n")}"

        // Populate Drug 2
        ivCompImg2.load(drug2.imageUrl)
        tvCompName2.text = drug2.name
        tvCompFormula2.text = drug2.formula
        tvCompMw2.text = "MW: ${"%.1f".format(drug2.mw)} Da"
        tvCompLogP2.text = "LogP: ${"%.2f".format(drug2.logP)}"
        tvCompHbdHba2.text = "HBD/HBA: ${drug2.hbd}/${drug2.hba}"
        tvCompTpsa2.text = "TPSA: ${"%.1f".format(drug2.tpsa)} Å²"
        tvCompLipinski2.text = "Lipinski: ${if (drug2.lipinskiPasses) "PASS ✓" else "FAIL ✗"}"
        tvCompVeber2.text = "Veber: ${if (drug2.veberPasses) "PASS ✓" else "FAIL ✗"}"
        tvCompAdmet2.text = "ADMET:\n• Oral Bio: ${drug2.oralBioavailability}\n• BBB: ${if (drug2.bbbPenetration) "Yes" else "No"}\n• hERG: ${if (drug2.hergInhibition) "Risk" else "Low"}"
        tvCompMl2.text = "ML Predictions:\n${drug2.predictionsSummary.replace("; ", "\n")}"

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
