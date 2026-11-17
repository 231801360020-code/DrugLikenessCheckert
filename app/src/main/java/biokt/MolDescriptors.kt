package biokt

import kotlin.math.*

// ============================================================
// MOLECULAR DESCRIPTORS
// Lipinski / Veber / ADMET / Fingerprints / Drug-likeness
// ============================================================

// ─── Descriptor Result ───────────────────────────────────────
data class MolecularDescriptors(
    val molecularFormula: String,
    val exactMass: Double,
    val numAtoms: Int,
    val numHeavyAtoms: Int,
    val numBonds: Int,
    val numRings: Int,
    val numAromaticRings: Int,
    val numRotatableBonds: Int,
    val numStereocenters: Int,

    // Lipinski Rule of Five
    val molecularWeight: Double,
    val logP: Double,
    val hBondDonors: Int,
    val hBondAcceptors: Int,
    val lipinskiPasses: Boolean,

    // Veber rules (oral bioavailability)
    val topologicalPolarSurfaceArea: Double, // TPSA (Å²)
    val veberPasses: Boolean,

    // Extended drug-likeness
    val molarRefractivity: Double,
    val fractionCSP3: Double,
    val numAromaticAtoms: Int,

    val ghosePasses: Boolean,
    val eganPasses: Boolean,
    val mueggeScore: Int,

    // ADMET estimates
    val admet: ADMETProfile
)

data class ADMETProfile(
    val oralBioavailability: String,
    val caco2Permeability: String,
    val pgpSubstrate: Boolean,
    val hia: String,

    val bbbPenetration: Boolean,
    val vdss: Double,
    val plasmaProteinBinding: String,

    val cyp3a4Substrate: Boolean,
    val cyp2d6Substrate: Boolean,
    val cyp2c9Substrate: Boolean,
    val cyp3a4Inhibitor: Boolean,
    val cyp2d6Inhibitor: Boolean,

    val halfLife: String,
    val renalClearance: String,

    val hergInhibition: Boolean,
    val amesTest: String,
    val oralToxicity: String,
    val skinSensitization: Boolean
)

// ─── Descriptor Calculator ───────────────────────────────────
object MolDescriptors {
    fun molecularWeight(mol: Molecule): Double {
        val avgMasses = mapOf(
            "H" to 1.008, "C" to 12.011, "N" to 14.007, "O" to 15.999,
            "F" to 18.998, "P" to 30.974, "S" to 32.065, "Cl" to 35.453,
            "Br" to 79.904, "I" to 126.905, "B" to 10.811, "Si" to 28.086,
            "Se" to 78.971, "Fe" to 55.845, "Zn" to 65.38
        )
        var mw = 0.0
        for (atom in mol.atoms) mw += avgMasses[atom.symbol] ?: 12.0
        mw += mol.atoms.sumOf { it.totalH * 1.008 }
        return mw
    }

    fun wilmanCrippenLogP(mol: Molecule): Double {
        val contributions = mapOf(
            "C_ar" to 0.1441, "C_sp3" to 0.1234, "C_sp2" to 0.0000,
            "N_ar" to -0.7736, "N_am" to -1.0190, "N_sp3" to -0.9275,
            "O_ar" to -0.2405, "O_sp3" to -0.2677, "O_co2" to -0.0200,
            "F" to 0.4202, "Cl" to 0.7338, "Br" to 0.8456, "I" to 0.8857,
            "S" to 0.6895, "P" to 0.8612, "H" to 0.1234
        )
        var logP = 0.0
        for (atom in mol.atoms) {
            val neighbors = mol.neighborsOf(atom.index)
            val hasO = neighbors.any { mol.atoms[it].symbol == "O" }
            val contrib = when (atom.symbol) {
                "C" -> when {
                    atom.isAromatic -> contributions["C_ar"]!!
                    mol.bonds.filter { it.atom1 == atom.index || it.atom2 == atom.index }
                        .any { it.type == BondType.DOUBLE } -> contributions["C_sp2"]!!
                    else -> contributions["C_sp3"]!!
                }
                "N" -> when {
                    atom.isAromatic -> contributions["N_ar"]!!
                    hasO -> contributions["N_am"]!!
                    else -> contributions["N_sp3"]!!
                }
                "O" -> when {
                    atom.isAromatic -> contributions["O_ar"]!!
                    neighbors.any { n -> mol.bonds.filter { it.atom1 == n || it.atom2 == n }
                        .any { it.type == BondType.DOUBLE } } -> contributions["O_co2"]!!
                    else -> contributions["O_sp3"]!!
                }
                "F" -> contributions["F"]!!
                "Cl" -> contributions["Cl"]!!
                "Br" -> contributions["Br"]!!
                "I" -> contributions["I"]!!
                "S" -> contributions["S"]!!
                "P" -> contributions["P"]!!
                else -> 0.0
            }
            logP += contrib
        }
        logP += mol.atoms.sumOf { it.totalH } * (contributions["H"] ?: 0.0)
        return logP
    }

