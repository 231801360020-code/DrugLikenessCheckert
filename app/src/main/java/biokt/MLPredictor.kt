package biokt

import kotlin.math.*

// ============================================================
// ML PREDICTOR — Inference-only machine learning for BioKt
// ============================================================

data class MLPrediction(
    val label: String,
    val confidence: Double,          // 0.0 – 1.0
    val probabilities: Map<String, Double>,
    val features: Map<String, Double>,
    val modelName: String,
    val algorithm: String
) {
    val isHighConfidence: Boolean get() = confidence >= 0.75
    val isMediumConfidence: Boolean get() = confidence in 0.5..0.75
    val isLowConfidence: Boolean get() = confidence < 0.5
}

class NaiveBayesClassifier(
    val name: String,
    private val classPriors: Map<String, Double>,
    private val means: Map<String, DoubleArray>,     // class -> feature means
    private val variances: Map<String, DoubleArray>, // class -> feature variances
    private val featureNames: List<String>
) {
    fun predict(features: DoubleArray): MLPrediction {
        require(features.size == featureNames.size)

        val logLikelihoods = classPriors.keys.associateWith { cls ->
            var ll = ln(classPriors[cls]!! + 1e-10)
            val mu  = means[cls]!!
            val sig = variances[cls]!!
            for (i in features.indices) {
                val v = sig[i].coerceAtLeast(1e-9)
                ll += -0.5 * ln(2 * PI * v) - (features[i] - mu[i]).pow(2) / (2 * v)
            }
            ll
        }

        val maxLL = logLikelihoods.values.maxOrNull() ?: 0.0
        val expScores = logLikelihoods.mapValues { exp(it.value - maxLL) }
        val total = expScores.values.sum()
        val probs = expScores.mapValues { if (total > 0) it.value / total else 0.0 }
        val best = probs.maxByOrNull { it.value }
        val bestLabel = best?.key ?: "unknown"
        val bestProb = best?.value ?: 0.0

        return MLPrediction(
            bestLabel, bestProb, probs,
            featureNames.zip(features.toList()).toMap(),
            name, "Naive Bayes"
        )
    }
}

object PretrainedModels {
    val drugActivityClassifier: NaiveBayesClassifier by lazy {
        NaiveBayesClassifier(
            name = "Drug Activity Classifier",
            classPriors = mapOf("active" to 0.35, "inactive" to 0.65),
            means = mapOf(
                "active"   to doubleArrayOf(320.0, 2.8, 2.0, 5.5, 62.0, 4.0, 1.8, 0.52),
                "inactive" to doubleArrayOf(420.0, 3.9, 3.1, 7.2, 88.0, 5.8, 2.9, 0.38)
            ),
            variances = mapOf(
                "active"   to doubleArrayOf(8100.0, 1.8, 1.2, 4.1, 680.0, 2.4, 0.9, 0.04),
                "inactive" to doubleArrayOf(9200.0, 2.1, 1.4, 5.2, 820.0, 3.1, 1.2, 0.05)
            ),
            featureNames = listOf("molecular_weight", "logP", "hbd", "hba", "tpsa", "rot_bonds", "rings", "fsp3")
        )
    }

    val toxicityClassifier: NaiveBayesClassifier by lazy {
        NaiveBayesClassifier(
            name = "Toxicity Classifier (Cramer-enhanced)",
            classPriors = mapOf("class_I_low" to 0.48, "class_II_moderate" to 0.32, "class_III_high" to 0.20),
            means = mapOf(
                "class_I_low"       to doubleArrayOf(180.0, 0.8, 1.1, 3.2, 42.0, 2.1, 0.8),
                "class_II_moderate"  to doubleArrayOf(310.0, 2.4, 2.3, 5.1, 72.0, 4.2, 1.6),
                "class_III_high"     to doubleArrayOf(450.0, 4.1, 3.8, 7.8, 98.0, 6.1, 2.4)
            ),
            variances = mapOf(
                "class_I_low"       to doubleArrayOf(3200.0, 0.6, 0.8, 2.1, 320.0, 1.4, 0.4),
                "class_II_moderate"  to doubleArrayOf(6800.0, 1.2, 1.1, 3.4, 580.0, 2.2, 0.8),
                "class_III_high"     to doubleArrayOf(9400.0, 2.2, 1.8, 4.8, 920.0, 3.6, 1.4)
            ),
            featureNames = listOf("molecular_weight", "logP", "hbd", "hba", "tpsa", "rot_bonds", "aromatic_rings")
        )
    }

    val hergClassifier: NaiveBayesClassifier by lazy {
        NaiveBayesClassifier(
            name = "hERG Inhibition Classifier",
            classPriors = mapOf("inhibitor" to 0.23, "non-inhibitor" to 0.77),
            means = mapOf(
                "inhibitor"     to doubleArrayOf(3.8, 350.0, 1.2, 3.8, 2.1, 42.0),
                "non-inhibitor" to doubleArrayOf(1.4, 280.0, 1.9, 5.8, 0.8, 78.0)
            ),
            variances = mapOf(
                "inhibitor"     to doubleArrayOf(1.4, 7200.0, 0.9, 2.8, 1.6, 420.0),
                "non-inhibitor" to doubleArrayOf(1.8, 6100.0, 1.2, 3.4, 0.8, 580.0)
            ),
            featureNames = listOf("logP", "molecular_weight", "hbd", "hba", "aromatic_rings", "tpsa")
        )
    }
}

object FeatureExtractor {
    fun drugFeatures(mol: Molecule): DoubleArray {
        val d = MolDescriptors.calculate(mol)
        return doubleArrayOf(
            d.molecularWeight,
            d.logP,
            d.hBondDonors.toDouble(),
            d.hBondAcceptors.toDouble(),
            d.topologicalPolarSurfaceArea,
            d.numRotatableBonds.toDouble(),
            d.numRings.toDouble(),
            d.fractionCSP3
        )
    }

    fun toxicityFeatures(mol: Molecule): DoubleArray {
        val d = MolDescriptors.calculate(mol)
        return doubleArrayOf(
            d.molecularWeight,
            d.logP,
            d.hBondDonors.toDouble(),
            d.hBondAcceptors.toDouble(),
            d.topologicalPolarSurfaceArea,
            d.numRotatableBonds.toDouble(),
            d.numAromaticRings.toDouble()
        )
    }

    fun hergFeatures(mol: Molecule): DoubleArray {
        val d = MolDescriptors.calculate(mol)
        return doubleArrayOf(
            d.logP,
            d.molecularWeight,
            d.hBondDonors.toDouble(),
            d.hBondAcceptors.toDouble(),
            d.numAromaticRings.toDouble(),
            d.topologicalPolarSurfaceArea
        )
    }
}

object DrugMLPredictor {
    fun predictActivity(mol: Molecule): MLPrediction =
        PretrainedModels.drugActivityClassifier
            .predict(FeatureExtractor.drugFeatures(mol))

    fun predictToxicity(mol: Molecule): MLPrediction =
        PretrainedModels.toxicityClassifier
            .predict(FeatureExtractor.toxicityFeatures(mol))

    fun predictHerg(mol: Molecule): MLPrediction =
        PretrainedModels.hergClassifier
            .predict(FeatureExtractor.hergFeatures(mol))

    fun fullAnalysis(mol: Molecule): Map<String, MLPrediction> = mapOf(
        "activity"  to predictActivity(mol),
        "toxicity"  to predictToxicity(mol),
        "herg_risk" to predictHerg(mol)
    )
}