    fun hBondDonors(mol: Molecule): Int =
        mol.atoms.count { atom -> (atom.symbol == "O" || atom.symbol == "N") && atom.totalH > 0 }

    fun hBondAcceptors(mol: Molecule): Int =
        mol.atoms.count { it.symbol == "N" || it.symbol == "O" }

    fun tpsa(mol: Molecule): Double {
        var tpsa = 0.0
        for (atom in mol.atoms) {
            tpsa += when (atom.symbol) {
                "N" -> when {
                    atom.isAromatic && atom.totalH == 0 -> 12.89
                    atom.isAromatic && atom.totalH == 1 -> 13.59
                    !atom.isAromatic && atom.totalH == 0 -> 3.24
                    !atom.isAromatic && atom.totalH == 1 -> 23.85
                    !atom.isAromatic && atom.totalH == 2 -> 26.02
                    else -> 0.0
                }
                "O" -> when {
                    atom.isAromatic -> 13.14
                    atom.totalH == 1 -> 20.23
                    else -> 17.07
                }
                "S" -> when {
                    atom.totalH == 1 -> 34.14
                    else -> 28.24
                }
                "P" -> when {
                    atom.totalH == 0 -> 9.81
                    else -> 23.47
                }
                else -> 0.0
            }
        }
        return tpsa
    }

    fun rotatableBonds(mol: Molecule): Int =
        mol.bonds.count { bond ->
            if (bond.type != BondType.SINGLE || bond.isRing) return@count false
            val a1 = mol.atoms[bond.atom1]
            val a2 = mol.atoms[bond.atom2]
            if (a1.symbol == "H" || a2.symbol == "H") return@count false
            val deg1 = mol.neighborsOf(bond.atom1).size
            val deg2 = mol.neighborsOf(bond.atom2).size
            deg1 > 1 && deg2 > 1
        }

    fun fractionCSP3(mol: Molecule): Double {
        val carbons = mol.atoms.filter { it.symbol == "C" }
        if (carbons.isEmpty()) return 0.0
        val sp3 = carbons.count { atom ->
            !atom.isAromatic && mol.bonds
                .filter { it.atom1 == atom.index || it.atom2 == atom.index }
                .none { it.type == BondType.DOUBLE || it.type == BondType.TRIPLE }
        }
        return sp3.toDouble() / carbons.size
    }

    fun molarRefractivity(mol: Molecule): Double {
        val mw = molecularWeight(mol)
        return 0.029 * mw + 1.512 * mol.atoms.count { it.symbol == "C" || it.symbol == "N" } + 0.686 * mol.atoms.count { it.symbol == "O" || it.symbol == "S" }
    }

    fun stereocenters(mol: Molecule): Int =
        mol.atoms.count { atom ->
            if (atom.symbol != "C") return@count false
            val neighbors = mol.neighborsOf(atom.index)
            if (neighbors.size != 4) return@count false
            val syms = neighbors.map { mol.atoms[it].symbol }
            syms.size == syms.toSet().size
        }

    fun lipinskiRo5(mw: Double, logP: Double, hbd: Int, hba: Int): Boolean =
        mw <= 500 && logP <= 5 && hbd <= 5 && hba <= 10

    fun veberRules(tpsa: Double, rotBonds: Int): Boolean =
        tpsa <= 140 && rotBonds <= 10

    fun ghoseFilter(mw: Double, logP: Double, mr: Double, numAtoms: Int): Boolean =
        mw in 160.0..480.0 && logP in -0.4..5.6 && mr in 40.0..130.0 && numAtoms in 20..70

    fun eganFilter(logP: Double, tpsa: Double): Boolean =
        logP in -1.0..6.0 && tpsa <= 150.0

    fun mueggeFilter(mol: Molecule, mw: Double, logP: Double, tpsa: Double, hbd: Int, hba: Int, rotB: Int): Int {
        var score = 0
        if (mw in 200.0..600.0) score++
        if (logP in -2.0..5.0) score++
        if (tpsa <= 150) score++
        if (hbd <= 5) score++
        if (hba <= 10) score++
        if (rotB <= 15) score++
        if (mol.rings.size <= 7) score++
        if (mol.rings.isNotEmpty()) score++
        if (mol.atoms.none { it.symbol in setOf("Fe","Hg","Pb","Cd") }) score++
        return score
    }

    fun estimateADMET(mol: Molecule, mw: Double, logP: Double, tpsa: Double, hbd: Int, hba: Int, mr: Double): ADMETProfile {
        val oralBio = when {
            tpsa < 60 && logP in 0.0..3.0 -> "High"
            tpsa > 140 || logP > 5.0 || logP < -2.0 -> "Low"
            else -> "Moderate"
        }
        val caco2 = if (tpsa < 60 && mw < 500 && logP > 0) "High" else "Low"
        val pgp = logP > 3.0 && mw > 400 && mol.rings.size >= 2
        val hia = when {
            tpsa < 75 && logP in -1.0..4.0 -> "High (>80%)"
            tpsa > 120 -> "Low (<20%)"
            else -> "Moderate (20-80%)"
        }
        val bbb = logP in 1.0..3.5 && mw < 450 && tpsa < 90 && hbd <= 3 && hba <= 7
        val vdss = when {
            logP > 3.0 -> 2.5 + (logP - 3.0) * 0.5
            logP < 0.0 -> 0.2
            else -> 0.7 + logP * 0.6
        }
        val ppb = when {
            logP > 3.0 -> "High >90%"
            logP > 1.0 -> "Moderate 50-90%"
            else -> "Low <50%"
        }
        val cyp3a4Sub = mw > 400 && mol.rings.count { it.isAromatic } >= 1
        val cyp2d6Sub = mol.atoms.any { it.symbol == "N" && !it.isAromatic } && mw < 500
        val cyp2c9Sub = mol.atoms.any { it.symbol == "O" } && logP in 1.0..5.0
        val cyp3a4Inh = mol.rings.count { it.isAromatic } >= 2 && mw > 350
        val cyp2d6Inh = mol.atoms.any { it.symbol == "N" } && mol.rings.count { it.isAromatic } >= 1
        val halfLife = when {
            mw < 200 -> "Short <2h"
            mw > 500 -> "Long >8h"
            else -> "Moderate 2-8h"
        }
        val renalClear = if (logP < 1.0 && mw < 300) "High" else "Low/Moderate"
        val herg = logP > 2.0 && mol.atoms.any { it.symbol == "N" && !it.isAromatic }
        val ames = if (mol.atoms.any { it.symbol == "N" && it.isAromatic } && mol.rings.count { it.isAromatic } >= 2) "Potentially mutagenic" else "Non-mutagenic"
        val oralTox = when {
            mw < 200 && logP < 1.0 -> "Class I (Low risk)"
            mol.rings.count { it.isAromatic } >= 3 -> "Class III (High risk)"
            else -> "Class II (Moderate risk)"
        }
        val skinSens = mol.atoms.any { it.symbol == "S" } && logP > 2.0

        return ADMETProfile(
            oralBio, caco2, pgp, hia, bbb, vdss, ppb,
            cyp3a4Sub, cyp2d6Sub, cyp2c9Sub, cyp3a4Inh, cyp2d6Inh,
            halfLife, renalClear, herg, ames, oralTox, skinSens
        )
    }

    fun calculate(mol: Molecule): MolecularDescriptors {
        val mw = molecularWeight(mol)
        val logp = wilmanCrippenLogP(mol)
        val hbd = hBondDonors(mol)
        val hba = hBondAcceptors(mol)
        val tpsa = tpsa(mol)
        val rotB = rotatableBonds(mol)
        val fsp3 = fractionCSP3(mol)
        val mr = molarRefractivity(mol)
        val nRings = mol.rings.size
        val nArRings = mol.rings.count { it.isAromatic }
        val nAtoms = mol.atoms.filter { it.symbol != "H" }.size
        val nHAtoms = mol.atoms.size + mol.atoms.sumOf { it.totalH }
        val mass = mol.atoms.sumOf { it.atomicMass } + mol.atoms.sumOf { it.totalH } * 1.008

        return MolecularDescriptors(
            molecularFormula = mol.molecularFormula(),
            exactMass = mass,
            numAtoms = nHAtoms,
            numHeavyAtoms = nAtoms,
            numBonds = mol.bonds.size,
            numRings = nRings,
            numAromaticRings = nArRings,
            numRotatableBonds = rotB,
            numStereocenters = stereocenters(mol),
            molecularWeight = mw,
            logP = logp,
            hBondDonors = hbd,
            hBondAcceptors = hba,
            lipinskiPasses = lipinskiRo5(mw, logp, hbd, hba),
            topologicalPolarSurfaceArea = tpsa,
            veberPasses = veberRules(tpsa, rotB),
            molarRefractivity = mr,
            fractionCSP3 = fsp3,
            numAromaticAtoms = mol.atoms.count { it.isAromatic },
            ghosePasses = ghoseFilter(mw, logp, mr, nHAtoms),
            eganPasses = eganFilter(logp, tpsa),
            mueggeScore = mueggeFilter(mol, mw, logp, tpsa, hbd, hba, rotB),
            admet = estimateADMET(mol, mw, logp, tpsa, hbd, hba, mr)
        )
    }
}
